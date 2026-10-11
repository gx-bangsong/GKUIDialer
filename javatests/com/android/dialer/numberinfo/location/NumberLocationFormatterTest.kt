/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberLocationFormatterTest {

    private fun cn(description: String?, carrier: String?, countryLevelOnly: Boolean = false) =
        RawGeo(
            regionCode = "CN", countryCallingCode = 86, description = description,
            carrier = carrier, countryLevelOnly = countryLevelOnly)

    @Test
    fun domesticCityLevel_showsPlaceAndCarrier() {
        val text = NumberLocationFormatter.format(cn("北京市", "中国移动"))!!
        assertEquals("北京市 中国移动", text)
    }

    @Test
    fun domesticLandline_hasPlaceWithoutCarrier() {
        assertEquals("广东省深圳市", NumberLocationFormatter.format(cn("广东省深圳市", null)))
    }

    @Test
    fun domesticCountryLevelOnly_showsCarrierNotTheCountryName() {
        // 19x numbers: libphonenumber only knows the country, the carrier is still known.
        assertEquals("中国电信",
            NumberLocationFormatter.format(cn("中国", "中国电信", countryLevelOnly = true)))
    }

    @Test
    fun domesticNothingKnown_isNull_notTheCountryName() {
        assertNull(NumberLocationFormatter.format(cn("中国", null, countryLevelOnly = true)))
        assertNull(NumberLocationFormatter.format(cn(null, null)))
    }

    @Test
    fun foreign_showsCountryNameAndCarrierIfAny() {
        val singapore = RawGeo(
            regionCode = "SG", countryCallingCode = 65, description = "新加坡",
            carrier = null, countryLevelOnly = true)
        assertEquals("新加坡", NumberLocationFormatter.format(singapore))
    }

    @Test
    fun foreign_nothingKnown_isNull() {
        val unknown = RawGeo(
            regionCode = "ZZ", countryCallingCode = 999, description = null,
            carrier = null, countryLevelOnly = true)
        assertNull(NumberLocationFormatter.format(unknown))
    }

    @Test
    fun partsAreNeverRepeated() {
        assertEquals("北京", NumberLocationFormatter.joinDistinct("北京", "北京"))
        assertEquals("广东移动", NumberLocationFormatter.joinDistinct("广东", "广东移动"))
        assertEquals("广东移动", NumberLocationFormatter.joinDistinct("广东移动", "广东"))
        assertEquals("北京市 中国移动", NumberLocationFormatter.joinDistinct("北京市", "中国移动"))
    }

    @Test
    fun domesticPlaceEqualToCarrier_isNotDuplicated() {
        assertEquals("中国电信", NumberLocationFormatter.format(cn("中国电信", "中国电信")))
    }
}
