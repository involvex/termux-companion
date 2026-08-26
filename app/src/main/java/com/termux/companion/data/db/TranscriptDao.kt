package com.termux.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranscriptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptEntity)

    @Query("SELECT * FROM transcript ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<TranscriptEntity>

    @Query("DELETE FROM transcript")
    suspend fun clearAll()
}