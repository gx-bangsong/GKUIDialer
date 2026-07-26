/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules.extractor

import com.android.dialer.numberinfo.rules.FieldSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Regex-based field extractor. Patterns are compiled once and cached; DOTALL is
 * enabled so rules can span line breaks without embedding (?s) everywhere.
 */
class RegexExtractor : Extractor {

    private val cache = ConcurrentHashMap<String, Pattern>()

    override fun extract(document: String, field: FieldSpec): String? {
        val patternText = field.pattern ?: return null
        val pattern = try {
            cache.getOrPut(patternText) {
                Pattern.compile(patternText, Pattern.DOTALL)
            }
        } catch (e: PatternSyntaxException) {
            return null // rule validation should have caught this; degrade quietly
        }
        val m = pattern.matcher(document)
        if (!m.find()) return null
        val group = field.group.coerceIn(0, m.groupCount())
        return m.group(group)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
