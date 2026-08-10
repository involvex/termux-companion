package com.termux.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommandHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: CommandHistoryEntity)

    @Query("SELECT * FROM command_history ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    suspend fun getRecentCommands(limit: Int = 50): List<CommandHistoryEntity>

    @Query("SELECT * FROM command_history WHERE command LIKE :prefix || '%' ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    suspend fun searchByPrefix(prefix: String, limit: Int = 10): List<CommandHistoryEntity>

    @Query("UPDATE command_history SET use_count = use_count + 1, timestamp = :timestamp WHERE command = :command")
    suspend fun incrementUseCount(command: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM command_history WHERE command = :command")
    suspend fun delete(command: String)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
