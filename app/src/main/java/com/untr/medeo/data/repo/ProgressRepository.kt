package com.untr.medeo.data.repo

import com.untr.medeo.data.local.ProgressDao
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.local.preferredKey
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.adjacentEpisodeIndex
import com.untr.medeo.data.model.matchingEpisodeIndex
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao
) {
    fun observeAll(): Flow<List<WatchProgress>> = progressDao.observeAll()

    fun observeAllByKey(): Flow<Map<String, WatchProgress>> =
        progressDao.observeAll().map { progresses ->
            buildMap {
                progresses.forEach { progress ->
                    put(progress.contentKey, progress)
                    putIfAbsent(progress.preferredKey, progress)
                }
            }
        }

    fun observeProgress(item: VodItem): Flow<WatchProgress?> =
        progressDao.observeProgress(
            contentKey = item.contentKey,
            sourceId = item.sourceId,
            vodId = item.vodId
        )

    suspend fun delete(contentKey: String) {
        progressDao.deleteByContentKey(contentKey)
    }

    suspend fun clearAll() {
        progressDao.deleteAll()
    }

    suspend fun restore(progress: WatchProgress) {
        progressDao.restoreIfAbsent(progress)
    }

    suspend fun save(
        detail: VodDetail,
        playSourceName: String,
        episodeIndex: Int,
        episodeName: String,
        positionMs: Long,
        durationMs: Long
    ) {
        progressDao.upsertForContent(
            WatchProgress(
                contentKey = detail.item.contentKey,
                name = detail.item.name,
                pic = detail.item.pic,
                year = detail.item.year,
                preferredSourceId = detail.item.sourceId,
                preferredVodId = detail.item.vodId,
                preferredSourceName = detail.item.sourceName,
                playSourceName = playSourceName,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updatePreferred(original: VodItem, recovered: VodItem) {
        val stored = progressDao.findByIdentity(
            contentKey = original.contentKey,
            sourceId = original.sourceId,
            vodId = original.vodId
        ) ?: return
        val existing = progressDao.findByContentKey(recovered.contentKey)
        val progress = listOfNotNull(stored, existing).maxBy { it.updatedAt }
        progressDao.replaceIdentity(
            oldContentKey = stored.contentKey,
            progress = progress.copy(
                contentKey = recovered.contentKey,
                name = recovered.name,
                pic = recovered.pic ?: progress.pic,
                year = recovered.year,
                preferredSourceId = recovered.sourceId,
                preferredVodId = recovered.vodId,
                preferredSourceName = recovered.sourceName
            )
        )
    }
}

fun WatchProgress.toVodItem(): VodItem {
    val progressText = if (isFinished()) {
        "本集接近看完"
    } else {
        positionMs.formatPlaybackPosition()
    }
    return VodItem(
        sourceId = preferredSourceId,
        sourceName = preferredSourceName,
        vodId = preferredVodId,
        name = name.ifBlank { "最近观看" },
        pic = pic,
        year = year,
        area = null,
        typeName = null,
        remarks = "$episodeName · $progressText"
    )
}

fun WatchProgress.isFinished(threshold: Double = WATCH_COMPLETION_THRESHOLD): Boolean =
    durationMs > 0L && positionMs.toDouble() / durationMs.toDouble() >= threshold

fun WatchProgress.nextEpisodeIndexIfFinished(episodes: List<Episode> = emptyList()): Int? {
    if (!isFinished()) return null
    val currentIndex = matchingEpisodeIndex(episodes, episodeName) ?: return null
    return adjacentEpisodeIndex(episodes, currentIndex, 1)
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

const val WATCH_COMPLETION_THRESHOLD = 0.9
