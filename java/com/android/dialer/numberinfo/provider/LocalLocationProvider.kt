/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.location.NumberLocationLoader

/**
 * Offline geo/carrier provider. Location-only NumberInfo; the aggregator
 * merges it under whatever mark/business identity other sources supply.
 */
class LocalLocationProvider(private val loader: NumberLocationLoader) : NumberInfoProvider {

    override val id: String get() = ID
    override val priority: Int get() = PRIORITY
    override val isOnline: Boolean get() = false

    override suspend fun query(number: String): NumberInfo? {
        val location = try {
            loader.get(number)
        } catch (e: Exception) {
            null
        } ?: return null
        return NumberInfo(
            number = number,
            location = location,
            source = ID,
            confidence = CONFIDENCE,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    companion object {
        const val ID = "local_geo"
        const val PRIORITY = 800
        /** Static offline table: trustworthy for location, carries nothing else. */
        const val CONFIDENCE = 0.9f
    }
}
