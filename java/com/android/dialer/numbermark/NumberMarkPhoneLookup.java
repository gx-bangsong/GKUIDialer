/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark;

import android.content.Context;
import android.text.TextUtils;

import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.NumberMarkInfo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import javax.inject.Inject;

/**
 * {@link PhoneLookup} implementation that surfaces third-party number-marking data (360, Baidu,
 * Sogou/电话邦) via {@link NumberMarkService}.
 *
 * <p>All of the actual fetching/parsing complexity is delegated to {@link NumberMarkService}; this
 * class is only responsible for adapting that API to the {@link PhoneLookup} contract (mapping to
 * and from the {@link NumberMarkInfo} proto sub-message) so the rest of Dialer (call log, in-call
 * UI) can consume marking data the same way it consumes CP2/Cequint/CNAP info.
 */
public final class NumberMarkPhoneLookup implements PhoneLookup<NumberMarkInfo> {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;
  private final NumberMarkService numberMarkService;

  @Inject
  NumberMarkPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
    this.numberMarkService = new NumberMarkService(appContext);
  }

  @Override
  public ListenableFuture<NumberMarkInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    if (!NumberMarkSettings.isEnabled(appContext)) {
      return Futures.immediateFuture(NumberMarkInfo.getDefaultInstance());
    }
    return backgroundExecutorService.submit(
        () -> toProto(numberMarkService.lookup(dialerPhoneNumber.getNormalizedNumber())));
  }

  private static NumberMarkInfo toProto(NumberMarkResult result) {
    NumberMarkInfo.Builder builder = NumberMarkInfo.newBuilder();
    if (result.isEmpty()) {
      return builder.build();
    }
    builder.setMarkType(result.getType().name());
    if (!TextUtils.isEmpty(result.getRawLabel())) {
      builder.setRawLabel(result.getRawLabel());
    }
    builder.setMarkCount(result.getMarkCount());
    if (!TextUtils.isEmpty(result.getLocation())) {
      builder.setLocation(result.getLocation());
    }
    return builder.build();
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, NumberMarkInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, NumberMarkInfo> existingInfoMap) {
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, NumberMarkInfo subMessage) {
    destination.setNumberMarkInfo(subMessage);
  }

  @Override
  public NumberMarkInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getNumberMarkInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {}

  @Override
  public void unregisterContentObservers() {}

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "NumberMarkPhoneLookup";
  }
}
