/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.calldetails;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;
import com.android.dialer.app.AccountSelectionActivity;
import com.android.dialer.calldetails.CallDetailsActivityCommon.AssistedDialingNumberParseWorker;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.numberinfo.incall.CallLogLabels;
import com.android.dialer.numberinfo.incall.CallerIdInfo;
import com.android.dialer.numberinfo.location.NumberLocationFormatter;
import com.android.dialer.numberinfo.ui.ManualMarkActivity;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.BidiTextView;

/**
 * ViewHolder for the header in {@link OldCallDetailsActivity} or {@link CallDetailsActivity}.
 *
 * <p>The header contains contact info and the primary callback button.
 */
public class CallDetailsHeaderViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener, OnLongClickListener, FailureListener {

  private final CallDetailsHeaderListener callDetailsHeaderListener;

  /** Number whose location lookup is currently wanted, or null. */
  private String pendingLocationNumber;
  private final ImageView callbackButton;
  private final TextView manualMarkEntry;
  private final BidiTextView nameView;
  private final BidiTextView numberView;
  private final TextView networkView;
  private final QuickContactBadge contactPhoto;
  private final Context context;
  private final TextView assistedDialingInternationalDirectDialCodeAndCountryCodeText;
  private final RelativeLayout assistedDialingContainer;

  private final String number;
  private final String postDialDigits;

  private @CallbackAction int callbackAction;

  CallDetailsHeaderViewHolder(
      View container,
      String number,
      String postDialDigits,
      CallDetailsHeaderListener callDetailsHeaderListener) {
    super(container);
    context = container.getContext();
    callbackButton = container.findViewById(R.id.call_back_button);
    manualMarkEntry = container.findViewById(R.id.manual_mark_entry);
    nameView = container.findViewById(R.id.contact_name);
    numberView = container.findViewById(R.id.phone_number);
    networkView = container.findViewById(R.id.network);
    contactPhoto = container.findViewById(R.id.quick_contact_photo);
    assistedDialingInternationalDirectDialCodeAndCountryCodeText =
        container.findViewById(R.id.assisted_dialing_text);
    assistedDialingContainer = container.findViewById(R.id.assisted_dialing_container);

    assistedDialingContainer.setOnClickListener(
        callDetailsHeaderListener::openAssistedDialingSettings);

    callbackButton.setOnClickListener(this);
    callbackButton.setOnLongClickListener(this);

    this.number = number;
    this.postDialDigits = postDialDigits;
    this.callDetailsHeaderListener = callDetailsHeaderListener;
  }

