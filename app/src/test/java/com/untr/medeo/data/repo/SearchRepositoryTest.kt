package com.untr.medeo.data.repo

import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.api.dto.VodItemDto
import com.untr.medeo.data.api.dto.VodListResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {
    private val source = VodSource(
        id = "test",
        name = "测试源",
        baseUrl = "https://example.com/api.php/provide/vod",
        defaultEnabled = true
    )

    @Test
    fun businessErrorIsFailedAndDropsPayload() {
        val previousState = SourcePageState(currentPage = 1, pageCount = 3)
        val response = VodListResponse(
            code = 1002,
            msg = "search unavailable",
            page = 2,
            pagecount = 5,
            list = listOf(itemDto())
        )

        val result = response.toSourceSearchResult(
            source = source,
            requestedPage = 2,
            previousState = previousState
        )

        assertTrue(result.failed)
        assertTrue(result.items.isEmpty())
        assertEquals(previousState, result.pageState)
    }

    @Test
    fun businessErrorWithoutPreviousPageStopsPagination() {
        val result = VodListResponse(code = 0).toSourceSearchResult(
            source = source,
            requestedPage = 2,
            previousState = null
        )

        assertTrue(result.failed)
        assertEquals(SourcePageState(currentPage = 2, pageCount = 2), result.pageState)
        assertFalse(result.pageState.hasMore)
    }

    @Test
    fun successfulResponseMapsItemsAndPagination() {
        val result = VodListResponse(
            code = 1,
            page = 2,
            pagecount = 4,
            list = listOf(itemDto())
        ).toSourceSearchResult(
            source = source,
            requestedPage = 2,
            previousState = null
        )

        assertFalse(result.failed)
        assertEquals(1, result.items.size)
        assertEquals("test", result.items.single().sourceId)
        assertEquals(42L, result.items.single().vodId)
        assertEquals("庆余年", result.items.single().name)
        assertEquals(SourcePageState(currentPage = 2, pageCount = 4), result.pageState)
        assertTrue(result.pageState.hasMore)
    }

    private fun itemDto(): VodItemDto = VodItemDto(
        vodId = 42L,
        vodName = "庆余年",
        vodYear = "2024",
        typeName = "国产剧"
    )
}
