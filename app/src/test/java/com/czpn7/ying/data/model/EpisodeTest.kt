package com.czpn7.ying.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeTest {
    @Test
    fun isDirectMedia_acceptsPlayableExtensionsWithQuery() {
        assertTrue(Episode("1", "https://a.com/index.m3u8").isDirectMedia())
        assertTrue(Episode("1", "https://a.com/video.mp4?token=1").isDirectMedia())
    }

    @Test
    fun isDirectMedia_rejectsSharePages() {
        assertFalse(Episode("1", "https://super.ffzy-online6.com/share/abc").isDirectMedia())
    }
}
