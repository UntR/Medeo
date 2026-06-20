package com.untr.medeo.player

import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun hasNextEpisode_returnsTrueBeforeLastEpisode() {
        val state = stateWithEpisodes(2)

        assertTrue(state.hasNextEpisode(detailIndex = 0, playSourceIndex = 0, episodeIndex = 0))
    }

    @Test
    fun hasNextEpisode_returnsFalseAtLastEpisode() {
        val state = stateWithEpisodes(2)

        assertFalse(state.hasNextEpisode(detailIndex = 0, playSourceIndex = 0, episodeIndex = 1))
    }

    @Test
    fun mergePlaybackDetails_appendsIncomingWithoutDuplicatingCurrentDetail() {
        val current = detail(sourceId = "dytt", sourceName = "电影天堂", vodId = 2)
        val duplicate = detail(sourceId = "dytt", sourceName = "电影天堂", vodId = 2)
        val incoming = detail(sourceId = "dbzy", sourceName = "豆瓣资源", vodId = 1)

        val merged = mergePlaybackDetails(
            current = listOf(current),
            incoming = listOf(duplicate, incoming)
        )

        assertEquals(listOf("dytt|2", "dbzy|1"), merged.map { it.item.key })
    }

    private fun stateWithEpisodes(count: Int): PlayerUiState =
        PlayerUiState(
            loading = false,
            details = listOf(detail(episodeCount = count))
        )

    private fun detail(
        sourceId: String = "dbzy",
        sourceName: String = "豆瓣资源",
        vodId: Long = 1,
        episodeCount: Int = 2
    ): VodDetail =
        VodDetail(
            item = VodItem(
                sourceId = sourceId,
                sourceName = sourceName,
                vodId = vodId,
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
            playSources = listOf(
                PlaySource(
                    name = "默认",
                    episodes = (1..episodeCount).map { index ->
                        Episode("第${index}集", "https://example.com/$sourceId/$index.m3u8")
                    }
                )
            )
        )
}
