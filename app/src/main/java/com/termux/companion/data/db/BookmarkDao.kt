package com.termux.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BookmarkEntity)

    @Query("SELECT * FROM bookmarks ORDER BY name ASC")
    suspend fun getAll(): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}