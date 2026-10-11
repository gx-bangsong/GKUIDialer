/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

/**
 * Normalized categories that a phone number can be publicly "marked" as by third-party number
 * marking services (e.g. 360, Baidu, Sogou/dianhuabang).
 *
 * <p>Every {@link com.android.dialer.numbermark.rule.NumberMarkExtractionRule} maps the
 * free-form Chinese labels used by each individual website (which change over time) onto this
 * small, stable set of values. Because callers of this API (call log, incall UI, harassment
 * blocking) only ever see {@link NumberMarkType}, a website redesign only requires updating the
 * keyword-to-type mapping data, not any of the code that consumes the result.
 */
public enum NumberMarkType {
  /** No mark information is available for this number. */
  UNMARKED,

  /** The number has been reported for fraud/scam activity. */
  FRAUD,

  /** General harassment calls that don't fit a more specific category. */
  HARASSMENT,

  /** Advertising, sales, or telemarketing calls. */
  ADVERTISING_SALES,

  /** Loans, financing, or investment solicitation. */
  LOAN_FINANCE,

  /** Insurance sales calls. */
  INSURANCE,

  /** Real estate or property agents. */
  REAL_ESTATE_AGENT,

  /** Courier/delivery notification calls. */
  EXPRESS_DELIVERY,

  /** Taxi/ride-hailing dispatch calls. */
  TAXI_RIDE_HAILING,

  /** Survey or market-research calls. */
  SURVEY_MARKETING,

  /** Recruiting, training, or other intermediary/agent solicitation. */
  INTERMEDIARY_AGENT,

  /** The number belongs to a verified business/organization (not spam). */
  VERIFIED_ORGANIZATION,

  /** Marked by a source, but the specific category couldn't be classified. */
  OTHER;

  /**
   * Returns true if this type generally represents an unwanted call that a user may want to
   * block or be warned about.
   */
  public boolean isNuisance() {
    switch (this) {
      case FRAUD:
      case HARASSMENT:
      case ADVERTISING_SALES:
      case LOAN_FINANCE:
      case INSURANCE:
      case REAL_ESTATE_AGENT:
      case SURVEY_MARKETING:
      case INTERMEDIARY_AGENT:
        return true;
      default:
        return false;
    }
  }
}
