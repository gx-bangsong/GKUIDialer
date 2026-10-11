package com.android.dialer.numberinfo.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(com.android.dialer.R.xml.preferences_root, rootKey)
    }
}
