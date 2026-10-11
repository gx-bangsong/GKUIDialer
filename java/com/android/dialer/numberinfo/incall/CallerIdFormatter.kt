/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import java.text.NumberFormat

/** Result of a lookup, pre-formatted for the in-call card. */
class CallerIdInfo(
    @JvmField val number: String,
    /** Business name for the name slot, e.g. "顺丰速运客服"; null if none. */
    @JvmField val bizName: String?,
    /** One-line label: "疑似诈骗 · 被 1,283 人标记" or "快递物流 · 已认证商家". */
    @JvmField val label: String?,
    /** Geo line for the location slot, e.g. "广东深圳 移动". */
    @JvmField val location: String?,
    /** True for negative marks — the UI shows spam styling/icon. */
    @JvmField val isSpam: Boolean,
    @JvmField val isBusiness: Boolean,
)

/**
 * Platform-free string composition for the in-call card. Strings come from
 * [Strings] so the logic can be unit-tested on a plain JVM; the bridge supplies
 * the real resource-backed implementation.
 */
object CallerIdFormatter {

    interface Strings {
        /** Localized name for a mark type; CUSTOM uses the source's raw label when present. */
        fun markName(type: MarkType, rawLabel: String?): String
        /** e.g. "被 %1$s 人标记" with the already-formatted count. */
        fun markCount(formattedCount: String): String
        /** e.g. "已认证商家". */
        fun verifiedBusiness(): String
    }

    const val SEPARATOR = " · "

    fun format(info: NumberInfo, strings: Strings): CallerIdInfo = CallerIdInfo(
        number = info.number,
        bizName = info.bizName,
        label = label(info, strings),
        location = info.location,
        isSpam = info.isSpamLike,
        isBusiness = info.isBusiness,
    )

    /**
     * Spam marks: "疑似诈骗 · 被 1,283 人标记" (count omitted when unknown).
     * Business: "快递物流 · 已认证商家", "快递物流", or null without category.
     * Positive marks (express/taxi) without business: the mark name.
     */
    fun label(
        info: NumberInfo,
        strings: Strings,
        formatCount: (Int) -> String = { NumberFormat.getIntegerInstance().format(it.toLong()) },
    ): String? {
        // Spam marks win the label slot even for businesses (anti-impersonation).
        if (info.markType != MarkType.NONE && info.isSpamLike) {
            val name = strings.markName(info.markType, info.rawMarkLabel)
            return if (info.markCount > 0) {
                name + SEPARATOR + strings.markCount(formatCount(info.markCount))
            } else {
                name
            }
        }
        if (info.isBusiness) {
            val parts = ArrayList<String>(2)
            info.bizCategory?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            if (info.bizVerified) {
                parts.add(strings.verifiedBusiness())
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
        }
        if (info.markType != MarkType.NONE) {
            return strings.markName(info.markType, info.rawMarkLabel) // express / taxi
        }
        return null
    }
}
