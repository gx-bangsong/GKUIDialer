/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import com.android.dialer.callscreening.ScreeningDecisionEngine.Action
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.callscreening.rule.ScreeningMode
import com.android.dialer.numberinfo.ContactStatus
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Engine-level rules for an UNKNOWN contact status: rule hits that would block
 * become SILENCE, except exact-number blacklist entries.
 */
class ScreeningUnknownContactTest {

    private val engine = ScreeningDecisionEngine()
    private val n = "13910001234"

    private fun rule(type: RuleType, value: String, id: Long = 1L) =
        BlockRule(id = id, type = type, value = value, enabled = true)

    @Test
    fun unknown_whitelistMode_silencesInsteadOfBlocking() {
        val d = engine.decide(n, null, emptyList(), ScreeningMode.WHITELIST,
            ContactStatus.UNKNOWN)
        assertEquals(Action.SILENCE, d.action)
        assertEquals(false, d.blocked)
    }

    @Test
    fun unknown_exactBlacklistStillBlocks() {
        val d = engine.decide(n, null, listOf(rule(RuleType.BLACKLIST, n)),
            ScreeningMode.BLACKLIST, ContactStatus.UNKNOWN)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("BLACKLIST:$n", d.summary)
    }

    @Test
    fun unknown_wildcardBlacklistSilences() {
        val d = engine.decide(n, null, listOf(rule(RuleType.BLACKLIST, "1391000*")),
            ScreeningMode.BLACKLIST, ContactStatus.UNKNOWN)
        assertEquals(Action.SILENCE, d.action)
    }

    @Test
    fun unknown_prefixAndLocationAndMarkRulesSilence() {
        val prefix = engine.decide(n, null, listOf(rule(RuleType.PREFIX, "139")),
            ScreeningMode.BLACKLIST, ContactStatus.UNKNOWN)
        assertEquals(Action.SILENCE, prefix.action)

        val location = engine.decide(n, info(location = "北京 移动"),
            listOf(rule(RuleType.LOCATION, "北京")),
            ScreeningMode.BLACKLIST, ContactStatus.UNKNOWN)
        assertEquals(Action.SILENCE, location.action)

        val mark = engine.decide(n,
            info(mark = MarkType.FRAUD, count = 50, source = Sources.SO360),
            listOf(rule(RuleType.MARK_TYPE, "fraud")),
            ScreeningMode.BLACKLIST, ContactStatus.UNKNOWN, markCountThreshold = 10)
        assertEquals(Action.SILENCE, mark.action)
    }

    @Test
    fun unknown_whitelistRuleStillAllows() {
        val d = engine.decide(n, null, listOf(rule(RuleType.WHITELIST, n)),
            ScreeningMode.WHITELIST, ContactStatus.UNKNOWN)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun unknown_noRuleHit_allows() {
        val d = engine.decide(n, null, emptyList(), ScreeningMode.BLACKLIST,
            ContactStatus.UNKNOWN)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun confirmedContact_alwaysAllows_evenInWhitelistMode() {
        val d = engine.decide(n, null, emptyList(), ScreeningMode.WHITELIST,
            ContactStatus.CONTACT)
        assertEquals(Action.ALLOW, d.action)
        assertEquals("SAFETY:contact", d.summary)
    }

    @Test
    fun confirmedNonContact_whitelistModeStillBlocks() {
        val d = engine.decide(n, null, emptyList(), ScreeningMode.WHITELIST,
            ContactStatus.NOT_CONTACT)
        assertEquals(Action.BLOCK, d.action)
    }

    private fun info(
        mark: MarkType = MarkType.NONE,
        count: Int = 0,
        location: String? = null,
        source: String = Sources.AGGREGATED,
    ) = NumberInfo(
        number = n, markType = mark, markCount = count,
        location = location, source = source)
}
