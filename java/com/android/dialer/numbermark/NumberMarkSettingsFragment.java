/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.android.dialer.numbermark.cache.NumberMarkCacheDatabaseHelper;
import com.android.dialer.numbermark.rule.NumberMarkRuleRepository;

/** Settings UI for the number marking (号码标记) feature: enable/disable and choose sources. */
public class NumberMarkSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener {

  private static final String KEY_ENABLE = "enable_number_mark";
  private static final String KEY_SOURCE_PREFIX = "number_mark_source_";
  private static final String KEY_CLEAR_CACHE = "number_mark_clear_cache";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setStorageDeviceProtected();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    addPreferencesFromResource(R.xml.number_mark_settings);

    Context context = requireContext();

    SwitchPreferenceCompat enablePref = findPreference(KEY_ENABLE);
    if (enablePref != null) {
      enablePref.setChecked(NumberMarkSettings.isEnabled(context));
      enablePref.setOnPreferenceChangeListener(this);
    }

    for (NumberMarkSource source : NumberMarkSource.values()) {
      SwitchPreferenceCompat sourcePref = findPreference(KEY_SOURCE_PREFIX + source.getId());
      if (sourcePref != null) {
        sourcePref.setChecked(NumberMarkSettings.isSourceEnabled(context, source));
        sourcePref.setOnPreferenceChangeListener(this);
      }
    }

    Preference clearCache = findPreference(KEY_CLEAR_CACHE);
    if (clearCache != null) {
      clearCache.setOnPreferenceClickListener(
          preference -> {
            new NumberMarkCacheDatabaseHelper(context.getApplicationContext()).clear();
            NumberMarkRuleRepository.invalidateCache();
            Toast.makeText(context, R.string.number_mark_cache_cleared, Toast.LENGTH_SHORT)
                .show();
            return true;
          });
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    Context context = requireContext();
    String key = preference.getKey();
    boolean enabled = (Boolean) newValue;

    if (KEY_ENABLE.equals(key)) {
      NumberMarkSettings.setEnabled(context, enabled);
      return true;
    }

    for (NumberMarkSource source : NumberMarkSource.values()) {
      if ((KEY_SOURCE_PREFIX + source.getId()).equals(key)) {
        NumberMarkSettings.setSourceEnabled(context, source, enabled);
        return true;
      }
    }
    return true;
  }
}
