/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.yellowpages.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A single yellow-pages (黄页) entry: a well-known organization/service phone number grouped under
 * a category, along with metadata used both to display it and to recognize it during incoming-call
 * number identification.
 *
 * <p>The data model intentionally mirrors the schema used by exTHmUI's
 * {@code YellowPage_Data}/{@code android_packages_apps_YellowPage} project (category + name +
 * number + icon), so that ruleset data exported from that project can be dropped in with minimal
 * transformation.
 */
public final class YellowPageEntry {

  @NonNull private final String category;
  @NonNull private final String name;
  @NonNull private final String number;
  @Nullable private final String iconAssetName;
  @Nullable private final String description;

  public YellowPageEntry(
      @NonNull String category,
      @NonNull String name,
      @NonNull String number,
      @Nullable String iconAssetName,
      @Nullable String description) {
    this.category = category;
    this.name = name;
    this.number = number;
    this.iconAssetName = iconAssetName;
    this.description = description;
  }

  @NonNull
  public String getCategory() {
    return category;
  }

  @NonNull
  public String getName() {
    return name;
  }

  @NonNull
  public String getNumber() {
    return number;
  }

  @Nullable
  public String getIconAssetName() {
    return iconAssetName;
  }

  @Nullable
  public String getDescription() {
    return description;
  }
}
