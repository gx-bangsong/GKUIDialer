/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Service tests with a fake geo source. The fake's answers are the libphonenumber
 * 9.0.x values quoted in the review (zh-Hans-CN and en-US); the assertions use
 * "contains" for place names. Real libphonenumber output is not exercised here.
 */
class NumberLocationServiceTest {

    private class FakeGeo(
        private val answer: (String, Locale) -> RawGeo?,
        private val parseableNumbers: (String) -> Boolean = { true },
    ) : PhoneNumberGeoSource {
        var lookups = 0
        override fun parseable(number: String): Boolean = parseableNumbers(number)
        override fun lookup(number: String, locale: Locale): RawGeo? {
            lookups++
            return answer(number, locale)
        }
    }

    private val zh = Locale.SIMPLIFIED_CHINESE
    private val en = Locale.US

    /** Sample data: zh values as libphonenumber gives them for zh-Hans-CN. */
    private fun sampleGeo(number: String, locale: Locale): RawGeo? {
        val english = locale.language == "en"
        return when (number) {
            "13910001234" -> RawGeo("CN", 86, if (english) "Beijing" else "北京市",
                if (english) "China Mobile" else "中国移动", countryLevelOnly = false)
            "13912345678" -> RawGeo("CN", 86,
                if (english) "Changzhou, Jiangsu" else "江苏省常州市",
                if (english) "China Mobile" else "中国移动", countryLevelOnly = false)
            "19912345678" -> RawGeo("CN", 86, if (english) "China" else "中国",
                if (english) "China Telecom" else "中国电信", countryLevelOnly = true)
            "075512345678" -> RawGeo("CN", 86, if (english) "Shenzhen, Guangdong" else "广东省深圳市",
                null, countryLevelOnly = false)
            "+6591234567" -> RawGeo("SG", 65, if (english) "Singapore" else "新加坡",
                null, countryLevelOnly = true)
            else -> null
        }
    }

    private fun service(geo: PhoneNumberGeoSource, table: (String) -> String? = { null }) =
        NumberLocationService(geoSource = geo, bundledTable = table)

    // -- samples ---------------------------------------------------------------

    @Test
    fun chinese_mobileCity_containsCityAndCarrier() {
        val text = service(FakeGeo(::sampleGeo)).describe("13910001234", zh)!!
        assertTrue(text, text.contains("北京"))
        assertTrue(text, text.contains("中国移动"))
    }

    @Test
    fun chinese_mobileProvinceAndCity_containsProvinceAndCarrier() {
        val text = service(FakeGeo(::sampleGeo)).describe("13912345678", zh)!!
        assertTrue(text, text.contains("江苏"))
        assertTrue(text, text.contains("中国移动"))
    }

    @Test
    fun chinese_19x_showsCarrier_notBareCountry() {
        assertEquals("中国电信", service(FakeGeo(::sampleGeo)).describe("19912345678", zh))
    }

    @Test
    fun chinese_landline_cityWithoutCarrier() {
        assertEquals("广东省深圳市",
            service(FakeGeo(::sampleGeo)).describe("075512345678", zh))
    }

    @Test
    fun chinese_foreignNumber_showsCountry() {
        assertEquals("新加坡", service(FakeGeo(::sampleGeo)).describe("+6591234567", zh))
    }

    @Test
    fun english_mobile_containsEnglishCity() {
        val text = service(FakeGeo(::sampleGeo)).describe("13912345678", en)!!
        assertTrue(text, text.contains("Changzhou"))
    }

    // -- cache is keyed by locale -----------------------------------------------

    @Test
    fun languageSwitch_doesNotReturnTextFromTheOtherLanguage() {
        val svc = service(FakeGeo(::sampleGeo))
        val chinese = svc.describe("13912345678", zh)!!
        val english = svc.describe("13912345678", en)!!
        assertTrue(english, english.contains("Changzhou"))
        assertTrue(chinese, chinese.contains("江苏"))
        // Back to Chinese after English: still Chinese.
        assertEquals(chinese, svc.describe("13912345678", zh))
    }

    @Test
    fun repeatedLookupInSameLocale_isServedFromCache() {
        val geo = FakeGeo(::sampleGeo)
        val svc = service(geo)
        svc.describe("13910001234", zh)
        svc.describe("13910001234", zh)
        assertEquals(1, geo.lookups)
    }

    @Test
    fun cacheIsBounded() {
        val svc = NumberLocationService(FakeGeo(answer = { _, _ -> null }), maxEntries = 4)
        for (i in 0 until 20) svc.describe("1380000%04d".format(i), zh)
        assertTrue(svc.cacheSize() <= 4)
    }

    // -- bundled table ----------------------------------------------------------

    @Test
    fun bundledTable_winsForChineseOnly() {
        val geo = FakeGeo(::sampleGeo)
        val svc = service(geo, table = { n -> if (n == "13910001234") "北京 移动" else null })
        assertEquals("北京 移动", svc.describe("13910001234", zh))
        // English does not use the Chinese table.
        assertTrue(svc.describe("13910001234", en)!!.contains("Beijing"))
    }

    // -- fallback and failures ----------------------------------------------------

    @Test
    fun describeOrFallback_unparseableNumber_returnsStoredValue() {
        val svc = service(FakeGeo(::sampleGeo, parseableNumbers = { false }))
        assertEquals("Chaozhou, Guangdong",
            svc.describeOrFallback("0768-2123456", en, fallback = "Chaozhou, Guangdong"))
        assertEquals("stored",
            svc.describeOrFallback("not a number", zh, fallback = "stored"))
    }

    @Test
    fun describeOrFallback_parseableNumber_isComputedNotFallen_back() {
        val svc = service(FakeGeo(::sampleGeo))
        // The stored English string must not be shown for a parseable number.
        val text = svc.describeOrFallback("13910001234", zh, fallback = "Chaozhou, Guangdong")
        assertTrue(text!!.contains("北京"))
    }

    @Test
    fun describeOrFallback_parseableButUnknown_isNull_notTheStoredValue() {
        val svc = service(FakeGeo({ _, _ -> null }))
        assertNull(svc.describeOrFallback("13910001234", zh, fallback = "Chaozhou, Guangdong"))
    }

    @Test
    fun geoSourceFailure_isNull_andDoesNotThrow() {
        val svc = service(FakeGeo({ _, _ -> throw IllegalStateException("metadata missing") }))
        assertNull(svc.describe("13910001234", zh))
    }
}
