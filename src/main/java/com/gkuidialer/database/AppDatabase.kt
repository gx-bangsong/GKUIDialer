package com.gkuidialer.database

@Database(entities = [SpamTagEntity::class, YellowPageEntity::class, PrefixRuleEntity::class, BlockLogEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spamTagDao(): SpamTagDao
    abstract fun yellowPageDao(): YellowPageDao
    abstract fun prefixRuleDao(): PrefixRuleDao
    abstract fun blockLogDao(): BlockLogDao
}
