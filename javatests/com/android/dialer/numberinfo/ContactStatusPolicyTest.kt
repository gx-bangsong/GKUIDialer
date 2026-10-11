/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactStatusPolicyTest {

    @Test
    fun found_isContact() {
        assertEquals(ContactStatus.CONTACT,
            ContactStatusPolicy.resolve(unlocked = true, permissionGranted = true, found = true))
    }

    @Test
    fun notFound_isNotContact() {
        assertEquals(ContactStatus.NOT_CONTACT,
            ContactStatusPolicy.resolve(unlocked = true, permissionGranted = true, found = false))
    }

    @Test
    fun noReadContactsPermission_isUnknown_evenIfQueryWouldSucceed() {
        assertEquals(ContactStatus.UNKNOWN,
            ContactStatusPolicy.resolve(unlocked = true, permissionGranted = false, found = false))
    }

    @Test
    fun deviceLocked_isUnknown_evenIfQueryWouldFindTheNumber() {
        // Before first unlock the provider may still answer, but the number is
        // not trustworthy for privacy decisions.
        assertEquals(ContactStatus.UNKNOWN,
            ContactStatusPolicy.resolve(unlocked = false, permissionGranted = true, found = true))
    }

    @Test
    fun nullCursorOrException_isUnknown() {
        assertEquals(ContactStatus.UNKNOWN,
            ContactStatusPolicy.resolve(unlocked = true, permissionGranted = true, found = null))
    }
}
