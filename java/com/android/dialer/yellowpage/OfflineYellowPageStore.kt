/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.yellowpage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.android.dialer.numberinfo.NumberInfoLog
import java.io.File

/**
 * Offline yellow-page database, exthmUI-style: a prebuilt SQLite db shipped in
 * assets, copied once to the no-backup dir and opened read-only.
 *
 * Schema (built by tools/gen_yellowpage_db.py):
 *   yellow_page(number TEXT PRIMARY KEY, name TEXT NOT NULL,
 *               category TEXT, verified INTEGER NOT NULL DEFAULT 0)
 *   meta(key TEXT PRIMARY KEY, value TEXT)         -- 'version', 'source'
 *
 * [YellowPageUpdater] can atomically replace the installed copy with a newer
 * downloaded database; a higher meta.version wins over the asset copy.
 */
class OfflineYellowPageStore(private val context: Context) : YellowPageStore {

    @Volatile
    private var db: SQLiteDatabase? = null
    private val lock = Any()

    override fun get(number: String): YellowPageStore.Entry? {
        val database = openDb() ?: return null
        return try {
            database.query(
                TABLE, arrayOf("number", "name", "category", "verified"),
                "number = ?", arrayOf(number), null, null, null,
            ).use { c ->
                if (!c.moveToFirst()) return null
                YellowPageStore.Entry(
                    number = c.getString(0),
                    name = c.getString(1),
                    category = c.getString(2),
                    verified = c.getInt(3) == 1)
            }
        } catch (e: Exception) {
            NumberInfoLog.w("yellow page lookup failed", e)
            null
        }
    }

    /** Installed database version (meta.version), 0 when unavailable. */
    fun installedVersion(): Long {
        val database = openDb() ?: return 0
        return try {
            database.rawQuery(
                "SELECT value FROM meta WHERE key = 'version'", null,
            ).use { c -> if (c.moveToFirst()) c.getString(0).toLongOrNull() ?: 0 else 0 }
        } catch (e: Exception) {
            0
        }
    }

    /** Called by YellowPageUpdater after replacing the db file. */
    fun reopen() {
        synchronized(lock) {
            db?.close()
            db = null
        }
    }

    internal fun databaseFile(): File =
        File(context.createDeviceProtectedStorageContext().noBackupFilesDir, DB_NAME)

    private fun openDb(): SQLiteDatabase? {
        db?.let { return it }
        synchronized(lock) {
            db?.let { return it }
            return try {
                val file = databaseFile()
                if (!file.exists()) {
                    copyFromAssets(file)
                }
                SQLiteDatabase.openDatabase(
                    file.path, null, SQLiteDatabase.OPEN_READONLY,
                ).also { db = it }
            } catch (e: Exception) {
                // Asset may legitimately be absent (yellow pages not bundled).
                NumberInfoLog.d("offline yellow page db unavailable: $e")
                null
            }
        }
    }

    private fun copyFromAssets(target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        context.assets.open(ASSET_PATH).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IllegalStateException("Failed to install yellow page db")
        }
    }

    companion object {
        const val ASSET_PATH = "yellowpage/yellowpage.db"
        const val DB_NAME = "yellowpage.db"
        private const val TABLE = "yellow_page"
    }
}
