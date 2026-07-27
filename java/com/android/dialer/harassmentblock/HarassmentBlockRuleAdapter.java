/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter listing the user's configured {@link HarassmentBlockRule}s. */
final class HarassmentBlockRuleAdapter
    extends RecyclerView.Adapter<HarassmentBlockRuleAdapter.ViewHolder> {

  /** Callbacks for row interactions. */
  interface Listener {
    void onRuleEnabledChanged(HarassmentBlockRule rule, boolean enabled);

    void onRuleDeleted(HarassmentBlockRule rule);
  }

  private final List<HarassmentBlockRule> rules = new ArrayList<>();
  private final Listener listener;

  HarassmentBlockRuleAdapter(Listener listener) {
    this.listener = listener;
  }

  void setRules(List<HarassmentBlockRule> newRules) {
    rules.clear();
    rules.addAll(newRules);
    notifyDataSetChanged();
  }

  int getRuleCount() {
    return rules.size();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_harassment_block_rule, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    HarassmentBlockRule rule = rules.get(position);
    holder.bind(rule, listener);
  }

  @Override
  public int getItemCount() {
    return rules.size();
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {
    private final TextView summaryText;
    private final TextView modeText;
    private final SwitchMaterial enableSwitch;
    private final ImageButton deleteButton;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      summaryText = itemView.findViewById(R.id.rule_summary_text);
      modeText = itemView.findViewById(R.id.rule_mode_text);
      enableSwitch = itemView.findViewById(R.id.rule_enable_switch);
      deleteButton = itemView.findViewById(R.id.rule_delete_button);
    }

    void bind(HarassmentBlockRule rule, Listener listener) {
      android.content.Context context = itemView.getContext();
      String summary;
      switch (rule.getType()) {
        case LOCATION:
          summary = context.getString(R.string.harassment_block_rule_summary_location,
              rule.getValue());
          break;
        case MARK_TYPE:
          com.android.dialer.numbermark.NumberMarkType markType = rule.getMarkType();
          String label =
              markType != null
                  ? context.getString(
                      com.android.dialer.numbermark.NumberMarkDisplayUtils.getLabelRes(markType))
                  : rule.getValue();
          summary = context.getString(R.string.harassment_block_rule_summary_mark_type, label);
          break;
        case NUMBER_PREFIX:
        default:
          summary = context.getString(R.string.harassment_block_rule_summary_prefix,
              rule.getValue());
          break;
      }
      summaryText.setText(summary);
      modeText.setText(
          rule.getMode() == HarassmentBlockMode.WHITELIST
              ? R.string.harassment_block_rule_mode_whitelist
              : R.string.harassment_block_rule_mode_blacklist);

      enableSwitch.setOnCheckedChangeListener(null);
      enableSwitch.setChecked(rule.isEnabled());
      enableSwitch.setOnCheckedChangeListener(
          (buttonView, isChecked) -> listener.onRuleEnabledChanged(rule, isChecked));

      deleteButton.setOnClickListener(v -> listener.onRuleDeleted(rule));
    }
  }
}
