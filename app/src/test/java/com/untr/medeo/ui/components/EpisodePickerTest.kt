package com.untr.medeo.ui.components

import com.untr.medeo.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodePickerTest {
    @Test fun episode83RemainsSameOriginalIndexAfterFilterAndSort() {
        val episodes = (1..100).map { Episode("第${it}集", "https://example.com/$it.m3u8") }
        assertEquals(82, visibleEpisodes(episodes, "83", false).single().index)
        assertEquals(82, visibleEpisodes(episodes, "83", true).single().index)
        assertEquals(99, visibleEpisodes(episodes, "", true).first().index)
    }
    @Test fun specialsAndLongTitlesRemainUnchangedAndSearchable() {
        val title = "特别篇 重聚（上）——演员与导演谈拍摄的故事"
        val episodes = listOf(Episode("第1集", "a"), Episode(title, "b"))
        assertEquals(title, visibleEpisodes(episodes, "重聚", true).single().value.name)
        assertEquals(1, visibleEpisodes(episodes, "特别篇", false).single().index)
    }
    @Test fun descendingSourceIsOrderedWithoutChangingPlaybackIndex() {
        val episodes = (3 downTo 1).map { Episode("EP$it", "$it") }
        assertEquals(listOf(2, 1, 0), visibleEpisodes(episodes, "", false).map { it.index })
    }
}