  private boolean hasAssistedDialingFeature(Integer features) {
    return (features & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
        == TelephonyManagerCompat.FEATURES_ASSISTED_DIALING;
  }

  void updateAssistedDialingInfo(CallDetailsEntry callDetailsEntry) {

    if (callDetailsEntry != null && hasAssistedDialingFeature(callDetailsEntry.getFeatures())) {
      showAssistedDialingContainer(true);
      callDetailsHeaderListener.createAssistedDialerNumberParserTask(
          new CallDetailsActivityCommon.AssistedDialingNumberParseWorker(),
          this::updateAssistedDialingText,
          this::onFailure);

    } else {
      showAssistedDialingContainer(false);
    }
  }

  private void showAssistedDialingContainer(boolean shouldShowContainer) {
    if (shouldShowContainer) {
      assistedDialingContainer.setVisibility(View.VISIBLE);
    } else {
      LogUtil.i(
          "CallDetailsHeaderViewHolder.updateAssistedDialingInfo",
          "hiding assisted dialing ui elements");
      assistedDialingContainer.setVisibility(View.GONE);
    }
  }

  private void updateAssistedDialingText(Integer countryCode) {

    // Try and handle any poorly formed inputs.
    if (countryCode <= 0) {
      onFailure(new IllegalStateException());
      return;
    }

    LogUtil.i(
        "CallDetailsHeaderViewHolder.updateAssistedDialingText", "Updating Assisted Dialing Text");
    assistedDialingInternationalDirectDialCodeAndCountryCodeText.setText(
        context.getString(
            R.string.assisted_dialing_country_code_entry, String.valueOf(countryCode)));
  }

  @Override
  public void onFailure(Throwable unused) {
    assistedDialingInternationalDirectDialCodeAndCountryCodeText.setText(
        R.string.assisted_dialing_country_code_entry_failure);
  }

  /** Populates the contact info fields based on the current contact information. */
  void updateContactInfo(DialerContact contact, @CallbackAction int callbackAction) {
    // Any location lookup started for a previous contact is no longer wanted.
    pendingLocationNumber = null;
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            contactPhoto,
            contact.getContactUri().isEmpty() ? null : Uri.parse(contact.getContactUri()),
            contact.getPhotoId(),
            contact.getPhotoUri().isEmpty() ? null : Uri.parse(contact.getPhotoUri()),
            contact.getNameOrNumber(),
            contact.getContactType());

    // Hide the secondary text of the header by default.
    // We will show it if needed (see below).
    numberView.setVisibility(View.GONE);
    numberView.setText(null);

    if (PhoneNumberHelper.isEmergencyNumber(context, contact.getNumber())) {
      nameView.setText(context.getResources().getString(R.string.emergency_number));
    } else {
      nameView.setText(contact.getNameOrNumber());
      if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
        numberView.setVisibility(View.VISIBLE);
        String secondaryInfo =
            TextUtils.isEmpty(contact.getNumberLabel())
                ? contact.getDisplayNumber()
                : context.getString(R.string.call_subject_type_and_number,
                    contact.getNumberLabel(),
                    contact.getDisplayNumber());
        numberView.setText(secondaryInfo);
      }
    }

    if (!TextUtils.isEmpty(contact.getSimDetails().getNetwork())) {
      networkView.setVisibility(View.VISIBLE);
      networkView.setText(contact.getSimDetails().getNetwork());
      if (contact.getSimDetails().getColor() != PhoneAccount.NO_HIGHLIGHT_COLOR) {
        networkView.setTextColor(contact.getSimDetails().getColor());
      }
    }

