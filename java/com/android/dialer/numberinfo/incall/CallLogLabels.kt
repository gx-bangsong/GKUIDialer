/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.incall

/**
 * Which parts of a local [CallerIdInfo] replace what a call-log row or a call
 * details header shows for a non-contact. Pure logic, unit-tested on the JVM.
 */
object CallLogLabels {

    /** The business name shown in place of the number, or null. */
    @JvmStatic
    fun nameFor(info: CallerIdInfo): String? = info.bizName?.takeIf { it.isNotBlank() }

    /**
     * The line shown in place of the location, for numbers marked as spam, or
     * null. Positive marks (express, taxi) and businesses keep the location.
     */
    @JvmStatic
    fun spamLineFor(info: CallerIdInfo): String? =
        info.label?.takeIf { info.isSpam && it.isNotBlank() }
}
