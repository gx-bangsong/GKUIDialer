package com.android.dialer.numberinfo.database

import androidx.room.*
import com.android.dialer.numberinfo.model.TagCache

@Dao
interface TagCacheDao {
    @Query("SELECT * FROM tag_cache WHERE phoneNumber = :phone")
    suspend fun getByPhone(phone: String): TagCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TagCache)

    @Query("DELETE FROM tag_cache WHERE cachedAt < :expireBefore")
    suspend fun deleteExpired(expireBefore: Long)
}
