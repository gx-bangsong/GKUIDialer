package com.android.dialer.numberinfo.model

import android.content.Context

enum class MarkType(
    val severity: Int,
    val isBlockable: Boolean
) {
    SPAM_MARKETING(severity = 3, isBlockable = true),
    SPAM_FRAUD(severity = 5, isBlockable = true),
    SPAM_HARASSMENT(severity = 4, isBlockable = true),
    FINANCE_DEBT(severity = 3, isBlockable = true),
    REAL_ESTATE(severity = 1, isBlockable = true),
    INSURANCE(severity = 1, isBlockable = true),
    DELIVERY(severity = 0, isBlockable = false),
    SERVICE_OFFICIAL(severity = 0, isBlockable = false),
    UNKNOWN(severity = 0, isBlockable = false);

    fun displayName(context: Context): String {
        return when (this) {
            SPAM_MARKETING -> context.getString(R.string.mark_type_spam_marketing)
            SPAM_FRAUD -> context.getString(R.string.mark_type_spam_fraud)
            SPAM_HARASSMENT -> context.getString(R.string.mark_type_spam_harassment)
            FINANCE_DEBT -> context.getString(R.string.mark_type_finance_debt)
            REAL_ESTATE -> context.getString(R.string.mark_type_real_estate)
            INSURANCE -> context.getString(R.string.mark_type_insurance)
            DELIVERY -> context.getString(R.string.mark_type_delivery)
            SERVICE_OFFICIAL -> context.getString(R.string.mark_type_service_official)
            UNKNOWN -> context.getString(R.string.mark_type_unknown)
        }
    }
}
