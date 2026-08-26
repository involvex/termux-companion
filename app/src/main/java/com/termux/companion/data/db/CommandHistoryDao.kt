package com.termux.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(command: CommandHistoryEntity)

    /**
     * Atomically records a command: bumps use count/timestamp when it already
     * exists (unique index on [CommandHistoryEntity.command]), otherwise inserts.
     */
    @Transaction
    suspend fun recordCommand(entity: CommandHistoryEntity) {
        if (incrementUseCount(entity.command, entity.timestamp) == 0) {
            insertIfAbsent(entity)
        }
    }

    @Query("SELECT * FROM command_history ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    suspend fun getRecentCommands(limit: Int = 50): List<CommandHistoryEntity>

    @Query("SELECT * FROM command_history ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    fun observeRecentCommands(limit: Int = 50): Flow<List<CommandHistoryEntity>>

    /** Chronological (newest first) ordering — required for ↑/↓ history recall. */
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentByTime(limit: Int = 100): Flow<List<CommandHistoryEntity>>

    @Query("SELECT * FROM command_history WHERE command LIKE :prefix || '%' ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    suspend fun searchByPrefix(prefix: String, limit: Int = 10): List<CommandHistoryEntity>

    @Query("SELECT * FROM command_history WHERE command LIKE '%' || :query || '%' ORDER BY use_count DESC, timestamp DESC LIMIT :limit")
    suspend fun searchBySubstring(query: String, limit: Int = 25): List<CommandHistoryEntity>

    @Query("UPDATE command_history SET use_count = use_count + 1, timestamp = :timestamp WHERE command = :command")
    suspend fun incrementUseCount(command: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM command_history WHERE command = :command")
    suspend fun delete(command: String)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
