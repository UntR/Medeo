package com.untr.medeo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite")
data class Favorite(
    @PrimaryKey val key: String,
    val name: String,
    val pic: String?,
    val sourceName: String,
    val savedAt: Long
)
