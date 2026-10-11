/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerIdFormatterTest {

    /** Deterministic strings; mirrors the English resources. */
    private val strings = object : CallerIdFormatter.Strings {
        override fun markName(type: MarkType, rawLabel: String?): String =
            if (type == MarkType.CUSTOM && !rawLabel.isNullOrBlank()) rawLabel
            else type.name.lowercase()

        override fun markCount(formattedCount: String): String = "Reported by $formattedCount users"
        override fun verifiedBusiness(): String = "Verified"
    }

    private val fixedCount: (Int) -> String = { "1,283" }

    private fun info(
        mark: MarkType = MarkType.NONE,
        count: Int = 0,
        raw: String? = null,
        biz: String? = null,
        category: String? = null,
        verified: Boolean = false,
        location: String? = null,
    ) = NumberInfo(
        number = "10086",
        markType = mark,
        markCount = count,
        rawMarkLabel = raw,
        bizName = biz,
        bizCategory = category,
        bizVerified = verified,
        location = location,
        source = Sources.AGGREGATED,
    )

    @Test
    fun spamMark_withCount_showsNameAndCount() {
        val label = CallerIdFormatter.label(
            info(mark = MarkType.FRAUD, count = 1283), strings, fixedCount)
        assertEquals("fraud · Reported by 1,283 users", label)
    }

    @Test
    fun spamMark_withoutCount_showsNameOnly() {
        assertEquals("harassment", CallerIdFormatter.label(
            info(mark = MarkType.HARASSMENT), strings, fixedCount))
    }

    @Test
    fun spamMark_winsOverBusinessIdentity() {
        // Anti-impersonation: a verified-looking business number that carries
        // a spam mark must show the spam label, not the business category.
        val label = CallerIdFormatter.label(
            info(mark = MarkType.FRAUD, biz = "顺丰速运客服", category = "快递物流", verified = true),
            strings, fixedCount)
        assertEquals("fraud", label)
    }

    @Test
    fun business_categoryAndVerified() {
        assertEquals("快递物流 · Verified", CallerIdFormatter.label(
            info(biz = "顺丰速运客服", category = "快递物流", verified = true), strings, fixedCount))
    }

    @Test
    fun business_categoryOnly() {
        assertEquals("银行", CallerIdFormatter.label(
            info(biz = "招商银行", category = "银行"), strings, fixedCount))
    }

    @Test
    fun business_blankCategory_unverified_isNull() {
        assertNull(CallerIdFormatter.label(
            info(biz = "某商家", category = "  "), strings, fixedCount))
    }

    @Test
    fun positiveMark_withoutBusiness_showsMarkName() {
        assertEquals("express", CallerIdFormatter.label(
            info(mark = MarkType.EXPRESS), strings, fixedCount))
    }

    @Test
    fun customMark_usesSourceRawLabel() {
        assertEquals("疑似中介", CallerIdFormatter.label(
            info(mark = MarkType.CUSTOM, raw = "疑似中介"), strings, fixedCount))
    }

    @Test
    fun noMarkNoBusiness_isNull() {
        assertNull(CallerIdFormatter.label(info(location = "北京 移动"), strings, fixedCount))
    }

    @Test
    fun format_mapsFlagsAndLocation() {
        val out = CallerIdFormatter.format(
            info(mark = MarkType.TELEMARKETING, count = 5, location = "广东深圳 移动"),
            strings)
        assertTrue(out.isSpam)
        assertFalse(out.isBusiness)
        assertEquals("广东深圳 移动", out.location)
        assertEquals("10086", out.number)
    }

    @Test
    fun format_businessIsNotSpam() {
        val out = CallerIdFormatter.format(info(biz = "顺丰速运客服", category = "快递物流"), strings)
        assertFalse(out.isSpam)
        assertTrue(out.isBusiness)
        assertEquals("顺丰速运客服", out.bizName)
    }
}
