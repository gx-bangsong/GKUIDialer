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

    /**
     * The business name shown in place of the number, or null. Spam-marked numbers keep the
     * number as the name: the spam label must stay visible, and the call log only shows the
     * label when the name slot is empty. This matches the in-call card, where spam wins.
     */
    @JvmStatic
    fun nameFor(info: CallerIdInfo): String? =
        info.bizName?.takeIf { it.isNotBlank() && !info.isSpam }

    /**
     * The line shown in place of the location, for numbers marked as spam, or
     * null. Positive marks (express, taxi) and businesses keep the location.
     */
    @JvmStatic
    fun spamLineFor(info: CallerIdInfo): String? =
        info.label?.takeIf { info.isSpam && it.isNotBlank() }
}
