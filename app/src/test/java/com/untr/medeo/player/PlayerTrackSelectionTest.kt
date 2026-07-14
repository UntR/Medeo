package com.untr.medeo.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerTrackSelectionTest {
    @Test
    fun `prefers an exact language tag`() {
        val index = findMatchingTrackLanguage(
            preferredLanguage = "zh-Hant",
            availableLanguages = listOf("en", "zh-Hans", "zh-Hant")
        )

        assertEquals(2, index)
    }

    @Test
    fun `falls back to the same base language`() {
        val index = findMatchingTrackLanguage(
            preferredLanguage = "en-US",
            availableLanguages = listOf("zh", "en-GB")
        )

        assertEquals(1, index)
    }

    @Test
    fun `does not reuse a missing or unknown language`() {
        assertNull(findMatchingTrackLanguage("ja", listOf("zh", "en")))
        assertNull(findMatchingTrackLanguage(null, listOf("zh", "en")))
        assertNull(findMatchingTrackLanguage("und", listOf("zh", "en")))
    }
}
