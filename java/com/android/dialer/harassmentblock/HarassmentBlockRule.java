/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A single blocking/allowing rule created by the user in the harassment-blocking (骚扰拦截) UI.
 *
 * <p>A rule always has a {@link #type}, which controls how {@link #value} is interpreted:
 *
 * <ul>
 *   <li>{@link HarassmentBlockRuleType#NUMBER_PREFIX}: {@code value} is a leading digit sequence
 *       (e.g. "400"). Prefixing {@code value} with "!" inverts the match (matches any number NOT
 *       starting with the given prefix), matching the common "反向匹配" convention.
 *   <li>{@link HarassmentBlockRuleType#LOCATION}: {@code value} is a substring to match against
 *       the number's geo-description (e.g. "北京"). Also supports a leading "!" to invert.
 *   <li>{@link HarassmentBlockRuleType#MARK_TYPE}: {@code value} is the {@code name()} of a
 *       {@link com.android.dialer.numbermark.NumberMarkType}.
 * </ul>
 */
public final class HarassmentBlockRule {

  private final long id;
  @NonNull private final HarassmentBlockRuleType type;
  @NonNull private final String value;
  @NonNull private final HarassmentBlockMode mode;
  private final boolean enabled;

  public HarassmentBlockRule(
      long id,
      @NonNull HarassmentBlockRuleType type,
      @NonNull String value,
      @NonNull HarassmentBlockMode mode,
      boolean enabled) {
    this.id = id;
    this.type = type;
    this.value = value;
    this.mode = mode;
    this.enabled = enabled;
  }

  public long getId() {
    return id;
  }

  @NonNull
  public HarassmentBlockRuleType getType() {
    return type;
  }

  @NonNull
  public String getValue() {
    return value;
  }

  public boolean isInverted() {
    return value.startsWith("!");
  }

  /** Returns {@link #getValue()} with any leading "!" inversion marker stripped. */
  @NonNull
  public String getMatchValue() {
    return isInverted() ? value.substring(1) : value;
  }

  @NonNull
  public HarassmentBlockMode getMode() {
    return mode;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Nullable
  public com.android.dialer.numbermark.NumberMarkType getMarkType() {
    if (type != HarassmentBlockRuleType.MARK_TYPE) {
      return null;
    }
    try {
      return com.android.dialer.numbermark.NumberMarkType.valueOf(getMatchValue());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
