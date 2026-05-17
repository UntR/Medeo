package com.untr.medeo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val key: String,
    @ColumnInfo(defaultValue = "")
    val name: String = "",
    val pic: String? = null,
    val sourceName: String,
    val playSourceName: String,
    val episodeIndex: Int,
    val episodeName: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)
