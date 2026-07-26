/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.net.HttpFetcher
import com.android.dialer.numberinfo.rules.ParseRuleEngine
import com.android.dialer.numberinfo.rules.RuleSet
import java.io.IOException
import java.net.URLEncoder

/**
 * Generic rule-driven web provider. 360 搜索 / 百度搜索 / 搜狗搜索 / 百度号码库
 * are all instances of this single class — they differ only in [id], which
 * selects the matching source block in rules.json. Adding a new web source is
 * therefore: (1) add a block to rules.json, (2) register one more instance.
 *
 * Result semantics for [query] (consumed by the repository's cache logic):
 *  - non-empty NumberInfo ......... source answered with data (positive-cache)
 *  - NumberInfo with isEmpty=true . source answered, no data (negative-cache)
 *  - null ......................... transport failure / timeout (do NOT cache)
 */
open class RuleBasedWebProvider(
    override val id: String,
    override val priority: Int,
    /** Supplies current rules; usually `ruleRepository::get`. Indirection keeps
     *  this class free of Context for plain-JVM unit tests. */
    private val ruleSetProvider: () -> RuleSet,
    private val engine: ParseRuleEngine = ParseRuleEngine(),
    private val fetcher: HttpFetcher = HttpFetcher(),
) : NumberInfoProvider {

    override val isOnline: Boolean get() = true

    class SourceUnavailableException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    override suspend fun query(number: String): NumberInfo? =
        try {
            queryOrThrow(number)
                // Distinguishable "answered: no data" marker for negative-caching.
                ?: NumberInfo(number = number, source = id)
        } catch (e: SourceUnavailableException) {
            null
        }

    /**
     * @throws SourceUnavailableException network / transport failure
     * @return null when the source answered but has no data for this number
     */
    @Throws(SourceUnavailableException::class)
    open suspend fun queryOrThrow(number: String): NumberInfo? {
        val rule = ruleSetProvider().source(id)
        if (rule == null || !rule.enabled) {
            NumberInfoLog.d("source=$id disabled or missing in rules")
            // Not a "no data" answer — must not be negative-cached.
            throw SourceUnavailableException("source $id disabled/missing")
        }
        val url = rule.request.urlTemplate.replace(
            "{number}", URLEncoder.encode(number, "UTF-8"))
        val document = try {
            fetcher.fetch(
                url = url,
                method = rule.request.method,
                headers = rule.request.headers,
                charset = rule.request.charset,
                timeoutMs = rule.request.timeoutMs,
            )
        } catch (e: IOException) {
            NumberInfoLog.ruleFailure(id, null, number, "fetch failed: $e")
            throw SourceUnavailableException("fetch failed for $id", e)
        }
        return engine.execute(rule, number, document)
    }
}
