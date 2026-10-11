package com.gkuidialer.database

@Dao
interface SpamTagDao {
    @Query("SELECT * FROM spam_tags WHERE phone_number = :phone")
    suspend fun getByPhone(phone: String): SpamTagEntity?
}
