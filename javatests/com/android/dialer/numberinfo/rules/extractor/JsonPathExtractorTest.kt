/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules.extractor

import com.android.dialer.numberinfo.rules.FieldSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonPathExtractorTest {

    private val extractor = JsonPathExtractor()

    private val doc = """
        {
          "phoneSearch": {
            "data": [
              { "name": "顺丰速运客服", "category": "快递物流",
                "certified": true, "markCount": 128,
                "tags": ["a", "b"] },
              { "name": "second" }
            ]
          },
          "empty": null
        }
    """.trimIndent()

    private fun path(p: String) =
        FieldSpec(pattern = null, group = 1, path = p, coerce = "string")

    @Test
    fun extract_nestedObjectAndArray() {
        assertEquals("顺丰速运客服",
            extractor.extract(doc, path("$.phoneSearch.data[0].name")))
        assertEquals("second",
            extractor.extract(doc, path("$.phoneSearch.data[1].name")))
    }

    @Test
    fun extract_bracketNotation() {
        assertEquals("快递物流",
            extractor.extract(doc, path("$['phoneSearch']['data'][0]['category']")))
    }

    @Test
    fun extract_scalarCoercion() {
        assertEquals("true",
            extractor.extract(doc, path("$.phoneSearch.data[0].certified")))
        assertEquals("128",
            extractor.extract(doc, path("$.phoneSearch.data[0].markCount")))
    }

    @Test
    fun extract_arrayElement() {
        assertEquals("b", extractor.extract(doc, path("$.phoneSearch.data[0].tags[1]")))
    }

    @Test
    fun extract_missingPathsReturnNull() {
        assertNull(extractor.extract(doc, path("$.phoneSearch.data[9].name")))
        assertNull(extractor.extract(doc, path("$.nope.deeper")))
        assertNull(extractor.extract(doc, path("$.empty")))
    }

    @Test
    fun extract_nonScalarTargetReturnsNull() {
        assertNull(extractor.extract(doc, path("$.phoneSearch.data")))
        assertNull(extractor.extract(doc, path("$.phoneSearch.data[0]")))
    }

    @Test
    fun extract_invalidPathReturnsNull() {
        assertNull(extractor.extract(doc, path("phoneSearch.data")))    // no $
        assertNull(extractor.extract(doc, path("$.a[")))                 // unclosed
        assertNull(extractor.extract(doc, path("$.a[-1]")))              // negative
        assertNull(extractor.extract(doc, path("$..name")))              // recursive
    }

    @Test
    fun extract_notJsonReturnsNull() {
        assertNull(extractor.extract("<html>not json</html>", path("$.a")))
    }

    @Test
    fun tokenize_mixedNotation() {
        val tokens = extractor.tokenize("$.a['b'][2].c")!!
        assertEquals(4, tokens.size)
        assertEquals(JsonPathExtractor.Token.Member("a"), tokens[0])
        assertEquals(JsonPathExtractor.Token.Member("b"), tokens[1])
        assertEquals(JsonPathExtractor.Token.Index(2), tokens[2])
        assertEquals(JsonPathExtractor.Token.Member("c"), tokens[3])
    }
}
