package com.czpn7.ying.data.repo

import com.czpn7.ying.data.local.ProgressDao
import com.czpn7.ying.data.local.WatchProgress
import com.czpn7.ying.data.model.VodDetail
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao
) {
    fun observeAllByKey(): Flow<Map<String, WatchProgress>> =
        progressDao.observeAll().map { progresses -> progresses.associateBy { it.key } }

    fun observeProgress(key: String): Flow<WatchProgress?> =
        progressDao.observeProgress(key)

    suspend fun save(
        detail: VodDetail,
        playSourceName: String,
        episodeIndex: Int,
        episodeName: String,
        positionMs: Long,
        durationMs: Long
    ) {
        progressDao.upsert(
            WatchProgress(
                key = detail.item.key,
                sourceName = detail.item.sourceName,
                playSourceName = playSourceName,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
