/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** SQLite-backed storage for {@link HarassmentBlockRule}s. */
public final class HarassmentBlockRuleDatabaseHelper extends SQLiteOpenHelper {

  private static final String FILENAME = "harassment_block_rules.db";
  private static final int VERSION = 1;

  private static final String TABLE = "block_rules";
  private static final String COL_ID = "_id";
  private static final String COL_TYPE = "type";
  private static final String COL_VALUE = "value";
  private static final String COL_MODE = "mode";
  private static final String COL_ENABLED = "enabled";

  public HarassmentBlockRuleDatabaseHelper(Context context) {
    super(context.getApplicationContext(), FILENAME, null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE "
            + TABLE
            + " ("
            + COL_ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_TYPE
            + " TEXT NOT NULL, "
            + COL_VALUE
            + " TEXT NOT NULL, "
            + COL_MODE
            + " TEXT NOT NULL, "
            + COL_ENABLED
            + " INTEGER NOT NULL DEFAULT 1)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + TABLE);
    onCreate(db);
  }

  public long addRule(@NonNull HarassmentBlockRule rule) {
    ContentValues values = toValues(rule);
    return getWritableDatabase().insert(TABLE, null, values);
  }

  public void updateRule(@NonNull HarassmentBlockRule rule) {
    ContentValues values = toValues(rule);
    getWritableDatabase()
        .update(TABLE, values, COL_ID + " = ?", new String[] {String.valueOf(rule.getId())});
  }

  public void deleteRule(long id) {
    getWritableDatabase().delete(TABLE, COL_ID + " = ?", new String[] {String.valueOf(id)});
  }

  public void setRuleEnabled(long id, boolean enabled) {
    ContentValues values = new ContentValues();
    values.put(COL_ENABLED, enabled ? 1 : 0);
    getWritableDatabase()
        .update(TABLE, values, COL_ID + " = ?", new String[] {String.valueOf(id)});
  }

  @NonNull
  public List<HarassmentBlockRule> getAllRules() {
    List<HarassmentBlockRule> rules = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
        COL_ID + " DESC")) {
      while (cursor != null && cursor.moveToNext()) {
        rules.add(fromCursor(cursor));
      }
    }
    return rules;
  }

  @NonNull
  public List<HarassmentBlockRule> getEnabledRules() {
    List<HarassmentBlockRule> rules = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .query(TABLE, null, COL_ENABLED + " = 1", null, null, null, COL_ID + " DESC")) {
      while (cursor != null && cursor.moveToNext()) {
        rules.add(fromCursor(cursor));
      }
    }
    return rules;
  }

  private static ContentValues toValues(HarassmentBlockRule rule) {
    ContentValues values = new ContentValues();
    values.put(COL_TYPE, rule.getType().name());
    values.put(COL_VALUE, rule.getValue());
    values.put(COL_MODE, rule.getMode().name());
    values.put(COL_ENABLED, rule.isEnabled() ? 1 : 0);
    return values;
  }

  private static HarassmentBlockRule fromCursor(Cursor cursor) {
    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
    HarassmentBlockRuleType type =
        HarassmentBlockRuleType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)));
    String value = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALUE));
    HarassmentBlockMode mode =
        HarassmentBlockMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE)));
    boolean enabled = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ENABLED)) != 0;
    return new HarassmentBlockRule(id, type, value, mode, enabled);
  }
}
