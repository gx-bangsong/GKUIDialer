/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import com.android.dialer.numberinfo.cache.ManualMarkDao
import com.android.dialer.numberinfo.cache.NumberInfoCacheDao
import com.android.dialer.numberinfo.provider.ManualMarkProvider
import com.android.dialer.numberinfo.provider.NumberInfoProvider
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.numberinfo.testing.FakeNumberInfoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Manual marks: set, correct, remove, priority over online data, and cache clearing. */
class NumberInfoManualMarkTest {

    private val number = "13910001234"

    private class InMemoryMarks : ManualMarkDao {
        val rows = HashMap<String, NumberInfo>()
        override fun get(number: String): NumberInfo? = rows[number]
        override fun put(number: String, markType: MarkType, note: String?) {
            rows[number] = NumberInfo(number = number, markType = markType, note = note,
                source = Sources.MANUAL, confidence = 1f)
        }
        override fun remove(number: String) {
            rows.remove(number)
        }
        override fun getAll(): List<NumberInfo> = rows.values.toList()
    }

    private class CountingCache : NumberInfoCacheDao {
        var clears = 0
        override fun getValid(number: String, ttlMillis: Long, negativeTtlMillis: Long) =
            emptyList<NumberInfo>()
        override fun hasFresh(
            number: String, source: String, ttlMillis: Long, negativeTtlMillis: Long,
        ) = false
        override fun upsert(info: NumberInfo) {}
        override fun upsertNegative(number: String, source: String, timestampMillis: Long) {}
        override fun evict(number: String) {}
        override fun clearAll() {
            clears++
        }
        override fun purgeOlderThan(olderThanMillis: Long) = 0
    }

    /** Online source that always says "telemarketing". */
    private class TelemarketingSource : NumberInfoProvider {
        override val id: String = "webfake"
        override val priority: Int = 100
        override val isOnline: Boolean = true
        override suspend fun query(number: String): NumberInfo? =
            NumberInfo(number = number, markType = MarkType.TELEMARKETING, markCount = 9,
                source = id)
    }

    private fun repository(marks: InMemoryMarks, cache: CountingCache): NumberInfoRepository {
        val settings = FakeNumberInfoSettings(onlineLookupEnabled = true)
        // Same registrations as NumberInfoComponent: the manual mark is a local provider.
        val providers = ProviderManager(settings).apply {
            register(ManualMarkProvider(marks))
            register(TelemarketingSource())
        }
        return NumberInfoRepository(
            contacts = ContactChecker { ContactStatus.NOT_CONTACT },
            settings = settings,
            providerManager = providers,
            cacheDao = cache,
            manualMarkDao = marks,
        )
    }

    @Test
    fun setMark_isReturnedLocally() = runBlocking {
        val marks = InMemoryMarks()
        val repo = repository(marks, CountingCache())
        repo.setManualMark(number, MarkType.FRAUD)
        assertEquals(MarkType.FRAUD, repo.manualMark(number)?.markType)
    }

    @Test
    fun manualMark_beatsOnlineData_inTheAggregatedResult() = runBlocking {
        val marks = InMemoryMarks()
        val repo = repository(marks, CountingCache())
        repo.setManualMark(number, MarkType.FRAUD)
        // The online source says telemarketing; the user's own mark wins.
        assertEquals(MarkType.FRAUD, repo.query(number)?.markType)
    }

    @Test
    fun correction_replacesThePreviousMark() = runBlocking {
        val marks = InMemoryMarks()
        val repo = repository(marks, CountingCache())
        repo.setManualMark(number, MarkType.FRAUD)
        repo.setManualMark(number, MarkType.AGENT)
        assertEquals(MarkType.AGENT, repo.manualMark(number)?.markType)
    }

    @Test
    fun clearMark_removesTheUsersMark_andOnlineDataAppliesAgain() = runBlocking {
        val marks = InMemoryMarks()
        val repo = repository(marks, CountingCache())
        repo.setManualMark(number, MarkType.FRAUD)
        repo.clearManualMark(number)
        assertNull(repo.manualMark(number))
        assertEquals(MarkType.TELEMARKETING, repo.query(number)?.markType)
    }

    @Test
    fun clearOnlineCache_clearsCacheOnly_andKeepsManualMarks() = runBlocking {
        val marks = InMemoryMarks()
        val cache = CountingCache()
        val repo = repository(marks, cache)
        repo.setManualMark(number, MarkType.FRAUD)
        repo.clearOnlineCache()
        assertEquals(1, cache.clears)
        assertEquals(MarkType.FRAUD, repo.manualMark(number)?.markType)
    }

    @Test
    fun nullOrInvalidNumber_isIgnored() = runBlocking {
        val marks = InMemoryMarks()
        val repo = repository(marks, CountingCache())
        repo.setManualMark(null, MarkType.FRAUD)
        repo.clearManualMark("abc")
        assertEquals(0, marks.rows.size)
        assertNull(repo.manualMark(null))
    }
}
