/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.dialer.numbermark.NumberMarkDisplayUtils;
import com.android.dialer.numbermark.NumberMarkType;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog used to create a new {@link HarassmentBlockRule}.
 *
 * <p>Presents:
 *
 * <ul>
 *   <li>A single-select chip row to choose the rule dimension (number segment / location / mark
 *       type), mirroring the segmented-chip pattern used throughout domestic Android skins.
 *   <li>For number-segment rules, a row of quick-input chips for common segments (400, 95xx,
 *       1069xx, virtual-carrier prefixes, etc.) that fill in the text field with one tap.
 *   <li>For mark-type rules, a multi-select chip group (multiple chips can be toggled on at once)
 *       so a single "add" action can create rules for several mark types together.
 * </ul>
 */
public final class AddHarassmentBlockRuleDialog {

  /** Invoked with the list of rules to persist once the user taps save. */
  public interface OnRulesReadyListener {
    void onRulesReady(@NonNull List<HarassmentBlockRule> rules);
  }

  private AddHarassmentBlockRuleDialog() {}

  public static void show(@NonNull Context context, @NonNull OnRulesReadyListener listener) {
    View view =
        LayoutInflater.from(context)
            .inflate(R.layout.dialog_add_harassment_block_rule, null, false);

    ChipGroup ruleTypeChipGroup = view.findViewById(R.id.rule_type_chip_group);
    Chip ruleTypeChipPrefix = view.findViewById(R.id.rule_type_chip_prefix);
    Chip ruleTypeChipLocation = view.findViewById(R.id.rule_type_chip_location);
    Chip ruleTypeChipMark = view.findViewById(R.id.rule_type_chip_mark);

    ChipGroup ruleModeChipGroup = view.findViewById(R.id.rule_mode_chip_group);
    Chip ruleModeChipBlacklist = view.findViewById(R.id.rule_mode_chip_blacklist);
    Chip ruleModeChipWhitelist = view.findViewById(R.id.rule_mode_chip_whitelist);

    View valueInputGroup = view.findViewById(R.id.rule_value_input_group);
    TextInputLayout valueInputLayout = view.findViewById(R.id.rule_value_input_layout);
    TextInputEditText valueEditText = view.findViewById(R.id.rule_value_edit_text);
    ChipGroup commonPrefixChipGroup = view.findViewById(R.id.common_prefix_chip_group);

    View markTypeGroup = view.findViewById(R.id.rule_mark_type_group);
    ChipGroup markTypeChipGroup = view.findViewById(R.id.mark_type_chip_group);

    // Populate the common-number-segment quick-input chips.
    for (String prefix : CommonNumberPrefixes.PREFIXES) {
      Chip chip = new Chip(context);
      chip.setText(prefix);
      chip.setCheckable(false);
      chip.setClickable(true);
      chip.setOnClickListener(v -> valueEditText.setText(prefix));
      commonPrefixChipGroup.addView(chip);
    }

    // Populate the multi-select mark-type chips (domestic-UI-style multi-select).
    for (NumberMarkType type : NumberMarkType.values()) {
      if (type == NumberMarkType.UNMARKED) {
        continue;
      }
      int labelRes = NumberMarkDisplayUtils.getLabelRes(type);
      if (labelRes == 0) {
        continue;
      }
      Chip chip = new Chip(context);
      chip.setText(labelRes);
      chip.setCheckable(true);
      chip.setTag(type);
      markTypeChipGroup.addView(chip);
    }

    Runnable updateVisibility =
        () -> {
          boolean isMarkType = ruleTypeChipMark.isChecked();
          valueInputGroup.setVisibility(isMarkType ? View.GONE : View.VISIBLE);
          markTypeGroup.setVisibility(isMarkType ? View.VISIBLE : View.GONE);
        };
    View.OnClickListener ruleTypeClickListener = v -> updateVisibility.run();
    ruleTypeChipPrefix.setOnClickListener(ruleTypeClickListener);
    ruleTypeChipLocation.setOnClickListener(ruleTypeClickListener);
    ruleTypeChipMark.setOnClickListener(ruleTypeClickListener);
    updateVisibility.run();

    AlertDialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(R.string.harassment_block_add_rule)
            .setView(view)
            .setNegativeButton(R.string.harassment_block_cancel, null)
            .setPositiveButton(R.string.harassment_block_save, null)
            .create();

    dialog.setOnShowListener(
        dialogInterface -> {
          View saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
          saveButton.setOnClickListener(
              v -> {
                HarassmentBlockMode mode =
                    ruleModeChipWhitelist.isChecked()
                        ? HarassmentBlockMode.WHITELIST
                        : HarassmentBlockMode.BLACKLIST;

                List<HarassmentBlockRule> rules = new ArrayList<>();

                if (ruleTypeChipMark.isChecked()) {
                  for (int i = 0; i < markTypeChipGroup.getChildCount(); i++) {
                    Chip chip = (Chip) markTypeChipGroup.getChildAt(i);
                    if (chip.isChecked()) {
                      NumberMarkType type = (NumberMarkType) chip.getTag();
                      rules.add(
                          new HarassmentBlockRule(
                              0, HarassmentBlockRuleType.MARK_TYPE, type.name(), mode, true));
                    }
                  }
                  if (rules.isEmpty()) {
                    valueInputLayout.setError(null);
                    Toast.makeText(
                            context, R.string.harassment_block_invalid_rule, Toast.LENGTH_SHORT)
                        .show();
                    return;
                  }
                } else {
                  String value =
                      valueEditText.getText() != null
                          ? valueEditText.getText().toString().trim()
                          : "";
                  if (TextUtils.isEmpty(value.replace("!", ""))) {
                    valueInputLayout.setError(
                        context.getString(R.string.harassment_block_invalid_rule));
                    return;
                  }
                  HarassmentBlockRuleType type =
                      ruleTypeChipLocation.isChecked()
                          ? HarassmentBlockRuleType.LOCATION
                          : HarassmentBlockRuleType.NUMBER_PREFIX;
                  rules.add(new HarassmentBlockRule(0, type, value, mode, true));
                }

                listener.onRulesReady(rules);
                dialog.dismiss();
              });
        });

    dialog.show();
  }
}
