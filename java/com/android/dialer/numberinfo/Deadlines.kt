/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [block] in [scope] and waits at most [timeoutMs] for its result.
 *
 * Unlike a plain `withTimeoutOrNull { block() }`, this returns on time even
 * when [block] is stuck in non-cancellable blocking work — DNS resolution,
 * HttpURLConnection connect/read, SQLite or ContentResolver calls — because
 * the work runs detached (not as a child of the caller) and only the *wait*
 * is bounded. On timeout the work is cancelled best-effort and abandoned; its
 * eventual result is discarded.
 *
 * @param scope must be backed by a SupervisorJob (so a failing block cannot
 *   cancel the scope) and a dispatcher that may block, e.g. Dispatchers.IO.
 * @return the block's result, or null on timeout. Exceptions thrown by
 *   [block] are rethrown to the caller.
 */
suspend fun <T> awaitWithin(
    timeoutMs: Long,
    scope: CoroutineScope,
    block: suspend CoroutineScope.() -> T,
): T? {
    val work = scope.async(block = block)
    try {
        return withTimeoutOrNull(timeoutMs) { work.await() }
    } finally {
        if (!work.isCompleted) work.cancel()
    }
}
