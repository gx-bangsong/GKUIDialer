/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.cache

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.Sources

/** User's manual marks / corrections — the highest-priority "source". */
interface ManualMarkDao {
    fun get(number: String): NumberInfo?
    fun put(number: String, markType: MarkType, note: String? = null)
    fun remove(number: String)
    fun getAll(): List<NumberInfo>
}

class ManualMarkDaoImpl(private val helper: SQLiteOpenHelper) : ManualMarkDao {

    override fun get(number: String): NumberInfo? {
        helper.readableDatabase.query(
            TABLE, null, "number = ?", arrayOf(number), null, null, null,
        ).use { c ->
            return if (c.moveToFirst()) fromRow(
                c.getString(c.getColumnIndexOrThrow("number")),
                c.getString(c.getColumnIndexOrThrow("mark_type")),
                c.getString(c.getColumnIndexOrThrow("note")),
                c.getLong(c.getColumnIndexOrThrow("created_at")),
            ) else null
        }
    }

    override fun put(number: String, markType: MarkType, note: String?) {
        val values = ContentValues().apply {
            put("number", number)
            put("mark_type", markType.id)
            put("note", note)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.replace(TABLE, null, values)
    }

    override fun remove(number: String) {
        helper.writableDatabase.delete(TABLE, "number = ?", arrayOf(number))
    }

    override fun getAll(): List<NumberInfo> {
        val out = ArrayList<NumberInfo>()
        helper.readableDatabase.query(
            TABLE, null, null, null, null, null, "created_at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out.add(fromRow(
                    c.getString(c.getColumnIndexOrThrow("number")),
                    c.getString(c.getColumnIndexOrThrow("mark_type")),
                    c.getString(c.getColumnIndexOrThrow("note")),
                    c.getLong(c.getColumnIndexOrThrow("created_at")),
                ))
            }
        }
        return out
    }

    private fun fromRow(number: String, markType: String?, note: String?, createdAt: Long) =
        NumberInfo(
            number = number,
            markType = MarkType.fromId(markType),
            source = Sources.MANUAL,
            confidence = 1f, // user's own judgement always wins
            note = note,
            timestampMillis = createdAt,
        )

    private companion object {
        const val TABLE = "manual_mark"
    }
}
