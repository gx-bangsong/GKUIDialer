/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark.impl;

import androidx.annotation.NonNull;

import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.numbermark.NumberMarkFetcher;
import com.android.dialer.numbermark.NumberMarkSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Default {@link NumberMarkFetcher} that performs a plain HTTP(S) GET against the source's search
 * URL and returns the raw response body, re-using the same lightweight HTTP helper as the existing
 * reverse-lookup feature ({@link LookupUtils}).
 */
public final class HttpNumberMarkFetcher implements NumberMarkFetcher {

  private static final Map<String, String> HEADERS = buildHeaders();

  private static Map<String, String> buildHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    return headers;
  }

  @NonNull
  @Override
  public String fetch(@NonNull NumberMarkSource source, @NonNull String number)
      throws IOException {
    return LookupUtils.httpGet(source.buildUrl(number), HEADERS);
  }
}
