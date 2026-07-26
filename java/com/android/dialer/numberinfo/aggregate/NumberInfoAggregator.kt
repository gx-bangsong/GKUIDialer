/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.aggregate

import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources

/**
 * Merges per-source results into the single NumberInfo shown to the user and
 * fed to the screening engine. Pure logic, fully unit-tested.
 *
 * Policy (docs §3.3):
 *  1. A manual mark short-circuits everything (user correction is truth).
 *  2. Business identity: pick from bizName-bearing results, preferring
 *     verified, then higher confidence, then higher markCount.
 *  3. Mark: group by markType (ignoring NONE), score = Σ confidence,
 *     tie-break on max markCount. markCount shown = max within winning group.
 *  4. Business and mark can coexist; a strong FRAUD signal is kept alongside
 *     bizName so the UI can warn about impersonation.
 *  5. location = first non-blank by source confidence order.
 */
class NumberInfoAggregator {

    fun aggregate(
        number: String,
        manual: NumberInfo?,
        results: List<NumberInfo>,
    ): NumberInfo? {
        if (manual != null && manual.markType != MarkType.NONE) {
            // Keep location/biz enrichment from other sources, but the mark
            // itself is the user's.
            val enrich = aggregate(number, null, results)
            return manual.copy(
                location = manual.location ?: enrich?.location,
                bizName = enrich?.bizName,
                bizCategory = enrich?.bizCategory,
                bizVerified = enrich?.bizVerified ?: false,
            )
        }

        val valid = results.filter { !it.isEmpty }
        if (valid.isEmpty()) return null

        // -- business identity --
        val biz = valid.filter { it.isBusiness }
            .sortedWith(
                compareByDescending<NumberInfo> { it.bizVerified }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.markCount })
            .firstOrNull()

        // -- mark voting --
        val markGroups = valid
            .filter { it.markType != MarkType.NONE }
            .groupBy { it.markType }
        val winner = markGroups.entries
            .sortedWith(
                compareByDescending<Map.Entry<MarkType, List<NumberInfo>>> { e ->
                    e.value.sumOf { it.confidence.toDouble() }
                }.thenByDescending { e -> e.value.maxOf { it.markCount } })
            .firstOrNull()

        val location = valid
            .sortedByDescending { it.confidence }
            .firstNotNullOfOrNull { it.location?.takeIf { l -> l.isNotBlank() } }

        val markType = winner?.key ?: MarkType.NONE
        val markCount = winner?.value?.maxOf { it.markCount } ?: 0
        val rawLabel = winner?.value
            ?.sortedByDescending { it.confidence }
            ?.firstNotNullOfOrNull { it.rawMarkLabel }

        if (biz == null && markType == MarkType.NONE && location == null) return null

        return NumberInfo(
            number = number,
            markType = markType,
            markCount = markCount,
            rawMarkLabel = rawLabel,
            bizName = biz?.bizName,
            bizCategory = biz?.bizCategory,
            bizVerified = biz?.bizVerified ?: false,
            location = location,
            source = Sources.AGGREGATED,
            confidence = winner?.value?.maxOf { it.confidence }
                ?: biz?.confidence ?: 0f,
            timestampMillis = System.currentTimeMillis(),
        )
    }
}
