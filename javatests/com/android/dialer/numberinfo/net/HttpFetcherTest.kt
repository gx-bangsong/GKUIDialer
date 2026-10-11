/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.net

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Exercises the real HttpURLConnection path against a loopback server. */
class HttpFetcherTest {

    private lateinit var server: ServerSocket

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun url() = "http://127.0.0.1:${server.localPort}/s?q=1"

    /** Accepts one connection, consumes the request head, then hands the socket to [respond]. */
    private fun serveOnce(respond: (Socket) -> Unit) = thread(isDaemon = true) {
        try {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                var tail = 0
                while (tail != 0x0d0a0d0a) { // read up to and including "\r\n\r\n"
                    val b = input.read()
                    if (b < 0) return@thread
                    tail = (tail shl 8) or b
                }
                respond(socket)
            }
        } catch (e: IOException) {
            // server closed by tearDown
        }
    }

    private fun Socket.writeResponse(status: String, body: ByteArray) {
        getOutputStream().apply {
            write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
            write(body)
            flush()
        }
    }

    @Test
    fun fetch_returnsDecodedBody() = runBlocking {
        val body = "该号码被1283人标记为诈骗电话"
        serveOnce { it.writeResponse("200 OK", body.toByteArray(Charsets.UTF_8)) }
        assertEquals(body, HttpFetcher().fetch(url(), timeoutMs = 5_000))
    }

    @Test
    fun fetch_httpError_throwsIOException() = runBlocking {
        serveOnce { it.writeResponse("500 Internal Server Error", ByteArray(0)) }
        try {
            HttpFetcher().fetch(url(), timeoutMs = 5_000)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun fetch_cancelledCaller_returnsPromptly_andAbortsConnection() = runBlocking {
        val clientGone = CountDownLatch(1)
        serveOnce { socket ->
            // Never answer; just wait for the client side to go away.
            socket.soTimeout = 15_000
            try {
                if (socket.getInputStream().read() < 0) clientGone.countDown()
            } catch (e: IOException) {
                clientGone.countDown() // reset by peer also means "aborted"
            }
        }
        val started = System.nanoTime()
        val result = withTimeoutOrNull(300) {
            HttpFetcher().fetch(url(), timeoutMs = 8_000)
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("fetch ignored cancellation for ${elapsedMs}ms", elapsedMs < 2_000)
        assertTrue("connection was left open after cancellation",
            clientGone.await(3, TimeUnit.SECONDS))
    }
}
