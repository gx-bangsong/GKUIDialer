/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import android.content.Context
import com.android.dialer.numberinfo.aggregate.NumberInfoAggregator
import com.android.dialer.numberinfo.cache.ManualMarkDao
import com.android.dialer.numberinfo.cache.ManualMarkDaoImpl
import com.android.dialer.numberinfo.cache.NumberInfoCacheDao
import com.android.dialer.numberinfo.cache.NumberInfoCacheDaoImpl
import com.android.dialer.numberinfo.cache.NumberInfoDbHelper
import com.android.dialer.numberinfo.location.LibPhoneNumberGeoSource
import com.android.dialer.numberinfo.location.NumberLocationLoader
import com.android.dialer.numberinfo.location.NumberLocationService
import com.android.dialer.numberinfo.provider.LocalLocationProvider
import com.android.dialer.numberinfo.provider.LocalYellowPageProvider
import com.android.dialer.numberinfo.provider.ManualMarkProvider
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.numberinfo.provider.RuleBasedWebProvider
import com.android.dialer.numberinfo.rules.RuleRepository
import com.android.dialer.numberinfo.settings.NumberInfoSettings
import com.android.dialer.numberinfo.settings.NumberInfoSettingsImpl
import com.android.dialer.yellowpage.OfflineYellowPageStore

/**
 * Composition root for the numberinfo stack — one lazily-built object graph
 * per process, callable from Java integration points
 * (`NumberInfoComponent.get(context).repository()`).
 *
 * Kept as a plain service locator on purpose: the surrounding app uses a
 * generated Dagger root component (Java annotation processing); hooking Kotlin
 * classes into it would require kapt, which Soong does not support. This class
 * is the single place that would change if that ever becomes possible.
 *
 * Provider registry (priorities per docs §3.1):
 *   manual(1000) > local_yp(900) > local_geo(800) > haoma(500) > sogou(250)
 *   > so360(200) > baidu(150)
 */
class NumberInfoComponent private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val settings: NumberInfoSettings by lazy { NumberInfoSettingsImpl(appContext) }

    private val dbHelper: NumberInfoDbHelper by lazy {
        NumberInfoDbHelper.getInstance(appContext)
    }

    private val cacheDao: NumberInfoCacheDao by lazy { NumberInfoCacheDaoImpl(dbHelper) }
    private val manualMarkDao: ManualMarkDao by lazy { ManualMarkDaoImpl(dbHelper) }

    private val ruleRepository: RuleRepository by lazy { RuleRepository(appContext) }

    private val yellowPageStore: OfflineYellowPageStore by lazy {
        OfflineYellowPageStore(appContext)
    }

    private val locationLoader: NumberLocationLoader by lazy {
        NumberLocationLoader { appContext.assets.open(NumberLocationLoader.ASSET_PATH) }
    }

    private val numberLocation: NumberLocationService by lazy {
        NumberLocationService(
            geoSource = LibPhoneNumberGeoSource(),
            bundledTable = { locationLoader.get(it) },
        )
    }

    private val providerManager: ProviderManager by lazy {
        ProviderManager(settings).apply {
            register(ManualMarkProvider(manualMarkDao))
            register(LocalYellowPageProvider(yellowPageStore))
            // LOCATION rules match Chinese text, whatever the UI language is.
            register(LocalLocationProvider { number ->
                numberLocation.describe(number, java.util.Locale.SIMPLIFIED_CHINESE)
            })
            // Rule-driven web sources. Adding a new one = a rules.json block
            // plus one line here.
            register(RuleBasedWebProvider(Sources.HAOMA, 500, ruleRepository::get))
            register(RuleBasedWebProvider(Sources.SOGOU, 250, ruleRepository::get))
            register(RuleBasedWebProvider(Sources.SO360, 200, ruleRepository::get))
            register(RuleBasedWebProvider(Sources.BAIDU, 150, ruleRepository::get))
        }
    }

    private val repository: NumberInfoRepository by lazy {
        NumberInfoRepository(
            AndroidContactChecker(appContext), settings, providerManager, cacheDao, manualMarkDao,
            NumberInfoAggregator())
    }

    fun settings(): NumberInfoSettings = settings
    fun ruleRepository(): RuleRepository = ruleRepository
    fun providerManager(): ProviderManager = providerManager
    fun repository(): NumberInfoRepository = repository
    fun manualMarkDao(): ManualMarkDao = manualMarkDao
    fun cacheDao(): NumberInfoCacheDao = cacheDao
    fun yellowPageStore(): OfflineYellowPageStore = yellowPageStore
    fun locationLoader(): NumberLocationLoader = locationLoader
    fun numberLocation(): NumberLocationService = numberLocation

    companion object {
        @Volatile
        private var instance: NumberInfoComponent? = null

        @JvmStatic
        fun get(context: Context): NumberInfoComponent =
            instance ?: synchronized(this) {
                instance ?: NumberInfoComponent(context).also { instance = it }
            }
    }
}
