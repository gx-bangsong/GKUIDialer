/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo

import com.android.dialer.numberinfo.cache.ManualMarkDao
import com.android.dialer.numberinfo.cache.NumberInfoCacheDao
import com.android.dialer.numberinfo.provider.NumberInfoProvider
import com.android.dialer.numberinfo.provider.ProviderManager
import com.android.dialer.numberinfo.testing.FakeNumberInfoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The contact status decides whether a number may go online. Only a confirmed
 * NOT_CONTACT may; CONTACT and UNKNOWN never reach an online source.
 */
class NumberInfoRepositoryContactTest {

    private val number = "13910001234"

    private class CountingOnlineProvider : NumberInfoProvider {
        override val id: String = "webfake"
        override val priority: Int = 100
        override val isOnline: Boolean = true
        var calls = 0
            private set

        override suspend fun query(number: String): NumberInfo? {
            calls++
            return NumberInfo(
                number = number, markType = MarkType.FRAUD, markCount = 5, source = id)
        }
    }

    private class NoopCache : NumberInfoCacheDao {
        override fun getValid(number: String, ttlMillis: Long, negativeTtlMillis: Long) =
            emptyList<NumberInfo>()
        override fun hasFresh(
            number: String, source: String, ttlMillis: Long, negativeTtlMillis: Long,
        ) = false
        override fun upsert(info: NumberInfo) {}
        override fun upsertNegative(number: String, source: String, timestampMillis: Long) {}
        override fun evict(number: String) {}
        override fun purgeOlderThan(olderThanMillis: Long) = 0
    }

    private class NoManualMarks : ManualMarkDao {
        override fun get(number: String): NumberInfo? = null
        override fun put(number: String, markType: MarkType, note: String?) {}
        override fun remove(number: String) {}
        override fun getAll(): List<NumberInfo> = emptyList()
    }

    private fun repository(
        status: ContactStatus,
        provider: CountingOnlineProvider,
    ): NumberInfoRepository {
        val settings = FakeNumberInfoSettings(onlineLookupEnabled = true)
        val providers = ProviderManager(settings).apply { register(provider) }
        return NumberInfoRepository(
            contacts = ContactChecker { status },
            settings = settings,
            providerManager = providers,
            cacheDao = NoopCache(),
            manualMarkDao = NoManualMarks(),
        )
    }

    @Test
    fun unknownStatus_neverCallsOnlineProvider() = runBlocking {
        val provider = CountingOnlineProvider()
        val info = repository(ContactStatus.UNKNOWN, provider).query(number)
        assertNull(info)
        assertEquals(0, provider.calls)
    }

    @Test
    fun contact_neverCallsOnlineProvider() = runBlocking {
        val provider = CountingOnlineProvider()
        assertNull(repository(ContactStatus.CONTACT, provider).query(number))
        assertEquals(0, provider.calls)
    }

    @Test
    fun confirmedNonContact_queriesOnlineProvider() = runBlocking {
        val provider = CountingOnlineProvider()
        val info = repository(ContactStatus.NOT_CONTACT, provider).query(number)
        assertNotNull(info)
        assertEquals(MarkType.FRAUD, info!!.markType)
        assertEquals(1, provider.calls)
    }

    @Test
    fun contactStatus_nullOrInvalidNumber_isUnknown() {
        val repo = repository(ContactStatus.NOT_CONTACT, CountingOnlineProvider())
        assertEquals(ContactStatus.UNKNOWN, repo.contactStatus(null))
        assertEquals(ContactStatus.UNKNOWN, repo.contactStatus("abc"))
        assertEquals(ContactStatus.NOT_CONTACT, repo.contactStatus(number))
    }
}
