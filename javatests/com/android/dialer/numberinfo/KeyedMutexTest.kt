/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyedMutexTest {

    @Test
    fun differentKeys_doNotWaitForEachOther() = runBlocking {
        val locks = KeyedMutex<String>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) {
            locks.withLock("13800138000") { release.await() }
        }
        // Must complete while the other number's lock is still held.
        withTimeout(1_000) {
            locks.withLock("17012345678") { }
        }
        release.complete(Unit)
        holder.await()
    }

    @Test
    fun sameKey_isSerialized() = runBlocking {
        val locks = KeyedMutex<String>()
        val inside = AtomicInteger(0)
        val maxInside = AtomicInteger(0)
        (1..8).map {
            async(Dispatchers.Default) {
                locks.withLock("13800138000") {
                    maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
                    delay(10)
                    inside.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, maxInside.get())
    }

    @Test
    fun entriesAreReleasedAfterUse() = runBlocking {
        val locks = KeyedMutex<String>()
        (1..50).map { i ->
            async(Dispatchers.Default) { locks.withLock("n$i") { delay(1) } }
        }.awaitAll()
        try {
            locks.withLock("boom") { throw IllegalStateException() }
        } catch (expected: IllegalStateException) {
        }
        assertEquals(0, locks.activeKeys())
    }
}
