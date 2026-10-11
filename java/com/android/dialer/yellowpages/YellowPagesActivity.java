/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.yellowpages;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.app.BaseActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCall;
import com.android.dialer.yellowpages.model.YellowPageEntry;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Yellow pages (黄页) directory: browse well-known service/organization numbers grouped by
 * category (local dataset, structured the same way as exTHmUI's YellowPage_Data project), search
 * them by name/number, place a call directly from an entry, or jump to Baidu's official number
 * platform (haoma.baidu.com) for numbers that aren't in the local dataset -- mirroring how
 * haoma.baidu.com itself surfaces enterprise-certified numbers and lets users verify/report a
 * number that isn't recognized.
 */
public class YellowPagesActivity extends BaseActivity {

  private static final String HAOMA_BAIDU_URL = "https://haoma.baidu.com/apps/plan/mplan/newmini";

  private YellowPagesAdapter adapter;
  private View emptyView;
  private List<YellowPagesRepository.YellowPageCategory> allCategories;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_yellow_pages);

    android.app.ActionBar actionBar = getActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.yellow_pages_label);
    }

    emptyView = findViewById(R.id.yellow_pages_empty_view);
    RecyclerView recyclerView = findViewById(R.id.yellow_pages_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    adapter =
        new YellowPagesAdapter(
            entry -> {
              Intent intent =
                  PreCall.getIntent(
                      YellowPagesActivity.this,
                      new CallIntentBuilder(
                          entry.getNumber(), CallInitiationType.Type.UNKNOWN_INITIATION));
              startActivity(intent);
            });
    recyclerView.setAdapter(adapter);

    allCategories = YellowPagesRepository.getCategories(this);
    adapter.setCategories(allCategories);
    updateEmptyView();

    TextInputEditText searchEditText = findViewById(R.id.yellow_pages_search_edit_text);
    searchEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            onSearchQueryChanged(s.toString());
          }
        });

    findViewById(R.id.yellow_pages_verify_on_baidu_button)
        .setOnClickListener(
            v -> {
              String query =
                  searchEditText.getText() != null ? searchEditText.getText().toString().trim()
                      : "";
              Uri.Builder uriBuilder = Uri.parse(HAOMA_BAIDU_URL).buildUpon();
              if (!TextUtils.isEmpty(query)) {
                uriBuilder.appendQueryParameter("number", query);
              }
              startActivity(new Intent(Intent.ACTION_VIEW, uriBuilder.build()));
            });

    setupInsets(findViewById(R.id.main_layout));
  }

  private void onSearchQueryChanged(String query) {
    if (TextUtils.isEmpty(query)) {
      adapter.setCategories(allCategories);
    } else {
      List<YellowPageEntry> results = YellowPagesRepository.search(this, query);
      adapter.setFlatEntries(results);
    }
    updateEmptyView();
  }

  private void updateEmptyView() {
    emptyView.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }
}
