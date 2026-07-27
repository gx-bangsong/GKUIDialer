package com.android.dialer.numberinfo.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manual_marks")
data class ManualMark(
    @PrimaryKey val phoneNumber: String,
    val note: String?,
    val markTime: Long = System.currentTimeMillis()
)
