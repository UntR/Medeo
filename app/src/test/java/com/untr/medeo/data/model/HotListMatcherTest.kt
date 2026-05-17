package com.untr.medeo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotListMatcherTest {
    @Test
    fun bestHotListMatchFor_acceptsExactTitleWithoutYear() {
        val hot = hotItem(title = "庆余年 第二季", year = null)
        val result = result(name = "庆余年 第二季", year = null)

        assertEquals(result, listOf(result).bestHotListMatchFor(hot))
    }

    @Test
    fun bestHotListMatchFor_rejectsMismatchedYear() {
        val hot = hotItem(title = "沙丘", year = "2021")
        val result = result(name = "沙丘", year = "1984")

        assertNull(listOf(result).bestHotListMatchFor(hot))
    }

    @Test
    fun bestHotListMatchFor_doesNotFallbackToFirstUnrelatedResult() {
        val hot = hotItem(title = "流浪地球", year = "2019")
        val unrelated = result(name = "流浪狗", year = "2019")

        assertNull(listOf(unrelated).bestHotListMatchFor(hot))
    }

    @Test
    fun bestHotListMatchFor_allowsContainedTitleWhenYearMatches() {
        val hot = hotItem(title = "庆余年 第二季", year = "2024")
        val result = result(name = "庆余年 第二季 第01集", year = "2024")

        assertEquals(result, listOf(result).bestHotListMatchFor(hot))
    }

    @Test
    fun bestHotListMatchFor_prefersYearMatchedContainedTitleOverExactWrongYear() {
        val hot = hotItem(title = "沙丘", year = "2021")
        val wrongYear = result(name = "沙丘", year = "1984")
        val yearMatched = result(name = "沙丘 上", year = "2021")

        assertEquals(yearMatched, listOf(wrongYear, yearMatched).bestHotListMatchFor(hot))
    }

    private fun hotItem(
        title: String,
        year: String?
    ): HotListItem = HotListItem(
        id = title,
        rank = 1,
        title = title,
        posterUrl = null,
        rating = null,
        ratingCount = null,
        subtitle = null,
        year = year,
        isNew = false,
        episodesInfo = null,
        doubanUri = null
    )

    private fun result(
        name: String,
        year: String?
    ): AggregatedResult {
        val item = VodItem(
            sourceId = "dbzy",
            sourceName = "豆瓣资源",
            vodId = name.hashCode().toLong(),
            name = name,
            pic = null,
            year = year,
            area = null,
            typeName = null,
            remarks = null
        )
        return AggregatedResult(
            dedupKey = item.dedupKey,
            primary = item,
            perSource = listOf(item)
        )
    }
}
