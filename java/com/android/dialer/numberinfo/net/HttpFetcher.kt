/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Minimal HTTP fetcher used by rule-based providers. Throws [IOException] on
 * network failure so the caller can distinguish "network failed" (do not
 * negative-cache) from "fetched fine but no data" (negative-cache).
 *
 * Two entry points:
 *  - [fetch] (suspending) is what latency-bounded callers use. Blocking
 *    socket I/O cannot be interrupted by coroutine cancellation (nor by
 *    Thread.interrupt), so the I/O runs on [executor] and cancellation both
 *    returns to the caller immediately and disconnects the connection, which
 *    aborts a blocked connect()/read() on the worker thread.
 *  - [fetchBlocking] for plain background threads (e.g. rule updates).
 */
open class HttpFetcher(
    private val executor: Executor = Dispatchers.IO.asExecutor(),
) {

    /**
     * Cancellable fetch. Cancelling the calling coroutine (e.g. an expired
     * withTimeout budget) makes this throw CancellationException right away
     * and aborts the in-flight request.
     */
    @Throws(IOException::class)
    open suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        charset: String = "UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): String {
        val conn = openConnection(url) // no network I/O happens before connect()
        val cancelled = AtomicBoolean(false)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                cancelled.set(true)
                // Safe from another thread; closes the socket under a blocked read.
                // (DNS lookups can't be aborted; they end on their own while the
                // caller has long moved on.)
                try {
                    conn.disconnect()
                } catch (ignored: Exception) {
                }
            }
            executor.execute {
                val result = runCatching {
                    execute(conn, method, headers, charset, timeoutMs, maxBytes, cancelled)
                }
                cont.resumeWith(result) // dropped if the caller was cancelled meanwhile
            }
        }
    }

    /** Blocking fetch; call from a background thread only. */
    @Throws(IOException::class)
    open fun fetchBlocking(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        charset: String = "UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): String = execute(openConnection(url), method, headers, charset, timeoutMs, maxBytes, null)

    protected open fun openConnection(url: String): HttpURLConnection =
        URL(url).openConnection() as HttpURLConnection

    private fun execute(
        conn: HttpURLConnection,
        method: String,
        headers: Map<String, String>,
        charset: String,
        timeoutMs: Int,
        maxBytes: Int,
        cancelled: AtomicBoolean?,
    ): String {
        fun checkCancelled() {
            if (cancelled?.get() == true) throw IOException("request cancelled")
        }
        // readTimeout bounds each read() call, not the whole body; a slowly
        // dripping server could otherwise hold the worker for a long time.
        val deadlineNanos = System.nanoTime() + 2L * timeoutMs * 1_000_000L
        try {
            checkCancelled()
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
                throw IOException("HTTP $code for host ${conn.url.host}")
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
                    checkCancelled()
                    if (System.nanoTime() > deadlineNanos) {
                        throw SocketTimeoutException("body not received within ${2 * timeoutMs}ms")
                    }
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
