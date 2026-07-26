/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

/**
 * Mark categories, normalized across all data sources.
 *
 * Raw labels from online sources (e.g. "疑似诈骗", "骚扰电话") are translated into
 * these ids via the per-source `mapping` block in rules.json, so adding/renaming
 * a source-side label never requires a code change.
 */
enum class MarkType(val id: String) {
    NONE("none"),
    /** 诈骗 */
    FRAUD("fraud"),
    /** 骚扰 */
    HARASSMENT("harassment"),
    /** 广告推销 */
    TELEMARKETING("telemarketing"),
    /** 房产/中介 */
    AGENT("agent"),
    /** 催收 */
    DEBT_COLLECTION("debt"),
    /** 快递/外卖（正向标记） */
    EXPRESS("express"),
    /** 出租车/网约车（正向标记） */
    TAXI("taxi"),
    /** 未知/用户自定义标记，原始文案保存在 [NumberInfo.rawMarkLabel] */
    CUSTOM("custom");

    val isNegative: Boolean
        get() = this == FRAUD || this == HARASSMENT || this == TELEMARKETING
                || this == AGENT || this == DEBT_COLLECTION

    companion object {
        @JvmStatic
        fun fromId(id: String?): MarkType =
            values().firstOrNull { it.id == id } ?: NONE
    }
}

/** Well-known provider/source ids. Keep in sync with rules.json `sources[].id`. */
object Sources {
    const val MANUAL = "manual"
    const val LOCAL_YELLOW_PAGE = "local_yp"
    const val SO360 = "so360"
    const val BAIDU = "baidu"
    const val SOGOU = "sogou"
    const val HAOMA = "haoma"
    /** Synthetic source id for aggregated results. */
    const val AGGREGATED = "aggregated"
}

/**
 * Unified, normalized result of a number lookup. All providers produce this type;
 * the UI and the call screening engine only ever consume this type.
 */
data class NumberInfo(
    /** Normalized number, see [NumberNormalizer]. */
    val number: String,
    val markType: MarkType = MarkType.NONE,
    /** How many users marked this number ("被 1283 人标记"); 0 if unknown. */
    val markCount: Int = 0,
    /** Original mark label as published by the source, e.g. "疑似诈骗". */
    val rawMarkLabel: String? = null,
    /** Yellow page business name, e.g. "顺丰速运客服". */
    val bizName: String? = null,
    /** Yellow page category, e.g. "快递物流". */
    val bizCategory: String? = null,
    /** Whether the business number is verified/certified by the source. */
    val bizVerified: Boolean = false,
    /** Geo location + carrier, e.g. "广东深圳 移动". */
    val location: String? = null,
    /** Source id, see [Sources]. */
    val source: String,
    /** 0..1, used by the aggregator for voting. */
    val confidence: Float = 0f,
    /** Epoch millis when this info was fetched; used for cache TTL. */
    val timestampMillis: Long = 0L,
    /** Free-form note (manual marks). */
    val note: String? = null,
) {
    val isSpamLike: Boolean
        get() = markType.isNegative

    val isBusiness: Boolean
        get() = !bizName.isNullOrBlank()

    val isEmpty: Boolean
        get() = markType == MarkType.NONE && !isBusiness && location.isNullOrBlank()
}
