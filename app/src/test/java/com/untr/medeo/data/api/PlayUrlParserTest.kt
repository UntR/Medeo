package com.untr.medeo.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUrlParserTest {
    @Test
    fun parse_returnsSourcesAndEpisodesForTypicalPayload() {
        val result = PlayUrlParser.parse(
            playFrom = "ffm3u8\$\$\$kbm3u8",
            playUrl = "第1集\$https://a.com/1.m3u8#第2集\$https://a.com/2.m3u8" +
                "\$\$\$" +
                "第1集\$https://b.com/1.m3u8"
        )

        assertEquals(2, result.size)
        assertEquals("ffm3u8", result[0].name)
        assertEquals(2, result[0].episodes.size)
        assertEquals("第2集", result[0].episodes[1].name)
        assertEquals("https://a.com/2.m3u8", result[0].episodes[1].url)
        assertEquals("kbm3u8", result[1].name)
        assertEquals("https://b.com/1.m3u8", result[1].episodes.single().url)
    }

    @Test
    fun parse_usesFallbackSourceNameWhenPlayFromIsMissing() {
        val result = PlayUrlParser.parse(
            playFrom = null,
            playUrl = "正片\$https://a.com/movie.m3u8"
        )

        assertEquals("线路1", result.single().name)
        assertEquals("正片", result.single().episodes.single().name)
    }

    @Test
    fun parse_filtersMalformedEpisodesAndBlankUrls() {
        val result = PlayUrlParser.parse(
            playFrom = "line",
            playUrl = "bad#第1集\$   #第2集\$https://a.com/2.m3u8"
        )

        assertEquals(1, result.single().episodes.size)
        assertEquals("第2集", result.single().episodes.single().name)
    }

    @Test
    fun parse_keepsDollarSignsInsideEpisodeNameAndUrl() {
        val result = PlayUrlParser.parse(
            playFrom = "line",
            playUrl = "第\$1集\$https://a.com/play.m3u8?token=a\$b"
        )

        assertEquals("第\$1集", result.single().episodes.single().name)
        assertEquals("https://a.com/play.m3u8?token=a\$b", result.single().episodes.single().url)
    }

    @Test
    fun parse_acceptsDoubleDollarAndPipeNameUrlSeparators() {
        val result = PlayUrlParser.parse(
            playFrom = "line",
            playUrl = "第1集\$\$https://a.com/1.m3u8#正片|https://a.com/movie.m3u8"
        )

        assertEquals(2, result.single().episodes.size)
        assertEquals("第1集", result.single().episodes[0].name)
        assertEquals("https://a.com/1.m3u8", result.single().episodes[0].url)
        assertEquals("正片", result.single().episodes[1].name)
        assertEquals("https://a.com/movie.m3u8", result.single().episodes[1].url)
    }

    @Test
    fun parse_returnsEmptyListForBlankPayload() {
        assertTrue(PlayUrlParser.parse(playFrom = "line", playUrl = "").isEmpty())
        assertTrue(PlayUrlParser.parse(playFrom = "line", playUrl = null).isEmpty())
    }
}
