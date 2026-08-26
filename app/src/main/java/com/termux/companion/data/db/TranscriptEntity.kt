package com.termux.companion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript",
    indices = [Index(value = ["timestamp"])]
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "is_error")
    val isError: Boolean = false,
    @ColumnInfo(name = "is_command")
    val isCommand: Boolean = false,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)