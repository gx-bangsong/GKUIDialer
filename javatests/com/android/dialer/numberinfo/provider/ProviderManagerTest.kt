/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.testing.FakeNumberInfoSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderManagerTest {

    private val settings = FakeNumberInfoSettings(onlineLookupEnabled = true)

    private fun fakeProvider(
        id: String,
        online: Boolean = true,
        priority: Int = 100,
        block: suspend (String) -> NumberInfo?,
    ) = object : NumberInfoProvider {
        override val id = id
        override val priority = priority
        override val isOnline = online
        override suspend fun query(number: String) = block(number)
    }

    private fun result(id: String, mark: MarkType = MarkType.FRAUD) =
        NumberInfo(number = "1", markType = mark, source = id, confidence = 0.5f)

    @Test
    fun queryOnline_collectsAllSources() = runBlocking {
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("a") { result("a") })
        pm.register(fakeProvider("b") { result("b") })
        val out = pm.queryOnline("1")
        assertEquals(setOf("a", "b"), out.map { it.source }.toSet())
    }

    @Test
    fun queryOnline_oneCrashingSourceDoesNotAffectOthers() = runBlocking {
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("bad") { throw RuntimeException("boom") })
        pm.register(fakeProvider("good") { result("good") })
        val out = pm.queryOnline("1")
        assertEquals(listOf("good"), out.map { it.source })
    }

    @Test
    fun queryOnline_hungSourceIsTimedOutIndependently() = runBlocking {
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("hung") { delay(60_000); result("hung") })
        pm.register(fakeProvider("fast") { result("fast") })
        val started = System.currentTimeMillis()
        val out = pm.queryOnline("1", perSourceTimeoutMs = 200)
        val elapsed = System.currentTimeMillis() - started
        assertEquals(listOf("fast"), out.map { it.source })
        assertTrue("timed out too slowly: ${elapsed}ms", elapsed < 5_000)
    }

    @Test
    fun queryOnline_skipPredicateExcludesFreshSources() = runBlocking {
        val pm = ProviderManager(settings)
        var aCalled = false
        pm.register(fakeProvider("a") { aCalled = true; result("a") })
        pm.register(fakeProvider("b") { result("b") })
        val out = pm.queryOnline("1", skip = { it == "a" })
        assertEquals(listOf("b"), out.map { it.source })
        assertFalse(aCalled)
    }

    @Test
    fun queryOnline_masterSwitchOffDisablesAllOnlineSources() = runBlocking {
        val pm = ProviderManager(FakeNumberInfoSettings(onlineLookupEnabled = false))
        var called = false
        pm.register(fakeProvider("a") { called = true; result("a") })
        assertTrue(pm.queryOnline("1").isEmpty())
        assertFalse(called)
    }

    @Test
    fun queryOnline_perSourceSwitchDisablesSingleSource() = runBlocking {
        settings.setSourceEnabled("a", false)
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("a") { result("a") })
        pm.register(fakeProvider("b") { result("b") })
        assertEquals(listOf("b"), pm.queryOnline("1").map { it.source })
    }

    @Test
    fun queryLocal_onlyLocalProviders_andIgnoresMasterSwitch() = runBlocking {
        val pm = ProviderManager(FakeNumberInfoSettings(onlineLookupEnabled = false))
        pm.register(fakeProvider("web") { result("web") })
        pm.register(fakeProvider("local", online = false, priority = 900) {
            result("local", MarkType.NONE).copy(bizName = "顺丰速运客服")
        })
        val out = pm.queryLocal("1")
        assertEquals(listOf("local"), out.map { it.source })
    }

    @Test
    fun queryOnline_blockingSourceIsTimedOutOnTime() = runBlocking {
        // Real sources block inside HttpURLConnection (DNS, connect, read), which
        // withTimeoutOrNull alone cannot interrupt. The budget must still hold.
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("blocking") { Thread.sleep(3_000); result("blocking") })
        pm.register(fakeProvider("fast") { result("fast") })
        val started = System.nanoTime()
        val out = pm.queryOnline("1", perSourceTimeoutMs = 300)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(listOf("fast"), out.map { it.source })
        assertTrue("queryOnline took ${elapsedMs}ms with a 300ms budget", elapsedMs < 1_500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun register_duplicateIdRejected() {
        val pm = ProviderManager(settings)
        pm.register(fakeProvider("a") { null })
        pm.register(fakeProvider("a") { null })
    }
}
