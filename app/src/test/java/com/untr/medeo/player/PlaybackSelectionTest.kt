package com.untr.medeo.player

import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSelectionTest {
    @Test
    fun resolvePlaybackSelection_usesProgressLineAndEpisodeForResumeRequest() {
        val detail = detail(
            playSources = listOf(
                playSource("线路A", 2),
                playSource("线路B", 3)
            )
        )
        val progress = progress(playSourceName = "线路B", episodeIndex = 2)

        val selection = resolvePlaybackSelection(
            detail = detail,
            requestedPlaySourceIndex = RESUME_PLAYBACK_INDEX,
            requestedEpisodeIndex = RESUME_PLAYBACK_INDEX,
            progress = progress
        )

        assertEquals(PlaybackSelection(playSourceIndex = 1, episodeIndex = 2), selection)
    }

    @Test
    fun resolvePlaybackSelection_keepsExplicitRouteSelection() {
        val detail = detail(
            playSources = listOf(
                playSource("线路A", 2),
                playSource("线路B", 3)
            )
        )
        val progress = progress(playSourceName = "线路B", episodeIndex = 2)

        val selection = resolvePlaybackSelection(
            detail = detail,
            requestedPlaySourceIndex = 0,
            requestedEpisodeIndex = 1,
            progress = progress
        )

        assertEquals(PlaybackSelection(playSourceIndex = 0, episodeIndex = 1), selection)
    }

    private fun detail(playSources: List<PlaySource>): VodDetail =
        VodDetail(
            item = VodItem(
                sourceId = "dbzy",
                sourceName = "豆瓣资源",
                vodId = 1,
                name = "庆余年",
                pic = null,
                year = "2024",
                area = null,
                typeName = "国产剧",
                remarks = null
            ),
            content = null,
            actor = null,
            director = null,
            playSources = playSources
        )

    private fun playSource(name: String, episodeCount: Int): PlaySource =
        PlaySource(
            name = name,
            episodes = (1..episodeCount).map { index ->
                Episode("第${index}集", "https://example.com/$name/$index.m3u8")
            }
        )

    private fun progress(playSourceName: String, episodeIndex: Int): WatchProgress =
        WatchProgress(
            key = "dbzy|1",
            name = "庆余年",
            pic = null,
            sourceName = "豆瓣资源",
            playSourceName = playSourceName,
            episodeIndex = episodeIndex,
            episodeName = "第${episodeIndex + 1}集",
            positionMs = 60_000,
            durationMs = 120_000,
            updatedAt = 1
        )
}
