/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

/**
 * The three dimensions a {@link HarassmentBlockRule} can match against.
 */
public enum HarassmentBlockRuleType {
  /** Matches by number prefix / segment, e.g. "400", "95", "1069". */
  NUMBER_PREFIX,

  /** Matches by the number's geographic/carrier attribution (归属地), e.g. "北京", "!上海". */
  LOCATION,

  /** Matches by a {@link com.android.dialer.numbermark.NumberMarkType} from NumberMarkService. */
  MARK_TYPE
}
