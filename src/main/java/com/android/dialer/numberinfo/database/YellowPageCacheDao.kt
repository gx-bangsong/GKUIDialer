package com.android.dialer.numberinfo.database

import androidx.room.*
import com.android.dialer.numberinfo.model.YellowPageCache

@Dao
interface YellowPageCacheDao {
    @Query("SELECT * FROM yellow_page_cache WHERE phoneNumber = :phone")
    suspend fun getByPhone(phone: String): YellowPageCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: YellowPageCache)

    @Query("DELETE FROM yellow_page_cache WHERE cachedAt < :expireBefore")
    suspend fun deleteExpired(expireBefore: Long)
}
