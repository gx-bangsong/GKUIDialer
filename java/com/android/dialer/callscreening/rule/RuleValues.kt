/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

import com.android.dialer.numberinfo.NumberNormalizer

/**
 * Canonical form of [BlockRule.value] for each [RuleType].
 *
 * Incoming numbers are canonicalized by [NumberNormalizer] before matching, so
 * rule values must use the same form — otherwise a blacklist entry pasted as
 * "+86 138 0013 8000" can never equal the incoming "13800138000".
 *
 * Applied when a rule is saved or imported, and again at match time so rows
 * written by older builds (stored verbatim) keep working without a migration.
 * Pure logic, no Android dependencies.
 */
object RuleValues {

    /** @return the canonical value, or null when [raw] is not usable for [type]. */
    @JvmStatic
    fun normalize(type: RuleType, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return when (type) {
            RuleType.PREFIX -> NumberNormalizer.normalizePrefix(value)
            RuleType.BLACKLIST, RuleType.WHITELIST ->
                if (value.endsWith("*")) {
                    // "1380013*" prefix form: canonicalize the prefix, keep the marker.
                    NumberNormalizer.normalizePrefix(value.trimEnd('*'))?.let { "$it*" }
                } else {
                    NumberNormalizer.normalize(value)
                }
            RuleType.LOCATION -> value
            RuleType.MARK_TYPE -> value.split(',', '，')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(",")
                .ifEmpty { null }
        }
    }
}
