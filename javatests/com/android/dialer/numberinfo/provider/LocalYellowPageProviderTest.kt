/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.Sources
import com.android.dialer.yellowpage.YellowPageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalYellowPageProviderTest {

    private class MapStore(private val rows: Map<String, YellowPageStore.Entry>) : YellowPageStore {
        override fun get(number: String): YellowPageStore.Entry? = rows[number]
    }

    private class ThrowingStore : YellowPageStore {
        override fun get(number: String): YellowPageStore.Entry? = throw IllegalStateException("corrupt db")
    }

    private val store = MapStore(
        mapOf(
            "95338" to YellowPageStore.Entry("95338", "顺丰速运客服", "快递物流", verified = true),
            "95013" to YellowPageStore.Entry("95013", "联通在信", "呼叫中心", verified = false),
        ),
    )

    @Test
    fun hit_returnsBusinessInfoFromLocalSource() = runBlocking {
        val info = LocalYellowPageProvider(store).query("95338")!!
        assertEquals("95338", info.number)
        assertEquals("顺丰速运客服", info.bizName)
        assertEquals("快递物流", info.bizCategory)
        assertTrue(info.bizVerified)
        assertTrue(info.isBusiness)
        assertEquals(Sources.LOCAL_YELLOW_PAGE, info.source)
    }

    @Test
    fun unverifiedEntry_keepsVerifiedFalse() = runBlocking {
        val info = LocalYellowPageProvider(store).query("95013")!!
        assertFalse(info.bizVerified)
    }

    @Test
    fun miss_returnsNull() = runBlocking {
        assertNull(LocalYellowPageProvider(store).query("10001"))
    }

    @Test
    fun storeException_degradesToNull() = runBlocking {
        assertNull(LocalYellowPageProvider(ThrowingStore()).query("95338"))
    }

    @Test
    fun providerIsLocalAndRanksAboveWebSources() {
        val provider = LocalYellowPageProvider(store)
        assertFalse(provider.isOnline)
        assertEquals(Sources.LOCAL_YELLOW_PAGE, provider.id)
        assertTrue(provider.priority > 500) // above haoma, the highest web source
        assertTrue(provider.priority < 1000) // below the user's manual mark
    }
}
