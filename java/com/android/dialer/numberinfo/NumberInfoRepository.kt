/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import com.android.dialer.numberinfo.aggregate.NumberInfoAggregator
import com.android.dialer.numberinfo.cache.ManualMarkDao
import com.android.dialer.numberinfo.cache.NumberInfoCacheDao
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.numberinfo.settings.NumberInfoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single façade over the numberinfo stack. Orchestrates:
 *
 *   manual mark  ─┐ (sync, fast lane)
 *   local sources ┤→ peekLocal() → instant first-frame display
 *   fresh cache  ─┘
 *   online sources → query()/refreshOnline() → cache → aggregate → callback
 *
 * Hard privacy rule enforced here: numbers present in the user's contacts are
 * NEVER queried online (they aren't queried at all — the dialer already shows
 * the contact).
 */
class NumberInfoRepository(
    private val contacts: ContactChecker,
    private val settings: NumberInfoSettings,
    private val providerManager: ProviderManager,
    private val cacheDao: NumberInfoCacheDao,
    private val manualMarkDao: ManualMarkDao,
    private val aggregator: NumberInfoAggregator = NumberInfoAggregator(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * Serializes online refreshes of the *same* number (the second caller then
     * finds fresh cache instead of fetching again). Different numbers never
     * wait for each other — a slow in-call refresh must not eat the screening
     * budget of the next incoming call.
     */
    private val refreshLocks = KeyedMutex<String>()

    fun interface Listener {
        /** Invoked on an IO thread; hop to main before touching views. */
        fun onNumberInfoUpdated(number: String, info: NumberInfo?)
    }

    /**
     * Local-only, synchronous-ish lookup for the in-call fast lane: manual mark,
     * local providers and *fresh* cache — no network, bounded latency.
     * Safe to call via runBlocking from latency-critical paths.
     */
    suspend fun peekLocal(rawNumber: String?): NumberInfo? {
        val number = NumberNormalizer.normalize(rawNumber) ?: return null
        val local = providerManager.queryLocal(number)
        val cached = try {
            cacheDao.getValid(number, settings.cacheTtlMillis, settings.negativeCacheTtlMillis)
        } catch (e: Exception) {
            emptyList()
        }
        return aggregator.aggregate(number, manualOf(local), local + cached)
    }

    /**
     * Full lookup: local first, then online sources for anything not freshly
     * cached. Blocking within the coroutine; run on Dispatchers.IO.
     */
    suspend fun query(
        rawNumber: String?,
        perSourceTimeoutMs: Long = ProviderManager.DEFAULT_SOURCE_TIMEOUT_MS,
    ): NumberInfo? = withContext(Dispatchers.IO) {
        val number = NumberNormalizer.normalize(rawNumber) ?: return@withContext null
        if (contacts.check(number) != ContactStatus.NOT_CONTACT) return@withContext null

        val local = providerManager.queryLocal(number)

        var online: List<NumberInfo> = emptyList()
        if (settings.onlineLookupEnabled) {
            online = refreshLocks.withLock(number) {
                fetchAndCacheOnline(number, perSourceTimeoutMs)
            }
        }
        // Read the cache after the online phase so results stored by a
        // concurrent refresh of this number (which we may have waited for)
        // are included. Fresh online results win over cached rows per source.
        val cached = try {
            cacheDao.getValid(number, settings.cacheTtlMillis, settings.negativeCacheTtlMillis)
        } catch (e: Exception) {
            emptyList()
        }
        val onlineSources = online.mapTo(HashSet()) { it.source }
        val merged = online + cached.filter { it.source !in onlineSources }
        aggregator.aggregate(number, manualOf(local), local + merged)
    }

    /** The manual mark travels through ManualMarkProvider like any source;
     *  the aggregator additionally receives it as the short-circuit argument. */
    private fun manualOf(local: List<NumberInfo>): NumberInfo? =
        local.firstOrNull { it.source == Sources.MANUAL && !it.isEmpty }

    /**
     * Fire-and-forget online refresh for the in-call UI: shows local data first
     * via [peekLocal], then updates through [listener] once online sources land.
     */
    fun refreshOnlineAsync(rawNumber: String?, listener: Listener) {
        val number = NumberNormalizer.normalize(rawNumber) ?: return
        if (!settings.onlineLookupEnabled) return
        scope.launch {
            try {
                if (contacts.check(number) != ContactStatus.NOT_CONTACT) return@launch
                val fetched = refreshLocks.withLock(number) {
                    fetchAndCacheOnline(number, ProviderManager.DEFAULT_SOURCE_TIMEOUT_MS)
                }
                if (fetched.isEmpty()) return@launch
                listener.onNumberInfoUpdated(number, peekLocal(number))
            } catch (e: Exception) {
                NumberInfoLog.w("online refresh failed for "
                        + NumberNormalizer.maskForLog(number), e)
            }
        }
    }

    /** User marks / corrects a number. Manual data beats all online sources. */
    suspend fun setManualMark(rawNumber: String?, markType: MarkType, note: String? = null) =
        withContext(Dispatchers.IO) {
            val number = NumberNormalizer.normalize(rawNumber) ?: return@withContext
            if (markType == MarkType.NONE) {
                manualMarkDao.remove(number)
            } else {
                manualMarkDao.put(number, markType, note)
            }
        }

    /** Queries online providers that lack fresh cache; writes back results. */
    private suspend fun fetchAndCacheOnline(
        number: String,
        perSourceTimeoutMs: Long,
    ): List<NumberInfo> {
        val results = providerManager.queryOnline(
            number,
            perSourceTimeoutMs = perSourceTimeoutMs,
            skip = { sourceId ->
                try {
                    cacheDao.hasFresh(
                        number, sourceId,
                        settings.cacheTtlMillis, settings.negativeCacheTtlMillis)
                } catch (e: Exception) {
                    false
                }
            },
        )
        val now = System.currentTimeMillis()
        val answered = results.associateBy { it.source }
        for (provider in providerManager.onlineProviders()) {
            val info = answered[provider.id]
            try {
                when {
                    info != null && !info.isEmpty -> cacheDao.upsert(info)
                    info != null ->
                        // Source answered with an empty result: negative-cache.
                        cacheDao.upsertNegative(number, provider.id, now)
                    // No result at all = timeout/transport failure — do NOT
                    // negative-cache; the source may just be down.
                }
            } catch (e: Exception) {
                NumberInfoLog.w("cache write failed for source=${provider.id}", e)
            }
        }
        return results.filter { !it.isEmpty }
    }

    /** The user's own mark for [rawNumber], or null. Local only. */
    suspend fun manualMark(rawNumber: String?): NumberInfo? = withContext(Dispatchers.IO) {
        val number = NumberNormalizer.normalize(rawNumber) ?: return@withContext null
        manualMarkDao.get(number)
    }

    /** Removes the user's mark; automatic data (yellow pages, online sources) applies again. */
    suspend fun clearManualMark(rawNumber: String?) = withContext(Dispatchers.IO) {
        val number = NumberNormalizer.normalize(rawNumber) ?: return@withContext
        manualMarkDao.remove(number)
    }

    /** Deletes cached online results. Manual marks and local data are kept. Blocking. */
    fun clearOnlineCache() {
        cacheDao.clearAll()
    }

    /**
     * Contact status for a raw number. Blocking (contacts provider); call from
     * a background thread. Anything other than [ContactStatus.NOT_CONTACT] must
     * be treated as "do not look this number up online".
     */
    fun contactStatus(rawNumber: String?): ContactStatus {
        val number = NumberNormalizer.normalize(rawNumber) ?: return ContactStatus.UNKNOWN
        return contacts.check(number)
    }
}
