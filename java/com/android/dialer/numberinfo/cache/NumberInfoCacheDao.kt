/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.cache

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo

/**
 * TTL'd per-source cache of online lookup results, including negative results
 * ("source answered: no data") so we don't hammer sources for clean numbers.
 *
 * Interface kept Room-DAO-shaped: if the tree ever gains working KSP support
 * the impl can be swapped without touching callers.
 */
interface NumberInfoCacheDao {

    /** Rows for [number] whose age is within their TTL. Negative rows are
     *  represented as NumberInfo with isEmpty == true. */
    fun getValid(number: String, ttlMillis: Long, negativeTtlMillis: Long): List<NumberInfo>

    /** True when [number] has a fresh row (positive or negative) for [source]. */
    fun hasFresh(number: String, source: String, ttlMillis: Long, negativeTtlMillis: Long): Boolean

    fun upsert(info: NumberInfo)

    /** Records "source answered but had no data" for TTL-gated retry. */
    fun upsertNegative(number: String, source: String, timestampMillis: Long)

    fun evict(number: String)

    /** Deletes rows older than [olderThanMillis]; returns deleted count. */
    fun purgeOlderThan(olderThanMillis: Long): Int
}

class NumberInfoCacheDaoImpl(private val helper: SQLiteOpenHelper) : NumberInfoCacheDao {

    override fun getValid(
        number: String, ttlMillis: Long, negativeTtlMillis: Long,
    ): List<NumberInfo> {
        val now = System.currentTimeMillis()
        val result = ArrayList<NumberInfo>()
        helper.readableDatabase.query(
            TABLE, null, "number = ?", arrayOf(number), null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                val negative = c.getInt(c.getColumnIndexOrThrow("negative")) == 1
                val fetchedAt = c.getLong(c.getColumnIndexOrThrow("fetched_at"))
                val ttl = if (negative) negativeTtlMillis else ttlMillis
                if (now - fetchedAt > ttl) continue
                result.add(fromCursor(c, negative))
            }
        }
        return result
    }

    override fun hasFresh(
        number: String, source: String, ttlMillis: Long, negativeTtlMillis: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        helper.readableDatabase.query(
            TABLE, arrayOf("fetched_at", "negative"),
            "number = ? AND source = ?", arrayOf(number, source), null, null, null,
        ).use { c ->
            if (!c.moveToFirst()) return false
            val negative = c.getInt(1) == 1
            val ttl = if (negative) negativeTtlMillis else ttlMillis
            return now - c.getLong(0) <= ttl
        }
    }

    override fun upsert(info: NumberInfo) {
        val values = ContentValues().apply {
            put("number", info.number)
            put("source", info.source)
            put("mark_type", info.markType.id)
            put("mark_count", info.markCount)
            put("raw_mark_label", info.rawMarkLabel)
            put("biz_name", info.bizName)
            put("biz_category", info.bizCategory)
            put("biz_verified", if (info.bizVerified) 1 else 0)
            put("location", info.location)
            put("confidence", info.confidence)
            put("fetched_at",
                if (info.timestampMillis > 0) info.timestampMillis
                else System.currentTimeMillis())
            put("negative", 0)
        }
        helper.writableDatabase.replace(TABLE, null, values)
    }

    override fun upsertNegative(number: String, source: String, timestampMillis: Long) {
        val values = ContentValues().apply {
            put("number", number)
            put("source", source)
            put("mark_type", MarkType.NONE.id)
            put("mark_count", 0)
            put("confidence", 0f)
            put("fetched_at", timestampMillis)
            put("negative", 1)
        }
        helper.writableDatabase.replace(TABLE, null, values)
    }

    override fun evict(number: String) {
        helper.writableDatabase.delete(TABLE, "number = ?", arrayOf(number))
    }

    override fun purgeOlderThan(olderThanMillis: Long): Int =
        helper.writableDatabase.delete(
            TABLE, "fetched_at < ?", arrayOf(olderThanMillis.toString()))

    private fun fromCursor(c: Cursor, negative: Boolean): NumberInfo {
        fun text(col: String): String? = c.getString(c.getColumnIndexOrThrow(col))
        return NumberInfo(
            number = text("number")!!,
            markType = if (negative) MarkType.NONE
                       else MarkType.fromId(text("mark_type")),
            markCount = c.getInt(c.getColumnIndexOrThrow("mark_count")),
            rawMarkLabel = text("raw_mark_label"),
            bizName = text("biz_name"),
            bizCategory = text("biz_category"),
            bizVerified = c.getInt(c.getColumnIndexOrThrow("biz_verified")) == 1,
            location = text("location"),
            source = text("source")!!,
            confidence = c.getFloat(c.getColumnIndexOrThrow("confidence")),
            timestampMillis = c.getLong(c.getColumnIndexOrThrow("fetched_at")),
        )
    }

    private companion object {
        const val TABLE = "number_info_cache"
    }
}
