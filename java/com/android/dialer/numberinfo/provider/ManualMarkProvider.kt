/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources
import com.android.dialer.numberinfo.cache.ManualMarkDao

/**
 * User's manual marks. Local, synchronous, highest priority — the aggregator
 * short-circuits on a manual result (user corrections beat any online source).
 */
class ManualMarkProvider(private val dao: ManualMarkDao) : NumberInfoProvider {

    override val id: String get() = Sources.MANUAL
    override val priority: Int get() = PRIORITY
    override val isOnline: Boolean get() = false

    override suspend fun query(number: String): NumberInfo? = queryBlocking(number)

    /** Synchronous path for the in-call fast lane (single indexed PK lookup). */
    fun queryBlocking(number: String): NumberInfo? =
        try {
            dao.get(number)
        } catch (e: Exception) {
            null
        }

    companion object {
        const val PRIORITY = 1000
    }
}
