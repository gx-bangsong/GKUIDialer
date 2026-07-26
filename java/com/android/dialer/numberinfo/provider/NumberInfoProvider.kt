/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.settings.NumberInfoSettings

/**
 * A single number-information data source.
 *
 * Contract:
 *  - [query] must NEVER throw; failures degrade to null and are logged inside
 *    the implementation. ProviderManager additionally wraps every call in its
 *    own try-catch + timeout, so one broken source can never take down another.
 *  - [query] runs on Dispatchers.IO (dispatched by ProviderManager); local
 *    providers should still return quickly (<5ms) because the in-call UI
 *    queries them synchronously via [ProviderManager.queryLocal].
 */
interface NumberInfoProvider {
    /** Stable id; for rule-driven providers this equals the rules.json source id. */
    val id: String

    /** Higher wins in aggregation ties. manual=1000, local_yp=900, haoma=500, web=100..300. */
    val priority: Int

    /** Online providers are gated by the privacy switches. */
    val isOnline: Boolean

    fun isEnabled(settings: NumberInfoSettings): Boolean =
        if (isOnline) settings.isOnlineSourceAllowed(id) else true

    /**
     * @param number normalized number ([com.android.dialer.numberinfo.NumberNormalizer])
     * @return normalized info or null when the source has no data / failed.
     */
    suspend fun query(number: String): NumberInfo?
}
