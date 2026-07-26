/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.aggregate

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberInfoAggregatorTest {

    private val aggregator = NumberInfoAggregator()
    private val n = "13800138000"

    private fun info(
        source: String,
        markType: MarkType = MarkType.NONE,
        markCount: Int = 0,
        confidence: Float = 0.5f,
        bizName: String? = null,
        bizVerified: Boolean = false,
        location: String? = null,
        rawLabel: String? = null,
    ) = NumberInfo(
        number = n, markType = markType, markCount = markCount,
        rawMarkLabel = rawLabel, bizName = bizName, bizVerified = bizVerified,
        location = location, source = source, confidence = confidence)

    @Test
    fun aggregate_emptyInputsReturnNull() {
        assertNull(aggregator.aggregate(n, null, emptyList()))
        assertNull(aggregator.aggregate(n, null,
            listOf(info("so360")))) // isEmpty result filtered out
    }

    @Test
    fun aggregate_manualMarkWins() {
        val manual = info(Sources.MANUAL, MarkType.EXPRESS, confidence = 1f)
        val online = listOf(
            info("so360", MarkType.FRAUD, 9999, 0.9f),
            info("baidu", MarkType.FRAUD, 9999, 0.9f))
        val out = aggregator.aggregate(n, manual, online)!!
        assertEquals(MarkType.EXPRESS, out.markType)
        assertEquals(Sources.MANUAL, out.source)
    }

    @Test
    fun aggregate_manualMarkEnrichedWithOnlineLocation() {
        val manual = info(Sources.MANUAL, MarkType.HARASSMENT, confidence = 1f)
        val online = listOf(info("so360", MarkType.FRAUD, 10, 0.7f, location = "北京"))
        val out = aggregator.aggregate(n, manual, online)!!
        assertEquals(MarkType.HARASSMENT, out.markType)
        assertEquals("北京", out.location)
    }

    @Test
    fun aggregate_votingSumsConfidencePerType() {
        // fraud: 0.6 + 0.6 = 1.2 beats telemarketing: 0.9
        val results = listOf(
            info("so360", MarkType.FRAUD, 100, 0.6f),
            info("sogou", MarkType.FRAUD, 80, 0.6f),
            info("haoma", MarkType.TELEMARKETING, 5000, 0.9f))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals(MarkType.FRAUD, out.markType)
        assertEquals(100, out.markCount) // max within winning group
    }

    @Test
    fun aggregate_tieBrokenByMarkCount() {
        val results = listOf(
            info("so360", MarkType.FRAUD, 10, 0.5f),
            info("baidu", MarkType.HARASSMENT, 900, 0.5f))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals(MarkType.HARASSMENT, out.markType)
        assertEquals(900, out.markCount)
    }

    @Test
    fun aggregate_bizPrefersVerifiedThenConfidence() {
        val results = listOf(
            info("so360", bizName = "山寨客服", confidence = 0.9f),
            info("haoma", bizName = "顺丰速运客服", bizVerified = true, confidence = 0.7f))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals("顺丰速运客服", out.bizName)
        assertTrue(out.bizVerified)
    }

    @Test
    fun aggregate_bizAndMarkCoexist() {
        // A bank hotline that some users also marked as telemarketing.
        val results = listOf(
            info("haoma", bizName = "招商银行", bizVerified = true, confidence = 0.9f),
            info("so360", MarkType.TELEMARKETING, 23, 0.7f))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals("招商银行", out.bizName)
        assertEquals(MarkType.TELEMARKETING, out.markType)
        assertEquals(23, out.markCount)
    }

    @Test
    fun aggregate_locationOnlyStillReturned() {
        val results = listOf(info("so360", location = "广东深圳 移动"))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals("广东深圳 移动", out.location)
        assertEquals(MarkType.NONE, out.markType)
        assertFalse(out.isBusiness)
    }

    @Test
    fun aggregate_locationTakenFromHighestConfidenceSource() {
        val results = listOf(
            info("baidu", MarkType.FRAUD, 1, 0.3f, location = "上海"),
            info("haoma", MarkType.FRAUD, 1, 0.9f, location = "北京"))
        val out = aggregator.aggregate(n, null, results)!!
        assertEquals("北京", out.location)
    }

    @Test
    fun aggregate_outputSourceIsAggregated() {
        val results = listOf(info("so360", MarkType.FRAUD, 10, 0.5f))
        assertEquals(Sources.AGGREGATED,
            aggregator.aggregate(n, null, results)!!.source)
    }
}
