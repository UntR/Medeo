package com.untr.medeo.data.local

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotContentType
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

    @Test
    fun collection_keepsMovieAndTvCachesIndependent() {
        val movie = HotListCachePayload(ts = 1L, category = "热门", type = "全部")
        val tv = HotListCachePayload(ts = 2L, category = "tv", type = "tv")
        val collection = HotListCacheCollection()
            .withCache(HotContentType.MOVIE, movie)
            .withCache(HotContentType.TV, tv)
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(HotListCacheCollection::class.java)
        val json = adapter.toJson(collection)
        val decoded = adapter.fromJson(json)

        assertTrue(json.contains("\"MOVIE\""))
        assertTrue(json.contains("\"TV\""))
        assertEquals(movie, decoded?.cacheFor(HotContentType.MOVIE))
        assertEquals(tv, decoded?.cacheFor(HotContentType.TV))
    }

    @Test
    fun legacyCache_isReadAsMovieOnly() {
        val legacy = HotListCachePayload(ts = 1L, category = "热门", type = "全部")
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val legacyAdapter = moshi.adapter(HotListCachePayload::class.java)
        val collection = decodeHotListCacheCollection(
            raw = legacyAdapter.toJson(legacy),
            collectionAdapter = moshi.adapter(HotListCacheCollection::class.java),
            legacyAdapter = legacyAdapter
        )

        assertEquals(legacy, collection.cacheFor(HotContentType.MOVIE))
        assertEquals(null, collection.cacheFor(HotContentType.TV))
    }

    @Test
    fun contentType_defaultsToMovieAndRestoresStoredTv() {
        assertEquals(HotContentType.MOVIE, HotContentType.fromStoredValue(null))
        assertEquals(HotContentType.MOVIE, HotContentType.fromStoredValue("unknown"))
        assertEquals(HotContentType.TV, HotContentType.fromStoredValue("tv"))
    }
}
