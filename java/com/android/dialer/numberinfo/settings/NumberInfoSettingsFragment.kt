/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.settings

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.R
import com.android.dialer.numberinfo.Sources
import com.android.dialer.numberinfo.rules.RuleUpdateResult
import java.util.concurrent.Executors

/**
 * "号码识别与黄页" settings page. Hosts the online-lookup privacy switch (default
 * OFF, explicit confirmation required to enable), one switch per online source,
 * and a manual rule refresh. Local features (offline yellow pages, geo, manual
 * marks) have no switch because nothing leaves the device.
 */
class NumberInfoSettingsFragment : PreferenceFragmentCompat() {

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val component = NumberInfoComponent.get(context)
        val settings = component.settings()
        val screen = preferenceManager.createPreferenceScreen(context)

        val onlineSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_ONLINE_ENABLED
            title = getString(R.string.numberinfo_online_lookup_title)
            summary = getString(R.string.numberinfo_online_lookup_summary)
            isPersistent = false
            isChecked = settings.onlineLookupEnabled
            isIconSpaceReserved = false
        }
        screen.addPreference(onlineSwitch)

        val sourcesCategory = PreferenceCategory(context).apply {
            title = getString(R.string.numberinfo_sources_category)
            isIconSpaceReserved = false
        }
        screen.addPreference(sourcesCategory)

        val sourcePrefs = ArrayList<SwitchPreferenceCompat>()
        for ((sourceId, titleRes) in ONLINE_SOURCES) {
            sourcePrefs.add(SwitchPreferenceCompat(context).apply {
                key = KEY_SOURCE_PREFIX + sourceId
                title = getString(titleRes)
                isPersistent = false
                isChecked = settings.isSourceEnabled(sourceId)
                // Per-source switches only matter while the master switch is on.
                isEnabled = settings.onlineLookupEnabled
                isIconSpaceReserved = false
                setOnPreferenceChangeListener { _, newValue ->
                    settings.setSourceEnabled(sourceId, newValue as Boolean)
                    true
                }
            })
        }
        sourcePrefs.forEach { sourcesCategory.addPreference(it) }

        onlineSwitch.setOnPreferenceChangeListener { _, newValue ->
            val enable = newValue as Boolean
            if (!enable) {
                settings.onlineLookupEnabled = false
                sourcePrefs.forEach { it.isEnabled = false }
                return@setOnPreferenceChangeListener true
            }
            // Opt-in requires an explicit confirmation; the switch only flips on "Enable".
            AlertDialog.Builder(context)
                .setTitle(R.string.numberinfo_online_confirm_title)
                .setMessage(R.string.numberinfo_online_confirm_message)
                .setPositiveButton(R.string.numberinfo_online_confirm_positive) { _, _ ->
                    settings.onlineLookupEnabled = true
                    onlineSwitch.isChecked = true
                    sourcePrefs.forEach { it.isEnabled = true }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            false // do not flip until the user confirms
        }

        val updatePref = Preference(context).apply {
            key = KEY_UPDATE_RULES
            title = getString(R.string.numberinfo_update_rules_title)
            isIconSpaceReserved = false
            // Read the rule file off the main thread; the item stays disabled until
            // we know an update source is configured.
            isEnabled = false
            setOnPreferenceClickListener {
                updateRules(component)
                true
            }
        }
        screen.addPreference(updatePref)
        worker.execute {
            val configured = !component.ruleRepository().get().ruleUpdateUrl.isNullOrBlank()
            mainHandler.post {
                if (isAdded) {
                    updatePref.isEnabled = configured
                    if (!configured) {
                        updatePref.summary = getString(R.string.numberinfo_update_rules_not_configured)
                    }
                }
            }
        }

        screen.addPreference(Preference(context).apply {
            key = KEY_CLEAR_CACHE
            title = getString(R.string.numberinfo_clear_cache_title)
            summary = getString(R.string.numberinfo_clear_cache_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                worker.execute {
                    // Deletes online results only; the user's own marks are a separate table.
                    component.repository().clearOnlineCache()
                    mainHandler.post {
                        if (isAdded) {
                            Toast.makeText(context, R.string.numberinfo_cache_cleared,
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
        })

        preferenceScreen = screen
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    /** Blocking download + validation runs off the main thread; result is posted back. */
    private fun updateRules(component: NumberInfoComponent) {
        worker.execute {
            val message = try {
                when (component.ruleRepository().updateFromRemote()) {
                    RuleUpdateResult.Updated -> R.string.numberinfo_update_rules_success
                    RuleUpdateResult.UpToDate -> R.string.numberinfo_update_rules_unchanged
                    RuleUpdateResult.NotConfigured ->
                        R.string.numberinfo_update_rules_not_configured
                    is RuleUpdateResult.Failed -> R.string.numberinfo_update_rules_failure
                }
            } catch (e: Exception) {
                NumberInfoLog.w("rule update failed", e)
                R.string.numberinfo_update_rules_failure
            }
            mainHandler.post {
                if (isAdded) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    companion object {
        private const val KEY_ONLINE_ENABLED = "numberinfo_online_enabled"
        private const val KEY_SOURCE_PREFIX = "numberinfo_source_"
        private const val KEY_UPDATE_RULES = "numberinfo_update_rules"
        private const val KEY_CLEAR_CACHE = "numberinfo_clear_cache"

        /** Online sources exposed as switches; ids must match rules.json. */
        private val ONLINE_SOURCES = listOf(
            Sources.SO360 to R.string.numberinfo_source_so360,
            Sources.BAIDU to R.string.numberinfo_source_baidu,
            Sources.SOGOU to R.string.numberinfo_source_sogou,
            Sources.HAOMA to R.string.numberinfo_source_haoma,
        )
    }
}
