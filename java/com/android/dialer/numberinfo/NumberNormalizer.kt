/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

/**
 * Canonicalizes phone numbers before querying/caching so that
 * "+86 138 0013 8000", "8613800138000" and "13800138000" all hit the same
 * cache row and the same block rule.
 *
 * Canonical form (CN-centric, matches what domestic sources index):
 *  - separators/garbage stripped, only digits kept (plus a possible leading '+')
 *  - CN country code (+86 / 0086 / 86) stripped, leaving the national number
 *  - other international numbers are kept in "+<cc><nsn>" form as-is
 *
 * Pure logic, no Android dependencies — unit-testable on any JVM.
 */
object NumberNormalizer {

    @JvmStatic
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            if (c in '0'..'9') {
                sb.append(c)
            } else if (c == '+' && sb.isEmpty()) {
                sb.append(c)
            }
            // everything else (spaces, dashes, parentheses, letters) is dropped
        }
        var n = sb.toString()
        if (n.isEmpty() || n == "+") return null

        // 0086... -> +86...
        if (n.startsWith("0086")) {
            n = "+86" + n.substring(4)
        }
        // Bare "86" prefix in front of an 11-digit mobile (861380013....)
        if (!n.startsWith("+") && n.length == 13 && n.startsWith("86") && n[2] == '1') {
            n = "+86" + n.substring(2)
        }
        // Strip CN country code entirely: national number is canonical.
        if (n.startsWith("+86")) {
            n = n.substring(3)
        }
        return if (n.isEmpty()) null else n
    }

    /** "13800138000" -> "138****8000"; used for log output only, never for queries. */
    @JvmStatic
    fun maskForLog(number: String?): String {
        if (number == null) return "<null>"
        val digits = number.filter { it in '0'..'9' }
        if (digits.length < 7) return "${digits.take(2)}****"
        return "${digits.take(3)}****${digits.takeLast(4)}"
    }
}
