package com.gkuidialer.database

@Dao
interface YellowPageDao {
    @Query("SELECT * FROM yellow_pages WHERE phone_number = :phone")
    suspend fun getByPhone(phone: String): YellowPageEntity?
}
