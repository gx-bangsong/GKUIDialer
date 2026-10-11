package com.android.dialer.numberinfo.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "yellow_page_cache")
data class YellowPageCache(
    @PrimaryKey val phoneNumber: String,
    val displayName: String,
    val category: String,
    val verified: Boolean,
    val location: String?,
    val cachedAt: Long = System.currentTimeMillis(),
    val ttlSeconds: Long = 604800
)
