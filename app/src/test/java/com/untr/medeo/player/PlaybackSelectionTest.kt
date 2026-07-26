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

    @Test
    fun resolvePlaybackSelection_usesProgressLineWithExplicitNextEpisode() {
        val detail = detail(
            playSources = listOf(
                playSource("线路A", 2),
                playSource("线路B", 3)
            )
        )
        val progress = progress(playSourceName = "线路B", episodeIndex = 1)

        val selection = resolvePlaybackSelection(
            detail = detail,
            requestedPlaySourceIndex = RESUME_PLAYBACK_INDEX,
            requestedEpisodeIndex = 2,
            progress = progress
        )

        assertEquals(PlaybackSelection(playSourceIndex = 1, episodeIndex = 2), selection)
    }

    @Test
    fun resolvePlaybackSelection_mapsEpisodeAfterCrossSourceLineChange() {
        val detail = detail(
            playSources = listOf(
                PlaySource(
                    name = "新线路",
                    episodes = listOf(
                        Episode("预告", "https://example.com/preview.m3u8"),
                        Episode("第3集", "https://example.com/3.m3u8")
                    )
                )
            )
        )
        val progress = progress(
            playSourceName = "旧线路",
            episodeIndex = 2,
            episodeName = "第03集"
        )

        val selection = resolvePlaybackSelection(
            detail = detail,
            requestedPlaySourceIndex = RESUME_PLAYBACK_INDEX,
            requestedEpisodeIndex = RESUME_PLAYBACK_INDEX,
            progress = progress
        )

        assertEquals(PlaybackSelection(playSourceIndex = 0, episodeIndex = 1), selection)
    }

    @Test
    fun resumePositionForSelection_usesMappedEpisodeAcrossSources() {
        val playSource = PlaySource(
            name = "新线路",
            episodes = listOf(
                Episode("预告", "https://example.com/preview.m3u8"),
                Episode("第3集", "https://example.com/3.m3u8")
            )
        )
        val progress = progress(
            playSourceName = "旧线路",
            episodeIndex = 2,
            episodeName = "第03集"
        )

        assertEquals(60_000L, resumePositionForSelection(playSource, 1, progress))
        assertEquals(0L, resumePositionForSelection(playSource, 0, progress))
    }

    @Test
    fun resolveProgressEpisodeIndex_prefersNormalizedEpisodeName() {
        val episodes = listOf(
            Episode("预告", "https://example.com/preview.m3u8"),
            Episode("第3集 正片", "https://example.com/3.m3u8")
        )
        val progress = progress(
            playSourceName = "线路A",
            episodeIndex = 0,
            episodeName = "第3集-正片"
        )

        assertEquals(1, resolveProgressEpisodeIndex(episodes, progress))
    }

    @Test
    fun resolveProgressEpisodeIndex_usesEpisodeNumberBeforeStoredIndex() {
        val episodes = listOf(
            Episode("EP01", "https://example.com/1.m3u8"),
            Episode("EP03", "https://example.com/3.m3u8")
        )
        val progress = progress(
            playSourceName = "线路A",
            episodeIndex = 0,
            episodeName = "第03集"
        )

        assertEquals(1, resolveProgressEpisodeIndex(episodes, progress))
    }

    @Test
    fun resolveProgressEpisodeIndex_clampsStoredIndexAsLastFallback() {
        val progress = progress(
            playSourceName = "线路A",
            episodeIndex = 9,
            episodeName = "特别篇"
        )

        assertEquals(1, resolveProgressEpisodeIndex(playSource("线路A", 2).episodes, progress))
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

    private fun progress(
        playSourceName: String,
        episodeIndex: Int,
        episodeName: String = "第${episodeIndex + 1}集"
    ): WatchProgress =
        WatchProgress(
            contentKey = "庆余年|2024",
            name = "庆余年",
            pic = null,
            year = "2024",
            preferredSourceId = "dbzy",
            preferredVodId = 1,
            preferredSourceName = "豆瓣资源",
            playSourceName = playSourceName,
            episodeIndex = episodeIndex,
            episodeName = episodeName,
            positionMs = 60_000,
            durationMs = 120_000,
            updatedAt = 1
        )
}
