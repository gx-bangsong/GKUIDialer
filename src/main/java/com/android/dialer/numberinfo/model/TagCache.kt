package com.android.dialer.numberinfo.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_cache")
data class TagCache(
    @PrimaryKey val phoneNumber: String,
    val tagText: String?,
    val tagTypeName: String?,
    val markCount: Int,
    val source: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val ttlSeconds: Long = 86400,
    val isNegativeCache: Boolean = false
)
