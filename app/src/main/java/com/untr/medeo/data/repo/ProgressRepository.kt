package com.untr.medeo.data.repo

import com.untr.medeo.data.local.ProgressDao
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
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

    suspend fun delete(key: String) {
        progressDao.deleteByKey(key)
    }

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
                name = detail.item.name,
                pic = detail.item.pic,
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

fun WatchProgress.toVodItem(): VodItem {
    val parts = key.split("|", limit = 2)
    val sourceId = parts.getOrNull(0).orEmpty()
    val vodId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return VodItem(
        sourceId = sourceId,
        sourceName = sourceName,
        vodId = vodId,
        name = name.ifBlank { "最近观看" },
        pic = pic,
        year = null,
        area = null,
        typeName = null,
        remarks = "${episodeName} · ${positionMs.formatPlaybackPosition()}"
    )
}

private fun Long.formatPlaybackPosition(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
