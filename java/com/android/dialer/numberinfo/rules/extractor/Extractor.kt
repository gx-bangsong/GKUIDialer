/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules.extractor

import com.android.dialer.numberinfo.rules.FieldSpec

/**
 * Extracts a single string field from a document (HTML/JSON text).
 * Implementations must be pure and thread-safe. Returning null means
 * "not found"; they should not throw for ordinary mismatches.
 */
interface Extractor {
    /** @return raw extracted string, or null when the field is absent. */
    fun extract(document: String, field: FieldSpec): String?
}
