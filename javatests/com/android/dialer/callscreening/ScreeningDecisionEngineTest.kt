/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import com.android.dialer.callscreening.ScreeningDecisionEngine.Action
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.callscreening.rule.ScreeningMode
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningDecisionEngineTest {

    private val engine = ScreeningDecisionEngine()
    private val n = "17012345678"

    private fun rule(
        type: RuleType, value: String, id: Long = 1L, enabled: Boolean = true,
    ) = BlockRule(id = id, type = type, value = value, enabled = enabled)

    private fun info(
        markType: MarkType = MarkType.NONE,
        markCount: Int = 0,
        location: String? = null,
        source: String = Sources.AGGREGATED,
    ) = NumberInfo(
        number = n, markType = markType, markCount = markCount,
        location = location, source = source, confidence = 0.7f)

    // -- mode gates -----------------------------------------------------------

    @Test
    fun off_allowsEverything() {
        val d = engine.decide(
            n, info(MarkType.FRAUD, 9999),
            listOf(rule(RuleType.BLACKLIST, n)), ScreeningMode.OFF)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun blankNumber_allowed() {
        val d = engine.decide("", null, listOf(rule(RuleType.PREFIX, "1")),
            ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    // -- safety overrides -----------------------------------------------------

    @Test
    fun emergencyNumbers_neverBlocked_evenInWhitelistMode() {
        for (emergency in listOf("110", "119", "120", "112")) {
            val d = engine.decide(emergency, null, emptyList(), ScreeningMode.WHITELIST)
            assertEquals("emergency $emergency", Action.ALLOW, d.action)
            assertEquals("SAFETY:emergency", d.summary)
        }
    }

    @Test
    fun contacts_neverBlocked_evenWhenBlacklisted() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.BLACKLIST, n)),
            ScreeningMode.BLACKLIST, isContact = true)
        assertEquals(Action.ALLOW, d.action)
        assertEquals("SAFETY:contact", d.summary)
    }

    // -- whitelist ------------------------------------------------------------

    @Test
    fun whitelist_beatsBlacklistInBlacklistMode() {
        val rules = listOf(
            rule(RuleType.BLACKLIST, n, id = 1),
            rule(RuleType.WHITELIST, n, id = 2))
        val d = engine.decide(n, null, rules, ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
        assertEquals(2L, d.ruleId)
    }

    @Test
    fun whitelistMode_blocksEverythingNotWhitelisted() {
        val d = engine.decide(n, null, emptyList(), ScreeningMode.WHITELIST)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("MODE:WHITELIST", d.summary)
    }

    @Test
    fun whitelistMode_allowsWhitelisted_exactAndPrefixForms() {
        val exact = engine.decide(
            n, null, listOf(rule(RuleType.WHITELIST, n)), ScreeningMode.WHITELIST)
        assertEquals(Action.ALLOW, exact.action)

        val prefix = engine.decide(
            n, null, listOf(rule(RuleType.WHITELIST, "170*")), ScreeningMode.WHITELIST)
        assertEquals(Action.ALLOW, prefix.action)
    }

    // -- blacklist mode: number rules ------------------------------------------

    @Test
    fun blacklist_exactMatch_blocks() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.BLACKLIST, n, id = 7)), ScreeningMode.BLACKLIST)
        assertEquals(Action.BLOCK, d.action)
        assertEquals(7L, d.ruleId)
        assertTrue(d.summary.startsWith("BLACKLIST:"))
    }

    @Test
    fun blacklist_wildcardPrefix_blocks() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.BLACKLIST, "1701234*")), ScreeningMode.BLACKLIST)
        assertEquals(Action.BLOCK, d.action)
    }

    @Test
    fun blacklist_noMatch_allows() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.BLACKLIST, "13800138000")), ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    // -- prefix rules -----------------------------------------------------------

    @Test
    fun prefix_hit_blocks() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.PREFIX, "170")), ScreeningMode.BLACKLIST)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("PREFIX:170", d.summary)
    }

    @Test
    fun prefix_longestMatchWins() {
        val rules = listOf(
            rule(RuleType.PREFIX, "17", id = 1),
            rule(RuleType.PREFIX, "1701", id = 2))
        val d = engine.decide(n, null, rules, ScreeningMode.BLACKLIST)
        assertEquals(2L, d.ruleId)
    }

    @Test
    fun disabledRule_isIgnored() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.PREFIX, "170", enabled = false)),
            ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    // -- location rules ----------------------------------------------------------

    @Test
    fun location_substringHit_blocks() {
        val d = engine.decide(
            n, info(location = "北京 联通"),
            listOf(rule(RuleType.LOCATION, "北京")), ScreeningMode.BLACKLIST)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("LOCATION:北京", d.summary)
    }

    @Test
    fun location_noInfo_allows() {
        val d = engine.decide(
            n, null, listOf(rule(RuleType.LOCATION, "北京")), ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun location_carrierMatchAlsoWorks() {
        val d = engine.decide(
            n, info(location = "广东深圳 移动"),
            listOf(rule(RuleType.LOCATION, "移动")), ScreeningMode.BLACKLIST)
        assertEquals(Action.BLOCK, d.action)
    }

    // -- mark type rules -----------------------------------------------------------

    @Test
    fun markType_hitAboveThreshold_blocks() {
        val d = engine.decide(
            n, info(MarkType.FRAUD, 1283),
            listOf(rule(RuleType.MARK_TYPE, "fraud,telemarketing")),
            ScreeningMode.BLACKLIST, markCountThreshold = 10)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("MARK_TYPE:fraud(1283)", d.summary)
    }

    @Test
    fun markType_belowThreshold_allows() {
        val d = engine.decide(
            n, info(MarkType.FRAUD, 3),
            listOf(rule(RuleType.MARK_TYPE, "fraud")),
            ScreeningMode.BLACKLIST, markCountThreshold = 10)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun markType_manualMarkBypassesThreshold() {
        val d = engine.decide(
            n, info(MarkType.FRAUD, 0, source = Sources.MANUAL),
            listOf(rule(RuleType.MARK_TYPE, "fraud")),
            ScreeningMode.BLACKLIST, markCountThreshold = 10)
        assertEquals(Action.BLOCK, d.action)
    }

    @Test
    fun markType_typeNotInCsv_allows() {
        val d = engine.decide(
            n, info(MarkType.EXPRESS, 500),
            listOf(rule(RuleType.MARK_TYPE, "fraud,harassment")),
            ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun markType_noneNeverBlocks_evenWithBogusCsv() {
        val d = engine.decide(
            n, info(MarkType.NONE, 9999),
            listOf(rule(RuleType.MARK_TYPE, "none")),
            ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
    }

    // -- helper units ------------------------------------------------------------

    @Test
    fun numberMatches_syntax() {
        assertTrue(engine.numberMatches("13800138000", "13800138000"))
        assertFalse(engine.numberMatches("13800138000", "13800138001"))
        assertTrue(engine.numberMatches("13800138000", "138*"))
        assertFalse(engine.numberMatches("13900139000", "138*"))
        assertFalse(engine.numberMatches("13800138000", "*"))   // bare star invalid
        assertFalse(engine.numberMatches("13800138000", ""))
    }

    @Test
    fun markTypeHit_csvParsing() {
        assertTrue(engine.markTypeHit("fraud, telemarketing", MarkType.TELEMARKETING))
        assertTrue(engine.markTypeHit("fraud", MarkType.FRAUD))
        assertFalse(engine.markTypeHit("fraud", MarkType.HARASSMENT))
        assertFalse(engine.markTypeHit("", MarkType.FRAUD))
        assertFalse(engine.markTypeHit("bogus,unknown", MarkType.FRAUD))
    }

    // -- rule precedence sanity ----------------------------------------------------

    @Test
    fun precedence_whitelistOverMarkAndPrefixAndLocation() {
        val rules = listOf(
            rule(RuleType.PREFIX, "170", id = 1),
            rule(RuleType.LOCATION, "北京", id = 2),
            rule(RuleType.MARK_TYPE, "fraud", id = 3),
            rule(RuleType.WHITELIST, "170*", id = 4))
        val d = engine.decide(
            n, info(MarkType.FRAUD, 5000, location = "北京 联通"),
            rules, ScreeningMode.BLACKLIST)
        assertEquals(Action.ALLOW, d.action)
        assertEquals(4L, d.ruleId)
    }
}
