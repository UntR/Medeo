package com.untr.medeo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY savedAt DESC")
    abstract fun observeAll(): Flow<List<Favorite>>

    @Query(
        """SELECT EXISTS(
            SELECT 1 FROM favorite
            WHERE contentKey = :contentKey
               OR (preferredSourceId = :sourceId AND preferredVodId = :vodId)
        )"""
    )
    abstract fun observeIsFavorite(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): Flow<Boolean>

    @Query(
        """SELECT * FROM favorite
           WHERE contentKey = :contentKey
              OR (preferredSourceId = :sourceId AND preferredVodId = :vodId)
           ORDER BY CASE WHEN contentKey = :contentKey THEN 0 ELSE 1 END
           LIMIT 1"""
    )
    abstract suspend fun findByIdentity(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): Favorite?

    @Query("SELECT * FROM favorite WHERE contentKey = :contentKey LIMIT 1")
    abstract suspend fun findByContentKey(contentKey: String): Favorite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(favorite: Favorite)

    @Query(
        """DELETE FROM favorite
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
    open suspend fun upsertForContent(favorite: Favorite) {
        deleteLegacyLocator(
            contentKey = favorite.contentKey,
            sourceId = favorite.preferredSourceId,
            vodId = favorite.preferredVodId
        )
        upsert(favorite)
    }

    @Query("DELETE FROM favorite WHERE contentKey = :contentKey")
    protected abstract suspend fun deleteByContentKey(contentKey: String)

    @Transaction
    open suspend fun replaceIdentity(oldContentKey: String, favorite: Favorite) {
        upsert(favorite)
        if (oldContentKey != favorite.contentKey) {
            deleteByContentKey(oldContentKey)
        }
    }

    @Query(
        """DELETE FROM favorite
           WHERE contentKey = :contentKey
              OR (preferredSourceId = :sourceId AND preferredVodId = :vodId)"""
    )
    abstract suspend fun deleteByIdentity(
        contentKey: String,
        sourceId: String,
        vodId: Long
    )
}
