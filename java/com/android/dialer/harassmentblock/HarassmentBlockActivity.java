/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.app.BaseActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

/**
 * Settings screen for the harassment blocking (骚扰拦截) feature: toggles the feature on/off,
 * switches between blacklist/whitelist mode, and manages the list of {@link HarassmentBlockRule}s.
 */
public class HarassmentBlockActivity extends BaseActivity {

  private HarassmentBlockRuleDatabaseHelper database;
  private HarassmentBlockRuleAdapter adapter;
  private View emptyView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_harassment_block);

    android.app.ActionBar actionBar = getActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.harassment_block_settings_label);
    }

    database = new HarassmentBlockRuleDatabaseHelper(this);

    SwitchMaterial enableSwitch = findViewById(R.id.harassment_block_enable_switch);
    enableSwitch.setChecked(HarassmentBlockSettings.isEnabled(this));
    enableSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> HarassmentBlockSettings.setEnabled(this, isChecked));

    ChipGroup modeChipGroup = findViewById(R.id.harassment_block_mode_chip_group);
    Chip blacklistChip = findViewById(R.id.mode_chip_blacklist);
    Chip whitelistChip = findViewById(R.id.mode_chip_whitelist);
    boolean isWhitelist = HarassmentBlockSettings.getMode(this) == HarassmentBlockMode.WHITELIST;
    blacklistChip.setChecked(!isWhitelist);
    whitelistChip.setChecked(isWhitelist);
    View.OnClickListener modeClickListener =
        v ->
            HarassmentBlockSettings.setMode(
                this,
                whitelistChip.isChecked()
                    ? HarassmentBlockMode.WHITELIST
                    : HarassmentBlockMode.BLACKLIST);
    blacklistChip.setOnClickListener(modeClickListener);
    whitelistChip.setOnClickListener(modeClickListener);

    emptyView = findViewById(R.id.harassment_block_empty_view);
    RecyclerView recyclerView = findViewById(R.id.harassment_block_rule_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    adapter =
        new HarassmentBlockRuleAdapter(
            new HarassmentBlockRuleAdapter.Listener() {
              @Override
              public void onRuleEnabledChanged(HarassmentBlockRule rule, boolean enabled) {
                database.setRuleEnabled(rule.getId(), enabled);
              }

              @Override
              public void onRuleDeleted(HarassmentBlockRule rule) {
                database.deleteRule(rule.getId());
                refreshRules();
              }
            });
    recyclerView.setAdapter(adapter);

    FloatingActionButton addFab = findViewById(R.id.harassment_block_add_fab);
    addFab.setOnClickListener(
        v ->
            AddHarassmentBlockRuleDialog.show(
                this,
                rules -> {
                  for (HarassmentBlockRule rule : rules) {
                    database.addRule(rule);
                  }
                  refreshRules();
                }));

    setupInsets(findViewById(R.id.main_layout));
    refreshRules();
  }

  private void refreshRules() {
    List<HarassmentBlockRule> rules = database.getAllRules();
    adapter.setRules(rules);
    emptyView.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }
}
