/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizerTest {

    @Test
    fun normalize_plainMobile() {
        assertEquals("13800138000", NumberNormalizer.normalize("13800138000"))
    }

    @Test
    fun normalize_plus86() {
        assertEquals("13800138000", NumberNormalizer.normalize("+8613800138000"))
    }

    @Test
    fun normalize_0086() {
        assertEquals("13800138000", NumberNormalizer.normalize("008613800138000"))
    }

    @Test
    fun normalize_bare86Prefix() {
        assertEquals("13800138000", NumberNormalizer.normalize("8613800138000"))
    }

    @Test
    fun normalize_spacesAndDashes() {
        assertEquals("13800138000", NumberNormalizer.normalize("+86 138-0013-8000"))
        assertEquals("13800138000", NumberNormalizer.normalize("138 0013 8000"))
    }

    @Test
    fun normalize_landlineWithAreaCode() {
        assertEquals("02110086", NumberNormalizer.normalize("021-10086"))
        assertEquals("01012345678", NumberNormalizer.normalize("(010) 1234 5678"))
    }

    @Test
    fun normalize_foreignNumberKeepsPlusForm() {
        assertEquals("+14155550100", NumberNormalizer.normalize("+1 415 555 0100"))
    }

    @Test
    fun normalize_shortCodesUntouched() {
        assertEquals("95533", NumberNormalizer.normalize("95533"))
        assertEquals("400123456", NumberNormalizer.normalize("400-123-456"))
    }

    @Test
    fun normalize_junkInput() {
        assertNull(NumberNormalizer.normalize(null))
        assertNull(NumberNormalizer.normalize(""))
        assertNull(NumberNormalizer.normalize("  "))
        assertNull(NumberNormalizer.normalize("+"))
        assertNull(NumberNormalizer.normalize("abc"))
    }

    @Test
    fun normalize_allSameCanonicalForm() {
        val variants = listOf(
            "13800138000", "+8613800138000", "008613800138000",
            "8613800138000", "+86 138 0013 8000")
        val canon = variants.map { NumberNormalizer.normalize(it) }.toSet()
        assertEquals(setOf("13800138000"), canon)
    }

    @Test
    fun maskForLog_masksMiddleDigits() {
        assertEquals("138****8000", NumberNormalizer.maskForLog("13800138000"))
        assertEquals("<null>", NumberNormalizer.maskForLog(null))
        assertEquals("95****", NumberNormalizer.maskForLog("95533"))
    }
}
