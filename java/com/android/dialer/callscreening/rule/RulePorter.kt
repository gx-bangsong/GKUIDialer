/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.rule

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Import/export of screening rules as JSON (pure string transform — the UI
 * layer handles SAF streams). Format:
 *
 * {
 *   "formatVersion": 1,
 *   "exportedAt": 1720000000000,
 *   "rules": [
 *     { "type": "PREFIX", "value": "170", "enabled": true, "note": "虚商" },
 *     { "type": "MARK_TYPE", "value": "fraud,telemarketing" }
 *   ]
 * }
 *
 * Import is strict about the envelope (bad file -> exception, nothing applied)
 * but tolerant per-row: unknown rule types and unusable values are skipped and
 * counted, so exports from a newer app version degrade gracefully. Values are
 * stored in canonical form ([RuleValues]).
 */
object RulePorter {

    const val FORMAT_VERSION = 1

    data class ImportResult(val rules: List<BlockRule>, val skipped: Int)

    @JvmStatic
    fun export(rules: List<BlockRule>, nowMillis: Long = System.currentTimeMillis()): String {
        val arr = JSONArray()
        for (rule in rules) {
            arr.put(JSONObject().apply {
                put("type", rule.type.name)
                put("value", rule.value)
                put("enabled", rule.enabled)
                rule.note?.let { put("note", it) }
            })
        }
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", nowMillis)
            .put("rules", arr)
            .toString(2)
    }

    /** @throws JSONException / IllegalArgumentException on a malformed envelope. */
    @JvmStatic
    @Throws(JSONException::class)
    fun parse(json: String): ImportResult {
        val root = JSONObject(json)
        val version = root.getInt("formatVersion")
        require(version in 1..FORMAT_VERSION) {
            "Unsupported rule export formatVersion=$version"
        }
        val arr = root.getJSONArray("rules")
        val rules = ArrayList<BlockRule>(arr.length())
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = RuleType.fromId(o.optString("type"))
            // Canonical form, same as rules saved from the editor; values that
            // are not usable for their type (e.g. "abc" as a number) are skipped.
            val value = type?.let { RuleValues.normalize(it, o.optString("value")) }
            if (type == null || value == null) {
                skipped++
                continue
            }
            rules.add(BlockRule(
                type = type,
                value = value,
                enabled = o.optBoolean("enabled", true),
                note = o.optString("note").takeIf { it.isNotBlank() },
            ))
        }
        return ImportResult(rules, skipped)
    }
}
