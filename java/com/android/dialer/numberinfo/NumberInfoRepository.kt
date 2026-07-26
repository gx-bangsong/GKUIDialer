/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.PhoneLookup
import com.android.dialer.numberinfo.aggregate.NumberInfoAggregator
import com.android.dialer.numberinfo.cache.ManualMarkDao
import com.android.dialer.numberinfo.cache.NumberInfoCacheDao
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.numberinfo.settings.NumberInfoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val context: Context,
    private val settings: NumberInfoSettings,
    private val providerManager: ProviderManager,
    private val cacheDao: NumberInfoCacheDao,
    private val manualMarkDao: ManualMarkDao,
    private val aggregator: NumberInfoAggregator = NumberInfoAggregator(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Serializes per-number online refreshes to avoid duplicate fetches. */
    private val refreshMutex = Mutex()

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
        if (isContact(number)) return@withContext null

        val local = providerManager.queryLocal(number)
        val cached = try {
            cacheDao.getValid(number, settings.cacheTtlMillis, settings.negativeCacheTtlMillis)
        } catch (e: Exception) {
            emptyList()
        }

        var online: List<NumberInfo> = emptyList()
        if (settings.onlineLookupEnabled) {
            online = refreshMutex.withLock {
                fetchAndCacheOnline(number, perSourceTimeoutMs)
            }
        }
        aggregator.aggregate(number, manualOf(local), local + cached + online)
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
                if (isContact(number)) return@launch
                val fetched = refreshMutex.withLock {
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

    /** Contacts are never looked up online (hard privacy rule). */
    internal fun isContact(number: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(number).build()
            context.contentResolver.query(
                uri, arrayOf(PhoneLookup._ID), null, null, null,
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
