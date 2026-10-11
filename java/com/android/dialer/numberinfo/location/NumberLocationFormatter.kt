/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

/**
 * Turns [RawGeo] into the one-line location shown in the call UI. Pure logic.
 *
 * Rules:
 *  - domestic (+86) with city-level data: "北京市 中国移动", "广东省深圳市"
 *  - domestic with country-level data only: carrier alone ("中国电信"), never the
 *    bare country name ("中国" tells the user nothing)
 *  - domestic with neither: null
 *  - foreign: the place name, plus the carrier when there is one
 *  - the two parts are never repeated ("北京 北京" becomes "北京")
 */
object NumberLocationFormatter {

    const val CN_CALLING_CODE = 86

    fun format(geo: RawGeo): String? {
        val place = geo.description?.trim()?.takeIf { it.isNotEmpty() }
        val carrier = geo.carrier?.trim()?.takeIf { it.isNotEmpty() }
        return if (geo.countryCallingCode == CN_CALLING_CODE) {
            if (place != null && !geo.countryLevelOnly) {
                joinDistinct(place, carrier)
            } else {
                carrier
            }
        } else {
            place?.let { joinDistinct(it, carrier) }
        }
    }

    internal fun joinDistinct(first: String, second: String?): String {
        if (second == null) return first
        if (first.contains(second)) return first
        if (second.contains(first)) return second
        return "$first $second"
    }
}