    updateLocation(contact);
    updateManualMarkEntry(contact);
    setCallbackAction(callbackAction);
  }

  /**
   * Non-contact numbers show local data on the header: the business name replaces the number, and
   * the spam label or the location fills the empty secondary line. The lookup is local only and runs
   * on a background worker; the result is applied only if the holder still shows that number.
   */
  private void updateLocation(DialerContact contact) {
    if (!contact.getContactUri().isEmpty()
        || PhoneNumberHelper.isEmergencyNumber(context, contact.getNumber())) {
      return;
    }
    String number = contact.getNumber();
    if (TextUtils.isEmpty(number)) {
      return;
    }
    pendingLocationNumber = number;
    // The name slot is replaced by a business name, so keep the formatted number for the
    // secondary line (it is the name the header shows for a non-contact).
    String numberText = contact.getNameOrNumber();
    callDetailsHeaderListener.createNumberInfoTask(
        number,
        info -> onNumberInfoLoaded(number, numberText, info),
        // Not onFailure(): that one shows the assisted-dialing error. A failed lookup just
        // leaves the header as it is.
        unused -> {});
  }

  /** Mark or correct a non-contact number. Contacts and emergency numbers get no entry. */
  private void updateManualMarkEntry(DialerContact contact) {
    String number = contact.getNumber();
    boolean show =
        contact.getContactUri().isEmpty()
            && !TextUtils.isEmpty(number)
            && !PhoneNumberHelper.isEmergencyNumber(context, number);
    manualMarkEntry.setVisibility(show ? View.VISIBLE : View.GONE);
    manualMarkEntry.setOnClickListener(
        show ? v -> context.startActivity(ManualMarkActivity.newIntent(context, number)) : null);
  }

  private void onNumberInfoLoaded(
      String number, String numberText, @Nullable CallerIdInfo info) {
    if (info == null || !number.equals(pendingLocationNumber)) {
      return;
    }
    String secondary;
    String businessName = CallLogLabels.nameFor(info);
    if (businessName != null) {
      nameView.setText(businessName);
      // Same as the call log: the number stays visible, with the location after it.
      secondary =
          TextUtils.isEmpty(info.location)
              ? numberText
              : numberText + NumberLocationFormatter.SEPARATOR + info.location;
    } else {
      secondary = CallLogLabels.spamLineFor(info);
      if (secondary == null) {
        secondary = info.location;
      }
    }
    // Only fill the line when it is empty (the number is shown there otherwise).
    if (TextUtils.isEmpty(secondary)
        || (numberView.getVisibility() == View.VISIBLE && !TextUtils.isEmpty(numberView.getText()))) {
      return;
    }
    numberView.setVisibility(View.VISIBLE);
    numberView.setText(secondary);
  }

  void updateContactInfo(CallDetailsHeaderInfo headerInfo, @CallbackAction int callbackAction) {
    GlidePhotoManagerComponent.get(context)
        .glidePhotoManager()
        .loadQuickContactBadge(contactPhoto, headerInfo.getPhotoInfo());

    nameView.setText(headerInfo.getPrimaryText());
    if (!headerInfo.getSecondaryText().isEmpty()) {
      numberView.setVisibility(View.VISIBLE);
      numberView.setText(headerInfo.getSecondaryText());
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
    }

    setCallbackAction(callbackAction);
  }

  private void setCallbackAction(@CallbackAction int callbackAction) {
    this.callbackAction = callbackAction;
    switch (callbackAction) {
      case CallbackAction.IMS_VIDEO:
        callbackButton.setVisibility(View.VISIBLE);
        callbackButton.setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
        break;
      case CallbackAction.VOICE:
        callbackButton.setVisibility(View.VISIBLE);
        callbackButton.setImageResource(R.drawable.quantum_ic_call_vd_theme_24);
        break;
      case CallbackAction.NONE:
        callbackButton.setVisibility(View.GONE);
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
    }
  }

  @Override
  public void onClick(View view) {
    if (view == callbackButton) {
      switch (callbackAction) {
        case CallbackAction.IMS_VIDEO:
          callDetailsHeaderListener.placeImsVideoCall(number);
          break;
        case CallbackAction.VOICE:
          callDetailsHeaderListener.placeVoiceCall(number, postDialDigits);
          break;
        case CallbackAction.NONE:
        default:
          throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
      }
    } else {
      throw Assert.createIllegalStateFailException("View OnClickListener not implemented: " + view);
    }
  }

  @Override
  public boolean onLongClick(View view) {
    if (view == callbackButton) {
      Intent intent = AccountSelectionActivity.createIntent(view.getContext(),
          number, CallInitiationType.Type.CALL_DETAILS);
      if (intent != null) {
        DialerUtils.startActivityWithErrorToast(view.getContext(), intent);
        return true;
      }
    }
    return false;
  }

  /** Listener for the call details header */
  interface CallDetailsHeaderListener {

    /** Places an IMS video call. */
    void placeImsVideoCall(String phoneNumber);

    /** Place a traditional voice call. */
    void placeVoiceCall(String phoneNumber, String postDialDigits);

    /** Access the Assisted Dialing settings * */
    void openAssistedDialingSettings(View view);

    void createAssistedDialerNumberParserTask(
        AssistedDialingNumberParseWorker worker,
        SuccessListener<Integer> onSuccess,
        FailureListener onFailure);

    /** Local-only caller info of [number] (business name, spam label, location); background worker. */
    void createNumberInfoTask(
        String number, SuccessListener<CallerIdInfo> onSuccess, FailureListener onFailure);
  }
}
