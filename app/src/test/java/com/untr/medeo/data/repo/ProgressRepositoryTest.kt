package com.untr.medeo.data.repo

import com.untr.medeo.data.local.WatchProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressRepositoryTest {
    @Test
    fun isFinished_returnsTrueAtNinetyPercentBoundary() {
        assertTrue(progress(positionMs = 90_000L, durationMs = 100_000L).isFinished())
    }

    @Test
    fun isFinished_returnsFalseBeforeNinetyPercentBoundary() {
        assertFalse(progress(positionMs = 89_999L, durationMs = 100_000L).isFinished())
    }

    @Test
    fun nextEpisodeIndexIfFinished_onlyReturnsNextEpisodeForFinishedProgress() {
        assertEquals(3, progress(positionMs = 90_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished())
        assertNull(progress(positionMs = 30_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished())
    }

    private fun progress(
        positionMs: Long,
        durationMs: Long
    ): WatchProgress =
        WatchProgress(
            key = "dbzy|1",
            name = "庆余年",
            pic = null,
            sourceName = "豆瓣资源",
            playSourceName = "默认",
            episodeIndex = 2,
            episodeName = "第3集",
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = 1L
        )
}
