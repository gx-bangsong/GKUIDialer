/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One mutex per key. Work for the same key is serialized; work for different
 * keys never waits on each other. Entries are dropped as soon as no coroutine
 * holds or waits for them, so memory stays bounded by in-flight keys.
 */
internal class KeyedMutex<K : Any> {

    private class Entry {
        val mutex = Mutex()
        var users = 0 // guarded by `entries`
    }

    private val entries = HashMap<K, Entry>()

    suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) {
                if (--entry.users == 0) entries.remove(key)
            }
        }
    }

    /** Number of keys currently held or awaited (for tests). */
    fun activeKeys(): Int = synchronized(entries) { entries.size }
}
