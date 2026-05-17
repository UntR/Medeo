package com.untr.medeo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Favorite::class,
        WatchProgress::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun progressDao(): ProgressDao
}
