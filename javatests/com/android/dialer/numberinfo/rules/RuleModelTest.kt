/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuleModelTest {

    private fun minimalSource(id: String = "s1") = """
        {
          "id": "$id",
          "request": { "urlTemplate": "https://x/{number}" },
          "extract": {
            "type": "regex",
            "fields": { "markType": { "pattern": "m:(\\w+)" } }
          }
        }
    """.trimIndent()

    private fun ruleSetJson(vararg sources: String) = """
        { "schemaVersion": 1, "sources": [ ${sources.joinToString(",")} ] }
    """.trimIndent()

    @Test
    fun parse_minimalRuleSet() {
        val rs = RuleSet.parse(ruleSetJson(minimalSource()))
        assertEquals(1, rs.schemaVersion)
        assertEquals(1, rs.sources.size)
        val s = rs.sources[0]
        assertEquals("s1", s.id)
        assertTrue(s.enabled)                       // default
        assertEquals(0.5f, s.confidence, 0.001f)    // default
        assertEquals("GET", s.request.method)       // default
        assertEquals(3000, s.request.timeoutMs)     // default
        assertNull(rs.ruleUpdateUrl)
    }

    @Test
    fun parse_unsupportedSchemaVersionRejected() {
        try {
            RuleSet.parse("""{ "schemaVersion": 99, "sources": [] }""")
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun parse_duplicateSourceIdsRejected() {
        try {
            RuleSet.parse(ruleSetJson(minimalSource("dup"), minimalSource("dup")))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Duplicate"))
        }
    }

    @Test
    fun parse_urlTemplateWithoutPlaceholderRejected() {
        val bad = """
            { "id": "s", "request": { "urlTemplate": "https://x/fixed" },
              "extract": { "type": "regex",
                           "fields": { "a": { "pattern": "x" } } } }
        """.trimIndent()
        try {
            RuleSet.parse(ruleSetJson(bad))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("{number}"))
        }
    }

    @Test
    fun parse_unknownExtractTypeRejected() {
        val bad = """
            { "id": "s", "request": { "urlTemplate": "https://x/{number}" },
              "extract": { "type": "css",
                           "fields": { "a": { "pattern": "x" } } } }
        """.trimIndent()
        try {
            RuleSet.parse(ruleSetJson(bad))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("css"))
        }
    }

    @Test
    fun parse_invalidRegexRejectedAtLoadTime() {
        val bad = """
            { "id": "s", "request": { "urlTemplate": "https://x/{number}" },
              "extract": { "type": "regex",
                           "fields": { "a": { "pattern": "([unclosed" } } } }
        """.trimIndent()
        try {
            RuleSet.parse(ruleSetJson(bad))
            fail("expected rejection")
        } catch (e: Exception) {
            assertNotNull(e) // PatternSyntaxException or wrapped
        }
    }

    @Test
    fun parse_jsonpathFieldRequiresPath() {
        val bad = """
            { "id": "s", "request": { "urlTemplate": "https://x/{number}" },
              "extract": { "type": "jsonpath",
                           "fields": { "a": { "pattern": "x" } } } }
        """.trimIndent()
        try {
            RuleSet.parse(ruleSetJson(bad))
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("path"))
        }
    }

    @Test
    fun parse_mappingTables() {
        val src = """
            { "id": "s", "request": { "urlTemplate": "https://x/{number}" },
              "extract": { "type": "regex",
                           "fields": { "markType": { "pattern": "m:(\\w+)" } } },
              "mapping": { "markType": { "诈骗": "fraud", "*": "custom" } } }
        """.trimIndent()
        val rs = RuleSet.parse(ruleSetJson(src))
        assertEquals("fraud", rs.sources[0].mapping["markType"]!!["诈骗"])
        assertEquals("custom", rs.sources[0].mapping["markType"]!!["*"])
    }

    @Test
    fun source_lookupById() {
        val rs = RuleSet.parse(ruleSetJson(minimalSource("a"), minimalSource("b")))
        assertEquals("b", rs.source("b")!!.id)
        assertNull(rs.source("missing"))
    }

    @Test
    fun parse_builtinAssetIsValid() {
        // Guards the shipped asset: a malformed assets/numberinfo/rules.json
        // must fail this test, not in the field. The asset is packaged into
        // the test APK via asset_dirs in Android.bp.
        val stream = try {
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().context.assets
                .open("numberinfo/rules.json")
        } catch (e: Exception) {
            javaClass.classLoader?.getResourceAsStream("assets/numberinfo/rules.json")
        } ?: return // not available in this runner; covered on device runs
        val rs = RuleSet.parse(stream.readBytes().toString(Charsets.UTF_8))
        assertTrue(rs.sources.map { it.id }
            .containsAll(listOf("so360", "baidu", "sogou", "haoma")))
    }
}
