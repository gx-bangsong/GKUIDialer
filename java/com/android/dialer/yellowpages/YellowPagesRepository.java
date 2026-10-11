/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.yellowpages;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.dialer.yellowpages.model.YellowPageEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and indexes the bundled yellow pages (黄页) dataset.
 *
 * <p>Like exTHmUI's {@code YellowPage_Data}/{@code android_packages_apps_YellowPage} project, the
 * dataset is plain structured data (JSON here, YAML there) organized as a list of categories each
 * containing named entries with a phone number and an icon reference -- deliberately kept
 * independent of any particular UI so the same data can back both the browsable directory UI and
 * automatic caller-ID style number recognition for incoming calls.
 */
public final class YellowPagesRepository {

  private static final String TAG = "YellowPagesRepository";

  private static volatile List<YellowPageCategory> cachedCategories;
  private static volatile Map<String, YellowPageEntry> cachedByNumber;

  private YellowPagesRepository() {}

  /** A named group of {@link YellowPageEntry} (e.g. "快递物流", "金融银行"). */
  public static final class YellowPageCategory {
    @NonNull public final String name;
    @NonNull public final List<YellowPageEntry> entries;

    YellowPageCategory(@NonNull String name, @NonNull List<YellowPageEntry> entries) {
      this.name = name;
      this.entries = entries;
    }
  }

  @NonNull
  public static synchronized List<YellowPageCategory> getCategories(Context context) {
    if (cachedCategories == null) {
      load(context);
    }
    return cachedCategories;
  }

  /** Returns the yellow-page entry exactly matching {@code number}, if any. */
  @Nullable
  public static synchronized YellowPageEntry findByNumber(Context context, String number) {
    if (cachedByNumber == null) {
      load(context);
    }
    if (TextUtils.isEmpty(number)) {
      return null;
    }
    return cachedByNumber.get(normalize(number));
  }

  @NonNull
  @WorkerThread
  public static List<YellowPageEntry> search(Context context, String query) {
    List<YellowPageEntry> results = new ArrayList<>();
    if (TextUtils.isEmpty(query)) {
      return results;
    }
    for (YellowPageCategory category : getCategories(context)) {
      for (YellowPageEntry entry : category.entries) {
        if (entry.getName().contains(query) || entry.getNumber().contains(query)) {
          results.add(entry);
        }
      }
    }
    return results;
  }

  private static String normalize(String number) {
    return number.replaceAll("[^0-9]", "");
  }

  private static synchronized void load(Context context) {
    List<YellowPageCategory> categories = new ArrayList<>();
    Map<String, YellowPageEntry> byNumber = new LinkedHashMap<>();

    String json = readBundledJson(context);
    if (!TextUtils.isEmpty(json)) {
      try {
        JSONArray categoriesArray = new JSONArray(json);
        for (int i = 0; i < categoriesArray.length(); i++) {
          JSONObject categoryObj = categoriesArray.getJSONObject(i);
          String categoryName = categoryObj.getString("category");
          JSONArray entriesArray = categoryObj.getJSONArray("entries");

          List<YellowPageEntry> entries = new ArrayList<>();
          for (int j = 0; j < entriesArray.length(); j++) {
            JSONObject entryObj = entriesArray.getJSONObject(j);
            YellowPageEntry entry =
                new YellowPageEntry(
                    categoryName,
                    entryObj.getString("name"),
                    entryObj.getString("number"),
                    entryObj.optString("icon", null),
                    entryObj.optString("description", null));
            entries.add(entry);
            byNumber.put(normalize(entry.getNumber()), entry);
          }
          categories.add(new YellowPageCategory(categoryName, entries));
        }
      } catch (JSONException e) {
        Log.e(TAG, "Failed to parse bundled yellow pages dataset", e);
      }
    }

    cachedCategories = categories;
    cachedByNumber = byNumber;
  }

  private static String readBundledJson(Context context) {
    try (InputStream is = context.getResources().openRawResource(R.raw.yellow_pages_data);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read bundled yellow pages dataset", e);
      return null;
    }
  }
}
