/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Persisted user preferences for the number mark (号码标记) feature. */
public final class NumberMarkSettings {

  private static final String PREFS_NAME = "number_mark_settings";
  private static final String KEY_ENABLED = "number_mark_enabled";
  private static final String KEY_SOURCES = "number_mark_sources";
  private static final String KEY_CACHE_TTL_HOURS = "number_mark_cache_ttl_hours";

  private static final int DEFAULT_CACHE_TTL_HOURS = 24 * 14;

  private NumberMarkSettings() {}

  private static SharedPreferences prefs(Context context) {
    return context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  public static boolean isEnabled(Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, true);
  }

  public static void setEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  /** Returns the set of {@link NumberMarkSource} IDs the user has enabled. All by default. */
  public static Set<String> getEnabledSourceIds(Context context) {
    Set<String> defaults = new HashSet<>();
    for (NumberMarkSource source : NumberMarkSource.values()) {
      defaults.add(source.getId());
    }
    return new HashSet<>(prefs(context).getStringSet(KEY_SOURCES, defaults));
  }

  public static void setEnabledSourceIds(Context context, Set<String> ids) {
    prefs(context).edit().putStringSet(KEY_SOURCES, ids).apply();
  }

  public static void setSourceEnabled(Context context, NumberMarkSource source, boolean enabled) {
    Set<String> ids = getEnabledSourceIds(context);
    if (enabled) {
      ids.add(source.getId());
    } else {
      ids.remove(source.getId());
    }
    setEnabledSourceIds(context, ids);
  }

  public static boolean isSourceEnabled(Context context, NumberMarkSource source) {
    return getEnabledSourceIds(context).contains(source.getId());
  }

  public static int getCacheTtlHours(Context context) {
    return prefs(context).getInt(KEY_CACHE_TTL_HOURS, DEFAULT_CACHE_TTL_HOURS);
  }

  public static void setCacheTtlHours(Context context, int hours) {
    prefs(context).edit().putInt(KEY_CACHE_TTL_HOURS, hours).apply();
  }
}
