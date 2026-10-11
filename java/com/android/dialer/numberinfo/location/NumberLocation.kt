/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import android.content.Context
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.NumberNormalizer
import java.util.Locale

/**
 * Computes the location of a number in a given language, offline.
 *
 * Source order:
 *  1. the bundled full mobile-prefix table, when one is installed (Chinese only)
 *  2. libphonenumber's geocoder and carrier mapper, in the requested locale
 *
 * Results are cached per (locale, number); the locale is part of the key so a
 * language switch never returns text from the other language. Blocking on the
 * first call (metadata load). Never call on the main thread.
 */
class NumberLocationService(
    private val geoSource: PhoneNumberGeoSource,
    /** Bundled table lookup for a normalized mobile number; null when absent. */
    private val bundledTable: (number: String) -> String? = { null },
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean =
            size > maxEntries
    }

    /**
     * @param number normalized number (see [NumberNormalizer])
     * @return location text, or null when there is nothing to show. Never throws.
     */
    fun describe(number: String, locale: Locale): String? {
        val key = locale.toLanguageTag() + "|" + number
        synchronized(lock) {
            if (cache.containsKey(key)) return cache[key]
        }
        val text = compute(number, locale)
        synchronized(lock) { cache[key] = text }
        return text
    }

    /**
     * For stored data (call log rows): compute from the number when it can be
     * parsed. Only when the number cannot be parsed at all is [fallback] returned,
     * so an unparseable entry keeps whatever was stored.
     */
    fun describeOrFallback(rawNumber: String?, locale: Locale, fallback: String?): String? {
        val number = NumberNormalizer.normalize(rawNumber) ?: return fallback
        if (!geoSource.parseable(number)) return fallback
        return describe(number, locale)
    }

    /** Entries currently cached (for tests). */
    internal fun cacheSize(): Int = synchronized(lock) { cache.size }

    private fun compute(number: String, locale: Locale): String? = try {
        val fromTable = if (locale.language == "zh") bundledTable(number) else null
        fromTable ?: geoSource.lookup(number, locale)?.let { NumberLocationFormatter.format(it) }
    } catch (e: Exception) {
        // Deliberately no number in the log.
        NumberInfoLog.w("number location lookup failed", e)
        null
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 512
    }
}

/** Java-friendly entry point. Blocking on first use; never call on the main thread. */
object NumberLocation {

    /**
     * @param rawNumber any number form; normalized here
     * @param fallback returned only when [rawNumber] cannot be parsed at all
     */
    @JvmStatic
    fun describe(
        context: Context,
        rawNumber: String?,
        locale: Locale,
        fallback: String?,
    ): String? = NumberInfoComponent.get(context).numberLocation()
        .describeOrFallback(rawNumber, locale, fallback)
}
