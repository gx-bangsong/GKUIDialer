/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

/**
 * Screening rule model. Rows live in the `block_rule` table (created by
 * NumberInfoDbHelper schema v1).
 *
 * `value` semantics per type:
 *  - PREFIX     number-prefix digits, e.g. "170", "9501", "+1"
 *  - LOCATION   substring matched against NumberInfo.location, e.g. "北京", "移动"
 *  - MARK_TYPE  CSV of MarkType ids, e.g. "fraud,telemarketing,debt"
 *  - BLACKLIST  normalized number; trailing '*' allows prefix form "1380013*"
 *  - WHITELIST  same syntax as BLACKLIST, but always means ALLOW
 */
enum class RuleType {
    PREFIX, LOCATION, MARK_TYPE, BLACKLIST, WHITELIST;

    companion object {
        @JvmStatic
        fun fromId(id: String?): RuleType? =
            values().firstOrNull { it.name == id }
    }
}

/** Screening operating mode (single global setting). */
enum class ScreeningMode {
    /** Screening disabled: everything is allowed. */
    OFF,
    /** Block what rules hit, allow the rest. */
    BLACKLIST,
    /** Allow only whitelist (and contacts); block everything else. */
    WHITELIST;

    companion object {
        @JvmStatic
        fun fromId(id: String?): ScreeningMode =
            values().firstOrNull { it.name == id } ?: OFF
    }
}

data class BlockRule(
    val id: Long = 0L,
    val type: RuleType,
    val value: String,
    val enabled: Boolean = true,
    val note: String? = null,
    val createdAt: Long = 0L,
)

/**
 * Common CN number segments offered as quick-input chips in the rule editor
 * and understood by the import/export format. Centralized here per the
 * "all segments/enums managed in one place" requirement.
 */
object CommonPrefixes {
    /** 虚拟运营商（携号转网前缀常见骚扰段）. */
    @JvmField
    val VIRTUAL_OPERATOR = listOf(
        "162", "165", "167", "170", "171",
        "1700", "1701", "1702", "1703", "1704", "1705", "1706", "1707",
        "1708", "1709", "1710", "1711", "1712",
        "1690", "1691", "1692", "1693", "1694", "1695", "1696", "1697",
        "1698", "1699",
    )

    /** 950/951/952… 呼叫中心号段（含 95013 类回拨源）. */
    @JvmField
    val CALL_CENTER = listOf("950", "951", "952", "953", "954", "955", "1010")

    /** 400/800 企业号段. */
    @JvmField
    val ENTERPRISE = listOf("400", "800")

    /** 常见骚扰高发区号样例（编辑页 chips 用，可自行输入任意区号）. */
    @JvmField
    val AREA_CODES = listOf("010", "021", "022", "023", "025", "0755", "0571")

    @JvmField
    val ALL_GROUPS: Map<String, List<String>> = linkedMapOf(
        "virtual_operator" to VIRTUAL_OPERATOR,
        "call_center" to CALL_CENTER,
        "enterprise" to ENTERPRISE,
        "area_codes" to AREA_CODES,
    )
}
