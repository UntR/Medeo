package com.untr.medeo.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration2To3_preservesFavoriteAndProgressWithLegacyIdentity() {
        createVersion2Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val db = database.openHelper.writableDatabase
            db.query(
                """SELECT contentKey, name, year, preferredSourceId,
                    preferredVodId, preferredSourceName, savedAt
                    FROM favorite""".trimIndent()
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("legacy:dbzy|101", cursor.getString(0))
                assertEquals("庆余年", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals("dbzy", cursor.getString(3))
                assertEquals(101L, cursor.getLong(4))
                assertEquals("豆瓣资源", cursor.getString(5))
                assertEquals(10L, cursor.getLong(6))
            }
            db.query(
                """SELECT contentKey, name, year, preferredSourceId,
                    preferredVodId, preferredSourceName, playSourceName,
                    episodeIndex, episodeName, positionMs, durationMs, updatedAt
                    FROM watch_progress""".trimIndent()
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("legacy:dbzy|101", cursor.getString(0))
                assertEquals("庆余年", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals("dbzy", cursor.getString(3))
                assertEquals(101L, cursor.getLong(4))
                assertEquals("豆瓣资源", cursor.getString(5))
                assertEquals("默认", cursor.getString(6))
                assertEquals(2, cursor.getInt(7))
                assertEquals("第3集", cursor.getString(8))
                assertEquals(60_000L, cursor.getLong(9))
                assertEquals(120_000L, cursor.getLong(10))
                assertEquals(20L, cursor.getLong(11))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun clearProgress_preservesFavorites() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.favoriteDao().upsertForContent(
                Favorite(
                    contentKey = "庆余年|2024",
                    name = "庆余年",
                    pic = null,
                    year = "2024",
                    preferredSourceId = "dbzy",
                    preferredVodId = 101L,
                    preferredSourceName = "豆瓣资源",
                    savedAt = 10L
                )
            )
            database.progressDao().upsertForContent(
                WatchProgress(
                    contentKey = "庆余年|2024",
                    name = "庆余年",
                    pic = null,
                    year = "2024",
                    preferredSourceId = "dbzy",
                    preferredVodId = 101L,
                    preferredSourceName = "豆瓣资源",
                    playSourceName = "默认",
                    episodeIndex = 2,
                    episodeName = "第3集",
                    positionMs = 60_000L,
                    durationMs = 120_000L,
                    updatedAt = 20L
                )
            )

            database.progressDao().deleteAll()

            val db = database.openHelper.writableDatabase
            db.query("SELECT COUNT(*) FROM watch_progress").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM favorite").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun undoDelete_restoresAllFieldsButNeverOverwritesNewProgress() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.progressDao()
            val original = WatchProgress("测试|2026", "测试", null, "2026", "dbzy", 1L,
                "测试源", "线路A", 82, "第83集", 60_000L, 120_000L, 20L)
            dao.upsertForContent(original)
            dao.deleteByContentKey(original.contentKey)
            dao.restoreIfAbsent(original)
            assertEquals(original, dao.findByContentKey(original.contentKey))
            val newer = original.copy(episodeIndex = 83, episodeName = "第84集", positionMs = 5_000L, updatedAt = 30L)
            dao.upsertForContent(newer)
            dao.restoreIfAbsent(original)
            assertEquals(newer, dao.findByContentKey(original.contentKey))
        } finally {
            database.close()
        }
    }

    private fun createVersion2Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS `favorite` (
                                `key` TEXT NOT NULL,
                                `name` TEXT NOT NULL,
                                `pic` TEXT,
                                `sourceName` TEXT NOT NULL,
                                `savedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`key`)
                            )""".trimIndent()
                        )
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS `watch_progress` (
                                `key` TEXT NOT NULL,
                                `sourceName` TEXT NOT NULL,
                                `playSourceName` TEXT NOT NULL,
                                `episodeIndex` INTEGER NOT NULL,
                                `episodeName` TEXT NOT NULL,
                                `positionMs` INTEGER NOT NULL,
                                `durationMs` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `name` TEXT NOT NULL DEFAULT '',
                                `pic` TEXT,
                                PRIMARY KEY(`key`)
                            )""".trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            helper.writableDatabase.apply {
                execSQL(
                    """INSERT INTO favorite
                        (`key`, `name`, `pic`, `sourceName`, `savedAt`)
                        VALUES ('dbzy|101', '庆余年', NULL, '豆瓣资源', 10)""".trimIndent()
                )
                execSQL(
                    """INSERT INTO watch_progress
                        (`key`, `sourceName`, `playSourceName`, `episodeIndex`,
                         `episodeName`, `positionMs`, `durationMs`, `updatedAt`, `name`, `pic`)
                        VALUES (
                            'dbzy|101', '豆瓣资源', '默认', 2,
                            '第3集', 60000, 120000, 20, '庆余年', NULL
                        )""".trimIndent()
                )
            }
        } finally {
            helper.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}
