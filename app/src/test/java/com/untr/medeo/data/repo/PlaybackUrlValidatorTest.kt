package com.untr.medeo.data.repo

import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlValidatorTest {
    @Test
    fun successfulHtmlResponseIsNotPlayable() {
        val result = evaluateSuccessfulPlaybackResponse(
            url = "https://example.com/play/123",
            contentType = "text/html; charset=utf-8",
            bodyPrefix = "<!doctype html><html><body>player</body></html>"
        )

        assertFalse(result.playable)
        assertEquals("返回内容是网页，不是媒体流", result.reason)
    }

    @Test
    fun extensionlessHlsResponseIsPlayable() {
        val result = evaluateSuccessfulPlaybackResponse(
            url = "https://media.example.com/play/123?token=1",
            contentType = "text/plain",
            bodyPrefix = "#EXTM3U\n#EXT-X-VERSION:3"
        )

        assertTrue(result.playable)
        assertNull(result.reason)
    }

    @Test
    fun opaqueExtensionlessResponseRemainsCandidate() {
        val result = evaluateSuccessfulPlaybackResponse(
            url = "https://media.example.com/signed/123?token=1",
            contentType = "application/octet-stream",
            bodyPrefix = ""
        )

        assertTrue(result.playable)
    }

    @Test
    fun m3u8UrlWithoutPlaylistBodyIsNotPlayable() {
        val result = evaluateSuccessfulPlaybackResponse(
            url = "https://media.example.com/index.m3u8",
            contentType = "application/octet-stream",
            bodyPrefix = "not a playlist"
        )

        assertFalse(result.playable)
        assertEquals("返回内容不是 HLS 播放列表", result.reason)
    }

    @Test
    fun explicitMediaLinesArePrioritizedStably() {
        val ambiguous = source("网页候选", "https://example.com/play/123")
        val hls = source("HLS", "https://example.com/1.m3u8")
        val mp4 = source("MP4", "https://example.com/1.mp4?token=1")

        val result = prioritizeKnownMediaLines(listOf(ambiguous, hls, mp4))

        assertEquals(listOf("HLS", "MP4", "网页候选"), result.map { it.name })
    }

    @Test
    fun failedPreflightLinesAreRemoved() {
        val invalid = source("网页候选", "https://example.com/play/123")
        val valid = source("媒体候选", "https://example.com/media/456")

        val result = applyPlaybackValidations(
            playSources = listOf(invalid, valid),
            validations = listOf(
                PlaybackValidation(false, "返回内容是网页，不是媒体流"),
                PlaybackValidation(true)
            )
        )

        assertEquals(listOf("媒体候选"), result.playSources.map { it.name })
        assertEquals("已排除 1 条未通过媒体预检的线路", result.issue)
    }

    @Test
    fun allFailedPreflightLinesReturnEmptyResult() {
        val result = applyPlaybackValidations(
            playSources = listOf(
                source("线路一", "https://example.com/play/1"),
                source("线路二", "https://example.com/play/2")
            ),
            validations = listOf(
                PlaybackValidation(false),
                PlaybackValidation(false)
            )
        )

        assertTrue(result.playSources.isEmpty())
        assertEquals("所有候选线路均未通过媒体预检", result.issue)
    }


    @Test
    fun preflightPrioritizesKnownMediaWithoutNetworkProbe() = runTest {
        val validator = PlaybackUrlValidator(OkHttpClient())
        val result = validator.preflight(
            listOf(
                source("网页候选", "https://example.invalid/play/1"),
                source("HLS", "https://example.invalid/1.m3u8")
            )
        )

        assertEquals(listOf("HLS", "网页候选"), result.playSources.map { it.name })
        assertNull(result.issue)
    }
    private fun source(name: String, url: String): PlaySource =
        PlaySource(
            name = name,
            episodes = listOf(Episode(name = "第1集", url = url))
        )
}
