/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.location

import java.util.Locale

/**
 * Raw geo facts for one number, as libphonenumber reports them. Kept free of
 * library types so [NumberLocationFormatter] can be tested with plain values.
 */
data class RawGeo(
    /** ISO region of the number, e.g. "CN", "SG". */
    val regionCode: String?,
    val countryCallingCode: Int,
    /** Geocoder text: city-level ("北京市") or country-level ("中国"). Null when empty. */
    val description: String?,
    /** Carrier name in the requested locale, e.g. "中国移动". Null when unknown. */
    val carrier: String?,
    /** True when [description] is only the country name (or missing). */
    val countryLevelOnly: Boolean,
)

/**
 * Seam between the location logic and libphonenumber. Production uses
 * [LibPhoneNumberGeoSource]; tests use a fake.
 */
interface PhoneNumberGeoSource {
    /** False when the string cannot be parsed as a phone number at all. */
    fun parseable(number: String): Boolean

    /** Null when the number is not parseable or not a valid number. */
    fun lookup(number: String, locale: Locale): RawGeo?
}

/**
 * libphonenumber-backed source. Uses PhoneNumberUtil (parse / validity / region),
 * PhoneNumberOfflineGeocoder (city or country text) and PhoneNumberToCarrierMapper
 * (carrier). Those classes come from the AOSP libphonenumber build that Dialer
 * already links statically.
 *
 * The first call loads the metadata (a few MB) — never call this on the main
 * thread. The library singletons are thread-safe.
 */
class LibPhoneNumberGeoSource : PhoneNumberGeoSource {

    private val util by lazy { com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance() }
    private val geocoder by lazy {
        com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder.getInstance()
    }
    private val carriers by lazy {
        com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper.getInstance()
    }

    override fun parseable(number: String): Boolean = try {
        util.parse(number, DEFAULT_REGION)
        true
    } catch (e: com.google.i18n.phonenumbers.NumberParseException) {
        false
    }

    override fun lookup(number: String, locale: Locale): RawGeo? {
        val parsed = try {
            util.parse(number, DEFAULT_REGION)
        } catch (e: com.google.i18n.phonenumbers.NumberParseException) {
            return null
        }
        if (!util.isValidNumber(parsed)) return null
        val region = util.getRegionCodeForNumber(parsed)
        val description = geocoder.getDescriptionForValidNumber(parsed, locale)
            ?.takeIf { it.isNotEmpty() }
        val countryName = region?.let { Locale("", it).getDisplayCountry(locale) }
        val carrier = carriers.getNameForValidNumber(parsed, locale)?.takeIf { it.isNotEmpty() }
        return RawGeo(
            regionCode = region,
            countryCallingCode = parsed.countryCode,
            description = description,
            carrier = carrier,
            countryLevelOnly = description == null || description == countryName,
        )
    }

    companion object {
        /** Numbers in the app are already national CN digits or +cc form; CN is the default. */
        const val DEFAULT_REGION = "CN"
    }
}
