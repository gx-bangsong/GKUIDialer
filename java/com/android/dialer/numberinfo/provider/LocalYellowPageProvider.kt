/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import com.android.dialer.yellowpage.YellowPageStore

/**
 * Offline yellow-page provider: local, synchronous, no privacy gate.
 * Registered at priority 900 so a local hit beats every online source
 * except the user's own manual mark.
 */
class LocalYellowPageProvider(private val store: YellowPageStore) : NumberInfoProvider {

    override val id: String get() = Sources.LOCAL_YELLOW_PAGE
    override val priority: Int get() = PRIORITY
    override val isOnline: Boolean get() = false

    override suspend fun query(number: String): NumberInfo? = queryBlocking(number)

    /** Synchronous fast lane for the in-call first frame. */
    fun queryBlocking(number: String): NumberInfo? {
        val entry = try {
            store.get(number)
        } catch (e: Exception) {
            null
        } ?: return null
        return NumberInfo(
            number = number,
            bizName = entry.name,
            bizCategory = entry.category,
            bizVerified = entry.verified,
            source = Sources.LOCAL_YELLOW_PAGE,
            confidence = CONFIDENCE,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    companion object {
        const val PRIORITY = 900
        /** Curated local data outranks scraped web results. */
        const val CONFIDENCE = 0.95f
    }
}
