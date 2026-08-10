package com.termux.companion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CommandHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
}
