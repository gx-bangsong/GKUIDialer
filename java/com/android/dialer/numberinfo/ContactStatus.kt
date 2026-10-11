/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.ContactsContract.PhoneLookup

/**
 * Whether a number belongs to the user's contacts.
 *
 * UNKNOWN means the answer could not be determined (no READ_CONTACTS, device
 * still locked after reboot, provider unavailable, query failed). UNKNOWN is
 * treated like CONTACT for every privacy decision: never queried online, never
 * decorated. The only difference is in call screening, where rule-based
 * blocking is downgraded to silencing (see ScreeningDecisionEngine).
 */
enum class ContactStatus {
    CONTACT,
    NOT_CONTACT,
    UNKNOWN,
}

/** Injectable contact lookup; the production implementation is [AndroidContactChecker]. */
fun interface ContactChecker {
    fun check(number: String): ContactStatus
}

/** Pure mapping from the observed facts to a status. Any missing fact is UNKNOWN. */
object ContactStatusPolicy {

    /**
     * @param unlocked user storage is unlocked (contacts are unreadable before first unlock)
     * @param permissionGranted READ_CONTACTS is granted
     * @param found result of the PhoneLookup query; null when the cursor was null or the query failed
     */
    @JvmStatic
    fun resolve(unlocked: Boolean, permissionGranted: Boolean, found: Boolean?): ContactStatus = when {
        !unlocked || !permissionGranted -> ContactStatus.UNKNOWN
        found == null -> ContactStatus.UNKNOWN
        found -> ContactStatus.CONTACT
        else -> ContactStatus.NOT_CONTACT
    }
}

/**
 * Reads the contacts provider via PhoneLookup. Blocking; call from a background
 * thread only. Never throws.
 */
class AndroidContactChecker(private val context: Context) : ContactChecker {

    override fun check(number: String): ContactStatus {
        if (number.isBlank()) return ContactStatus.UNKNOWN
        val unlocked = try {
            context.getSystemService(UserManager::class.java).isUserUnlocked
        } catch (e: Exception) {
            false
        }
        val permitted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!unlocked || !permitted) {
            return ContactStatusPolicy.resolve(unlocked, permitted, found = null)
        }
        val found: Boolean? = try {
            val uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(number).build()
            context.contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() }
        } catch (e: Exception) {
            NumberInfoLog.w("contact lookup failed", e) // never logs the number itself
            null
        }
        return ContactStatusPolicy.resolve(unlocked = true, permissionGranted = true, found = found)
    }
}
