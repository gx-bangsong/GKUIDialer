/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.yellowpage

/**
 * Read-only source of offline yellow-page entries. Interface split so the
 * provider can be unit-tested on a plain JVM with an in-memory fake.
 */
interface YellowPageStore {
    data class Entry(
        val number: String,
        val name: String,
        val category: String?,
        val verified: Boolean,
    )

    /** Point lookup by normalized number. Must be fast (<1ms) and never throw. */
    fun get(number: String): Entry?
}
