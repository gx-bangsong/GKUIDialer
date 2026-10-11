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
import com.android.dialer.numberinfo.location.NumberLocationService
import com.android.dialer.numberinfo.location.PhoneNumberGeoSource
import com.android.dialer.numberinfo.location.RawGeo
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pipeline tests with fake contacts, local data and online source; no Android types. */
class ScreeningPipelineTest {

    private val n = "13910001234"
    private val engine = ScreeningDecisionEngine()

    private class Harness(
        val status: ContactStatus,
        val rules: List<BlockRule> = emptyList(),
        val mode: ScreeningMode = ScreeningMode.BLACKLIST,
        val onlineEnabled: Boolean = true,
        val local: NumberInfo? = null,
        val online: NumberInfo? = null,
    ) {
        var onlineCalls = 0
        var onlineBudgetSeen = -1L

        fun pipeline(engine: ScreeningDecisionEngine) = ScreeningPipeline(
            engine = engine,
            contactStatus = { status },
            loadRules = { rules },
            mode = { mode },
            markCountThreshold = { 10 },
            localInfo = { local },
            onlineEnabled = { onlineEnabled },
            onlineInfo = { _, budget ->
                onlineCalls++
                onlineBudgetSeen = budget
                online
            },
        )
    }

    private fun rule(type: RuleType, value: String, id: Long = 1L) =
        BlockRule(id = id, type = type, value = value, enabled = true)

    private fun markRule() = rule(RuleType.MARK_TYPE, "fraud,harassment")

    private fun onlineFraud() = NumberInfo(
        number = n, markType = MarkType.FRAUD, markCount = 1283,
        source = Sources.SO360)

    // -- the four cases from the contact-status contract ---------------------

    @Test
    fun unknown_neverQueriesOnlineSources_evenWithMarkRuleAndOptIn() = runBlocking {
        val h = Harness(ContactStatus.UNKNOWN, rules = listOf(markRule()),
            online = onlineFraud())
        h.pipeline(engine).decide(n)
        assertEquals(0, h.onlineCalls)
    }

    @Test
    fun unknown_whitelistMode_silencesInsteadOfRejecting() = runBlocking {
        val h = Harness(ContactStatus.UNKNOWN, mode = ScreeningMode.WHITELIST)
        val d = h.pipeline(engine).decide(n)
        assertEquals(Action.SILENCE, d.action)
        assertEquals(0, h.onlineCalls)
    }

    @Test
    fun unknown_exactBlacklistEntry_stillRejects() = runBlocking {
        val h = Harness(ContactStatus.UNKNOWN,
            rules = listOf(rule(RuleType.BLACKLIST, n)))
        val d = h.pipeline(engine).decide(n)
        assertEquals(Action.BLOCK, d.action)
    }

    @Test
    fun confirmedContact_allowsEvenWhenBlacklisted_andNeverGoesOnline() = runBlocking {
        val h = Harness(ContactStatus.CONTACT,
            rules = listOf(rule(RuleType.BLACKLIST, n), markRule()),
            online = onlineFraud())
        val d = h.pipeline(engine).decide(n)
        assertEquals(Action.ALLOW, d.action)
        assertEquals("SAFETY:contact", d.summary)
        assertEquals(0, h.onlineCalls)
    }

    // -- ordinary behaviour kept intact -------------------------------------

    @Test
    fun confirmedNonContact_withMarkRuleAndOptIn_queriesOnlineOnce_andBlocks() = runBlocking {
        val h = Harness(ContactStatus.NOT_CONTACT, rules = listOf(markRule()),
            online = onlineFraud())
        val d = h.pipeline(engine).decide(n)
        assertEquals(1, h.onlineCalls)
        assertEquals(Action.BLOCK, d.action)
        assertEquals(ScreeningPipeline.ONLINE_BUDGET_MS, h.onlineBudgetSeen)
    }

    @Test
    fun confirmedNonContact_optInOff_neverGoesOnline() = runBlocking {
        val h = Harness(ContactStatus.NOT_CONTACT, rules = listOf(markRule()),
            onlineEnabled = false, online = onlineFraud())
        val d = h.pipeline(engine).decide(n)
        assertEquals(0, h.onlineCalls)
        assertEquals(Action.ALLOW, d.action)
    }

    @Test
    fun confirmedNonContact_withoutMarkRule_neverGoesOnline() = runBlocking {
        val h = Harness(ContactStatus.NOT_CONTACT,
            rules = listOf(rule(RuleType.PREFIX, "139")), online = onlineFraud())
        val d = h.pipeline(engine).decide(n)
        assertEquals(0, h.onlineCalls)
        assertEquals(Action.BLOCK, d.action) // prefix decided locally
    }

    @Test
    fun localBlockIsFinal_noOnlineLookup() = runBlocking {
        val h = Harness(ContactStatus.NOT_CONTACT,
            rules = listOf(rule(RuleType.PREFIX, "139"), markRule()), online = onlineFraud())
        val d = h.pipeline(engine).decide(n)
        assertEquals(Action.BLOCK, d.action)
        assertEquals(0, h.onlineCalls)
    }

    @Test
    fun locationRule_blocksWithOnlineOff_usingChineseLocationText() = runBlocking {
        // 13910001234 is not in any bundled table; the text comes from the geo source.
        val geo = object : PhoneNumberGeoSource {
            override fun parseable(number: String) = true
            override fun lookup(number: String, locale: Locale) =
                RawGeo("CN", 86, "北京市", "中国移动", countryLevelOnly = false)
        }
        val location = NumberLocationService(geo).describe(n, Locale.SIMPLIFIED_CHINESE)
        val local = NumberInfo(number = n, location = location, source = "local_geo")
        val h = Harness(ContactStatus.NOT_CONTACT,
            rules = listOf(rule(RuleType.LOCATION, "北京")),
            onlineEnabled = false, local = local)
        val d = h.pipeline(engine).decide(n)
        assertEquals(Action.BLOCK, d.action)
        assertEquals("LOCATION:北京", d.summary)
        assertEquals(0, h.onlineCalls)
    }
}
