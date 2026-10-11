/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.net.HttpFetcher
import com.android.dialer.numberinfo.rules.RuleSet
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedWebProviderTest {

    private val rules = RuleSet.parse("""
        {
          "schemaVersion": 1,
          "sources": [{
            "id": "so360",
            "confidence": 0.7,
            "request": { "urlTemplate": "https://example.com/s?q={number}" },
            "extract": {
              "type": "regex",
              "fields": {
                "markType":  { "pattern": "被\\d+人标记为“([^”]+)”" },
                "markCount": { "pattern": "被(\\d+)人标记", "coerce": "int" }
              },
              "successRequires": ["markType"]
            },
            "mapping": { "markType": { "疑似诈骗": "fraud", "*": "custom" } }
          },
          {
            "id": "disabled_src",
            "enabled": false,
            "request": { "urlTemplate": "https://example.com/{number}" },
            "extract": { "type": "regex",
                         "fields": { "markType": { "pattern": "x" } } }
          }]
        }
    """.trimIndent())

    private class FakeFetcher(
        private val body: String? = null,
        private val failure: IOException? = null,
    ) : HttpFetcher() {
        var lastUrl: String? = null
        override suspend fun fetch(
            url: String, method: String, headers: Map<String, String>,
            charset: String, timeoutMs: Int, maxBytes: Int,
        ): String {
            lastUrl = url
            failure?.let { throw it }
            return body!!
        }
    }

    private fun provider(fetcher: HttpFetcher, id: String = "so360") =
        RuleBasedWebProvider(id, 200, { rules }, fetcher = fetcher)

    @Test
    fun query_positiveResult() = runBlocking {
        val fetcher = FakeFetcher("<p>该号码被1283人标记为“疑似诈骗”</p>")
        val info = provider(fetcher).query("13800138000")!!
        assertEquals(MarkType.FRAUD, info.markType)
        assertEquals(1283, info.markCount)
        assertEquals("so360", info.source)
    }

    @Test
    fun query_numberIsUrlEncodedIntoTemplate() = runBlocking {
        val fetcher = FakeFetcher("被1人标记为“疑似诈骗”")
        provider(fetcher).query("+14155550100")
        assertEquals("https://example.com/s?q=%2B14155550100", fetcher.lastUrl)
    }

    @Test
    fun query_noData_returnsEmptyMarkerForNegativeCache() = runBlocking {
        val fetcher = FakeFetcher("<html>没有相关结果</html>")
        val info = provider(fetcher).query("13800138000")
        assertNotNull(info)          // answered
        assertTrue(info!!.isEmpty)   // ... but empty -> negative-cache
    }

    @Test
    fun query_networkFailure_returnsNullNotEmpty() = runBlocking {
        val fetcher = FakeFetcher(failure = IOException("timeout"))
        assertNull(provider(fetcher).query("13800138000")) // do NOT cache
    }

    @Test
    fun query_disabledSource_returnsNull() = runBlocking {
        val fetcher = FakeFetcher("irrelevant")
        assertNull(provider(fetcher, id = "disabled_src").query("123"))
    }

    @Test
    fun query_unknownSourceId_returnsNull() = runBlocking {
        val fetcher = FakeFetcher("irrelevant")
        assertNull(provider(fetcher, id = "no_such_rule").query("123"))
    }

    @Test
    fun query_neverThrows() = runBlocking {
        val fetcher = object : HttpFetcher() {
            override suspend fun fetch(
                url: String, method: String, headers: Map<String, String>,
                charset: String, timeoutMs: Int, maxBytes: Int,
            ): String = throw IOException("boom")
        }
        assertNull(provider(fetcher).query("123")) // exception swallowed
    }
}
