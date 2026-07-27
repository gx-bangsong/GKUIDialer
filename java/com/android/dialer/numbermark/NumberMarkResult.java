/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable result of looking up a phone number against a number marking source (360, Baidu,
 * Sogou/dianhuabang, etc).
 */
public final class NumberMarkResult {

  /** A result representing "no information found". */
  public static final NumberMarkResult EMPTY =
      new NumberMarkResult(
          /* sourceId= */ "", NumberMarkType.UNMARKED, /* rawLabel= */ null, 0, /* location= */
          null);

  private final String sourceId;
  private final NumberMarkType type;
  @Nullable private final String rawLabel;
  private final int markCount;
  @Nullable private final String location;

  public NumberMarkResult(
      @NonNull String sourceId,
      @NonNull NumberMarkType type,
      @Nullable String rawLabel,
      int markCount,
      @Nullable String location) {
    this.sourceId = sourceId;
    this.type = type;
    this.rawLabel = rawLabel;
    this.markCount = markCount;
    this.location = location;
  }

  /** ID of the {@link NumberMarkSource} that produced this result. */
  @NonNull
  public String getSourceId() {
    return sourceId;
  }

  /** The normalized mark type. */
  @NonNull
  public NumberMarkType getType() {
    return type;
  }

  /** The original, unnormalized label text as shown by the source, if any (e.g. "广告推销"). */
  @Nullable
  public String getRawLabel() {
    return rawLabel;
  }

  /** Number of times other users have applied this mark, or 0 if unknown. */
  public int getMarkCount() {
    return markCount;
  }

  /** Free-form location/carrier text associated with the number, if provided by the source. */
  @Nullable
  public String getLocation() {
    return location;
  }

  public boolean isEmpty() {
    return type == NumberMarkType.UNMARKED && rawLabel == null;
  }

  @Override
  public String toString() {
    return "NumberMarkResult{"
        + "sourceId='"
        + sourceId
        + '\''
        + ", type="
        + type
        + ", rawLabel='"
        + rawLabel
        + '\''
        + ", markCount="
        + markCount
        + ", location='"
        + location
        + '\''
        + '}';
  }
}
