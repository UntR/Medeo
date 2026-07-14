package com.untr.medeo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Favorite::class,
        WatchProgress::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun progressDao(): ProgressDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN pic TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `favorite_v3` (
                `contentKey` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `pic` TEXT,
                `year` TEXT,
                `preferredSourceId` TEXT NOT NULL,
                `preferredVodId` INTEGER NOT NULL,
                `preferredSourceName` TEXT NOT NULL,
                `savedAt` INTEGER NOT NULL,
                PRIMARY KEY(`contentKey`)
            )""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO `favorite_v3` (
                `contentKey`, `name`, `pic`, `year`, `preferredSourceId`,
                `preferredVodId`, `preferredSourceName`, `savedAt`
            )
            SELECT
                'legacy:' || `key`,
                `name`,
                `pic`,
                NULL,
                CASE
                    WHEN instr(`key`, '|') > 0 THEN substr(`key`, 1, instr(`key`, '|') - 1)
                    ELSE ''
                END,
                CASE
                    WHEN instr(`key`, '|') > 0 THEN CAST(substr(`key`, instr(`key`, '|') + 1) AS INTEGER)
                    ELSE 0
                END,
                `sourceName`,
                `savedAt`
            FROM `favorite`""".trimIndent()
        )
        db.execSQL("DROP TABLE `favorite`")
        db.execSQL("ALTER TABLE `favorite_v3` RENAME TO `favorite`")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `watch_progress_v3` (
                `contentKey` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `pic` TEXT,
                `year` TEXT,
                `preferredSourceId` TEXT NOT NULL,
                `preferredVodId` INTEGER NOT NULL,
                `preferredSourceName` TEXT NOT NULL,
                `playSourceName` TEXT NOT NULL,
                `episodeIndex` INTEGER NOT NULL,
                `episodeName` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`contentKey`)
            )""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO `watch_progress_v3` (
                `contentKey`, `name`, `pic`, `year`, `preferredSourceId`,
                `preferredVodId`, `preferredSourceName`, `playSourceName`,
                `episodeIndex`, `episodeName`, `positionMs`, `durationMs`, `updatedAt`
            )
            SELECT
                'legacy:' || `key`,
                `name`,
                `pic`,
                NULL,
                CASE
                    WHEN instr(`key`, '|') > 0 THEN substr(`key`, 1, instr(`key`, '|') - 1)
                    ELSE ''
                END,
                CASE
                    WHEN instr(`key`, '|') > 0 THEN CAST(substr(`key`, instr(`key`, '|') + 1) AS INTEGER)
                    ELSE 0
                END,
                `sourceName`,
                `playSourceName`,
                `episodeIndex`,
                `episodeName`,
                `positionMs`,
                `durationMs`,
                `updatedAt`
            FROM `watch_progress`""".trimIndent()
        )
        db.execSQL("DROP TABLE `watch_progress`")
        db.execSQL("ALTER TABLE `watch_progress_v3` RENAME TO `watch_progress`")
    }
}
