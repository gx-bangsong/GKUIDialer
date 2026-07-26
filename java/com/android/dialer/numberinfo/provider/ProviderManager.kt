/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.NumberNormalizer
import com.android.dialer.numberinfo.settings.NumberInfoSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Registry + query orchestrator for all [NumberInfoProvider]s.
 *
 * Fault isolation: online sources are queried concurrently inside a
 * supervisorScope; each source gets its own try-catch AND its own
 * [withTimeoutOrNull] budget, so a hung or crashing source can neither
 * block nor cancel its siblings.
 */
class ProviderManager(private val settings: NumberInfoSettings) {

    private val providers = ArrayList<NumberInfoProvider>()

    @Synchronized
    fun register(provider: NumberInfoProvider) {
        require(providers.none { it.id == provider.id }) {
            "Provider ${provider.id} already registered"
        }
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }

    @Synchronized
    fun onlineProviders(): List<NumberInfoProvider> =
        providers.filter { it.isOnline && it.isEnabled(settings) }

    @Synchronized
    fun localProviders(): List<NumberInfoProvider> =
        providers.filter { !it.isOnline && it.isEnabled(settings) }

    /**
     * Queries all enabled online providers concurrently. Runs on the caller's
     * dispatcher — callers must be on Dispatchers.IO.
     *
     * @param perSourceTimeoutMs hard budget per source; the call-screening path
     *   passes a small value (~800ms), the in-call refresh path a larger one.
     * @param skip predicate to exclude sources that already have fresh cache.
     */
    suspend fun queryOnline(
        number: String,
        perSourceTimeoutMs: Long = DEFAULT_SOURCE_TIMEOUT_MS,
        skip: (sourceId: String) -> Boolean = { false },
    ): List<NumberInfo> {
        val targets = onlineProviders().filterNot { skip(it.id) }
        if (targets.isEmpty()) return emptyList()
        return supervisorScope {
            targets.map { provider ->
                async {
                    try {
                        withTimeoutOrNull(perSourceTimeoutMs) {
                            provider.query(number)
                        }
                    } catch (e: Exception) {
                        // Providers shouldn't throw, but belt-and-suspenders:
                        NumberInfoLog.w("provider=${provider.id} threw for "
                                + NumberNormalizer.maskForLog(number), e)
                        null
                    }
                }
            }.mapNotNull { it.await() }
        }
    }

    /**
     * Synchronous local-only lookup for the in-call fast lane. Providers are
     * iterated by priority; first non-empty result per aspect is merged by the
     * aggregator upstream — here we simply collect everything local.
     */
    suspend fun queryLocal(number: String): List<NumberInfo> =
        localProviders().mapNotNull { provider ->
            try {
                provider.query(number)
            } catch (e: Exception) {
                NumberInfoLog.w("local provider=${provider.id} failed", e)
                null
            }
        }

    companion object {
        const val DEFAULT_SOURCE_TIMEOUT_MS = 3000L
        /** Budget used from CallScreeningService (which has a 5s hard limit). */
        const val SCREENING_SOURCE_TIMEOUT_MS = 800L
    }
}
