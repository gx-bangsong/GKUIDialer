/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted user preferences for the harassment blocking (骚扰拦截) feature. */
public final class HarassmentBlockSettings {

  private static final String PREFS_NAME = "harassment_block_settings";
  private static final String KEY_ENABLED = "harassment_block_enabled";
  private static final String KEY_MODE = "harassment_block_mode";

  private HarassmentBlockSettings() {}

  private static SharedPreferences prefs(Context context) {
    return context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  public static boolean isEnabled(Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, false);
  }

  public static void setEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  public static HarassmentBlockMode getMode(Context context) {
    String value = prefs(context).getString(KEY_MODE, HarassmentBlockMode.BLACKLIST.name());
    try {
      return HarassmentBlockMode.valueOf(value);
    } catch (IllegalArgumentException e) {
      return HarassmentBlockMode.BLACKLIST;
    }
  }

  public static void setMode(Context context, HarassmentBlockMode mode) {
    prefs(context).edit().putString(KEY_MODE, mode.name()).apply();
  }
}
