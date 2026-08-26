package com.termux.companion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["path"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean = true,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)