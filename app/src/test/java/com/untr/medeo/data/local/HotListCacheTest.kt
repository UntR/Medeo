package com.untr.medeo.data.local

import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.HotListResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotListCacheTest {
    @Test
    fun toCachePayload_roundTripsHotListResult() {
        val result = HotListResult(
            items = listOf(
                HotListItem(
                    id = "douban-1",
                    rank = 1,
                    title = "庆余年",
                    posterUrl = "https://example.com/poster.jpg",
                    rating = 8.1,
                    ratingCount = 1000,
                    subtitle = "国产剧",
                    year = "2024",
                    isNew = true,
                    episodesInfo = "第40集",
                    doubanUri = "douban://movie/1"
                )
            ),
            categoryFilters = listOf(HotFilter("热门", "热门")),
            typeFilters = listOf(HotFilter("全部", "热门", "全部"))
        )

        val payload = result.toCachePayload(
            category = "热门",
            type = "全部",
            nowMs = 100L
        )

        assertEquals("热门", payload.category)
        assertEquals("全部", payload.type)
        assertEquals(result, payload.toHotListResult())
    }

    @Test
    fun isFresh_respectsOneWeekTtl() {
        val payload = HotListCachePayload(
            ts = 1_000L,
            category = "热门",
            type = "全部"
        )

        assertTrue(payload.isFresh(nowMs = 1_000L + HOT_LIST_CACHE_TTL_MS))
        assertFalse(payload.isFresh(nowMs = 1_001L + HOT_LIST_CACHE_TTL_MS))
    }
}
