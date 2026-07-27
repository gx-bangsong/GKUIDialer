/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.ui

import android.content.Intent
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.android.dialer.callscreening.R
import com.android.dialer.callscreening.ScreeningComponent
import com.android.dialer.callscreening.record.BlockedCallsActivity
import com.android.dialer.callscreening.rule.ScreeningMode

/**
 * "骚扰拦截" settings page, hosted by DialerSettingsActivity. Preferences are
 * built in code because the backing store is ScreeningSettings (device-
 * protected SharedPreferences), not the default preference store.
 */
class ScreeningSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val settings = ScreeningComponent.get(context).settings()
        val screen = preferenceManager.createPreferenceScreen(context)

        // Mode selector
        screen.addPreference(ListPreference(context).apply {
            key = "screening_mode"
            title = getString(R.string.screening_mode_title)
            isPersistent = false
            entries = arrayOf(
                getString(R.string.screening_mode_off),
                getString(R.string.screening_mode_blacklist),
                getString(R.string.screening_mode_whitelist))
            entryValues = arrayOf(
                ScreeningMode.OFF.name,
                ScreeningMode.BLACKLIST.name,
                ScreeningMode.WHITELIST.name)
            value = settings.mode.name
            summary = modeSummary(settings.mode)
            isIconSpaceReserved = false
            setOnPreferenceChangeListener { pref, newValue ->
                val mode = ScreeningMode.fromId(newValue as String)
                settings.mode = mode
                pref.summary = modeSummary(mode)
                true
            }
        })

        // Rule management
        screen.addPreference(Preference(context).apply {
            title = getString(R.string.block_rules_title)
            isIconSpaceReserved = false
            intent = Intent(context, BlockRuleListActivity::class.java)
        })

        // Blocked call history
        screen.addPreference(Preference(context).apply {
            title = getString(R.string.blocked_calls_title)
            isIconSpaceReserved = false
            intent = Intent(context, BlockedCallsActivity::class.java)
        })

        // Skip missed-call notification for blocked calls
        screen.addPreference(SwitchPreferenceCompat(context).apply {
            key = "skip_notification"
            title = getString(R.string.screening_skip_notification_title)
            isPersistent = false
            isChecked = settings.skipCallNotification
            isIconSpaceReserved = false
            setOnPreferenceChangeListener { _, newValue ->
                settings.skipCallNotification = newValue as Boolean
                true
            }
        })

        preferenceScreen = screen
    }

    private fun modeSummary(mode: ScreeningMode): String = getString(
        when (mode) {
            ScreeningMode.OFF -> R.string.screening_mode_off
            ScreeningMode.BLACKLIST -> R.string.screening_mode_blacklist_summary
            ScreeningMode.WHITELIST -> R.string.screening_mode_whitelist_summary
        })
}
