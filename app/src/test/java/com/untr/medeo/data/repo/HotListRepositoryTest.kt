package com.untr.medeo.data.repo

import com.untr.medeo.data.api.DoubanHotApi
import com.untr.medeo.data.api.dto.DoubanHotItemDto
import com.untr.medeo.data.api.dto.DoubanHotResponse
import com.untr.medeo.data.api.dto.DoubanHotTagDto
import com.untr.medeo.data.api.dto.DoubanHotTypeDto
import com.untr.medeo.data.local.HotListCachePayload
import com.untr.medeo.data.local.HotListCacheStorage
import com.untr.medeo.data.model.HotContentType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HotListRepositoryTest {
    @Test
    fun `movie and tv use independent endpoints parameters and caches`() = runTest {
        val api = RecordingDoubanHotApi()
        val storage = InMemoryHotListCacheStorage()
        val repository = HotListRepository(api, storage)

        repository.recentHot(
            contentType = HotContentType.MOVIE,
            category = "热门",
            type = "全部"
        )
        repository.recentHot(
            contentType = HotContentType.TV,
            category = "tv",
            type = "tv"
        )

        assertEquals(
            listOf(
                HotCall(HotContentType.MOVIE, "热门", "全部"),
                HotCall(HotContentType.TV, "tv", "tv")
            ),
            api.calls
        )
        assertEquals("movie-item", storage.values[HotContentType.MOVIE]?.items?.single()?.id)
        assertEquals("tv-item", storage.values[HotContentType.TV]?.items?.single()?.id)
    }

    @Test
    fun `tv filters come from the tv response`() = runTest {
        val repository = HotListRepository(
            api = RecordingDoubanHotApi(),
            cacheStorage = InMemoryHotListCacheStorage()
        )

        val result = repository.recentHot(contentType = HotContentType.TV)

        assertEquals(listOf("tv", "show"), result.categoryFilters.map { it.category })
        assertEquals(listOf("tv", "国产剧"), result.typeFilters.map { it.type })
        assertNotNull(result.items.single())
    }
}

private data class HotCall(
    val contentType: HotContentType,
    val category: String,
    val type: String
)

private class RecordingDoubanHotApi : DoubanHotApi {
    val calls = mutableListOf<HotCall>()

    override suspend fun recentHotMovie(
        start: Int,
        limit: Int,
        category: String,
        type: String
    ): DoubanHotResponse {
        calls += HotCall(HotContentType.MOVIE, category, type)
        return response(
            id = "movie-item",
            tags = listOf(
                DoubanHotTagDto(
                    title = "热门电影",
                    category = "热门",
                    types = listOf(DoubanHotTypeDto("全部", "全部"))
                )
            )
        )
    }

    override suspend fun recentHotTv(
        start: Int,
        limit: Int,
        category: String,
        type: String
    ): DoubanHotResponse {
        calls += HotCall(HotContentType.TV, category, type)
        return response(
            id = "tv-item",
            tags = listOf(
                DoubanHotTagDto(
                    title = "剧集",
                    category = "tv",
                    selected = true,
                    types = listOf(
                        DoubanHotTypeDto("全部", "tv", selected = true),
                        DoubanHotTypeDto("国产剧", "国产剧")
                    )
                ),
                DoubanHotTagDto(
                    title = "综艺",
                    category = "show",
                    types = listOf(DoubanHotTypeDto("全部", "show"))
                )
            )
        )
    }

    private fun response(
        id: String,
        tags: List<DoubanHotTagDto>
    ): DoubanHotResponse = DoubanHotResponse(
        items = listOf(DoubanHotItemDto(id = id, title = id)),
        tags = tags
    )
}

private class InMemoryHotListCacheStorage : HotListCacheStorage {
    val values = mutableMapOf<HotContentType, HotListCachePayload>()

    override suspend fun hotListCache(contentType: HotContentType): HotListCachePayload? =
        values[contentType]

    override suspend fun setHotListCache(
        contentType: HotContentType,
        payload: HotListCachePayload
    ) {
        values[contentType] = payload
    }
}
