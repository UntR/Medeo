package com.untr.medeo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite")
data class Favorite(
    @PrimaryKey val contentKey: String,
    val name: String,
    val pic: String?,
    val year: String?,
    val preferredSourceId: String,
    val preferredVodId: Long,
    val preferredSourceName: String,
    val savedAt: Long
)

val Favorite.preferredKey: String get() = "$preferredSourceId|$preferredVodId"
