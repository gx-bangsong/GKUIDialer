/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark.rule;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and stores the JSON ruleset consumed by {@link KeywordExtractionRule}.
 *
 * <p>This is the key piece of decoupling for the number-marking feature: the raw HTML/keyword
 * patterns for each source (360, Baidu, Sogou/电话邦) live in versioned JSON data, not in Java
 * code. There are two layers, applied in priority order:
 *
 * <ol>
 *   <li>An "override" ruleset, cached in local SharedPreferences after being fetched from a remote
 *       URL (see {@link #applyRemoteRuleset(Context, String)}). This lets a fix for a site markup
 *       change be pushed out without an app update.
 *   <li>The ruleset bundled in the APK at {@code res/raw/number_mark_rules.json}, used as a
 *       fallback so the feature keeps working even if the remote ruleset was never fetched (e.g.
 *       offline first run).
 * </ol>
 */
public final class NumberMarkRuleRepository {

  private static final String TAG = "NumberMarkRuleRepo";

  private static final String PREFS_NAME = "number_mark_rules";
  private static final String KEY_RULESET_JSON = "ruleset_json";
  private static final String KEY_RULESET_VERSION = "ruleset_version";

  private static volatile NumberMarkRuleset cachedRuleset;

  private NumberMarkRuleRepository() {}

  /** Returns the currently active ruleset, loading and caching it on first access. */
  public static synchronized NumberMarkRuleset getRuleset(Context context) {
    if (cachedRuleset != null) {
      return cachedRuleset;
    }
    String json = readOverrideJson(context);
    if (TextUtils.isEmpty(json)) {
      json = readBundledJson(context);
    }
    cachedRuleset = parse(json);
    return cachedRuleset;
  }

  /** Forces the in-memory cache to be dropped so the next {@link #getRuleset} call reloads. */
  public static synchronized void invalidateCache() {
    cachedRuleset = null;
  }

  public static int getCurrentVersion(Context context) {
    return prefs(context).getInt(KEY_RULESET_VERSION, 0);
  }

  /**
   * Applies a freshly-downloaded ruleset JSON, persisting it so it survives process death and
   * takes effect immediately. Callers are expected to have already fetched {@code json} from a
   * trusted, versioned remote endpoint (e.g. a rules file hosted alongside app updates) and to
   * validate its version is newer via {@link #getCurrentVersion(Context)} beforehand.
   *
   * @return true if the JSON parsed successfully and was applied.
   */
  public static synchronized boolean applyRemoteRuleset(Context context, String json) {
    NumberMarkRuleset parsed = parse(json);
    if (parsed == null || parsed.sources.isEmpty()) {
      Log.w(TAG, "Refusing to apply empty/invalid remote ruleset");
      return false;
    }
    prefs(context)
        .edit()
        .putString(KEY_RULESET_JSON, json)
        .putInt(KEY_RULESET_VERSION, parsed.version)
        .apply();
    cachedRuleset = parsed;
    return true;
  }

  /** Clears any downloaded override, reverting to the ruleset bundled in the APK. */
  public static synchronized void resetToBundled(Context context) {
    prefs(context).edit().remove(KEY_RULESET_JSON).remove(KEY_RULESET_VERSION).apply();
    cachedRuleset = null;
  }

  private static SharedPreferences prefs(Context context) {
    return context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  private static String readOverrideJson(Context context) {
    return prefs(context).getString(KEY_RULESET_JSON, null);
  }

  private static String readBundledJson(Context context) {
    try (InputStream is = context.getResources().openRawResource(R.raw.number_mark_rules);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read bundled number mark ruleset", e);
      return null;
    }
  }

  private static NumberMarkRuleset parse(String json) {
    if (TextUtils.isEmpty(json)) {
      return new NumberMarkRuleset(0, new HashMap<>(), new HashMap<>());
    }
    try {
      JSONObject root = new JSONObject(json);
      int version = root.optInt("version", 0);

      Map<String, String[]> keywordMap = new HashMap<>();
      JSONArray keywordArray = root.optJSONArray("keywordMap");
      if (keywordArray != null) {
        for (int i = 0; i < keywordArray.length(); i++) {
          JSONObject entry = keywordArray.getJSONObject(i);
          String type = entry.getString("type");
          JSONArray keywords = entry.getJSONArray("keywords");
          String[] arr = new String[keywords.length()];
          for (int j = 0; j < keywords.length(); j++) {
            arr[j] = keywords.getString(j);
          }
          keywordMap.put(type, arr);
        }
      }

      Map<String, SourceConfig> sources = new HashMap<>();
      JSONObject sourcesObj = root.optJSONObject("sources");
      if (sourcesObj != null) {
        for (java.util.Iterator<String> it = sourcesObj.keys(); it.hasNext(); ) {
          String sourceId = it.next();
          JSONObject sourceObj = sourcesObj.getJSONObject(sourceId);
          sources.put(sourceId, SourceConfig.fromJson(sourceObj));
        }
      }
      return new NumberMarkRuleset(version, keywordMap, sources);
    } catch (JSONException e) {
      Log.e(TAG, "Failed to parse number mark ruleset", e);
      return new NumberMarkRuleset(0, new HashMap<>(), new HashMap<>());
    }
  }

  /** Parsed, immutable ruleset. */
  public static final class NumberMarkRuleset {
    public final int version;
    public final Map<String, String[]> keywordMap;
    public final Map<String, SourceConfig> sources;

    NumberMarkRuleset(
        int version, Map<String, String[]> keywordMap, Map<String, SourceConfig> sources) {
      this.version = version;
      this.keywordMap = keywordMap;
      this.sources = sources;
    }
  }

  /** Per-source regex configuration. */
  public static final class SourceConfig {
    public final String[] snippetPatterns;
    public final String countPattern;
    public final String locationPattern;

    SourceConfig(String[] snippetPatterns, String countPattern, String locationPattern) {
      this.snippetPatterns = snippetPatterns;
      this.countPattern = countPattern;
      this.locationPattern = locationPattern;
    }

    static SourceConfig fromJson(JSONObject obj) throws JSONException {
      JSONArray patterns = obj.optJSONArray("snippetPatterns");
      String[] snippetPatterns = new String[patterns == null ? 0 : patterns.length()];
      if (patterns != null) {
        for (int i = 0; i < patterns.length(); i++) {
          snippetPatterns[i] = patterns.getString(i);
        }
      }
      return new SourceConfig(
          snippetPatterns, obj.optString("countPattern", null), obj.optString("locationPattern",
              null));
    }
  }
}
