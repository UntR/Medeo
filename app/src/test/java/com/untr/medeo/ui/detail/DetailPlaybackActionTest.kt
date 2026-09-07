package com.untr.medeo.ui.detail

import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import org.junit.Assert.*
import org.junit.Test

class DetailPlaybackActionTest {
    @Test fun firstPlayAndResumeUseVisibleLine() {
        assertEquals("播放 EP01", detailPlaybackAction(detail("EP01", "EP02"), 0, null).label)
        assertEquals("继续观看 EP02", detailPlaybackAction(detail("EP01", "EP02"), 0, progress("EP02", 30_000)).label)
    }
    @Test fun finishedMovieStillOffersCurrentFilmWithoutPromisingNext() {
        val action = detailPlaybackAction(detail("正片"), 0, progress("正片"))
        assertEquals(0, action.episodeIndex)
        assertEquals("本片接近看完", action.status)
        assertFalse(action.label.contains("下一集"))
    }
    @Test fun latestEpisodeCanBecomeNextAfterAnUpdate() {
        val progress = progress("EP02")
        val caughtUp = detailPlaybackAction(detail("EP01", "EP02"), 0, progress)
        assertTrue(caughtUp.status!!.contains("当前线路已追平"))
        assertEquals("看下一集 EP03", detailPlaybackAction(detail("EP01", "EP02", "EP03"), 0, progress).label)
    }
    @Test fun missingEpisodeRequiresExplicitChoice() {
        val action = detailPlaybackAction(detail("EP01", "EP03"), 0, progress("EP02"))
        assertNull(action.episodeIndex)
        assertEquals("请选择集数", action.label)
    }
    @Test fun reversedAndPreviewInsertedLineUsesNamesNotStoredIndices() {
        val action = detailPlaybackAction(detail("预告", "EP03", "EP02", "EP01"), 0, progress("第2集"))
        assertEquals(1, action.episodeIndex)
        assertEquals("看下一集 EP03", action.label)
    }
    @Test fun missingNextDoesNotSkipAcrossGap() {
        val action = detailPlaybackAction(detail("EP01", "EP03"), 0, progress("EP01"))
        assertEquals(0, action.episodeIndex)
        assertFalse(action.label.contains("下一集"))
    }
    private fun detail(vararg names: String) = VodDetail(
        VodItem("a", "源A", 1, "测试", null, "2026", null, null, null), null, null, null,
        listOf(PlaySource("A", names.map { Episode(it, "https://example.com/$it.m3u8") })))
    private fun progress(name: String, position: Long = 95_000) = WatchProgress(
        "测试|2026", "测试", null, "2026", "a", 1, "源A", "A", 99, name, position, 100_000, 1)
}
