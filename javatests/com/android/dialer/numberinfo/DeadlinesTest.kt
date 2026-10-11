/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeadlinesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun awaitWithin_returnsResultOfFastBlock() = runBlocking {
        assertEquals("ok", awaitWithin(1_000, scope) { "ok" })
    }

    @Test
    fun awaitWithin_returnsOnTime_evenWhenBlockIsStuckInBlockingCall() = runBlocking {
        val started = System.nanoTime()
        // Thread.sleep stands in for DNS / socket reads / binder calls that
        // ignore coroutine cancellation.
        val result = awaitWithin(200, scope) { Thread.sleep(3_000); "late" }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("waited ${elapsedMs}ms for a 200ms budget", elapsedMs < 1_000)
    }

    @Test
    fun awaitWithin_rethrowsBlockFailure() = runBlocking {
        try {
            awaitWithin(1_000, scope) { throw IOException("boom") }
            fail("expected IOException")
        } catch (expected: IOException) {
        }
        // A failing block must not cancel the shared scope.
        assertEquals(1, awaitWithin(1_000, scope) { 1 })
    }
}
