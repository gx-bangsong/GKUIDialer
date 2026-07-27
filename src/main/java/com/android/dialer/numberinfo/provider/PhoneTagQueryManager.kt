package com.android.dialer.numberinfo.provider

import kotlinx.coroutines.*

class PhoneTagQueryManager(
    private val providers: List<PhoneTagProvider>
) {
    suspend fun queryAll(phone: String): PhoneTagResult? = withContext(Dispatchers.IO) {
        val results = providers.filter { it.isEnabled }
            .map { async { runCatching { it.query(phone) }.getOrNull() } }
            .awaitAll()
        results.filterNotNull()
            .maxByOrNull { it.markCount } ?: results.firstOrNull()
    }
}
