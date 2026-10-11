/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import android.content.Context
import com.android.dialer.numberinfo.NumberInfoLog

/**
 * Offline CN geo/carrier lookup.
 *
 * Mobile numbers: exact 7-digit prefix lookup in the bundled CSV.
 *   Data file: numberinfo/phone_location.csv asset (UTF-8, one record per line)
 *     prefix7,province,city,carrier        e.g. "1380013,北京,北京,移动"
 *   Lines starting with '#' and blank lines are ignored. The table is sorted
 *   on load, so the file does not need to be ordered.
 *
 * Landlines: area-code fallback from a small built-in map (3-digit codes
 * first, then 4-digit codes such as 0755).
 *
 * The parsing/lookup core lives in [PhoneLocationTable] so it is unit-testable
 * on a plain JVM; this class only handles the asset I/O and lazy loading.
 */
class NumberLocationLoader(private val context: Context) {

    @Volatile
    private var table: PhoneLocationTable? = null
    private val lock = Any()

    /** @param number normalized number; @return "广东深圳 移动" style or null. */
    fun get(number: String): String? {
        if (number.length < 3 || !number.all { it in '0'..'9' }) return null
        return when {
            number.length >= 11 && number[0] == '1' && number[1] in '3'..'9' ->
                loadTable()?.lookupMobile(number)
            number[0] == '0' -> PhoneLocationTable.lookupAreaCode(number)
            else -> null
        }
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

        /** Built-in landline fallback: "北京 固话" style. */
        private val AREA_CODES = mapOf(
            "010" to "北京 固话", "021" to "上海 固话", "022" to "天津 固话",
            "023" to "重庆 固话", "020" to "广州 固话", "025" to "南京 固话",
            "027" to "武汉 固话", "028" to "成都 固话", "029" to "西安 固话",
            "0755" to "深圳 固话", "0571" to "杭州 固话", "0574" to "宁波 固话",
            "0512" to "苏州 固话", "0531" to "济南 固话", "0532" to "青岛 固话",
            "0551" to "合肥 固话", "0591" to "福州 固话", "0592" to "厦门 固话",
            "0731" to "长沙 固话", "0771" to "南宁 固话", "0851" to "贵阳 固话",
            "0871" to "昆明 固话", "0931" to "兰州 固话", "0951" to "银川 固话",
            "0971" to "西宁 固话", "0991" to "乌鲁木齐 固话", "0891" to "拉萨 固话",
            "0311" to "石家庄 固话", "0351" to "太原 固话", "0371" to "郑州 固话",
            "0431" to "长春 固话", "0451" to "哈尔滨 固话", "024" to "沈阳 固话",
            "0471" to "呼和浩特 固话", "0898" to "海口 固话", "0791" to "南昌 固话",
        )

        /** Landline lookup: 3-digit area code first, then 4-digit. */
        fun lookupAreaCode(number: String): String? {
            if (number.length < 3 || number[0] != '0') return null
            AREA_CODES[number.take(3)]?.let { return it }
            AREA_CODES[number.take(4)]?.let { return it }
            return null
        }

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
                else -> "$place $carrierName"
            }
        }
    }
}
