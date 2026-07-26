/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Privacy switches and tuning for the numberinfo stack. Interface + impl split
 * keeps ProviderManager/Repository unit-testable on a plain JVM (tests supply
 * a trivial fake instead of mocking SharedPreferences).
 */
interface NumberInfoSettings {
    /** Master switch for all online lookups. Default OFF (requires opt-in). */
    var onlineLookupEnabled: Boolean

    /** Per-source switch; defaults ON but only effective when the master is ON. */
    fun isSourceEnabled(sourceId: String): Boolean
    fun setSourceEnabled(sourceId: String, enabled: Boolean)

    /** Effective check used by ProviderManager for online providers. */
    fun isOnlineSourceAllowed(sourceId: String): Boolean =
        onlineLookupEnabled && isSourceEnabled(sourceId)

    /** Cache TTL for positive results. */
    var cacheTtlMillis: Long

    /** Negative results ("no data") are retried sooner. */
    var negativeCacheTtlMillis: Long

    companion object {
        const val DEFAULT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000 // 7 days
        const val DEFAULT_NEGATIVE_TTL_MILLIS = 24L * 60 * 60 * 1000 // 1 day
    }
}

/**
 * SharedPreferences-backed implementation. Uses device-protected storage so
 * screening decisions work before first unlock, matching LookupSettings.
 *
 * Defaults follow the compliance policy in docs/numberinfo-architecture.md §6:
 * online lookup is OFF until the user explicitly opts in; purely-local features
 * (offline yellow pages, manual marks, geo db) have no switch because they
 * never leave the device.
 */
class NumberInfoSettingsImpl(context: Context) : NumberInfoSettings {

    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var onlineLookupEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE_ENABLED, value).apply()

    override fun isSourceEnabled(sourceId: String): Boolean =
        prefs.getBoolean(KEY_SOURCE_PREFIX + sourceId, true)

    override fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOURCE_PREFIX + sourceId, enabled).apply()
    }

    override var cacheTtlMillis: Long
        get() = prefs.getLong(KEY_CACHE_TTL, NumberInfoSettings.DEFAULT_TTL_MILLIS)
        set(value) = prefs.edit().putLong(KEY_CACHE_TTL, value).apply()

    override var negativeCacheTtlMillis: Long
        get() = prefs.getLong(
            KEY_NEGATIVE_TTL, NumberInfoSettings.DEFAULT_NEGATIVE_TTL_MILLIS)
        set(value) = prefs.edit().putLong(KEY_NEGATIVE_TTL, value).apply()

    companion object {
        private const val PREFS_NAME = "numberinfo_settings"
        private const val KEY_ONLINE_ENABLED = "online_lookup_enabled"
        private const val KEY_SOURCE_PREFIX = "source_enabled_"
        private const val KEY_CACHE_TTL = "cache_ttl_millis"
        private const val KEY_NEGATIVE_TTL = "negative_cache_ttl_millis"
    }
}
