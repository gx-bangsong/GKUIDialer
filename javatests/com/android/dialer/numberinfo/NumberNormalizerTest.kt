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

    // -- E.164 landlines: the trunk prefix "0" must come back ----------------

    @Test
    fun normalize_e164Landline_restoresTrunkPrefix() {
        assertEquals("01012345678", NumberNormalizer.normalize("+861012345678"))        // 北京
        assertEquals("02161234567", NumberNormalizer.normalize("+86 21 6123 4567"))     // 上海
        assertEquals("075512345678", NumberNormalizer.normalize("+86 755 1234 5678"))   // 深圳
        assertEquals("05718888888", NumberNormalizer.normalize("0086 571 888 8888"))    // 7-digit subscriber
    }

    @Test
    fun normalize_e164AndDomesticLandlineAgree() {
        assertEquals(
            NumberNormalizer.normalize("010-1234 5678"),
            NumberNormalizer.normalize("+86 10 1234 5678"))
        assertEquals(
            NumberNormalizer.normalize("0755-12345678"),
            NumberNormalizer.normalize("+8675512345678"))
    }

    @Test
    fun normalize_e164WithStrayTrunkZero() {
        // "+86 (0)10 …" is a common mis-format; the zero must not be doubled.
        assertEquals("01012345678", NumberNormalizer.normalize("+86 (0)10 1234 5678"))
    }

    @Test
    fun normalize_e164ServiceNumbersKeepNationalForm() {
        assertEquals("4001234567", NumberNormalizer.normalize("+86 400 123 4567"))
        assertEquals("8001234567", NumberNormalizer.normalize("+86 800 123 4567"))
        assertEquals("95533", NumberNormalizer.normalize("+86 95533"))
        assertEquals("10086", NumberNormalizer.normalize("+86 10086"))
        // Mobiles are unaffected by the landline rule.
        assertEquals("13800138000", NumberNormalizer.normalize("+86 138 0013 8000"))
    }

    @Test
    fun normalize_internationalAccessPrefix00() {
        assertEquals("+14155550100", NumberNormalizer.normalize("001 415 555 0100"))
        assertEquals(
            NumberNormalizer.normalize("+44 20 7946 0958"),
            NumberNormalizer.normalize("0044 20 7946 0958"))
    }

    @Test
    fun normalize_fullWidthDigitsFromCjkInput() {
        assertEquals("13800138000", NumberNormalizer.normalize("１３８ ００１３ ８０００"))
        assertEquals("13800138000", NumberNormalizer.normalize("＋８６１３８００１３８０００"))
    }

    @Test
    fun normalize_bareInternationalPrefixIsJunk() {
        assertNull(NumberNormalizer.normalize("00"))
        assertNull(NumberNormalizer.normalize("+86"))
    }

    // -- prefixes typed into rules -------------------------------------------

    @Test
    fun normalizePrefix_domesticFormsUntouched() {
        assertEquals("170", NumberNormalizer.normalizePrefix("170"))
        assertEquals("010", NumberNormalizer.normalizePrefix("010"))
        assertEquals("95", NumberNormalizer.normalizePrefix("95"))
    }

    @Test
    fun normalizePrefix_countryCodeForms() {
        assertEquals("010", NumberNormalizer.normalizePrefix("+86 10"))
        assertEquals("021", NumberNormalizer.normalizePrefix("+86 21"))
        assertEquals("0755", NumberNormalizer.normalizePrefix("0086-755"))
        assertEquals("138", NumberNormalizer.normalizePrefix("+86 138"))
        assertEquals("400", NumberNormalizer.normalizePrefix("+86 400"))
        assertEquals("010", NumberNormalizer.normalizePrefix("+86 010")) // stray trunk zero
        assertEquals("+1", NumberNormalizer.normalizePrefix("001"))
        assertEquals("+44", NumberNormalizer.normalizePrefix("+44"))
    }

    @Test
    fun normalizePrefix_rejectsEmptyResults() {
        assertNull(NumberNormalizer.normalizePrefix("+86"))
        assertNull(NumberNormalizer.normalizePrefix("0086"))
        assertNull(NumberNormalizer.normalizePrefix("00"))
        assertNull(NumberNormalizer.normalizePrefix("--"))
        assertNull(NumberNormalizer.normalizePrefix(null))
    }

    @Test
    fun maskForLog_masksMiddleDigits() {
        assertEquals("138****8000", NumberNormalizer.maskForLog("13800138000"))
        assertEquals("<null>", NumberNormalizer.maskForLog(null))
        assertEquals("95****", NumberNormalizer.maskForLog("95533"))
    }
}
