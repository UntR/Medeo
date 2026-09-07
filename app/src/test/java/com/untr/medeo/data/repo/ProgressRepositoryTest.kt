package com.untr.medeo.data.repo

import com.untr.medeo.data.local.ProgressDao
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressRepositoryTest {
    @Test
    fun clearAll_onlyDeletesWatchProgressRows() = runTest {
        val dao = RecordingProgressDao()
        val repository = ProgressRepository(dao)

        repository.clearAll()

        assertEquals(1, dao.deleteAllCalls)
        assertEquals(0, dao.deleteByContentKeyCalls)
    }

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
        val episodes = (1..4).map { Episode("第${it}集", "https://example.com/$it.m3u8") }
        assertEquals(3, progress(positionMs = 90_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished(episodes))
        assertNull(progress(positionMs = 90_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished())
        assertNull(progress(positionMs = 90_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished(episodes.take(3)))
        assertNull(progress(positionMs = 30_000L, durationMs = 100_000L).nextEpisodeIndexIfFinished())
    }

    private fun progress(
        positionMs: Long,
        durationMs: Long
    ): WatchProgress =
        WatchProgress(
            contentKey = "庆余年|2024",
            name = "庆余年",
            pic = null,
            year = "2024",
            preferredSourceId = "dbzy",
            preferredVodId = 1,
            preferredSourceName = "豆瓣资源",
            playSourceName = "默认",
            episodeIndex = 2,
            episodeName = "第3集",
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = 1L
        )
}

private class RecordingProgressDao : ProgressDao() {
    var deleteAllCalls = 0
    var deleteByContentKeyCalls = 0

    override fun observeProgress(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): Flow<WatchProgress?> = emptyFlow()

    override suspend fun findByIdentity(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ): WatchProgress? = null

    override suspend fun findByContentKey(contentKey: String): WatchProgress? = null

    override fun observeAll(): Flow<List<WatchProgress>> = emptyFlow()

    protected override suspend fun upsert(progress: WatchProgress) = Unit

    protected override suspend fun deleteLegacyLocator(
        contentKey: String,
        sourceId: String,
        vodId: Long
    ) = Unit

    override suspend fun deleteByContentKey(contentKey: String) {
        deleteByContentKeyCalls += 1
    }

    override suspend fun deleteAll() {
        deleteAllCalls += 1
    }
}
