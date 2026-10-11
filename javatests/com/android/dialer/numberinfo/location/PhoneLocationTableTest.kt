/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneLocationTableTest {

    @Test
    fun parse_skipsCommentsBlanksAndMalformedLines() {
        val table = PhoneLocationTable.parse(
            sequenceOf(
                "# header comment",
                "",
                "1380013,北京,北京,移动",
                "not,enough,fields",
                "abc,北京,北京,移动",
            ),
        )
        assertEquals(1, table.size)
        assertEquals("北京 · 移动", table.lookupMobile("13800130000"))
    }

    @Test
    fun parse_sortsUnorderedInput_soBinarySearchStillWorks() {
        val table = PhoneLocationTable.parse(
            sequenceOf(
                "1590000,北京,北京,移动",
                "1300000,山东,济南,联通",
                "1700000,广东,广州,电信",
            ),
        )
        assertEquals("山东济南 · 联通", table.lookupMobile("13000001234"))
        assertEquals("广东广州 · 电信", table.lookupMobile("17000001234"))
        assertEquals("北京 · 移动", table.lookupMobile("15900001234"))
    }

    @Test
    fun parse_duplicatePrefix_keepsFirst() {
        val table = PhoneLocationTable.parse(
            sequenceOf(
                "1380000,北京,北京,移动",
                "1380000,上海,上海,移动",
            ),
        )
        assertEquals(1, table.size)
        assertEquals("北京 · 移动", table.lookupMobile("13800001234"))
    }

    @Test
    fun lookupMobile_isExactOnSevenDigitPrefix() {
        val table = PhoneLocationTable.parse(sequenceOf("1380013,北京,北京,移动"))
        assertNull(table.lookupMobile("13800120000")) // different 7-digit prefix
        assertNull(table.lookupMobile("138001")) // too short
    }

    @Test
    fun formatLocation_citySameAsProvince_isNotDuplicated() {
        assertEquals("北京 · 移动", PhoneLocationTable.formatLocation("北京", "北京", "移动"))
    }

    @Test
    fun formatLocation_provinceAndCity_areConcatenated() {
        assertEquals("广东深圳 · 电信", PhoneLocationTable.formatLocation("广东", "深圳", "电信"))
    }

    @Test
    fun formatLocation_missingCarrier_omitsIt() {
        assertEquals("广东深圳", PhoneLocationTable.formatLocation("广东", "深圳", ""))
    }

    @Test
    fun formatLocation_emptyPlace_returnsCarrierOnly() {
        assertEquals("移动", PhoneLocationTable.formatLocation("", "", "移动"))
    }
}
