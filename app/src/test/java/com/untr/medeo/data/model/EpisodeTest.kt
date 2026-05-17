package com.untr.medeo.data.model

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
    fun isDirectMedia_keepsExtensionlessSignedHttpCandidates() {
        assertTrue(Episode("1", "https://media.example.com/play/abc123?sign=1").isDirectMedia())
    }

    @Test
    fun isDirectMedia_keepsUrlsWithUnencodedPathCharacters() {
        assertTrue(Episode("1", "https://media.example.com/video file.m3u8").isDirectMedia())
        assertTrue(Episode("1", "https://media.example.com/video{1}.m3u8").isDirectMedia())
    }

    @Test
    fun isDirectMedia_rejectsSharePages() {
        assertFalse(Episode("1", "https://super.ffzy-online6.com/share/abc").isDirectMedia())
    }

    @Test
    fun isDirectMedia_rejectsKnownNonMediaUrls() {
        assertFalse(Episode("1", "ftp://a.com/video.m3u8").isDirectMedia())
        assertFalse(Episode("1", "https://a.com/index.html").isDirectMedia())
        assertFalse(Episode("1", "https://pan.baidu.com/s/abc").isDirectMedia())
    }
}
