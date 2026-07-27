【Room Database完整Schema（三模块共用）】

@Entity(tableName = "spam_tags")
class SpamTagEntity(...)

@Entity(tableName = "prefix_rules")
class PrefixRuleEntity(...)

@Entity(tableName = "yellow_pages")
class YellowPageEntity(...)

@Entity(tableName = "block_logs")
class BlockLogEntity(...)

@Dao interfaces: SpamTagDao, PrefixRuleDao, YellowPageDao, BlockLogDao

Database: AppDatabase (RoomDatabase)
Version 1
