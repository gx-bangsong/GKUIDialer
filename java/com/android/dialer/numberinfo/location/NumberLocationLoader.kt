/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import android.content.Context
import com.android.dialer.numberinfo.NumberInfoLog

/**
 * Optional full mobile-prefix table, loaded from an asset when one is licensed
 * and bundled. Without it (the default: no asset) [get] returns null and
 * [NumberLocationService] falls back to libphonenumber.
 *   Asset: numberinfo/phone_location.csv (UTF-8), one record per line:
 *     prefix7,province,city,carrier        e.g. "1380013,北京,北京,移动"
 *   Lines starting with '#' and blank lines are ignored; the table is sorted on
 *   load. Only exact 7-digit mobile prefixes are covered by this table.
 *
 * The parsing/lookup core lives in [PhoneLocationTable] so it is unit-testable
 * on a plain JVM; this class only handles the asset I/O and lazy loading.
 */
class NumberLocationLoader(private val context: Context) {

    @Volatile
    private var table: PhoneLocationTable? = null
    private val lock = Any()

    /** @param number normalized number; @return "北京 移动" style or null (mobile only). */
    fun get(number: String): String? {
        if (number.length < 11 || number[0] != '1' || number[1] !in '3'..'9') return null
        if (!number.all { it in '0'..'9' }) return null
        return loadTable()?.lookupMobile(number)
    }

    private fun loadTable(): PhoneLocationTable? {
        table?.let { return it }
        synchronized(lock) {
            table?.let { return it }
            return try {
                context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    PhoneLocationTable.parse(lines)
                }.also { table = it }
            } catch (e: Exception) {
                NumberInfoLog.d("phone location table unavailable: $e")
                null
            }
        }
    }

    companion object {
        const val ASSET_PATH = "numberinfo/phone_location.csv"
    }
}

/** Pure, Android-free core of [NumberLocationLoader]. */
class PhoneLocationTable private constructor(
    private val prefixes: IntArray,
    private val locations: Array<String>,
) {
    val size: Int get() = prefixes.size

    /** Exact 7-digit prefix match via binary search; null when absent. */
    fun lookupMobile(number: String): String? {
        if (number.length < PREFIX_LEN) return null
        val key = number.substring(0, PREFIX_LEN).toIntOrNull() ?: return null
        var lo = 0
        var hi = prefixes.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                prefixes[mid] < key -> lo = mid + 1
                prefixes[mid] > key -> hi = mid - 1
                else -> return locations[mid]
            }
        }
        return null
    }

    companion object {
        private const val PREFIX_LEN = 7

        /**
         * Parses CSV lines `prefix7,province,city,carrier`. Malformed lines are
         * skipped (never throws); result is sorted by prefix. Duplicate prefixes
         * keep the first occurrence.
         */
        fun parse(lines: Sequence<String>): PhoneLocationTable {
            val rows = ArrayList<Pair<Int, String>>(4096)
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(',')
                if (parts.size < 4) continue
                val prefix = parts[0].trim().toIntOrNull() ?: continue
                rows.add(prefix to formatLocation(parts[1], parts[2], parts[3]))
            }
            rows.sortBy { it.first }
            val dedup = ArrayList<Pair<Int, String>>(rows.size)
            for (row in rows) {
                if (dedup.isEmpty() || dedup.last().first != row.first) dedup.add(row)
            }
            return PhoneLocationTable(
                IntArray(dedup.size) { dedup[it].first },
                Array(dedup.size) { dedup[it].second })
        }

        /** "北京","北京","移动" -> "北京 移动"; "广东","深圳","电信" -> "广东深圳 电信". */
        fun formatLocation(province: String, city: String, carrier: String): String {
            val p = province.trim()
            val c = city.trim()
            val carrierName = carrier.trim()
            val place = if (p == c || c.isEmpty()) p.ifEmpty { c } else p + c
            return when {
                place.isEmpty() -> carrierName
                carrierName.isEmpty() -> place
                else -> place + NumberLocationFormatter.SEPARATOR + carrierName
            }
        }
    }
}
