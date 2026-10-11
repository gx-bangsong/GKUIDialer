package com.android.dialer.numberinfo.database

import androidx.room.*
import com.android.dialer.numberinfo.model.PrefixRule
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefixRuleDao {
    @Query("SELECT * FROM prefix_rules ORDER BY orderIndex ASC")
    fun getAllAsFlow(): Flow<List<PrefixRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: PrefixRule): Long

    @Delete
    suspend fun delete(rule: PrefixRule)

    @Update
    suspend fun update(rule: PrefixRule)

    @Query("UPDATE prefix_rules SET orderIndex = :newIndex WHERE id = :id")
    suspend fun updateOrder(id: Long, newIndex: Int)

    @Query("SELECT * FROM prefix_rules WHERE :phone LIKE prefix || '%' ORDER BY orderIndex ASC")
    suspend fun findMatchingRules(phone: String): List<PrefixRule>
}
