/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import com.android.dialer.numberinfo.MarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseRuleEngineTest {

    private val engine = ParseRuleEngine()

    // -- helpers ------------------------------------------------------------

    private fun regexRule(
        id: String = "so360",
        confidence: Float = 0.7f,
        successRequires: List<String> = listOf("markType|bizName"),
        mapping: Map<String, String> = mapOf(
            "疑似诈骗" to "fraud", "骚扰电话" to "harassment", "*" to "custom"),
    ) = SourceRule(
        id = id, name = id, enabled = true, confidence = confidence,
        request = RequestSpec(
            urlTemplate = "https://example.com/s?q={number}",
            method = "GET", headers = emptyMap(), charset = "UTF-8", timeoutMs = 3000),
        extract = ExtractSpec(
            type = ExtractSpec.TYPE_REGEX,
            preRegex = null,
            fields = mapOf(
                "markType" to FieldSpec("该号码被\\d+人标记为[\"“]?([^\"”<]+)", 1, null, "string"),
                "markCount" to FieldSpec("该号码被(\\d+)人标记", 1, null, "int"),
                "bizName" to FieldSpec("biz-name\">([^<]+)<", 1, null, "string"),
                "location" to FieldSpec("归属地[:：]([^<\\s]+)", 1, null, "string"),
            ),
            successRequires = successRequires),
        mapping = mapOf("markType" to mapping),
    )

    private fun jsonRule() = SourceRule(
        id = "haoma", name = "haoma", enabled = true, confidence = 0.9f,
        request = RequestSpec("https://h/{number}", "GET", emptyMap(), "UTF-8", 3000),
        extract = ExtractSpec(
            type = ExtractSpec.TYPE_JSONPATH,
            preRegex = "window\\.__STATE__\\s*=\\s*(\\{.+?\\});",
            fields = mapOf(
                "bizName" to FieldSpec(null, 1, "$.data[0].name", "string"),
                "bizCategory" to FieldSpec(null, 1, "$.data[0].category", "string"),
                "bizVerified" to FieldSpec(null, 1, "$.data[0].certified", "boolean"),
                "markCount" to FieldSpec(null, 1, "$.data[0].markCount", "int"),
            ),
            successRequires = listOf("bizName")),
        mapping = emptyMap(),
    )

    // -- regex pipeline -----------------------------------------------------

    @Test
    fun execute_regexMarkResult() {
        val html = "<div>该号码被1283人标记为“疑似诈骗” 归属地:广东深圳</div>"
        val info = engine.execute(regexRule(), "13800138000", html)!!
        assertEquals(MarkType.FRAUD, info.markType)
        assertEquals(1283, info.markCount)
        assertEquals("疑似诈骗", info.rawMarkLabel)
        assertEquals("广东深圳", info.location)
        assertEquals("so360", info.source)
        assertEquals(0.7f, info.confidence, 0.0001f)
    }

    @Test
    fun execute_regexBizResult() {
        val html = "<span class=\"biz-name\">顺丰速运客服</span>"
        val info = engine.execute(regexRule(), "95338", html)!!
        assertEquals("顺丰速运客服", info.bizName)
        assertEquals(MarkType.NONE, info.markType)
    }

    @Test
    fun execute_unknownLabelFallsBackToWildcard() {
        val html = "该号码被5人标记为快递小哥"
        val info = engine.execute(regexRule(), "13800138000", html)!!
        assertEquals(MarkType.CUSTOM, info.markType)
        assertEquals("快递小哥", info.rawMarkLabel)
    }

    @Test
    fun execute_noWildcardMapsToCustom() {
        val rule = regexRule(mapping = mapOf("疑似诈骗" to "fraud"))
        val info = engine.execute(rule, "1", "该号码被5人标记为未知类型")!!
        assertEquals(MarkType.CUSTOM, info.markType)
    }

    @Test
    fun execute_successRequiresUnmetReturnsNull() {
        // Page contains only a location — neither markType nor bizName.
        assertNull(engine.execute(regexRule(), "13800138000", "归属地:北京"))
    }

    @Test
    fun execute_emptyDocumentReturnsNull() {
        assertNull(engine.execute(regexRule(), "13800138000", ""))
        assertNull(engine.execute(regexRule(), "13800138000", "<html>无结果</html>"))
    }

    // -- jsonpath + preRegex pipeline ----------------------------------------

    @Test
    fun execute_jsonWithPreRegex() {
        val html = """
            <script>window.__STATE__ = {"data":[{"name":"招商银行信用卡中心",
            "category":"银行","certified":true,"markCount":42}]};</script>
        """.trimIndent().replace("\n", "")
        val info = engine.execute(jsonRule(), "4008205555", html)!!
        assertEquals("招商银行信用卡中心", info.bizName)
        assertEquals("银行", info.bizCategory)
        assertTrue(info.bizVerified)
        assertEquals(42, info.markCount)
    }

    @Test
    fun execute_preRegexNoMatchReturnsNull() {
        assertNull(engine.execute(jsonRule(), "1", "<html>page changed!</html>"))
    }

    @Test
    fun execute_preRegexMatchesButJsonInvalid_degradesToNull() {
        val html = "window.__STATE__ = {broken json!!};"
        assertNull(engine.execute(jsonRule(), "1", html))
    }

    // -- helpers under test directly -----------------------------------------

    @Test
    fun requirementsMet_orGroupsAndAndSemantics() {
        // OR within a group
        assertTrue(engine.requirementsMet(
            listOf("a|b"), mapOf("b" to "x")))
        assertFalse(engine.requirementsMet(
            listOf("a|b"), mapOf("c" to "x")))
        // AND across groups
        assertTrue(engine.requirementsMet(
            listOf("a", "b|c"), mapOf("a" to "1", "c" to "2")))
        assertFalse(engine.requirementsMet(
            listOf("a", "b|c"), mapOf("a" to "1")))
        // empty requires = always valid
        assertTrue(engine.requirementsMet(emptyList(), emptyMap()))
        // blank values do not count
        assertFalse(engine.requirementsMet(listOf("a"), mapOf("a" to "  ")))
    }

    @Test
    fun coerceInt_toleratesFormattedNumbers() {
        assertEquals(1283, engine.coerceInt("1,283"))
        assertEquals(1283, engine.coerceInt("1283次"))
        assertEquals(0, engine.coerceInt(null))
        assertEquals(0, engine.coerceInt("many"))
        assertEquals(Int.MAX_VALUE, engine.coerceInt("99999999999999"))
    }

    @Test
    fun coerceBoolean_variants() {
        assertTrue(engine.coerceBoolean("true"))
        assertTrue(engine.coerceBoolean("TRUE"))
        assertTrue(engine.coerceBoolean("1"))
        assertTrue(engine.coerceBoolean("是"))
        assertFalse(engine.coerceBoolean("false"))
        assertFalse(engine.coerceBoolean("0"))
        assertFalse(engine.coerceBoolean(null))
    }

    @Test
    fun mapMarkType_missingTableMapsToCustom() {
        val rule = regexRule().copy(mapping = emptyMap())
        assertEquals(MarkType.CUSTOM, engine.mapMarkType(rule, "whatever"))
    }

    @Test
    fun execute_neverThrows_evenWithHostileDocument() {
        val hostile = "\u0000\uFFFF<<<>>>${"x".repeat(100_000)}"
        assertNull(engine.execute(regexRule(), "1", hostile))
        assertNotNull(engine) // reached without exception
    }
}
