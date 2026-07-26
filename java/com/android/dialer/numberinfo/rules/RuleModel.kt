/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import org.json.JSONException
import org.json.JSONObject

/** org.json's optString(key, null) is annotated non-null; this is the honest form. */
private fun JSONObject.optNullableString(key: String): String? {
    val v = opt(key) ?: return null
    if (v === JSONObject.NULL) return null
    return v.toString().takeIf { it.isNotBlank() }
}

/**
 * In-memory model of rules.json. Parsing is strict about structure (bad file is
 * rejected as a whole so a broken hot-update never replaces a good one) but
 * lenient about unknown keys (forward compatibility).
 *
 * JSON shape (see assets/numberinfo/rules.json for the authoritative sample):
 *
 * {
 *   "schemaVersion": 1,
 *   "updatedAt": "...",
 *   "ruleUpdateUrl": "https://.../rules.json",   // optional
 *   "sources": [ {
 *       "id": "so360", "name": "...", "enabled": true, "confidence": 0.7,
 *       "request": { "urlTemplate": "...{number}...", "method": "GET",
 *                    "headers": {..}, "charset": "UTF-8", "timeoutMs": 3000 },
 *       "extract": { "type": "regex" | "jsonpath",
 *                    "preRegex": "...",           // optional, group 1 is kept
 *                    "fields": { "<field>": { "pattern": "..", "group": 1,
 *                                             "path": "$.a.b[0].c",
 *                                             "coerce": "int|boolean|string" } },
 *                    "successRequires": ["markType|bizName"] },
 *       "mapping": { "markType": { "诈骗": "fraud", "*": "custom" } }
 *   } ]
 * }
 */
data class RuleSet(
    val schemaVersion: Int,
    val updatedAt: String?,
    val ruleUpdateUrl: String?,
    val sources: List<SourceRule>,
) {
    fun source(id: String): SourceRule? = sources.firstOrNull { it.id == id }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1

        /** @throws JSONException / IllegalArgumentException on malformed input. */
        @JvmStatic
        @Throws(JSONException::class)
        fun parse(json: String): RuleSet {
            val root = JSONObject(json)
            val version = root.getInt("schemaVersion")
            require(version == SUPPORTED_SCHEMA_VERSION) {
                "Unsupported rules schemaVersion=$version"
            }
            val sourcesJson = root.getJSONArray("sources")
            val sources = ArrayList<SourceRule>(sourcesJson.length())
            for (i in 0 until sourcesJson.length()) {
                sources.add(SourceRule.parse(sourcesJson.getJSONObject(i)))
            }
            require(sources.map { it.id }.toSet().size == sources.size) {
                "Duplicate source ids in rules"
            }
            return RuleSet(
                schemaVersion = version,
                updatedAt = root.optNullableString("updatedAt"),
                ruleUpdateUrl = root.optNullableString("ruleUpdateUrl"),
                sources = sources,
            )
        }
    }
}

data class SourceRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val confidence: Float,
    val request: RequestSpec,
    val extract: ExtractSpec,
    /** field name -> (source label -> normalized id); "*" is the wildcard key. */
    val mapping: Map<String, Map<String, String>>,
) {
    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun parse(o: JSONObject): SourceRule {
            val id = o.getString("id")
            require(id.isNotBlank()) { "source id must not be blank" }
            val mapping = HashMap<String, Map<String, String>>()
            o.optJSONObject("mapping")?.let { m ->
                for (field in m.keys()) {
                    val table = m.getJSONObject(field)
                    val entries = HashMap<String, String>()
                    for (k in table.keys()) {
                        entries[k] = table.getString(k)
                    }
                    mapping[field] = entries
                }
            }
            return SourceRule(
                id = id,
                name = o.optString("name", id),
                enabled = o.optBoolean("enabled", true),
                confidence = o.optDouble("confidence", 0.5).toFloat(),
                request = RequestSpec.parse(o.getJSONObject("request")),
                extract = ExtractSpec.parse(o.getJSONObject("extract")),
                mapping = mapping,
            )
        }
    }
}

data class RequestSpec(
    val urlTemplate: String,
    val method: String,
    val headers: Map<String, String>,
    val charset: String,
    val timeoutMs: Int,
) {
    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun parse(o: JSONObject): RequestSpec {
            val urlTemplate = o.getString("urlTemplate")
            require(urlTemplate.contains("{number}")) {
                "urlTemplate must contain {number} placeholder"
            }
            val headers = HashMap<String, String>()
            o.optJSONObject("headers")?.let { h ->
                for (k in h.keys()) headers[k] = h.getString(k)
            }
            return RequestSpec(
                urlTemplate = urlTemplate,
                method = o.optString("method", "GET"),
                headers = headers,
                charset = o.optString("charset", "UTF-8"),
                timeoutMs = o.optInt("timeoutMs", 3000),
            )
        }
    }
}

data class ExtractSpec(
    /** "regex" or "jsonpath" ("css" is reserved, currently rejected at load). */
    val type: String,
    /** Optional regex applied first; group 1 (or whole match) becomes the new document. */
    val preRegex: String?,
    val fields: Map<String, FieldSpec>,
    /**
     * Validity requirements. Each entry is an OR-group: "markType|bizName" means
     * at least one of the two must be non-blank. All entries must pass (AND).
     */
    val successRequires: List<String>,
) {
    companion object {
        const val TYPE_REGEX = "regex"
        const val TYPE_JSONPATH = "jsonpath"

        @JvmStatic
        @Throws(JSONException::class)
        fun parse(o: JSONObject): ExtractSpec {
            val type = o.getString("type")
            require(type == TYPE_REGEX || type == TYPE_JSONPATH) {
                "Unsupported extract type: $type"
            }
            val fieldsJson = o.getJSONObject("fields")
            val fields = HashMap<String, FieldSpec>()
            for (name in fieldsJson.keys()) {
                fields[name] = FieldSpec.parse(type, fieldsJson.getJSONObject(name))
            }
            require(fields.isNotEmpty()) { "extract.fields must not be empty" }
            val requires = ArrayList<String>()
            o.optJSONArray("successRequires")?.let { arr ->
                for (i in 0 until arr.length()) requires.add(arr.getString(i))
            }
            return ExtractSpec(
                type = type,
                preRegex = o.optNullableString("preRegex"),
                fields = fields,
                successRequires = requires,
            )
        }
    }
}

data class FieldSpec(
    /** regex extractor */
    val pattern: String?,
    val group: Int,
    /** jsonpath extractor */
    val path: String?,
    /** "string" (default) | "int" | "boolean" */
    val coerce: String,
) {
    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun parse(extractType: String, o: JSONObject): FieldSpec {
            val pattern = o.optNullableString("pattern")
            val path = o.optNullableString("path")
            when (extractType) {
                ExtractSpec.TYPE_REGEX ->
                    require(pattern != null) { "regex field requires 'pattern'" }
                ExtractSpec.TYPE_JSONPATH ->
                    require(path != null) { "jsonpath field requires 'path'" }
            }
            // Validate the regex eagerly so a broken hot-update is rejected at load.
            pattern?.let { Regex(it) }
            return FieldSpec(
                pattern = pattern,
                group = o.optInt("group", 1),
                path = path,
                coerce = o.optString("coerce", "string"),
            )
        }
    }
}
