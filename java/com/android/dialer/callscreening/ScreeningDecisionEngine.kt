/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.callscreening.rule.ScreeningMode
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources

/**
 * Pure screening decision function — no Android dependencies, exhaustively
 * unit-tested. The service supplies the inputs; this class only decides.
 *
 * Priority (docs §4.3):
 *   0. hardcoded safety: emergency numbers and contacts are always allowed
 *   1. WHITELIST rule hit                          -> ALLOW (both modes)
 *   2. mode == WHITELIST                           -> BLOCK everything else
 *   3. mode == BLACKLIST:
 *        BLACKLIST rule (exact / trailing-'*')     -> BLOCK
 *        PREFIX rule (longest match wins)          -> BLOCK
 *        LOCATION rule (substring of location)     -> BLOCK
 *        MARK_TYPE rule (type hit + count gate)    -> BLOCK
 *   4. otherwise                                   -> ALLOW
 */
class ScreeningDecisionEngine {

    enum class Action { ALLOW, BLOCK }

    /**
     * @param ruleId id of the deciding rule, null for mode-implied decisions
     * @param summary stable machine-readable reason, e.g. "PREFIX:170",
     *   "MODE:WHITELIST", "MARK_TYPE:fraud(1283)"; persisted into the blocked
     *   call record so history survives rule deletion.
     */
    data class Decision(
        val action: Action,
        val ruleId: Long? = null,
        val summary: String = "",
    ) {
        val blocked: Boolean get() = action == Action.BLOCK
    }

    /**
     * @param number normalized number (NumberNormalizer form)
     * @param info aggregated NumberInfo, null when nothing is known
     * @param isContact true when the number matches a contact
     * @param markCountThreshold minimum markCount for MARK_TYPE blocking;
     *   bypassed when the mark is the user's own (source == manual)
     */
    fun decide(
        number: String,
        info: NumberInfo?,
        rules: List<BlockRule>,
        mode: ScreeningMode,
        isContact: Boolean = false,
        markCountThreshold: Int = 0,
    ): Decision {
        if (mode == ScreeningMode.OFF) return ALLOW_DEFAULT
        if (number.isBlank()) return ALLOW_DEFAULT
        if (isEmergencyNumber(number)) return Decision(Action.ALLOW, null, "SAFETY:emergency")
        if (isContact) return Decision(Action.ALLOW, null, "SAFETY:contact")

        val active = rules.filter { it.enabled }

        // 1. Whitelist beats everything in both modes.
        active.firstOrNull { it.type == RuleType.WHITELIST && numberMatches(number, it.value) }
            ?.let { return Decision(Action.ALLOW, it.id, "WHITELIST:${it.value}") }

        // 2. Whitelist mode: everything not whitelisted is blocked.
        if (mode == ScreeningMode.WHITELIST) {
            return Decision(Action.BLOCK, null, "MODE:WHITELIST")
        }

        // 3. Blacklist mode.
        active.firstOrNull { it.type == RuleType.BLACKLIST && numberMatches(number, it.value) }
            ?.let { return Decision(Action.BLOCK, it.id, "BLACKLIST:${it.value}") }

        // Prefix rules: longest matching prefix wins (deterministic when both
        // "17" and "170" exist).
        active.filter { it.type == RuleType.PREFIX && it.value.isNotBlank() }
            .filter { number.startsWith(it.value) }
            .maxByOrNull { it.value.length }
            ?.let { return Decision(Action.BLOCK, it.id, "PREFIX:${it.value}") }

        val location = info?.location
        if (!location.isNullOrBlank()) {
            active.firstOrNull {
                it.type == RuleType.LOCATION && it.value.isNotBlank()
                        && location.contains(it.value)
            }?.let { return Decision(Action.BLOCK, it.id, "LOCATION:${it.value}") }
        }

        if (info != null && info.markType != MarkType.NONE) {
            val manual = info.source == Sources.MANUAL
            val countOk = manual || info.markCount >= markCountThreshold
            if (countOk) {
                active.firstOrNull {
                    it.type == RuleType.MARK_TYPE && markTypeHit(it.value, info.markType)
                }?.let {
                    return Decision(
                        Action.BLOCK, it.id,
                        "MARK_TYPE:${info.markType.id}(${info.markCount})")
                }
            }
        }

        return ALLOW_DEFAULT
    }

    /** Exact match, or prefix match when the rule value ends with '*'. */
    internal fun numberMatches(number: String, ruleValue: String): Boolean {
        if (ruleValue.isBlank()) return false
        return if (ruleValue.endsWith("*")) {
            val prefix = ruleValue.dropLast(1)
            prefix.isNotEmpty() && number.startsWith(prefix)
        } else {
            number == ruleValue
        }
    }

    /** CSV of MarkType ids ("fraud,telemarketing") contains the given type. */
    internal fun markTypeHit(csv: String, markType: MarkType): Boolean =
        csv.split(',').any { MarkType.fromId(it.trim()) == markType && markType != MarkType.NONE }

    internal fun isEmergencyNumber(number: String): Boolean =
        number in EMERGENCY_NUMBERS

    companion object {
        private val ALLOW_DEFAULT = Decision(Action.ALLOW)

        /** Safety net; the platform also refuses to screen true emergency calls. */
        @JvmField
        val EMERGENCY_NUMBERS = setOf(
            "110", "119", "120", "122", "112", "911", "999", "12110")
    }
}
