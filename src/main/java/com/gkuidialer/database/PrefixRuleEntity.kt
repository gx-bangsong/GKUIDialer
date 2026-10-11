package com.gkuidialer.database

@Entity(tableName = "prefix_rules")
data class PrefixRuleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val prefix: String,
    val matchType: String,
    val strategy: String,
    val note: String?,
    val orderIndex: Int
)
