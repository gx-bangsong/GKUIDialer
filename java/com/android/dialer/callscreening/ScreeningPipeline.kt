/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import com.android.dialer.callscreening.ScreeningDecisionEngine.Action
import com.android.dialer.callscreening.ScreeningDecisionEngine.Decision
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.callscreening.rule.ScreeningMode
import com.android.dialer.numberinfo.ContactStatus
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.provider.ProviderManager

/**
 * Decision pipeline for one incoming number, separated from the service so it
 * can be unit-tested on a plain JVM. Every collaborator is injected.
 *
 * Contacts are confirmed with the contacts provider on every call rather than
 * taken from Telecom. Telecom only skips contacts for screening apps that lack
 * READ_CONTACTS (CallScreeningServiceFilter); this app holds it, so contact
 * calls reach the service too. Anything other than a confirmed non-contact
 * stays off the network, so a contacts provider that is not readable yet (for
 * example before first unlock) can never leak a contact's number online.
 */
class ScreeningPipeline(
    private val engine: ScreeningDecisionEngine,
    private val contactStatus: suspend (String) -> ContactStatus,
    private val loadRules: suspend () -> List<BlockRule>,
    private val mode: () -> ScreeningMode,
    private val markCountThreshold: () -> Int,
    private val localInfo: suspend (String) -> NumberInfo?,
    private val onlineEnabled: () -> Boolean,
    private val onlineInfo: suspend (number: String, budgetMs: Long) -> NumberInfo?,
) {

    /** @param number normalized number; call from a background dispatcher. */
    suspend fun decide(number: String): Decision {
        val status = contactStatus(number)
        val rules = loadRules()
        val currentMode = mode()
        val threshold = markCountThreshold()

        // Phase 1: local data only (manual mark, fresh cache, local providers).
        val local = localInfo(number)
        val localDecision = engine.decide(number, local, rules, currentMode, status, threshold)
        if (localDecision.blocked || localDecision.action == Action.SILENCE) return localDecision
        // Whitelist mode never needs online data.
        if (currentMode == ScreeningMode.WHITELIST) return localDecision
        // Contacts and unknown-status numbers are never sent online.
        if (status != ContactStatus.NOT_CONTACT) return localDecision

        // Phase 2: online marks, only when a MARK_TYPE rule could change the
        // outcome and the user has opted in.
        val hasMarkRule = rules.any { it.type == RuleType.MARK_TYPE }
        if (!hasMarkRule || !onlineEnabled()) return localDecision
        val online = onlineInfo(number, ONLINE_BUDGET_MS)
        return engine.decide(number, online ?: local, rules, currentMode, status, threshold)
    }

    companion object {
        /** Per-online-source budget inside the screening path. */
        const val ONLINE_BUDGET_MS = ProviderManager.SCREENING_SOURCE_TIMEOUT_MS
    }
}
