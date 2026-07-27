package com.android.dialer.numberinfo.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prefix_rules")
data class PrefixRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prefix: String,
    val matchType: String,
    val strategy: String,
    val note: String?,
    val orderIndex: Int
)
