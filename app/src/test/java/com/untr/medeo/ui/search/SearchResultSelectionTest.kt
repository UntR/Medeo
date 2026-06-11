package com.untr.medeo.ui.search

import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodItem
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

    private fun item(
        sourceId: String,
        sourceName: String,
        vodId: Long
    ): VodItem = VodItem(
        sourceId = sourceId,
        sourceName = sourceName,
        vodId = vodId,
        name = "庆余年",
        pic = null,
        year = "2024",
        area = null,
        typeName = "国产剧",
        remarks = null
    )
}
