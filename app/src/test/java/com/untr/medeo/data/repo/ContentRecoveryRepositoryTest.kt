package com.untr.medeo.data.repo

import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRecoveryRepositoryTest {
    @Test
    fun recoverContent_returnsNoEnabledSourcesWithoutNetworkWork() = runTest {
        var called = false

        val result = recoverContent(
            original = item("dbzy", 1),
            enabledSources = emptyList(),
            loadDetails = {
                called = true
                emptyList()
            },
            search = {
                called = true
                emptyList()
            }
        )

        assertSame(ContentRecoveryResult.NoEnabledSources, result)
        assertFalse(called)
    }

    @Test
    fun recoverContent_usesPlayableOriginalBeforeSearching() = runTest {
        val original = item("dbzy", 1)
        var searched = false

        val result = recoverContent(
            original = original,
            enabledSources = listOf(source("dbzy")),
            loadDetails = { candidates -> listOf(detail(candidates.single())) },
            search = {
                searched = true
                emptyList()
            }
        )

        assertTrue(result is ContentRecoveryResult.Success && !result.usedFallback)
        assertFalse(searched)
    }

    @Test
    fun recoverContent_recoversFromAnotherEnabledSourceWhenOriginalIsDisabled() = runTest {
        val original = item("dbzy", 1)
        val recovered = item("dytt", 9)

        val result = recoverContent(
            original = original,
            enabledSources = listOf(source("dytt")),
            loadDetails = { candidates -> candidates.map(::detail) },
            search = { listOf(aggregate(recovered)) }
        )

        assertTrue(result is ContentRecoveryResult.Success && result.usedFallback)
        assertEquals("dytt|9", (result as ContentRecoveryResult.Success).primary.key)
    }

    @Test
    fun recoverContent_fallsBackAfterOriginalHasNoPlayableDetails() = runTest {
        val original = item("dbzy", 1)
        val recovered = item("dytt", 9)

        val result = recoverContent(
            original = original,
            enabledSources = listOf(source("dbzy"), source("dytt")),
            loadDetails = { candidates ->
                candidates.filterNot { it.key == original.key }.map(::detail)
            },
            search = { listOf(aggregate(recovered)) }
        )

        assertTrue(result is ContentRecoveryResult.Success && result.usedFallback)
    }

    @Test
    fun matchingRecoveryResult_rejectsFuzzyTitleAndWrongYear() {
        val fuzzy = aggregate(item("dbzy", 1, name = "庆余年特别篇", year = "2024"))
        val wrongYear = aggregate(item("dytt", 2, name = "庆余年", year = "2025"))

        val result = matchingRecoveryResult(
            results = listOf(fuzzy, wrongYear),
            name = "庆余年",
            year = "2024"
        )

        assertNull(result)
    }

    private fun source(id: String) =
        VodSource(
            id = id,
            name = id,
            baseUrl = "https://example.com/$id/",
            defaultEnabled = true
        )

    private fun item(
        sourceId: String,
        vodId: Long,
        name: String = "庆余年",
        year: String? = "2024"
    ) = VodItem(
        sourceId = sourceId,
        sourceName = sourceId,
        vodId = vodId,
        name = name,
        pic = null,
        year = year,
        area = null,
        typeName = null,
        remarks = null
    )

    private fun aggregate(item: VodItem) =
        AggregatedResult(
            dedupKey = item.dedupKey,
            primary = item,
            perSource = listOf(item)
        )

    private fun detail(item: VodItem) =
        VodDetail(
            item = item,
            content = null,
            actor = null,
            director = null,
            playSources = listOf(
                PlaySource(
                    name = "默认",
                    episodes = listOf(Episode("第1集", "https://example.com/1.m3u8"))
                )
            )
        )
}
