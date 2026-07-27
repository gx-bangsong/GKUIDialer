/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.ui

import android.content.Context
import com.android.dialer.callscreening.R
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.numberinfo.MarkType

/** Shared label helpers for the screening UI (all strings via resources). */
object ScreeningUi {

    @JvmStatic
    fun ruleTypeLabel(context: Context, type: RuleType): String = context.getString(
        when (type) {
            RuleType.PREFIX -> R.string.rule_type_prefix
            RuleType.LOCATION -> R.string.rule_type_location
            RuleType.MARK_TYPE -> R.string.rule_type_mark
            RuleType.BLACKLIST -> R.string.rule_type_blacklist
            RuleType.WHITELIST -> R.string.rule_type_whitelist
        })

    @JvmStatic
    fun markTypeLabel(context: Context, markType: MarkType): String = context.getString(
        when (markType) {
            MarkType.FRAUD -> R.string.mark_type_fraud
            MarkType.HARASSMENT -> R.string.mark_type_harassment
            MarkType.TELEMARKETING -> R.string.mark_type_telemarketing
            MarkType.AGENT -> R.string.mark_type_agent
            MarkType.DEBT_COLLECTION -> R.string.mark_type_debt
            MarkType.EXPRESS -> R.string.mark_type_express
            MarkType.TAXI -> R.string.mark_type_taxi
            else -> R.string.mark_type_custom
        })

    /** Human-readable one-liner for a rule row. */
    @JvmStatic
    fun ruleSummary(context: Context, rule: BlockRule): String =
        if (rule.type == RuleType.MARK_TYPE) {
            rule.value.split(',')
                .mapNotNull { id ->
                    MarkType.fromId(id.trim())
                        .takeIf { it != MarkType.NONE }
                        ?.let { markTypeLabel(context, it) }
                }
                .joinToString(" / ")
        } else {
            rule.value
        }

    /** MarkTypes offered as blocking criteria (negative ones + custom). */
    @JvmField
    val BLOCKABLE_MARK_TYPES = listOf(
        MarkType.FRAUD, MarkType.HARASSMENT, MarkType.TELEMARKETING,
        MarkType.AGENT, MarkType.DEBT_COLLECTION, MarkType.CUSTOM)
}
