/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark.rule;

import androidx.annotation.NonNull;

import com.android.dialer.numbermark.NumberMarkResult;

/**
 * Extracts a {@link NumberMarkResult} from the raw response body of a {@link
 * com.android.dialer.numbermark.NumberMarkSource}.
 *
 * <p>This is intentionally the *only* piece of code that needs to know anything about the
 * structure of a particular website's response. Everything else in the number-marking pipeline
 * (fetching, caching, aggregating across sources, and displaying results) only depends on this
 * interface, never on any given source's markup.
 *
 * <p>The default implementation ({@link KeywordExtractionRule}) is entirely data-driven: its
 * behavior is controlled by a JSON ruleset (see {@link NumberMarkRuleRepository}) rather than by
 * hard-coded parsing logic. When a source changes its page layout, only the JSON ruleset needs to
 * be updated (and can even be updated over-the-air via {@link NumberMarkRuleRepository} without an
 * app update) -- no code changes or app releases are required.
 */
public interface NumberMarkExtractionRule {

  /**
   * @param sourceId the {@link com.android.dialer.numbermark.NumberMarkSource#getId()} that
   *     produced {@code responseBody}.
   * @param number the phone number that was searched for.
   * @param responseBody the raw (HTML/text) response body returned by the source.
   * @return the extracted result, or {@link NumberMarkResult#EMPTY} if nothing could be parsed.
   */
  @NonNull
  NumberMarkResult extract(@NonNull String sourceId, @NonNull String number,
      @NonNull String responseBody);
}
