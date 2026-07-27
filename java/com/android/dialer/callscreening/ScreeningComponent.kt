/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening

import android.content.Context
import com.android.dialer.callscreening.record.BlockedCallRecordDao
import com.android.dialer.callscreening.record.BlockedCallRecordDaoImpl
import com.android.dialer.callscreening.rule.BlockRuleDao
import com.android.dialer.callscreening.rule.BlockRuleDaoImpl
import com.android.dialer.numberinfo.cache.NumberInfoDbHelper

/** Composition root for the screening stack (same pattern as NumberInfoComponent). */
class ScreeningComponent private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val settings: ScreeningSettings by lazy { ScreeningSettingsImpl(appContext) }

    private val blockRuleDao: BlockRuleDao by lazy {
        BlockRuleDaoImpl(NumberInfoDbHelper.getInstance(appContext))
    }

    private val blockedCallRecordDao: BlockedCallRecordDao by lazy {
        BlockedCallRecordDaoImpl(NumberInfoDbHelper.getInstance(appContext))
    }

    fun settings(): ScreeningSettings = settings
    fun blockRuleDao(): BlockRuleDao = blockRuleDao
    fun blockedCallRecordDao(): BlockedCallRecordDao = blockedCallRecordDao

    companion object {
        @Volatile
        private var instance: ScreeningComponent? = null

        @JvmStatic
        fun get(context: Context): ScreeningComponent =
            instance ?: synchronized(this) {
                instance ?: ScreeningComponent(context).also { instance = it }
            }
    }
}
