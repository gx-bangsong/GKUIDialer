/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.dialer.numberinfo.ContactStatus
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Java-friendly façade used by the InCallUI (ContactInfoCache). Two-phase
 * delivery mirroring the architecture doc: local data lands in one fast
 * callback, online enrichment (only if the privacy switch is on) lands in a
 * second callback on the same listener.
 *
 * All callbacks are invoked on the main thread. Lookups run on Dispatchers.IO,
 * so calling [query] from the main thread never blocks the call setup path.
 */
object NumberInfoBridge {

    fun interface Listener {
        /** Main thread. May be called twice: local phase, then online phase. */
        fun onNumberInfo(callId: String, info: CallerIdInfo?)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Looks up caller-id info for an in-call display. Never blocks the caller;
     * safe to invoke from the main thread.
     */
    @JvmStatic
    fun query(context: Context, callId: String, rawNumber: String?, listener: Listener) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val repository = NumberInfoComponent.get(appContext).repository()
                // Anything but a confirmed non-contact is left alone: contacts, and
                // numbers whose status is unknown (locked device, no permission).
                if (repository.contactStatus(rawNumber) != ContactStatus.NOT_CONTACT) {
                    return@launch
                }
                // Phase 1: local (manual mark / offline yellow pages / geo / cache).
                repository.peekLocal(rawNumber)?.let {
                    postResult(appContext, callId, it, listener)
                }
                // Phase 2: online refresh; listener fires again when data lands.
                repository.refreshOnlineAsync(rawNumber) { _, refreshed ->
                    if (refreshed != null) {
                        postResult(appContext, callId, refreshed, listener)
                    }
                }
            } catch (e: Exception) {
                NumberInfoLog.w("in-call numberinfo lookup failed", e)
            }
        }
    }

    private fun postResult(
        context: Context, callId: String, info: NumberInfo, listener: Listener,
    ) {
        val callerId = format(context, info)
        mainHandler.post { listener.onNumberInfo(callId, callerId) }
    }

    /** Builds display strings using the app's resources. */
    @JvmStatic
    fun format(context: Context, info: NumberInfo): CallerIdInfo =
        CallerIdFormatter.format(info, ResourceStrings(context))

    @JvmStatic
    fun formatLabel(context: Context, info: NumberInfo): String? =
        CallerIdFormatter.label(info, ResourceStrings(context))

    private class ResourceStrings(private val context: Context) : CallerIdFormatter.Strings {
        override fun markName(type: MarkType, rawLabel: String?): String {
            if (type == MarkType.CUSTOM && !rawLabel.isNullOrBlank()) {
                return rawLabel // the source's own wording wins for custom marks
            }
            val resId = when (type) {
                MarkType.FRAUD -> R.string.numberinfo_mark_fraud
                MarkType.HARASSMENT -> R.string.numberinfo_mark_harassment
                MarkType.TELEMARKETING -> R.string.numberinfo_mark_telemarketing
                MarkType.AGENT -> R.string.numberinfo_mark_agent
                MarkType.DEBT_COLLECTION -> R.string.numberinfo_mark_debt
                MarkType.EXPRESS -> R.string.numberinfo_mark_express
                MarkType.TAXI -> R.string.numberinfo_mark_taxi
                else -> R.string.numberinfo_mark_custom
            }
            return context.getString(resId)
        }

        override fun markCount(formattedCount: String): String =
            context.getString(R.string.numberinfo_mark_count, formattedCount)

        override fun verifiedBusiness(): String =
            context.getString(R.string.numberinfo_verified_business)
    }
}
