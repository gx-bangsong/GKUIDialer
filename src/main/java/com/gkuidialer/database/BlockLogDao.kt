package com.gkuidialer.database

@Dao
interface BlockLogDao {
    @Insert
    suspend fun insert(log: BlockLogEntity)

    @Query("SELECT * FROM block_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecent(): List<BlockLogEntity>
}
