/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberLocationLoaderTest {

    @Test
    fun missingAsset_returnsNull_andIsOpenedOnlyOnce() {
        var opens = 0
        val loader = NumberLocationLoader {
            opens++
            throw IOException("no such asset")
        }
        repeat(5) { assertNull(loader.get("13800138000")) }
        repeat(5) { assertNull(loader.get("13910001234")) }
        assertEquals(1, opens)
        assertEquals(1, loader.openAttempts())
    }

    @Test
    fun presentAsset_servesMobilePrefixes_andIsOpenedOnlyOnce() {
        var opens = 0
        val csv = "# comment\n1380013,北京,北京,移动\n"
        val loader = NumberLocationLoader {
            opens++
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        }
        assertEquals("北京 · 移动", loader.get("13800130000"))
        assertEquals("北京 · 移动", loader.get("13800130001"))
        assertNull(loader.get("13900130000"))
        assertEquals(1, opens)
    }

    @Test
    fun landlineNumbers_neverOpenTheAsset() {
        var opens = 0
        val loader = NumberLocationLoader {
            opens++
            throw IOException("should not be opened for landlines")
        }
        assertNull(loader.get("01012345678"))
        assertNull(loader.get("075512345678"))
        assertEquals(0, opens)
    }
}
