package com.untr.medeo.player

import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import org.junit.Assert.assertFalse
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

    private fun stateWithEpisodes(count: Int): PlayerUiState =
        PlayerUiState(
            loading = false,
            details = listOf(
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
                    playSources = listOf(
                        PlaySource(
                            name = "默认",
                            episodes = (1..count).map { index ->
                                Episode("第${index}集", "https://example.com/$index.m3u8")
                            }
                        )
                    )
                )
            )
        )
}
