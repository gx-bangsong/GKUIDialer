/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Fetches the raw response for a phone number search against a single {@link NumberMarkSource}.
 *
 * <p>Kept separate from {@link com.android.dialer.numbermark.rule.NumberMarkExtractionRule} so
 * that networking concerns (headers, retries, mobile-vs-desktop UA selection) can evolve
 * independently of how the response is parsed.
 */
public interface NumberMarkFetcher {

  @NonNull
  String fetch(@NonNull NumberMarkSource source, @NonNull String number) throws IOException;
}
