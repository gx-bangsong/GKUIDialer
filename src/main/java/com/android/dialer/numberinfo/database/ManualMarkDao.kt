package com.android.dialer.numberinfo.database

import androidx.room.*
import com.android.dialer.numberinfo.model.ManualMark

@Dao
interface ManualMarkDao {
    @Query("SELECT * FROM manual_marks WHERE phoneNumber = :phone")
    suspend fun getByPhone(phone: String): ManualMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mark: ManualMark)

    @Delete
    suspend fun delete(mark: ManualMark)
}
