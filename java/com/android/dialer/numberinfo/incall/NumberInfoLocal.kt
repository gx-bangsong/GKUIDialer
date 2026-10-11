/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import android.content.Context
import com.android.dialer.numberinfo.ContactStatus
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberInfoLog
import kotlinx.coroutines.runBlocking

/**
 * Local-only lookup for list and detail rows: manual mark, offline yellow pages,
 * offline location and fresh cache. Never touches the network.
 *
 * Blocking; call on a worker thread only. Returns null for contacts, for numbers
 * whose contact status is unknown, and when nothing is known locally.
 */
object NumberInfoLocal {

    @JvmStatic
    fun lookup(context: Context, rawNumber: String?): CallerIdInfo? {
        return try {
            val repository = NumberInfoComponent.get(context.applicationContext).repository()
            if (repository.contactStatus(rawNumber) != ContactStatus.NOT_CONTACT) {
                return null
            }
            val info = runBlocking { repository.peekLocal(rawNumber) } ?: return null
            NumberInfoBridge.format(context, info)
        } catch (e: Exception) {
            NumberInfoLog.w("local number info lookup failed", e)
            null
        }
    }
}
