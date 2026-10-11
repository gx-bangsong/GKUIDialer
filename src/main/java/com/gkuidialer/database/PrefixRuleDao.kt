package com.gkuidialer.database

@Dao
interface PrefixRuleDao {
    @Query("SELECT * FROM prefix_rules ORDER BY orderIndex ASC")
    suspend fun getAll(): List<PrefixRuleEntity>
}
