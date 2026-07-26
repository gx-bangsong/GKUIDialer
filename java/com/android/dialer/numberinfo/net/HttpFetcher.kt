/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Minimal HTTP fetcher used by rule-based providers. Blocking; must only be
 * called from Dispatchers.IO. Throws [IOException] on network failure so the
 * caller can distinguish "network failed" (do not negative-cache) from
 * "fetched fine but no data" (negative-cache).
 */
open class HttpFetcher {

    @Throws(IOException::class)
    open fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        charset: String = "UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept-Encoding", "gzip")
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code for host ${URL(url).host}")
            }
            val raw: InputStream = conn.inputStream
            val stream =
                if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                    GZIPInputStream(raw)
                } else {
                    raw
                }
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) break // page is bigger than we care about
                    out.write(buf, 0, n)
                }
                return out.toByteArray().toString(charsetOf(charset))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun charsetOf(name: String): Charset =
        try {
            Charset.forName(name)
        } catch (e: Exception) {
            Charsets.UTF_8
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3000
        const val DEFAULT_MAX_BYTES = 512 * 1024
    }
}
