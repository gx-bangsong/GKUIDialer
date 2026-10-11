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
import com.android.dialer.numberinfo.awaitWithin
import com.android.dialer.callscreening.rule.ScreeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Official CallScreeningService implementation. Telecom binds the default
 * dialer's screening service unless the user has chosen another screening app,
 * so no extra role request is made here.
 *
 * Contacts: Telecom only skips contacts for screening apps that lack
 * READ_CONTACTS. This app holds READ_CONTACTS, so contact calls are delivered
 * here too and every call is checked against the contacts provider
 * ([ScreeningPipeline]). An unreadable contacts provider (before first unlock,
 * no permission, query failure) is UNKNOWN: such a call is silenced instead of
 * blocked, and never looked up online.
 *
 * Latency contract: Telecom gives ~5s before it treats the service as
 * unresponsive and completes the call normally. Budget here:
 *   - local phase (rules + manual + cache + local providers): tens of ms
 *   - online phase: only when a MARK_TYPE rule exists and nothing local
 *     decided; hard-capped at [ScreeningPipeline.ONLINE_BUDGET_MS] per source
 *     and [TOTAL_BUDGET_MS] overall; on timeout we fail open (allow).
 *   - the total budget bounds the *wait* ([awaitWithin]), so blocking work
 *     (DNS, sockets, SQLite, contacts) can never delay the response past it.
 *
 * Decisions themselves are delegated to [ScreeningPipeline] and the pure
 * [ScreeningDecisionEngine].
 */
class DialerCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = ScreeningDecisionEngine()

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            screen(callDetails)
        } catch (e: Exception) {
            // Never leave Telecom waiting on an exception: fail open.
            NumberInfoLog.w("screening setup failed, failing open", e)
            respondAllow(callDetails)
        }
    }

    private fun screen(callDetails: Call.Details) {
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
                awaitWithin(TOTAL_BUDGET_MS, scope) {
                    decide(component, screening, number)
                } ?: ScreeningDecisionEngine.Decision(
                    ScreeningDecisionEngine.Action.ALLOW, null, "TIMEOUT:fail-open")
            } catch (e: Exception) {
                NumberInfoLog.w("screening crashed for "
                        + NumberNormalizer.maskForLog(number) + ", failing open", e)
                ScreeningDecisionEngine.Decision(ScreeningDecisionEngine.Action.ALLOW)
            }

            when (decision.action) {
                ScreeningDecisionEngine.Action.BLOCK -> {
                    // Answer Telecom first; bookkeeping must not eat into its deadline.
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
                    recordBlockedCall(screening, component, number, decision)
                }
                ScreeningDecisionEngine.Action.SILENCE -> {
                    // Rings silently and stays in the call log; not a blocked call.
                    respondToCall(callDetails, CallResponse.Builder().setSilenceCall(true).build())
                    NumberInfoLog.i("silenced "
                            + NumberNormalizer.maskForLog(number) + " (${decision.summary})")
                }
                ScreeningDecisionEngine.Action.ALLOW -> respondAllow(callDetails)
            }
        }
    }

    private suspend fun decide(
        component: NumberInfoComponent,
        screening: ScreeningComponent,
        number: String,
    ): ScreeningDecisionEngine.Decision {
        val settings = screening.settings()
        val repository = component.repository()
        val pipeline = ScreeningPipeline(
            engine = engine,
            contactStatus = { repository.contactStatus(it) },
            loadRules = { screening.blockRuleDao().getEnabled() },
            mode = { settings.mode },
            markCountThreshold = { settings.markCountThreshold },
            localInfo = { repository.peekLocal(it) },
            onlineEnabled = { component.settings().onlineLookupEnabled },
            onlineInfo = { n, budgetMs -> repository.query(n, perSourceTimeoutMs = budgetMs) },
        )
        return pipeline.decide(number)
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
    }
}
