package com.untr.medeo.ui.favorites

import com.untr.medeo.data.local.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {
    @Test fun eleventhRecordCanBeFoundWithoutTruncation() {
        val records = (11 downTo 1).map { RecentWatchItem(progress("记录$it", it.toLong())) }
        assertEquals(11, filterWatchHistory(records, "").size)
        assertEquals("记录1", filterWatchHistory(records, "记录1").last().item.name)
    }
    @Test fun historicalCompletionDoesNotPromiseNextWithoutCatalogue() {
        val record = RecentWatchItem(progress("电影", 1).copy(positionMs = 95_000))
        assertEquals("查看选集", record.actionLabel)
        assertTrue(record.item.remarks!!.contains("本集接近看完"))
    }
    private fun progress(name: String, id: Long) = WatchProgress(
        "$name|2026", name, null, "2026", "a", id, "源A", "A", 0, "正片", 30_000, 100_000, id)
}
