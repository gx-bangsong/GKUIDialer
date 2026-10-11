/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark.rule;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.dialer.numbermark.NumberMarkResult;
import com.android.dialer.numbermark.NumberMarkType;
import com.android.dialer.numbermark.rule.NumberMarkRuleRepository.NumberMarkRuleset;
import com.android.dialer.numbermark.rule.NumberMarkRuleRepository.SourceConfig;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default, fully data-driven {@link NumberMarkExtractionRule}.
 *
 * <p>Parsing works in three independent steps, each governed entirely by the JSON ruleset (see
 * {@link NumberMarkRuleRepository}):
 *
 * <ol>
 *   <li>Locate a short "snippet" of text near the mark information, using one of the source's
 *       {@code snippetPatterns} regexes (the first one that matches wins).
 *   <li>Classify the snippet by scanning it for keywords listed under {@code keywordMap}. This
 *       mapping is shared across all sources since the vocabulary used by 360/Baidu/Sogou/电话邦
 *       overlaps heavily (e.g. "广告", "诈骗", "骚扰").
 *   <li>Optionally extract a report count and location string via {@code countPattern} /
 *       {@code locationPattern}.
 * </ol>
 *
 * <p>None of these steps hard-code assumptions about a specific site's HTML structure -- if a
 * source changes its markup, only its regex patterns in the JSON ruleset need to be updated.
 */
public final class KeywordExtractionRule implements NumberMarkExtractionRule {

  private static final String TAG = "KeywordExtractionRule";

  @NonNull
  @Override
  public NumberMarkResult extract(
      @NonNull String sourceId, @NonNull String number, @NonNull String responseBody) {
    NumberMarkRuleset ruleset = ruleset();
    SourceConfig config = ruleset.sources.get(sourceId);
    if (config == null) {
      return NumberMarkResult.EMPTY;
    }

    String snippet = findSnippet(config, responseBody);
    if (TextUtils.isEmpty(snippet)) {
      return NumberMarkResult.EMPTY;
    }

    NumberMarkType type = classify(ruleset.keywordMap, snippet);
    int count = extractInt(config.countPattern, responseBody);
    String location = extractString(config.locationPattern, responseBody);

    return new NumberMarkResult(sourceId, type, snippet.trim(), count, location);
  }

  private NumberMarkRuleset ruleset() {
    // Lazily resolved via the repository; kept as a method (rather than constructor injection) so
    // this class has no Android Context dependency and stays trivially unit-testable given a
    // ruleset built in-memory.
    return currentRuleset;
  }

  // Set by NumberMarkComponent at composition time; defaults to an empty ruleset so this class
  // never throws NPEs before it's wired up.
  private static volatile NumberMarkRuleset currentRuleset =
      new NumberMarkRuleset(0, new java.util.HashMap<>(), new java.util.HashMap<>());

  public static void setRuleset(NumberMarkRuleset ruleset) {
    currentRuleset = ruleset;
  }

  private static String findSnippet(SourceConfig config, String responseBody) {
    for (String patternStr : config.snippetPatterns) {
      try {
        Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
          return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        }
      } catch (RuntimeException e) {
        Log.e(TAG, "Bad snippet pattern: " + patternStr, e);
      }
    }
    return null;
  }

  private static NumberMarkType classify(Map<String, String[]> keywordMap, String snippet) {
    for (Map.Entry<String, String[]> entry : keywordMap.entrySet()) {
      for (String keyword : entry.getValue()) {
        if (!TextUtils.isEmpty(keyword) && snippet.contains(keyword)) {
          try {
            return NumberMarkType.valueOf(entry.getKey());
          } catch (IllegalArgumentException e) {
            Log.e(TAG, "Unknown NumberMarkType in ruleset: " + entry.getKey());
          }
        }
      }
    }
    return NumberMarkType.OTHER;
  }

  private static int extractInt(String patternStr, String responseBody) {
    String value = extractString(patternStr, responseBody);
    if (TextUtils.isEmpty(value)) {
      return 0;
    }
    try {
      return Integer.parseInt(value.replaceAll("[^0-9]", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String extractString(String patternStr, String responseBody) {
    if (TextUtils.isEmpty(patternStr)) {
      return null;
    }
    try {
      Matcher matcher = Pattern.compile(patternStr, Pattern.DOTALL).matcher(responseBody);
      if (matcher.find()) {
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
      }
    } catch (RuntimeException e) {
      Log.e(TAG, "Bad pattern: " + patternStr, e);
    }
    return null;
  }
}
