package com.untr.medeo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentIdentityTest {
    @Test
    fun contentKey_normalizesNameAndYear() {
        assertEquals("庆余年第二季|2024", contentKey("庆 余-年（第二季）", " 2024 "))
    }

    @Test
    fun contentKey_isSharedAcrossSourcesForSameTitleAndYear() {
        val first = item(sourceId = "dbzy", vodId = 1, year = "2024")
        val second = item(sourceId = "dytt", vodId = 99, year = "2024")

        assertEquals(first.contentKey, second.contentKey)
        assertNotEquals(first.key, second.key)
    }

    @Test
    fun contentKey_keepsRemakesWithDifferentYearsSeparate() {
        assertNotEquals(contentKey("无间道", "2002"), contentKey("无间道", "2026"))
    }

    private fun item(sourceId: String, vodId: Long, year: String) =
        VodItem(
            sourceId = sourceId,
            sourceName = sourceId,
            vodId = vodId,
            name = "庆余年",
            pic = null,
            year = year,
            area = null,
            typeName = null,
            remarks = null
        )
}
