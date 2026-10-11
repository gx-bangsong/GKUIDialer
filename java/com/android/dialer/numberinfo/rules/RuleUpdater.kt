/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import com.android.dialer.numberinfo.NumberInfoLog

/** Outcome of a remote rule refresh. Each case has its own message in the settings UI. */
sealed class RuleUpdateResult {
    /** A newer or different rule set was downloaded, validated and installed. */
    object Updated : RuleUpdateResult()

    /** The remote rule set is identical to the installed one. */
    object UpToDate : RuleUpdateResult()

    /** No update URL is configured (empty `ruleUpdateUrl`); nothing was fetched. */
    object NotConfigured : RuleUpdateResult()

    /** Download, validation or installation failed; the installed rules are unchanged. */
    data class Failed(val reason: String) : RuleUpdateResult()
}

/**
 * Pure download-validate-install flow for remote rules, with the platform
 * pieces injected so it can be tested on a plain JVM. Blocking; call from a
 * background thread only.
 *
 * Nothing is installed unless the whole body parses as a [RuleSet], so a broken
 * download can never replace working rules.
 */
class RuleUpdater(
    private val current: () -> RuleSet,
    private val fetch: (url: String) -> String,
    private val install: (body: String, parsed: RuleSet) -> Unit,
) {

    fun update(url: String?): RuleUpdateResult {
        if (url.isNullOrBlank()) return RuleUpdateResult.NotConfigured

        val body = try {
            fetch(url)
        } catch (e: Exception) {
            NumberInfoLog.w("rule update: download failed", e)
            return RuleUpdateResult.Failed("network")
        }

        val parsed = try {
            RuleSet.parse(body)
        } catch (e: Exception) {
            NumberInfoLog.w("rule update: remote rules rejected as invalid", e)
            return RuleUpdateResult.Failed("invalid")
        }

        if (parsed == current()) return RuleUpdateResult.UpToDate

        return try {
            install(body, parsed)
            NumberInfoLog.i("rules updated from remote, updatedAt=${parsed.updatedAt}")
            RuleUpdateResult.Updated
        } catch (e: Exception) {
            NumberInfoLog.w("rule update: install failed", e)
            RuleUpdateResult.Failed("install")
        }
    }
}
