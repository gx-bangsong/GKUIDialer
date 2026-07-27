package com.gkuidialer.database

@Entity(tableName = "block_logs")
data class BlockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val ruleMatched: String,
    val strategyExecuted: String,
    val timestamp: Long = System.currentTimeMillis()
)
