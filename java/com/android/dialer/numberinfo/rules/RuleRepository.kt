/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.rules

import android.content.Context
import com.android.dialer.numberinfo.NumberInfoLog
import com.android.dialer.numberinfo.net.HttpFetcher
import java.io.File
import java.io.IOException

/**
 * Loads and hot-updates parse rules.
 *
 * Precedence: filesDir override (hot-updated) > built-in assets copy.
 * A remote update only replaces the local override after the whole file has
 * been parsed and validated ([RuleSet.parse]); a broken download can therefore
 * never brick parsing — worst case we keep serving the previous rules.
 *
 * Thread-safety: [get] may be called from any thread; loading is synchronized
 * and the parsed RuleSet is cached until [invalidate] or a successful update.
 */
class RuleRepository(
    private val context: Context,
    private val fetcher: HttpFetcher = HttpFetcher(),
) {

    @Volatile
    private var cached: RuleSet? = null

    private val lock = Any()

    fun get(): RuleSet {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val loaded = loadOverride() ?: loadBuiltin()
            cached = loaded
            return loaded
        }
    }

    fun invalidate() {
        synchronized(lock) { cached = null }
    }

    /**
     * Fetches rules from [RuleSet.ruleUpdateUrl] (or [explicitUrl]) and installs
     * them atomically. Blocking — call from Dispatchers.IO only.
     *
     * @return true if new rules were installed.
     */
    @Throws(IOException::class)
    fun updateFromRemote(explicitUrl: String? = null): Boolean {
        val url = explicitUrl ?: get().ruleUpdateUrl ?: return false
        val body = fetcher.fetch(url, timeoutMs = 10_000)
        val parsed = try {
            RuleSet.parse(body) // full validation before anything is written
        } catch (e: Exception) {
            NumberInfoLog.w("Remote rules rejected (invalid): $e")
            return false
        }
        synchronized(lock) {
            val tmp = File(overrideFile().parentFile, OVERRIDE_NAME + ".tmp")
            tmp.parentFile?.mkdirs()
            tmp.writeText(body)
            if (!tmp.renameTo(overrideFile())) {
                tmp.delete()
                throw IOException("Failed to install rules override")
            }
            cached = parsed
        }
        NumberInfoLog.i("Rules updated from remote, updatedAt=${parsed.updatedAt}")
        return true
    }

    /** Removes the hot-updated override, reverting to built-in asset rules. */
    fun resetToBuiltin() {
        synchronized(lock) {
            overrideFile().delete()
            cached = null
        }
    }

    private fun loadOverride(): RuleSet? {
        val f = overrideFile()
        if (!f.isFile) return null
        return try {
            RuleSet.parse(f.readText())
        } catch (e: Exception) {
            // Corrupt override (e.g. interrupted write on old builds): drop it.
            NumberInfoLog.w("Override rules corrupt, falling back to builtin: $e")
            f.delete()
            null
        }
    }

    private fun loadBuiltin(): RuleSet {
        return try {
            context.assets.open(ASSET_PATH).use { input ->
                RuleSet.parse(input.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // Should be impossible (asset ships with the APK and is covered by
            // unit tests), but degrade to an empty set rather than crash a call.
            NumberInfoLog.w("Builtin rules failed to load: $e")
            RuleSet(RuleSet.SUPPORTED_SCHEMA_VERSION, null, null, emptyList())
        }
    }

    private fun overrideFile(): File =
        File(File(context.filesDir, DIR_NAME), OVERRIDE_NAME)

    companion object {
        const val ASSET_PATH = "numberinfo/rules.json"
        private const val DIR_NAME = "numberinfo"
        private const val OVERRIDE_NAME = "rules.json"
    }
}
