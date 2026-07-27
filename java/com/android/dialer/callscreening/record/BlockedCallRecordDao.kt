/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.record

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper

/** One screened-out call. `ruleSummary` snapshots the reason at block time. */
data class BlockedCallRecord(
    val id: Long = 0L,
    val number: String,
    val blockedAt: Long,
    val ruleId: Long? = null,
    val ruleSummary: String? = null,
    /** JSON snapshot of the NumberInfo at block time (nullable). */
    val numberInfoJson: String? = null,
)

interface BlockedCallRecordDao {
    fun insert(record: BlockedCallRecord): Long
    fun getAll(limit: Int = 500): List<BlockedCallRecord>
    fun delete(id: Long)
    fun deleteAll()
    fun countSince(sinceMillis: Long): Int
}

class BlockedCallRecordDaoImpl(private val helper: SQLiteOpenHelper) : BlockedCallRecordDao {

    override fun insert(record: BlockedCallRecord): Long {
        val values = ContentValues().apply {
            put("number", record.number)
            put("blocked_at", record.blockedAt)
            record.ruleId?.let { put("rule_id", it) }
            put("rule_summary", record.ruleSummary)
            put("number_info_json", record.numberInfoJson)
        }
        return helper.writableDatabase.insert(TABLE, null, values)
    }

    override fun getAll(limit: Int): List<BlockedCallRecord> {
        val out = ArrayList<BlockedCallRecord>()
        helper.readableDatabase.query(
            TABLE, null, null, null, null, null, "blocked_at DESC", limit.toString(),
        ).use { c ->
            while (c.moveToNext()) out.add(fromCursor(c))
        }
        return out
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    override fun deleteAll() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    override fun countSince(sinceMillis: Long): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE blocked_at >= ?",
            arrayOf(sinceMillis.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun fromCursor(c: Cursor): BlockedCallRecord {
        val ruleIdIdx = c.getColumnIndexOrThrow("rule_id")
        return BlockedCallRecord(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            number = c.getString(c.getColumnIndexOrThrow("number")),
            blockedAt = c.getLong(c.getColumnIndexOrThrow("blocked_at")),
            ruleId = if (c.isNull(ruleIdIdx)) null else c.getLong(ruleIdIdx),
            ruleSummary = c.getString(c.getColumnIndexOrThrow("rule_summary")),
            numberInfoJson = c.getString(c.getColumnIndexOrThrow("number_info_json")),
        )
    }

    private companion object {
        const val TABLE = "blocked_call_record"
    }
}
