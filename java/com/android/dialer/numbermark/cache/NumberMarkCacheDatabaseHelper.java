/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.numbermark.cache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.android.dialer.numbermark.NumberMarkResult;
import com.android.dialer.numbermark.NumberMarkType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local SQLite cache of {@link NumberMarkResult}s so we don't hit the network (or scrape a search
 * engine) every time the same number is looked up.
 */
public final class NumberMarkCacheDatabaseHelper extends SQLiteOpenHelper {

  private static final String FILENAME = "number_mark_cache.db";
  private static final int VERSION = 1;

  private static final String TABLE = "number_mark_cache";
  private static final String COL_NUMBER = "number";
  private static final String COL_SOURCE_ID = "source_id";
  private static final String COL_TYPE = "type";
  private static final String COL_RAW_LABEL = "raw_label";
  private static final String COL_MARK_COUNT = "mark_count";
  private static final String COL_LOCATION = "location";
  private static final String COL_TIMESTAMP = "timestamp_millis";

  public NumberMarkCacheDatabaseHelper(Context context) {
    super(context, FILENAME, null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE "
            + TABLE
            + " ("
            + COL_NUMBER
            + " TEXT NOT NULL, "
            + COL_SOURCE_ID
            + " TEXT NOT NULL, "
            + COL_TYPE
            + " TEXT NOT NULL, "
            + COL_RAW_LABEL
            + " TEXT, "
            + COL_MARK_COUNT
            + " INTEGER DEFAULT 0, "
            + COL_LOCATION
            + " TEXT, "
            + COL_TIMESTAMP
            + " INTEGER NOT NULL, "
            + "PRIMARY KEY ("
            + COL_NUMBER
            + ", "
            + COL_SOURCE_ID
            + "))");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + TABLE);
    onCreate(db);
  }

  public void put(String number, NumberMarkResult result) {
    ContentValues values = new ContentValues();
    values.put(COL_NUMBER, number);
    values.put(COL_SOURCE_ID, result.getSourceId());
    values.put(COL_TYPE, result.getType().name());
    values.put(COL_RAW_LABEL, result.getRawLabel());
    values.put(COL_MARK_COUNT, result.getMarkCount());
    values.put(COL_LOCATION, result.getLocation());
    values.put(COL_TIMESTAMP, System.currentTimeMillis());
    getWritableDatabase()
        .insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  /**
   * Returns all cached results for {@code number} that are still within {@code ttlHours}, or an
   * empty list if none / all expired.
   */
  public List<NumberMarkResult> get(String number, int ttlHours) {
    List<NumberMarkResult> results = new ArrayList<>();
    long minTimestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(ttlHours);
    try (Cursor cursor =
        getReadableDatabase()
            .query(
                TABLE,
                null,
                COL_NUMBER + " = ? AND " + COL_TIMESTAMP + " >= ?",
                new String[] {number, String.valueOf(minTimestamp)},
                null,
                null,
                null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(fromCursor(cursor));
      }
    }
    return results;
  }

  @Nullable
  private NumberMarkResult fromCursor(Cursor cursor) {
    String sourceId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE_ID));
    NumberMarkType type;
    try {
      type = NumberMarkType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)));
    } catch (IllegalArgumentException e) {
      type = NumberMarkType.OTHER;
    }
    String rawLabel = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_LABEL));
    int markCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MARK_COUNT));
    String location = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCATION));
    return new NumberMarkResult(sourceId, type, rawLabel, markCount, location);
  }

  public void clear() {
    getWritableDatabase().delete(TABLE, null, null);
  }
}
