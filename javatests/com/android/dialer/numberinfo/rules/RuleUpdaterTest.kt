/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleUpdaterTest {

    private fun source(id: String) = """
        {
          "id": "$id",
          "request": { "urlTemplate": "https://x/{number}" },
          "extract": { "type": "regex",
            "fields": { "markType": { "pattern": "m:(\\w+)" } } }
        }
    """.trimIndent()

    private fun ruleSet(vararg ids: String) =
        """{ "schemaVersion": 1, "sources": [ ${ids.joinToString(",") { source(it) }} ] }"""

    private val installed = RuleSet.parse(ruleSet("a"))

    private class Recorder {
        var fetched: String? = null
        var installedBody: String? = null
        var installCalls = 0
    }

    private fun updater(
        rec: Recorder,
        fetch: (String) -> String = { rec.fetched = it; ruleSet("b") },
        installFails: Boolean = false,
    ) = RuleUpdater(
        current = { installed },
        fetch = fetch,
        install = { body, _ ->
            rec.installCalls++
            if (installFails) throw IOException("disk full")
            rec.installedBody = body
        },
    )

    @Test
    fun nullOrBlankUrl_isNotConfigured_andNothingIsFetched() {
        val rec = Recorder()
        assertEquals(RuleUpdateResult.NotConfigured, updater(rec).update(null))
        assertEquals(RuleUpdateResult.NotConfigured, updater(rec).update("   "))
        assertEquals(null, rec.fetched)
        assertEquals(0, rec.installCalls)
    }

    @Test
    fun networkFailure_isFailed_andInstallsNothing() {
        val rec = Recorder()
        val result = updater(rec, fetch = { throw IOException("timeout") })
            .update("https://rules.example/r.json")
        assertTrue(result is RuleUpdateResult.Failed)
        assertEquals(0, rec.installCalls)
    }

    @Test
    fun invalidDownload_isFailed_andTheInstalledRulesAreKept() {
        val rec = Recorder()
        val result = updater(rec, fetch = { "<html>captive portal</html>" })
            .update("https://rules.example/r.json")
        assertTrue(result is RuleUpdateResult.Failed)
        assertEquals(0, rec.installCalls)
    }

    @Test
    fun identicalRemoteRules_isUpToDate_withoutReinstalling() {
        val rec = Recorder()
        val result = updater(rec, fetch = { ruleSet("a") }).update("https://rules.example/r.json")
        assertEquals(RuleUpdateResult.UpToDate, result)
        assertEquals(0, rec.installCalls)
    }

    @Test
    fun newRemoteRules_areInstalled_andReportUpdated() {
        val rec = Recorder()
        val body = ruleSet("b")
        val result = updater(rec, fetch = { body }).update("https://rules.example/r.json")
        assertEquals(RuleUpdateResult.Updated, result)
        assertEquals(1, rec.installCalls)
        assertEquals(body, rec.installedBody)
    }

    @Test
    fun installFailure_isFailed_notUpdated() {
        val rec = Recorder()
        val result = updater(rec, fetch = { ruleSet("b") }, installFails = true)
            .update("https://rules.example/r.json")
        assertTrue(result is RuleUpdateResult.Failed)
        assertEquals(1, rec.installCalls)
    }

    @Test
    fun resultsAreDistinct_soTheUiCanWordEachOne() {
        val all = listOf(
            RuleUpdateResult.Updated, RuleUpdateResult.UpToDate,
            RuleUpdateResult.NotConfigured, RuleUpdateResult.Failed("network"))
        assertEquals(all.size, all.toSet().size)
    }
}
