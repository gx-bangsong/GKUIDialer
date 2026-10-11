/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.yellowpage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.net.HttpFetcher
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Optional whole-file updater for the offline yellow-page database.
 *
 * Update flow (all on Dispatchers.IO, caller-driven — e.g. from settings):
 *  1. download the db to a temp file (size-capped)
 *  2. verify SHA-256 against the expected digest
 *  3. sanity-open it read-only and check schema + meta.version > installed
 *  4. atomic rename over the installed copy, then [OfflineYellowPageStore.reopen]
 *
 * Any failure leaves the previous database untouched.
 */
class YellowPageUpdater(
    private val context: Context,
    private val store: OfflineYellowPageStore,
) {

    /**
     * @param url https source of the sqlite file
     * @param expectedSha256 hex digest the download must match
     * @return true if a newer database was installed
     */
    @Throws(IOException::class)
    fun update(url: String, expectedSha256: String): Boolean {
        val target = store.databaseFile()
        val tmp = File(target.parentFile, target.name + ".download")
        try {
            download(url, tmp)

            val digest = sha256(tmp)
            if (!digest.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("yellow page db digest mismatch")
            }

            val newVersion = validateAndReadVersion(tmp)
            val installed = store.installedVersion()
            if (newVersion <= installed) {
                NumberInfoLog.i("yellow page db not newer ($newVersion <= $installed), skipping")
                return false
            }

            store.reopen() // close the old handle before replacing the file
            if (!tmp.renameTo(target)) {
                throw IOException("failed to install yellow page db")
            }
            store.reopen()
            NumberInfoLog.i("yellow page db updated to version $newVersion")
            return true
        } finally {
            tmp.delete()
        }
    }

    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = DOWNLOAD_TIMEOUT_MS
            conn.readTimeout = DOWNLOAD_TIMEOUT_MS
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} downloading yellow page db")
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_DB_BYTES) {
                            throw IOException("yellow page db exceeds size cap")
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun validateAndReadVersion(file: File): Long =
        SQLiteDatabase.openDatabase(
            file.path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            // Schema sanity: the lookup query must work.
            db.rawQuery("SELECT number, name, category, verified FROM yellow_page LIMIT 1", null)
                .use { /* just proving the table shape */ }
            db.rawQuery("SELECT value FROM meta WHERE key = 'version'", null).use { c ->
                if (c.moveToFirst()) c.getString(0).toLongOrNull() ?: 0 else 0
            }
        }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 30_000
        private const val MAX_DB_BYTES = 64L * 1024 * 1024
    }
}
