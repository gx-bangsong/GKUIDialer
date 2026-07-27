/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.dialer.numbermark.cache.NumberMarkCacheDatabaseHelper;
import com.android.dialer.numbermark.impl.HttpNumberMarkFetcher;
import com.android.dialer.numbermark.rule.KeywordExtractionRule;
import com.android.dialer.numbermark.rule.NumberMarkExtractionRule;
import com.android.dialer.numbermark.rule.NumberMarkRuleRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level entry point for the number marking (号码标记) feature.
 *
 * <p>This is the only class most callers (call log, incall UI, harassment blocking) need to talk
 * to. It orchestrates, per configured {@link NumberMarkSource}:
 *
 * <ol>
 *   <li>Checking the local cache ({@link NumberMarkCacheDatabaseHelper}).
 *   <li>Fetching fresh data via {@link NumberMarkFetcher} on a cache miss.
 *   <li>Delegating parsing to {@link NumberMarkExtractionRule}, which is fully decoupled from the
 *       networking/caching logic here -- see that interface's docs for why.
 * </ol>
 *
 * and then aggregates all sources' results into one final {@link NumberMarkResult} by majority
 * vote (falling back to the highest report count as a tiebreaker).
 *
 * <p>This class performs blocking I/O and must be called from a background thread.
 */
@WorkerThread
public final class NumberMarkService {

  private static final String TAG = "NumberMarkService";

  private final Context appContext;
  private final NumberMarkFetcher fetcher;
  private final NumberMarkExtractionRule extractionRule;
  private final NumberMarkCacheDatabaseHelper cache;

  public NumberMarkService(Context context) {
    this(
        context,
        new HttpNumberMarkFetcher(),
        new KeywordExtractionRule(),
        new NumberMarkCacheDatabaseHelper(context.getApplicationContext()));
  }

  /** Visible for testing; allows injecting fakes for the fetcher/rule/cache. */
  public NumberMarkService(
      Context context,
      NumberMarkFetcher fetcher,
      NumberMarkExtractionRule extractionRule,
      NumberMarkCacheDatabaseHelper cache) {
    this.appContext = context.getApplicationContext();
    this.fetcher = fetcher;
    this.extractionRule = extractionRule;
    this.cache = cache;
  }

  /**
   * Looks up the mark information for {@code number}, using cached data when available and
   * falling back to live fetches from all user-enabled {@link NumberMarkSource}s otherwise.
   *
   * @return the aggregated result across sources, or {@link NumberMarkResult#EMPTY} if nothing is
   *     available or the feature is disabled.
   */
  @NonNull
  public NumberMarkResult lookup(@NonNull String number) {
    if (!NumberMarkSettings.isEnabled(appContext) || TextUtils.isEmpty(number)) {
      return NumberMarkResult.EMPTY;
    }

    // Make sure the extraction rule is using the latest ruleset (bundled or remotely updated).
    KeywordExtractionRule.setRuleset(NumberMarkRuleRepository.getRuleset(appContext));

    Set<String> enabledSourceIds = NumberMarkSettings.getEnabledSourceIds(appContext);
    int ttlHours = NumberMarkSettings.getCacheTtlHours(appContext);

    List<NumberMarkResult> results = new ArrayList<>();
    List<NumberMarkResult> cached = cache.get(number, ttlHours);
    Set<String> alreadyHave = new java.util.HashSet<>();
    for (NumberMarkResult result : cached) {
      if (enabledSourceIds.contains(result.getSourceId())) {
        results.add(result);
        alreadyHave.add(result.getSourceId());
      }
    }

    for (NumberMarkSource source : NumberMarkSource.values()) {
      if (!enabledSourceIds.contains(source.getId()) || alreadyHave.contains(source.getId())) {
        continue;
      }
      NumberMarkResult result = fetchAndExtract(source, number);
      if (result != null) {
        cache.put(number, result);
        results.add(result);
      }
    }

    return aggregate(results);
  }

  /**
   * Cache-only variant of {@link #lookup(String)}, intended for latency-sensitive callers (e.g.
   * real-time incoming call screening) that cannot afford to block on a network fetch. Returns
   * {@link NumberMarkResult#EMPTY} if nothing is cached yet; a background {@link #lookup(String)}
   * call can be used to warm the cache for next time.
   */
  @NonNull
  public NumberMarkResult lookupCachedOnly(@NonNull String number) {
    if (!NumberMarkSettings.isEnabled(appContext) || TextUtils.isEmpty(number)) {
      return NumberMarkResult.EMPTY;
    }
    Set<String> enabledSourceIds = NumberMarkSettings.getEnabledSourceIds(appContext);
    int ttlHours = NumberMarkSettings.getCacheTtlHours(appContext);
    List<NumberMarkResult> results = new ArrayList<>();
    for (NumberMarkResult result : cache.get(number, ttlHours)) {
      if (enabledSourceIds.contains(result.getSourceId())) {
        results.add(result);
      }
    }
    return aggregate(results);
  }

  @Nullable
  private NumberMarkResult fetchAndExtract(NumberMarkSource source, String number) {
    try {
      String body = fetcher.fetch(source, number);
      return extractionRule.extract(source.getId(), number, body);
    } catch (IOException e) {
      Log.w(TAG, "Failed to fetch number mark from " + source.getId(), e);
      return null;
    } catch (RuntimeException e) {
      // Defensive: a source's markup can change unexpectedly, but a parsing failure for one
      // source must never break lookups against the others.
      Log.e(TAG, "Failed to parse number mark from " + source.getId(), e);
      return null;
    }
  }

  /** Combines multiple sources' results via majority vote on {@link NumberMarkType}. */
  @NonNull
  private static NumberMarkResult aggregate(List<NumberMarkResult> results) {
    Map<NumberMarkType, Integer> votes = new EnumMap<>(NumberMarkType.class);
    NumberMarkResult best = null;
    int bestScore = -1;

    for (NumberMarkResult result : results) {
      if (result == null || result.isEmpty()) {
        continue;
      }
      int votesForType = votes.merge(result.getType(), 1, Integer::sum);
      int score = votesForType * 1000 + result.getMarkCount();
      if (score > bestScore) {
        bestScore = score;
        best = result;
      }
    }
    return best != null ? best : NumberMarkResult.EMPTY;
  }
}
