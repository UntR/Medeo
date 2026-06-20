package com.untr.medeo.ui.search

import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.repo.aggregateSearchResults
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultSelectionTest {
    @Test
    fun matchingResultFor_findsResultWhenSelectedItemIsSecondarySource() {
        val primary = item(sourceId = "dbzy", sourceName = "豆瓣资源", vodId = 1)
        val secondary = item(sourceId = "dytt", sourceName = "电影天堂", vodId = 2)
        val result = AggregatedResult(
            dedupKey = primary.dedupKey,
            primary = primary,
            perSource = listOf(primary, secondary)
        )

        assertEquals(result, listOf(result).matchingResultFor(secondary))
    }

    @Test
    fun aggregateSearchResults_sortsExactTitleBeforePrefixMatch() {
        val prefix = item(
            sourceId = "dbzy",
            sourceName = "豆瓣资源",
            vodId = 1,
            name = "庆余年 番外"
        )
        val exact = item(
            sourceId = "dytt",
            sourceName = "电影天堂",
            vodId = 2,
            name = "庆余年"
        )

        val results = aggregateSearchResults(
            items = listOf(prefix, exact),
            query = "庆余年",
            playbackPriority = { sourceId -> if (sourceId == "dbzy") 0 else 1 }
        )

        assertEquals("庆余年", results.first().primary.name)
    }

    private fun item(
        sourceId: String,
        sourceName: String,
        vodId: Long,
        name: String = "庆余年"
    ): VodItem = VodItem(
        sourceId = sourceId,
        sourceName = sourceName,
        vodId = vodId,
        name = name,
        pic = null,
        year = "2024",
        area = null,
        typeName = "国产剧",
        remarks = null
    )
}
