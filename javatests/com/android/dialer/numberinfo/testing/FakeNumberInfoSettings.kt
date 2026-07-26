/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.testing

import com.android.dialer.numberinfo.settings.NumberInfoSettings

/** In-memory NumberInfoSettings for plain-JVM tests. */
class FakeNumberInfoSettings(
    override var onlineLookupEnabled: Boolean = true,
    override var cacheTtlMillis: Long = NumberInfoSettings.DEFAULT_TTL_MILLIS,
    override var negativeCacheTtlMillis: Long =
        NumberInfoSettings.DEFAULT_NEGATIVE_TTL_MILLIS,
) : NumberInfoSettings {

    private val disabledSources = HashSet<String>()

    override fun isSourceEnabled(sourceId: String): Boolean =
        sourceId !in disabledSources

    override fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        if (enabled) disabledSources.remove(sourceId) else disabledSources.add(sourceId)
    }
}
