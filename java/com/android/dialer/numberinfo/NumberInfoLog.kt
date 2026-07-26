/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import android.util.Log

/**
 * Central log gate for the numberinfo stack.
 *
 * Requirements it enforces:
 *  - phone numbers are always masked ([NumberNormalizer.maskForLog]);
 *  - rule/source failures are logged with enough context ("which source, which
 *    field, why") to diagnose a broken parse rule from a bugreport.
 */
object NumberInfoLog {
    private const val TAG = "DialerNumberInfo"

    @JvmStatic
    fun d(msg: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, msg)
    }

    @JvmStatic
    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    @JvmStatic
    fun w(msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
    }

    /** Structured "rule failed" record: source + field + reason, number masked. */
    @JvmStatic
    fun ruleFailure(sourceId: String, field: String?, number: String?, reason: String) {
        Log.w(TAG, "rule-failure source=$sourceId field=${field ?: "-"}"
                + " number=${NumberNormalizer.maskForLog(number)} reason=$reason")
    }
}
