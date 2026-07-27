/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import android.content.Context
import android.content.SharedPreferences
import com.android.dialer.callscreening.rule.ScreeningMode

/** Screening configuration. Interface + impl split mirrors NumberInfoSettings. */
interface ScreeningSettings {
    /** Global mode; OFF by default (screening is opt-in, docs §6). */
    var mode: ScreeningMode

    /** Minimum online markCount before a MARK_TYPE rule may block. */
    var markCountThreshold: Int

    /** Also hide the missed-call notification for blocked calls. Default true. */
    var skipCallNotification: Boolean

    /** Also drop blocked calls from the system call log. Default false —
     *  we keep the system log and show our own blocked list on top. */
    var skipCallLog: Boolean
}

class ScreeningSettingsImpl(context: Context) : ScreeningSettings {

    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var mode: ScreeningMode
        get() = ScreeningMode.fromId(prefs.getString(KEY_MODE, ScreeningMode.OFF.name))
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    override var markCountThreshold: Int
        get() = prefs.getInt(KEY_MARK_THRESHOLD, DEFAULT_MARK_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_MARK_THRESHOLD, value).apply()

    override var skipCallNotification: Boolean
        get() = prefs.getBoolean(KEY_SKIP_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_NOTIFICATION, value).apply()

    override var skipCallLog: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CALL_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CALL_LOG, value).apply()

    companion object {
        private const val PREFS_NAME = "callscreening_settings"
        private const val KEY_MODE = "screening_mode"
        private const val KEY_MARK_THRESHOLD = "mark_count_threshold"
        private const val KEY_SKIP_NOTIFICATION = "skip_call_notification"
        private const val KEY_SKIP_CALL_LOG = "skip_call_log"

        /** A handful of malicious marks shouldn't block a reused number. */
        const val DEFAULT_MARK_THRESHOLD = 10
    }
}
