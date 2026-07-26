/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * numberinfo.db — plain SQLiteOpenHelper by design (Soong has no kapt, so Room
 * is structurally unavailable for Kotlin sources in this tree; see
 * docs/numberinfo-architecture.md).
 *
 * Schema v1 creates all tables for the whole feature set (cache, manual marks,
 * block rules, blocked-call records) so later feature batches need no
 * migration. DAO implementations live next to this class; upper layers depend
 * on the DAO *interfaces* only.
 */
class NumberInfoDbHelper(context: Context) : SQLiteOpenHelper(
    context.createDeviceProtectedStorageContext(), DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE number_info_cache (
                number TEXT NOT NULL,
                source TEXT NOT NULL,
                mark_type TEXT NOT NULL DEFAULT 'none',
                mark_count INTEGER NOT NULL DEFAULT 0,
                raw_mark_label TEXT,
                biz_name TEXT,
                biz_category TEXT,
                biz_verified INTEGER NOT NULL DEFAULT 0,
                location TEXT,
                confidence REAL NOT NULL DEFAULT 0,
                fetched_at INTEGER NOT NULL,
                negative INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (number, source)
            )
            """.trimIndent())
        db.execSQL("CREATE INDEX idx_cache_number ON number_info_cache (number)")

        db.execSQL(
            """
            CREATE TABLE manual_mark (
                number TEXT NOT NULL PRIMARY KEY,
                mark_type TEXT NOT NULL,
                note TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent())

        db.execSQL(
            """
            CREATE TABLE block_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                value TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                note TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent())

        db.execSQL(
            """
            CREATE TABLE blocked_call_record (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                blocked_at INTEGER NOT NULL,
                rule_id INTEGER,
                rule_summary TEXT,
                number_info_json TEXT
            )
            """.trimIndent())
        db.execSQL("CREATE INDEX idx_blocked_number ON blocked_call_record (number)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the first shipped schema; add ALTERs here for future versions.
    }

    companion object {
        const val DB_NAME = "numberinfo.db"
        const val DB_VERSION = 1

        @Volatile
        private var instance: NumberInfoDbHelper? = null

        @JvmStatic
        fun getInstance(context: Context): NumberInfoDbHelper =
            instance ?: synchronized(this) {
                instance ?: NumberInfoDbHelper(context.applicationContext)
                    .also { instance = it }
            }
    }
}
