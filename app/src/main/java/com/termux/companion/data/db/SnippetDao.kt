package com.termux.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {

    /** Returns -1 when the unique-name constraint rejected the insert. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snippet: SnippetEntity): Long

    @Update
    suspend fun update(snippet: SnippetEntity)

    @Query("SELECT * FROM snippets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<SnippetEntity>

    @Query("SELECT * FROM snippets WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): SnippetEntity?

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
