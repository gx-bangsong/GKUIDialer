/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules.extractor

import com.android.dialer.numberinfo.rules.FieldSpec
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Lightweight JSONPath subset evaluator — zero third-party dependencies
 * (org.json ships in the platform).
 *
 * Supported syntax:
 *   $                    root
 *   .name  /  ['name']   object member
 *   [0]                  array index (non-negative)
 *
 * Examples: `$.phoneSearch.data[0].name`, `$['a']['b'][2].c`
 *
 * Anything fancier (filters, wildcards, recursive descent) is intentionally
 * out of scope: parse rules should point at a concrete node; structural
 * variance belongs in alternative sources / rule updates.
 */
class JsonPathExtractor : Extractor {

    override fun extract(document: String, field: FieldSpec): String? {
        val path = field.path ?: return null
        val tokens = tokenize(path) ?: return null
        val root = try {
            JSONTokener(document).nextValue()
        } catch (e: Exception) {
            return null // not JSON — degrade to "not found"
        }
        var node: Any? = root
        for (token in tokens) {
            node = when (token) {
                is Token.Member -> (node as? JSONObject)?.opt(token.name)
                is Token.Index -> (node as? JSONArray)?.opt(token.index)
            }
            if (node == null || node === JSONObject.NULL) return null
        }
        return when (node) {
            is String -> node.trim().takeIf { it.isNotEmpty() }
            is Boolean, is Int, is Long, is Double -> node.toString()
            else -> null // objects/arrays are not scalar fields
        }
    }

    internal sealed class Token {
        data class Member(val name: String) : Token()
        data class Index(val index: Int) : Token()
    }

    /** @return null when the path is syntactically invalid. */
    internal fun tokenize(path: String): List<Token>? {
        if (!path.startsWith("$")) return null
        val tokens = ArrayList<Token>()
        var i = 1
        while (i < path.length) {
            when (path[i]) {
                '.' -> {
                    i++
                    val start = i
                    while (i < path.length && path[i] != '.' && path[i] != '[') i++
                    if (i == start) return null
                    tokens.add(Token.Member(path.substring(start, i)))
                }
                '[' -> {
                    val close = path.indexOf(']', i)
                    if (close < 0) return null
                    val inner = path.substring(i + 1, close).trim()
                    if (inner.length >= 2 &&
                        ((inner.first() == '\'' && inner.last() == '\'') ||
                         (inner.first() == '"' && inner.last() == '"'))) {
                        tokens.add(Token.Member(inner.substring(1, inner.length - 1)))
                    } else {
                        val idx = inner.toIntOrNull() ?: return null
                        if (idx < 0) return null
                        tokens.add(Token.Index(idx))
                    }
                    i = close + 1
                }
                else -> return null
            }
        }
        return tokens
    }
}
