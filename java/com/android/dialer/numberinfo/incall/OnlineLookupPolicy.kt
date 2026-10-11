/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import com.android.dialer.numberinfo.ContactStatus

/**
 * When the in-call path may send a number to an online source. Pure logic.
 *
 * All three must hold: the call is incoming (outgoing calls are looked up locally only), the
 * number is confirmed as a non-contact (contacts and unknown status never leave the device), and
 * the user has turned on online lookup.
 */
object OnlineLookupPolicy {

    @JvmStatic
    fun mayQueryOnline(
        isIncoming: Boolean,
        contact: ContactStatus,
        onlineLookupEnabled: Boolean,
    ): Boolean = isIncoming && contact == ContactStatus.NOT_CONTACT && onlineLookupEnabled
}
