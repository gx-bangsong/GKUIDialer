package com.android.dialer.numberinfo.provider

import com.android.dialer.numberinfo.model.MarkType

interface PhoneTagProvider {
    val providerName: String
    val priority: Int
    val isEnabled: Boolean
    suspend fun query(phoneNumber: String): PhoneTagResult?
    fun getRuleVersion(): Int
}

data class PhoneTagResult(
    val phoneNumber: String,
    val name: String?,
    val tag: String?,
    val tagType: MarkType?,
    val markCount: Int = 0,
    val isScam: Boolean = false,
    val location: String?,
    val carrier: String?,
    val source: String
)
