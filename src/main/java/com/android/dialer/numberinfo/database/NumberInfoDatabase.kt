package com.android.dialer.numberinfo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.android.dialer.numberinfo.model.*

@Database(
    entities = [TagCache::class, ManualMark::class, PrefixRule::class, YellowPageCache::class],
    version = 1,
    exportSchema = false
)
abstract class NumberInfoDatabase : RoomDatabase() {
    abstract fun tagCacheDao(): TagCacheDao
    abstract fun manualMarkDao(): ManualMarkDao
    abstract fun prefixRuleDao(): PrefixRuleDao
    abstract fun yellowPageDao(): YellowPageCacheDao
}
