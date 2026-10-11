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
 * Canonical form (CN-centric, matches what domestic sources index and what
 * users type into rules):
 *  - separators/garbage stripped, only digits kept (plus a possible leading '+');
 *    full-width digits (１３８…) from CJK input methods are accepted
 *  - "00" international access prefix is treated as '+' ("0044…" == "+44…")
 *  - CN country code (+86 / 0086 / 86) stripped, leaving the domestic dialing
 *    form: landlines get their trunk prefix back ("+86 10 1234 5678" ->
 *    "01012345678"), mobiles / 400 / 800 / short codes stay as they are
 *  - other international numbers are kept in "+<cc><nsn>" form as-is
 *
 * Pure logic, no Android dependencies — unit-testable on any JVM.
 */
object NumberNormalizer {

    @JvmStatic
    fun normalize(raw: String?): String? {
        var n = digitsWithLeadingPlus(raw) ?: return null
        n = internationalForm(n)
        // Bare "86" prefix in front of an 11-digit mobile (861380013....)
        if (!n.startsWith("+") && n.length == 13 && n.startsWith("86") && n[2] == '1') {
            n = "+$n"
        }
        // Strip CN country code: the domestic dialing form is canonical.
        if (n.startsWith("+86")) {
            n = domesticFromNsn(n.substring(3))
        }
        return n.takeIf { it.isNotEmpty() && it != "+" }
    }

    /**
     * Canonicalizes a number *prefix* typed into a block rule ("170",
     * "+86 10", "0086-755", "001"), following the same conventions as
     * [normalize]. Returns null when nothing usable is left — in particular for
     * a bare country code ("+86"), which must never degrade into an empty
     * prefix that would match every number.
     */
    @JvmStatic
    fun normalizePrefix(raw: String?): String? {
        var n = digitsWithLeadingPlus(raw) ?: return null
        n = internationalForm(n)
        if (n.startsWith("+86")) {
            val nsn = n.substring(3)
            n = when {
                nsn.isEmpty() -> return null
                nsn.startsWith("0") -> nsn
                nsn.startsWith("10") -> "0$nsn" // 北京区号 010
                nsn.startsWith("1") -> nsn // 手机号段 13x–19x
                nsn.startsWith("400") || nsn.startsWith("800") -> nsn
                // "4", "40", "8", "80": could be 400/800 or an area code — keep.
                "400".startsWith(nsn) || "800".startsWith(nsn) -> nsn
                else -> "0$nsn" // 2X / 3XX–9XX 区号
            }
        }
        return n.takeIf { it.isNotEmpty() && it != "+" }
    }

    /** Keeps decimal digits (any script, e.g. full-width) and one leading '+'. */
    private fun digitsWithLeadingPlus(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            val digit = Character.digit(c, 10)
            if (digit >= 0) {
                sb.append('0' + digit)
            } else if ((c == '+' || c == '\uFF0B') && sb.isEmpty()) {
                sb.append('+')
            }
            // everything else (spaces, dashes, parentheses, letters) is dropped
        }
        return sb.toString().takeIf { it.isNotEmpty() && it != "+" }
    }

    /** "00<cc>…" (international access prefix used in CN) -> "+<cc>…". */
    private fun internationalForm(n: String): String =
        if (n.startsWith("00")) "+" + n.substring(2) else n

    /**
     * Converts a CN national significant number (what follows +86) into the
     * domestic dialing form. Only landlines change: they regain the trunk
     * prefix "0", otherwise a "010" rule or cache entry would never match a
     * Beijing number delivered as "+86 10 …".
     */
    private fun domesticFromNsn(nsn: String): String = when {
        nsn.startsWith("0") -> nsn // "+86 (0)10 …" mis-format: already domestic
        nsn.startsWith("400") || nsn.startsWith("800") -> nsn
        CN_LANDLINE_NSN.matches(nsn) -> "0$nsn"
        else -> nsn // mobiles, 95xxx / 10xxx service numbers, unknown shapes
    }

    /** Area code (10, 2X, or 3-digit 3XX–9XX) followed by a 7–8 digit subscriber number. */
    private val CN_LANDLINE_NSN = Regex("(?:10|2\\d)\\d{8}|[3-9]\\d{9,10}")

    /** "13800138000" -> "138****8000"; used for log output only, never for queries. */
    @JvmStatic
    fun maskForLog(number: String?): String {
        if (number == null) return "<null>"
        val digits = number.filter { it in '0'..'9' }
        if (digits.length < 7) return "${digits.take(2)}****"
        return "${digits.take(3)}****${digits.takeLast(4)}"
    }
}
