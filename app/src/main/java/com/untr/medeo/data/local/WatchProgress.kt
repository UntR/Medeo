package com.untr.medeo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val contentKey: String,
    val name: String = "",
    val pic: String? = null,
    val year: String?,
    val preferredSourceId: String,
    val preferredVodId: Long,
    val preferredSourceName: String,
    val playSourceName: String,
    val episodeIndex: Int,
    val episodeName: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

val WatchProgress.preferredKey: String get() = "$preferredSourceId|$preferredVodId"
