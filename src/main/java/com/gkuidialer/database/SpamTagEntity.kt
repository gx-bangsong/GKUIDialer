package com.gkuidialer.database

@Entity(tableName = "spam_tags")
data class SpamTagEntity(
    @PrimaryKey val phoneNumber: String,
    val tag: String,
    val tagType: String,
    val markCount: Int,
    val isScam: Boolean,
    val source: String
)
