/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import androidx.annotation.NonNull;

/** Common Chinese phone number segments (号段) offered as quick-input chips in the add-rule UI. */
public final class CommonNumberPrefixes {

  private CommonNumberPrefixes() {}

  @NonNull
  public static final String[] PREFIXES =
      new String[] {
        "400", "95", "955", "96", "1069", "170", "171", "165", "162", "167", "144", "173", "178"
      };
}
