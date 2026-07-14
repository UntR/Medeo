package com.untr.medeo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProgressDao {
    @Query(
        """SELECT * FROM watch_progress
           WHERE contentKey = :contentKey
              OR (preferredSourceId = :sourceId AND preferredVodId = :vodId)
           ORDER BY updatedAt DESC
           LIMIT 1"""
    )
    abstract fun observeProgress(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): Flow<WatchProgress?>

    @Query(
        """SELECT * FROM watch_progress
           WHERE contentKey = :contentKey
              OR (preferredSourceId = :sourceId AND preferredVodId = :vodId)
           ORDER BY updatedAt DESC
           LIMIT 1"""
    )
    abstract suspend fun findByIdentity(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE contentKey = :contentKey LIMIT 1")
    abstract suspend fun findByContentKey(contentKey: String): WatchProgress?

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    abstract fun observeAll(): Flow<List<WatchProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(progress: WatchProgress)

    @Query(
        """DELETE FROM watch_progress
           WHERE preferredSourceId = :sourceId
             AND preferredVodId = :vodId
             AND contentKey != :contentKey"""
    )
    protected abstract suspend fun deleteLegacyLocator(
        contentKey: String,
        sourceId: String,
        vodId: Long
    )

    @Transaction
    open suspend fun upsertForContent(progress: WatchProgress) {
        deleteLegacyLocator(
            contentKey = progress.contentKey,
            sourceId = progress.preferredSourceId,
            vodId = progress.preferredVodId
        )
        upsert(progress)
    }

    @Transaction
    open suspend fun replaceIdentity(oldContentKey: String, progress: WatchProgress) {
        upsert(progress)
        if (oldContentKey != progress.contentKey) {
            deleteByContentKey(oldContentKey)
        }
    }

    @Query("DELETE FROM watch_progress WHERE contentKey = :contentKey")
    abstract suspend fun deleteByContentKey(contentKey: String)

    @Query("DELETE FROM watch_progress")
    abstract suspend fun deleteAll()
}
