/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

import com.android.dialer.numberinfo.ContactStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineLookupPolicyTest {

    private fun allowed(incoming: Boolean, status: ContactStatus, enabled: Boolean) =
        OnlineLookupPolicy.mayQueryOnline(incoming, status, enabled)

    @Test
    fun incomingNonContact_withOptIn_mayQuery() {
        assertEquals(true, allowed(true, ContactStatus.NOT_CONTACT, true))
    }

    @Test
    fun outgoingCall_neverQueries() {
        assertEquals(false, allowed(false, ContactStatus.NOT_CONTACT, true))
    }

    @Test
    fun contactOrUnknown_neverQueries() {
        assertEquals(false, allowed(true, ContactStatus.CONTACT, true))
        assertEquals(false, allowed(true, ContactStatus.UNKNOWN, true))
    }

    @Test
    fun withoutOptIn_neverQueries() {
        assertEquals(false, allowed(true, ContactStatus.NOT_CONTACT, false))
    }
}
