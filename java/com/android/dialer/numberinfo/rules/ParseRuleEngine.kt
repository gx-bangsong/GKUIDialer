/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.rules.extractor.Extractor
import com.android.dialer.numberinfo.rules.extractor.JsonPathExtractor
import com.android.dialer.numberinfo.rules.extractor.RegexExtractor
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Executes a [SourceRule] against a fetched document and produces a normalized
 * [NumberInfo]. Pure logic — networking lives in the provider, rule loading in
 * [RuleRepository]. Every failure degrades to null and is logged with
 * source/field context so a broken rule can be identified from logs.
 *
 * Pipeline:
 *   document --preRegex?--> narrowed document
 *            --extractor(type).fields--> raw string map
 *            --successRequires--> validity gate (null if unmet)
 *            --mapping + coerce--> NumberInfo
 */
class ParseRuleEngine(
    private val regexExtractor: Extractor = RegexExtractor(),
    private val jsonPathExtractor: Extractor = JsonPathExtractor(),
) {

    /** @return normalized info, or null when the document has no (valid) data. */
    fun execute(rule: SourceRule, number: String, document: String): NumberInfo? {
        try {
            var doc = document
            rule.extract.preRegex?.let { pre ->
                doc = applyPreRegex(rule, number, pre, doc) ?: return null
            }

            val extractor = when (rule.extract.type) {
                ExtractSpec.TYPE_REGEX -> regexExtractor
                ExtractSpec.TYPE_JSONPATH -> jsonPathExtractor
                else -> {
                    NumberInfoLog.ruleFailure(rule.id, null, number,
                        "unsupported extract type ${rule.extract.type}")
                    return null
                }
            }

            val raw = HashMap<String, String>()
            for ((name, spec) in rule.extract.fields) {
                val value = try {
                    extractor.extract(doc, spec)
                } catch (e: Exception) {
                    NumberInfoLog.ruleFailure(rule.id, name, number, "extractor threw: $e")
                    null
                }
                if (value != null) raw[name] = value
            }

            if (!requirementsMet(rule.extract.successRequires, raw)) {
                // Not an error: the page simply has no data for this number.
                NumberInfoLog.d("source=${rule.id} no data (successRequires unmet)")
                return null
            }

            return buildInfo(rule, number, raw)
        } catch (e: Exception) {
            NumberInfoLog.ruleFailure(rule.id, null, number, "engine crashed: $e")
            return null
        }
    }

    private fun applyPreRegex(
        rule: SourceRule, number: String, pre: String, doc: String,
    ): String? {
        return try {
            val m = Pattern.compile(pre, Pattern.DOTALL).matcher(doc)
            if (!m.find()) {
                NumberInfoLog.ruleFailure(rule.id, "preRegex", number, "no match")
                null
            } else {
                (if (m.groupCount() >= 1) m.group(1) else m.group(0)) ?: run {
                    NumberInfoLog.ruleFailure(rule.id, "preRegex", number, "empty group")
                    null
                }
            }
        } catch (e: PatternSyntaxException) {
            NumberInfoLog.ruleFailure(rule.id, "preRegex", number, "bad pattern: $e")
            null
        }
    }

    /** Each entry is an OR-group ("a|b"); all entries must pass. */
    internal fun requirementsMet(requires: List<String>, raw: Map<String, String>): Boolean {
        for (orGroup in requires) {
            val ok = orGroup.split('|').any { field ->
                !raw[field.trim()].isNullOrBlank()
            }
            if (!ok) return false
        }
        return true
    }

    private fun buildInfo(rule: SourceRule, number: String, raw: Map<String, String>): NumberInfo {
        val rawLabel = raw["markType"]
        val markType = rawLabel?.let { mapMarkType(rule, it) } ?: MarkType.NONE
        return NumberInfo(
            number = number,
            markType = markType,
            markCount = coerceInt(raw["markCount"]),
            rawMarkLabel = rawLabel,
            bizName = raw["bizName"],
            bizCategory = raw["bizCategory"],
            bizVerified = coerceBoolean(raw["bizVerified"]),
            location = raw["location"],
            source = rule.id,
            confidence = rule.confidence,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    /** Source label -> MarkType via the rule's mapping table; "*" is wildcard. */
    internal fun mapMarkType(rule: SourceRule, label: String): MarkType {
        val table = rule.mapping["markType"] ?: return MarkType.CUSTOM
        val mapped = table[label] ?: table["*"] ?: return MarkType.CUSTOM
        return MarkType.fromId(mapped)
    }

    /** "1,283" / "1283次" / "about 1283" all coerce to 1283. */
    internal fun coerceInt(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        val digits = value.filter { it in '0'..'9' }
        if (digits.isEmpty()) return 0
        return try {
            digits.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }

    internal fun coerceBoolean(value: String?): Boolean =
        value != null && (value.equals("true", true) || value == "1" || value == "是")
}
