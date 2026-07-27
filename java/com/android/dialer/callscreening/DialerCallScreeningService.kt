/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import android.telecom.Call
import android.telecom.CallScreeningService
import com.android.dialer.callscreening.record.BlockedCallRecord
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.NumberNormalizer
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.callscreening.rule.ScreeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Official CallScreeningService implementation. Because this app holds
 * ROLE_DIALER, Telecom binds it automatically for every non-contact incoming
 * call — no extra role request needed.
 *
 * Latency contract: Telecom gives ~5s before it treats the service as
 * unresponsive and completes the call normally. Budget here:
 *   - local phase (rules + manual + cache + local providers): tens of ms
 *   - online phase: only when a MARK_TYPE rule exists and nothing local
 *     decided; hard-capped at [ONLINE_BUDGET_MS] per source and
 *     [TOTAL_BUDGET_MS] overall — on timeout we fail open (allow).
 *
 * Decisions themselves are delegated to the pure [ScreeningDecisionEngine].
 */
class DialerCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = ScreeningDecisionEngine()

    override fun onScreenCall(callDetails: Call.Details) {
        // Only incoming calls are screened; anything else is allowed through.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondAllow(callDetails)
            return
        }
        val rawNumber = callDetails.handle?.schemeSpecificPart
        val number = NumberNormalizer.normalize(rawNumber)
        if (number == null) {
            // Hidden/invalid caller id — leave policy to the platform.
            respondAllow(callDetails)
            return
        }

        val component = NumberInfoComponent.get(applicationContext)
        val screening = ScreeningComponent.get(applicationContext)
        val settings = screening.settings()

        if (settings.mode == ScreeningMode.OFF) {
            respondAllow(callDetails)
            return
        }

        scope.launch {
            val decision = try {
                withTimeoutOrNull(TOTAL_BUDGET_MS) {
                    decide(component, screening, number)
                } ?: ScreeningDecisionEngine.Decision(
                    ScreeningDecisionEngine.Action.ALLOW, null, "TIMEOUT:fail-open")
            } catch (e: Exception) {
                NumberInfoLog.w("screening crashed for "
                        + NumberNormalizer.maskForLog(number) + ", failing open", e)
                ScreeningDecisionEngine.Decision(ScreeningDecisionEngine.Action.ALLOW)
            }

            if (decision.blocked) {
                recordBlockedCall(screening, component, number, decision)
                respondToCall(
                    callDetails,
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(settings.skipCallNotification)
                        .setSkipCallLog(settings.skipCallLog)
                        .build())
                NumberInfoLog.i("blocked "
                        + NumberNormalizer.maskForLog(number) + " (${decision.summary})")
            } else {
                respondAllow(callDetails)
            }
        }
    }

    private suspend fun decide(
        component: NumberInfoComponent,
        screening: ScreeningComponent,
        number: String,
    ): ScreeningDecisionEngine.Decision {
        val settings = screening.settings()
        val rules = screening.blockRuleDao().getEnabled()
        val repository = component.repository()

        // Contacts: Telecom usually doesn't screen them, but double-check —
        // the engine treats it as an unconditional allow.
        val isContact = repository.isContact(number)

        // Phase 1: local-only info (manual mark, fresh cache, local providers).
        val localInfo = repository.peekLocal(number)
        val localDecision = engine.decide(
            number, localInfo, rules, settings.mode, isContact,
            settings.markCountThreshold)
        if (localDecision.blocked || settings.mode == ScreeningMode.WHITELIST) {
            // Whitelist mode never needs online data; blacklist local hit is final.
            return localDecision
        }

        // Phase 2: online marks — only worth the latency when a MARK_TYPE rule
        // could actually change the outcome and the privacy switch is on.
        val hasMarkRule = rules.any {
            it.type == com.android.dialer.callscreening.rule.RuleType.MARK_TYPE
        }
        if (!hasMarkRule || !component.settings().onlineLookupEnabled) {
            return localDecision
        }
        val onlineInfo = repository.query(number, perSourceTimeoutMs = ONLINE_BUDGET_MS)
        return engine.decide(
            number, onlineInfo ?: localInfo, rules, settings.mode, isContact,
            settings.markCountThreshold)
    }

    private fun recordBlockedCall(
        screening: ScreeningComponent,
        component: NumberInfoComponent,
        number: String,
        decision: ScreeningDecisionEngine.Decision,
    ) {
        try {
            val infoJson = try {
                // Snapshot whatever we know locally at block time.
                kotlinx.coroutines.runBlocking {
                    component.repository().peekLocal(number)
                }?.let { info ->
                    JSONObject()
                        .put("markType", info.markType.id)
                        .put("markCount", info.markCount)
                        .put("bizName", info.bizName ?: JSONObject.NULL)
                        .put("location", info.location ?: JSONObject.NULL)
                        .toString()
                }
            } catch (e: Exception) {
                null
            }
            screening.blockedCallRecordDao().insert(
                BlockedCallRecord(
                    number = number,
                    blockedAt = System.currentTimeMillis(),
                    ruleId = decision.ruleId,
                    ruleSummary = decision.summary,
                    numberInfoJson = infoJson))
        } catch (e: Exception) {
            NumberInfoLog.w("failed to persist blocked-call record", e)
        }
    }

    private fun respondAllow(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Keep well under Telecom's ~5s screening deadline. */
        const val TOTAL_BUDGET_MS = 3500L

        /** Per-online-source budget inside the screening path. */
        const val ONLINE_BUDGET_MS = ProviderManager.SCREENING_SOURCE_TIMEOUT_MS
    }
}
