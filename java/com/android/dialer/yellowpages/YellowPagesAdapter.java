/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.yellowpages;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.yellowpages.model.YellowPageEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the yellow pages (黄页) directory list, grouping {@link
 * YellowPageEntry} items under category headers -- similar to the categorized directory list used
 * by exTHmUI's YellowPage app.
 */
final class YellowPagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int VIEW_TYPE_HEADER = 0;
  private static final int VIEW_TYPE_ENTRY = 1;

  /** Callback for tapping the call button on an entry. */
  interface Listener {
    void onCallClicked(YellowPageEntry entry);
  }

  private final List<Object> items = new ArrayList<>();
  private final Listener listener;

  YellowPagesAdapter(Listener listener) {
    this.listener = listener;
  }

  void setCategories(List<YellowPagesRepository.YellowPageCategory> categories) {
    items.clear();
    for (YellowPagesRepository.YellowPageCategory category : categories) {
      items.add(category.name);
      items.addAll(category.entries);
    }
    notifyDataSetChanged();
  }

  /** Displays a flat (ungrouped) list of entries, e.g. search results. */
  void setFlatEntries(List<YellowPageEntry> entries) {
    items.clear();
    items.addAll(entries);
    notifyDataSetChanged();
  }

  boolean isEmpty() {
    return items.isEmpty();
  }

  @Override
  public int getItemViewType(int position) {
    return items.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_ENTRY;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == VIEW_TYPE_HEADER) {
      View view = inflater.inflate(R.layout.item_yellow_page_category_header, parent, false);
      return new HeaderViewHolder(view);
    }
    View view = inflater.inflate(R.layout.item_yellow_page_entry, parent, false);
    return new EntryViewHolder(view, listener);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Object item = items.get(position);
    if (holder instanceof HeaderViewHolder) {
      ((HeaderViewHolder) holder).bind((String) item);
    } else {
      ((EntryViewHolder) holder).bind((YellowPageEntry) item);
    }
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static final class HeaderViewHolder extends RecyclerView.ViewHolder {
    private final TextView headerText;

    HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      headerText = itemView.findViewById(R.id.yellow_page_category_header);
    }

    void bind(String category) {
      headerText.setText(category);
    }
  }

  static final class EntryViewHolder extends RecyclerView.ViewHolder {
    private final TextView nameText;
    private final TextView numberText;
    private final ImageView iconView;
    private final View callButton;
    private final Listener listener;

    EntryViewHolder(@NonNull View itemView, Listener listener) {
      super(itemView);
      this.listener = listener;
      nameText = itemView.findViewById(R.id.yellow_page_name);
      numberText = itemView.findViewById(R.id.yellow_page_number);
      iconView = itemView.findViewById(R.id.yellow_page_icon);
      callButton = itemView.findViewById(R.id.yellow_page_call_button);
    }

    void bind(YellowPageEntry entry) {
      nameText.setText(entry.getName());
      numberText.setText(entry.getNumber());
      itemView.setOnClickListener(v -> listener.onCallClicked(entry));
      callButton.setOnClickListener(v -> listener.onCallClicked(entry));
    }
  }
}
