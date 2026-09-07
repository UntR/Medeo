package com.untr.medeo.ui.detail

import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.episodeNumber
import com.untr.medeo.data.model.matchingEpisodeIndex
import com.untr.medeo.data.repo.isFinished
import com.untr.medeo.data.repo.nextEpisodeIndexIfFinished

internal data class DetailPlaybackAction(val label: String, val episodeIndex: Int?, val status: String? = null)

internal fun detailPlaybackAction(detail: VodDetail, lineIndex: Int, progress: WatchProgress?): DetailPlaybackAction {
    val episodes = detail.playSources.getOrNull(lineIndex)?.episodes.orEmpty()
    if (episodes.isEmpty()) return DetailPlaybackAction("暂无可播放线路", null)
    if (progress == null) {
        val first = episodes.indices.minByOrNull { episodeNumber(episodes[it].name) ?: Int.MAX_VALUE } ?: 0
        return DetailPlaybackAction("播放 ${episodes[first].name}", first)
    }
    val current = matchingEpisodeIndex(episodes, progress.episodeName)
        ?: return DetailPlaybackAction("请选择集数", null, "当前线路未能匹配 ${progress.episodeName}，请手动选集")
    if (!progress.isFinished()) return DetailPlaybackAction("继续观看 ${episodes[current].name}", current)
    val next = progress.nextEpisodeIndexIfFinished(episodes)
    if (next != null) return DetailPlaybackAction("看下一集 ${episodes[next].name}", next, "${progress.episodeName} 接近看完")
    val currentNumber = episodeNumber(episodes[current].name)
    val status = when {
        episodes.size == 1 && currentNumber == null -> "本片接近看完"
        currentNumber != null && episodes.all { (episodeNumber(it.name) ?: 0) <= currentNumber } -> "当前线路已追平，尚未确认全剧完结"
        else -> "本集接近看完，当前线路没有可确认的下一集"
    }
    return DetailPlaybackAction("继续观看 ${episodes[current].name}", current, status)
}
