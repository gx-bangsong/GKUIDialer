/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.dialer.R;

/** Helpers for presenting {@link NumberMarkType} values in the UI. */
public final class NumberMarkDisplayUtils {

  private NumberMarkDisplayUtils() {}

  @StringRes
  public static int getLabelRes(NumberMarkType type) {
    switch (type) {
      case FRAUD:
        return R.string.number_mark_type_fraud;
      case HARASSMENT:
        return R.string.number_mark_type_harassment;
      case ADVERTISING_SALES:
        return R.string.number_mark_type_advertising_sales;
      case LOAN_FINANCE:
        return R.string.number_mark_type_loan_finance;
      case INSURANCE:
        return R.string.number_mark_type_insurance;
      case REAL_ESTATE_AGENT:
        return R.string.number_mark_type_real_estate_agent;
      case EXPRESS_DELIVERY:
        return R.string.number_mark_type_express_delivery;
      case TAXI_RIDE_HAILING:
        return R.string.number_mark_type_taxi_ride_hailing;
      case SURVEY_MARKETING:
        return R.string.number_mark_type_survey_marketing;
      case INTERMEDIARY_AGENT:
        return R.string.number_mark_type_intermediary_agent;
      case VERIFIED_ORGANIZATION:
        return R.string.number_mark_type_verified_organization;
      case OTHER:
        return R.string.number_mark_type_other;
      case UNMARKED:
      default:
        return 0;
    }
  }

  /**
   * Returns a display string for {@code rawTypeName}/{@code rawLabel} as produced by
   * {@link com.android.dialer.phonelookup.consolidator.PhoneLookupInfoConsolidator}, preferring a
   * localized label for the normalized type and falling back to the source's original raw text.
   */
  @Nullable
  public static String getDisplayLabel(
      Context context, @Nullable String rawTypeName, @Nullable String rawLabel) {
    if (!TextUtils.isEmpty(rawTypeName)) {
      try {
        NumberMarkType type = NumberMarkType.valueOf(rawTypeName);
        int labelRes = getLabelRes(type);
        if (labelRes != 0) {
          return context.getString(labelRes);
        }
      } catch (IllegalArgumentException e) {
        // Fall through to raw label.
      }
    }
    return TextUtils.isEmpty(rawLabel) ? null : rawLabel;
  }
}
