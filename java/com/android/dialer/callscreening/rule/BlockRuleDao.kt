/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper

/** CRUD for screening rules (`block_rule` table, NumberInfoDbHelper schema). */
interface BlockRuleDao {
    fun getAll(): List<BlockRule>
    fun getEnabled(): List<BlockRule>
    /** @return row id of the inserted rule */
    fun insert(rule: BlockRule): Long
    fun update(rule: BlockRule)
    fun setEnabled(id: Long, enabled: Boolean)
    fun delete(id: Long)
    fun deleteAll()
}

class BlockRuleDaoImpl(private val helper: SQLiteOpenHelper) : BlockRuleDao {

    override fun getAll(): List<BlockRule> = query(null)

    override fun getEnabled(): List<BlockRule> = query("enabled = 1")

    override fun insert(rule: BlockRule): Long =
        helper.writableDatabase.insert(TABLE, null, toValues(rule))

    override fun update(rule: BlockRule) {
        helper.writableDatabase.update(
            TABLE, toValues(rule), "id = ?", arrayOf(rule.id.toString()))
    }

    override fun setEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        helper.writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    override fun deleteAll() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    private fun query(selection: String?): List<BlockRule> {
        val out = ArrayList<BlockRule>()
        helper.readableDatabase.query(
            TABLE, null, selection, null, null, null, "created_at DESC",
        ).use { c ->
            while (c.moveToNext()) fromCursor(c)?.let { out.add(it) }
        }
        return out
    }

    private fun toValues(rule: BlockRule) = ContentValues().apply {
        if (rule.id > 0) put("id", rule.id)
        put("type", rule.type.name)
        put("value", rule.value)
        put("enabled", if (rule.enabled) 1 else 0)
        put("note", rule.note)
        put("created_at",
            if (rule.createdAt > 0) rule.createdAt else System.currentTimeMillis())
    }

    /** Unknown rule types (from a newer export) are skipped, not crashed on. */
    private fun fromCursor(c: Cursor): BlockRule? {
        val type = RuleType.fromId(c.getString(c.getColumnIndexOrThrow("type")))
            ?: return null
        return BlockRule(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = type,
            value = c.getString(c.getColumnIndexOrThrow("value")),
            enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
            note = c.getString(c.getColumnIndexOrThrow("note")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        )
    }

    private companion object {
        const val TABLE = "block_rule"
    }
}
