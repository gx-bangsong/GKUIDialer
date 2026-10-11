/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.incallui.harassmentblock;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.DialerExecutorFactory;
import com.android.dialer.harassmentblock.HarassmentBlockEvaluator;
import com.android.dialer.harassmentblock.HarassmentBlockEvaluator.Decision;
import com.android.dialer.harassmentblock.HarassmentBlockRule;
import com.android.dialer.harassmentblock.HarassmentBlockRuleDatabaseHelper;
import com.android.dialer.harassmentblock.HarassmentBlockSettings;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.numbermark.NumberMarkResult;
import com.android.dialer.numbermark.NumberMarkService;
import com.android.dialer.numbermark.NumberMarkType;
import com.android.dialer.phonenumbergeoutil.PhoneNumberGeoUtil;
import com.android.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

import java.util.List;

/**
 * {@link CallList.Listener} that implements the harassment blocking (骚扰拦截) feature: rejects
 * incoming calls that match the user's configured black/white-list rules based on number segment
 * (号段/归属地) or {@link NumberMarkType}.
 *
 * <p>The actual matching logic lives in {@link HarassmentBlockEvaluator}, which is pure and has no
 * Android dependencies; this class is only responsible for gathering the inputs it needs
 * (contact-book membership, geo description, cached number-mark data) and applying its decision to
 * a live {@link DialerCall}.
 */
public final class HarassmentBlockCallListener implements CallList.Listener {

  private final Context context;
  private final DialerExecutorFactory dialerExecutorFactory;

  public HarassmentBlockCallListener(
      Context context, @NonNull DialerExecutorFactory dialerExecutorFactory) {
    this.context = context;
    this.dialerExecutorFactory = Assert.isNotNull(dialerExecutorFactory);
  }

  @Override
  public void onIncomingCall(final DialerCall call) {
    if (!HarassmentBlockSettings.isEnabled(context)) {
      return;
    }
    String number = call.getNumber();
    if (TextUtils.isEmpty(number)) {
      return;
    }

    EvaluateWorker worker = new EvaluateWorker(context, number, call.getCountryIso());
    dialerExecutorFactory
        .createNonUiTaskBuilder(worker)
        .onSuccess(
            decision -> {
              if (decision.block) {
                LogUtil.i(
                    "HarassmentBlockCallListener.onIncomingCall",
                    "blocking incoming call, matchedRule=%s",
                    decision.matchedRule);
                call.reject(false, null);
              }
            })
        .build()
        .executeParallel(null);
  }

  private static final class EvaluateWorker implements Worker<Void, Decision> {

    private final Context appContext;
    private final String number;
    private final String countryIso;

    EvaluateWorker(Context appContext, String number, String countryIso) {
      this.appContext = appContext.getApplicationContext();
      this.number = number;
      this.countryIso = countryIso;
    }

    @Override
    @NonNull
    public Decision doInBackground(@Nullable Void input) {
      HarassmentBlockRuleDatabaseHelper db = new HarassmentBlockRuleDatabaseHelper(appContext);
      List<HarassmentBlockRule> rules = db.getEnabledRules();

      boolean isKnownContact = isKnownContact(appContext, number);
      String location = getLocation(appContext, number, countryIso);
      NumberMarkType markType = getCachedMarkType(appContext, number);

      return HarassmentBlockEvaluator.evaluate(
          number,
          location,
          markType,
          isKnownContact,
          HarassmentBlockSettings.getMode(appContext),
          rules);
    }

    private static boolean isKnownContact(Context context, String number) {
      if (!PermissionsUtil.hasContactsReadPermissions(context)) {
        return false;
      }
      Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
      try (android.database.Cursor cursor =
          context
              .getContentResolver()
              .query(uri, new String[] {PhoneLookup._ID}, null, null, null)) {
        return cursor != null && cursor.getCount() > 0;
      } catch (SecurityException | IllegalArgumentException e) {
        return false;
      }
    }

    private static String getLocation(Context context, String number, String countryIso) {
      PhoneNumberGeoUtil geoUtil = PhoneNumberGeoUtilComponent.get(context).getPhoneNumberGeoUtil();
      String iso = TextUtils.isEmpty(countryIso) ? GeoUtil.getCurrentCountryIso(context) : countryIso;
      return geoUtil.getGeoDescription(context, number, iso);
    }

    private static NumberMarkType getCachedMarkType(Context context, String number) {
      NumberMarkService service = new NumberMarkService(context);
      String normalized = PhoneNumberUtils.formatNumberToE164(number, GeoUtil.getCurrentCountryIso(
          context));
      NumberMarkResult result =
          service.lookupCachedOnly(normalized != null ? normalized : number);
      if (result.isEmpty()) {
        // Cache miss: kick off a full (network) lookup on the shared background executor to warm
        // the cache for the next call from this number, but don't block on it for this call's
        // decision (real-time call screening must not wait on network I/O).
        DialerExecutorComponent.get(context)
            .backgroundExecutor()
            .submit(() -> service.lookup(normalized != null ? normalized : number));
        return null;
      }
      return result.getType();
    }
  }

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onDisconnect(DialerCall call) {}

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}
}
