/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RulePorterTest {

    private val rules = listOf(
        BlockRule(id = 1, type = RuleType.PREFIX, value = "170", note = "虚商号段"),
        BlockRule(id = 2, type = RuleType.MARK_TYPE, value = "fraud,telemarketing"),
        BlockRule(id = 3, type = RuleType.WHITELIST, value = "13800138000",
            enabled = false),
    )

    @Test
    fun roundTrip_preservesRules() {
        val json = RulePorter.export(rules, nowMillis = 1720000000000L)
        val result = RulePorter.parse(json)
        assertEquals(0, result.skipped)
        assertEquals(3, result.rules.size)

        val prefix = result.rules[0]
        assertEquals(RuleType.PREFIX, prefix.type)
        assertEquals("170", prefix.value)
        assertEquals("虚商号段", prefix.note)
        assertTrue(prefix.enabled)

        val whitelist = result.rules[2]
        assertEquals(RuleType.WHITELIST, whitelist.type)
        assertEquals(false, whitelist.enabled)
    }

    @Test
    fun roundTrip_idsAreNotExported() {
        // Imported rules get fresh ids on insert; exports must not pin them.
        val json = RulePorter.export(rules)
        for (r in RulePorter.parse(json).rules) {
            assertEquals(0L, r.id)
        }
    }

    @Test
    fun parse_unknownTypeSkippedNotFatal() {
        val json = """
            { "formatVersion": 1, "rules": [
                { "type": "PREFIX", "value": "170" },
                { "type": "FUTURE_RULE", "value": "x" },
                { "type": "LOCATION", "value": "" }
            ] }
        """.trimIndent()
        val result = RulePorter.parse(json)
        assertEquals(1, result.rules.size)
        assertEquals(2, result.skipped)   // unknown type + blank value
    }

    @Test
    fun parse_futureFormatVersionRejected() {
        try {
            RulePorter.parse("""{ "formatVersion": 99, "rules": [] }""")
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("formatVersion"))
        }
    }

    @Test
    fun parse_garbageRejected() {
        try {
            RulePorter.parse("not json at all")
            fail("expected exception")
        } catch (expected: Exception) {
            // envelope failures must throw so the UI can tell the user
        }
    }

    @Test
    fun parse_missingNoteBecomesNull() {
        val json = """
            { "formatVersion": 1, "rules": [ { "type": "PREFIX", "value": "9501" } ] }
        """.trimIndent()
        assertNull(RulePorter.parse(json).rules[0].note)
    }

    @Test
    fun parse_normalizesValues() {
        val json = """
            { "formatVersion": 1, "rules": [
              { "type": "BLACKLIST", "value": "+86 138 0013 8000" },
              { "type": "PREFIX", "value": "+86 10" },
              { "type": "WHITELIST", "value": "0086-139 0000*" },
              { "type": "MARK_TYPE", "value": " fraud , telemarketing,fraud" }
            ] }
        """.trimIndent()
        val result = RulePorter.parse(json)
        assertEquals(0, result.skipped)
        assertEquals(
            listOf("13800138000", "010", "1390000*", "fraud,telemarketing"),
            result.rules.map { it.value })
    }

    @Test
    fun parse_skipsValuesThatAreNotNumbers() {
        val json = """
            { "formatVersion": 1, "rules": [
              { "type": "BLACKLIST", "value": "abc" },
              { "type": "PREFIX", "value": "+86" },
              { "type": "PREFIX", "value": "170" }
            ] }
        """.trimIndent()
        val result = RulePorter.parse(json)
        assertEquals(2, result.skipped)
        assertEquals(listOf("170"), result.rules.map { it.value })
    }

    @Test
    fun export_emptyListIsValid() {
        val result = RulePorter.parse(RulePorter.export(emptyList()))
        assertEquals(0, result.rules.size)
        assertEquals(0, result.skipped)
    }
}
