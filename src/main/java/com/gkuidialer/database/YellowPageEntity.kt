package com.gkuidialer.database

@Entity(tableName = "yellow_pages")
data class YellowPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String,
    val category: String,
    val verified: Boolean,
    val location: String?,
    val source: String
)
