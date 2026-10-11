/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

/**
 * Identifies a third-party website that can be used to look up whether a phone number has been
 * publicly "marked" (标记) by other users, e.g. as spam, fraud, or advertising.
 *
 * <p>Each source only knows how to build a search URL; it does NOT know how to parse the result.
 * Parsing is delegated to a separate, independently updatable {@link
 * com.android.dialer.numbermark.rule.NumberMarkExtractionRule}. This separation is what allows the
 * rules (regexes/keywords tied to a particular site's current HTML/JSON layout) to be updated --
 * potentially by simply shipping a new JSON config -- without having to touch or re-review any of
 * the networking or UI code whenever one of these sites changes its markup.
 */
public enum NumberMarkSource {
  /** 360 search (so.com) phone number search results. */
  SO_360("360", "https://www.so.com/s?q=%s"),

  /** Baidu search (baidu.com) phone number search results. */
  BAIDU("baidu", "https://www.baidu.com/s?wd=%s"),

  /** Sogou search results, which surface 电话邦 (dianhua.cn) number-mark cards. */
  SOGOU_DIANHUABANG("sogou_dianhuabang", "https://www.sogou.com/web?query=%s");

  private final String id;
  private final String urlTemplate;

  NumberMarkSource(String id, String urlTemplate) {
    this.id = id;
    this.urlTemplate = urlTemplate;
  }

  /** Stable identifier used for preferences and the extraction rule mapping. */
  public String getId() {
    return id;
  }

  /** Builds the URL to fetch for a given raw phone number. */
  public String buildUrl(String number) {
    return String.format(urlTemplate, number);
  }

  public static NumberMarkSource fromId(String id) {
    for (NumberMarkSource source : values()) {
      if (source.id.equals(id)) {
        return source;
      }
    }
    return null;
  }
}
