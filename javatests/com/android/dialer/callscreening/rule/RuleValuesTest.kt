/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleValuesTest {

    private fun norm(type: RuleType, raw: String?) = RuleValues.normalize(type, raw)

    @Test
    fun blacklist_exactNumbersAreCanonicalized() {
        assertEquals("13800138000", norm(RuleType.BLACKLIST, "+86 138 0013 8000"))
        assertEquals("13800138000", norm(RuleType.BLACKLIST, "138-0013-8000"))
        assertEquals("13800138000", norm(RuleType.BLACKLIST, "１３８００１３８０００")) // full-width
        assertEquals("01012345678", norm(RuleType.BLACKLIST, "+86 10 1234 5678"))
        assertEquals("+14155550100", norm(RuleType.WHITELIST, "001 415 555 0100"))
    }

    @Test
    fun blacklist_prefixFormKeepsTrailingStar() {
        assertEquals("1380013*", norm(RuleType.BLACKLIST, "138 0013*"))
        assertEquals("1380013*", norm(RuleType.WHITELIST, "+86 138-0013 *"))
        assertEquals("010*", norm(RuleType.BLACKLIST, "+86 10*"))
    }

    @Test
    fun blacklist_unusableValuesAreRejected() {
        assertNull(norm(RuleType.BLACKLIST, "abc"))
        assertNull(norm(RuleType.BLACKLIST, "*"))
        assertNull(norm(RuleType.BLACKLIST, "+86"))
        assertNull(norm(RuleType.WHITELIST, "  "))
        assertNull(norm(RuleType.WHITELIST, null))
    }

    @Test
    fun prefix_isCanonicalizedLikeNumbers() {
        assertEquals("170", norm(RuleType.PREFIX, " 170 "))
        assertEquals("170", norm(RuleType.PREFIX, "+86 170"))
        assertEquals("010", norm(RuleType.PREFIX, "+86 10"))
        assertEquals("0755", norm(RuleType.PREFIX, "0086-755"))
        assertEquals("400", norm(RuleType.PREFIX, "+86 400"))
        assertEquals("+1", norm(RuleType.PREFIX, "+1"))
    }

    @Test
    fun prefix_bareCountryCodeIsRejected() {
        // Would otherwise become "" and match every number.
        assertNull(norm(RuleType.PREFIX, "+86"))
        assertNull(norm(RuleType.PREFIX, "0086"))
        assertNull(norm(RuleType.PREFIX, "-"))
    }

    @Test
    fun location_isTrimmedOnly() {
        assertEquals("北京", norm(RuleType.LOCATION, " 北京 "))
        assertEquals("广东 移动", norm(RuleType.LOCATION, "广东 移动"))
    }

    @Test
    fun markType_csvIsCleanedUp() {
        assertEquals("fraud,telemarketing",
            norm(RuleType.MARK_TYPE, " fraud , telemarketing,fraud"))
        assertEquals("fraud,debt", norm(RuleType.MARK_TYPE, "fraud，debt")) // full-width comma
        assertNull(norm(RuleType.MARK_TYPE, " , "))
    }
}
