package com.termux.companion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CommandHistoryEntity::class, SnippetEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        /**
         * v1 -> v2: deduplicate history rows by command (merging use counts),
         * then enforce a unique index so duplicates can never reappear.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `command_history_v2` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`command` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`use_count` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `command_history_v2` (`command`, `timestamp`, `use_count`) " +
                        "SELECT `command`, MAX(`timestamp`), SUM(`use_count`) " +
                        "FROM `command_history` GROUP BY `command`"
                )
                db.execSQL("DROP TABLE `command_history`")
                db.execSQL("ALTER TABLE `command_history_v2` RENAME TO `command_history`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_command_history_command` " +
                        "ON `command_history` (`command`)"
                )
            }
        }

        /** v2 -> v3: new snippets table with a unique name index (FEAT-009). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `snippets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`command` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_snippets_name` ON `snippets` (`name`)"
                )
            }
        }
    }
}
