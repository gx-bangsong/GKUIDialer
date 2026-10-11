/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallLogLabelsTest {

    private fun info(
        bizName: String? = null,
        label: String? = null,
        location: String? = "北京市 · 中国移动",
        isSpam: Boolean = false,
    ) = CallerIdInfo(
        number = "10086", bizName = bizName, label = label, location = location,
        isSpam = isSpam, isBusiness = bizName != null)

    @Test
    fun businessName_replacesTheNumber() {
        assertEquals("顺丰速运客服", CallLogLabels.nameFor(info(bizName = "顺丰速运客服")))
    }

    @Test
    fun blankOrMissingBusinessName_isNull() {
        assertNull(CallLogLabels.nameFor(info()))
        assertNull(CallLogLabels.nameFor(info(bizName = "  ")))
    }

    @Test
    fun spamLabel_replacesTheLocationLine() {
        assertEquals("疑似诈骗 · 被 1,283 人标记",
            CallLogLabels.spamLineFor(info(label = "疑似诈骗 · 被 1,283 人标记", isSpam = true)))
    }

    @Test
    fun positiveMark_keepsTheLocation() {
        // Express / taxi marks are not spam: the location line stays.
        assertNull(CallLogLabels.spamLineFor(info(label = "快递外卖", isSpam = false)))
    }

    @Test
    fun businessWithoutSpam_keepsTheLocation() {
        assertNull(CallLogLabels.spamLineFor(info(bizName = "顺丰速运客服", label = "快递物流")))
    }
}
