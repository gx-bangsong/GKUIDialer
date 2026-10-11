/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.numbermark.NumberMarkType;

import java.util.List;

/**
 * Pure decision logic for the harassment-blocking (骚扰拦截) feature.
 *
 * <p>Deliberately has no Android framework or I/O dependencies so it can be unit tested and reused
 * both from the real-time call screening path ({@link
 * com.android.incallui.harassmentblock.HarassmentBlockCallListener}) and from the settings UI
 * (e.g. to preview whether a rule would match a given number).
 */
public final class HarassmentBlockEvaluator {

  private HarassmentBlockEvaluator() {}

  /** Result of evaluating a call against the current harassment-block configuration. */
  public static final class Decision {
    public final boolean block;
    @Nullable public final HarassmentBlockRule matchedRule;

    Decision(boolean block, @Nullable HarassmentBlockRule matchedRule) {
      this.block = block;
      this.matchedRule = matchedRule;
    }

    static Decision allow() {
      return new Decision(false, null);
    }

    static Decision block(HarassmentBlockRule rule) {
      return new Decision(true, rule);
    }
  }

  /**
   * @param number the raw dialed/incoming number.
   * @param location the number's geo/carrier attribution description, e.g. "北京 移动", or null.
   * @param markType the {@link NumberMarkType} reported by NumberMarkService, or null/UNMARKED.
   * @param isKnownContact whether the number belongs to a saved local contact.
   * @param mode the active blocking mode (blacklist or whitelist).
   * @param rules only rules matching {@code mode} and with {@link HarassmentBlockRule#isEnabled()}
   *     are considered.
   */
  @NonNull
  public static Decision evaluate(
      @NonNull String number,
      @Nullable String location,
      @Nullable NumberMarkType markType,
      boolean isKnownContact,
      @NonNull HarassmentBlockMode mode,
      @NonNull List<HarassmentBlockRule> rules) {

    if (mode == HarassmentBlockMode.WHITELIST) {
      if (isKnownContact) {
        return Decision.allow();
      }
      for (HarassmentBlockRule rule : rules) {
        if (rule.isEnabled()
            && rule.getMode() == HarassmentBlockMode.WHITELIST
            && matches(rule, number, location, markType)) {
          // Whitelist rule matched: explicitly allow.
          return Decision.allow();
        }
      }
      // No whitelist rule (and not a contact): block everything else.
      return new Decision(true, null);
    }

    // BLACKLIST mode: block only if a blacklist rule matches.
    for (HarassmentBlockRule rule : rules) {
      if (rule.isEnabled()
          && rule.getMode() == HarassmentBlockMode.BLACKLIST
          && matches(rule, number, location, markType)) {
        return Decision.block(rule);
      }
    }
    return Decision.allow();
  }

  private static boolean matches(
      HarassmentBlockRule rule,
      String number,
      @Nullable String location,
      @Nullable NumberMarkType markType) {
    boolean matched;
    switch (rule.getType()) {
      case NUMBER_PREFIX:
        matched = number != null && number.startsWith(rule.getMatchValue());
        break;
      case LOCATION:
        matched =
            !TextUtils.isEmpty(location) && location.contains(rule.getMatchValue());
        break;
      case MARK_TYPE:
        matched = markType != null && markType == rule.getMarkType();
        break;
      default:
        matched = false;
    }
    return rule.isInverted() != matched;
  }
}
