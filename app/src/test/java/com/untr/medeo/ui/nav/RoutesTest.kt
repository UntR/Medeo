package com.untr.medeo.ui.nav

import com.untr.medeo.player.RESUME_PLAYBACK_INDEX
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun search_encodesQueryAsOptionalRouteArgument() {
        assertEquals(
            "search?query=Dune%20Part%202",
            Routes.search("Dune Part 2")
        )
    }

    @Test
    fun resumePlayer_usesResumePlaybackIndexForLineAndEpisode() {
        assertEquals(
            "player/dbzy/1/$RESUME_PLAYBACK_INDEX/$RESUME_PLAYBACK_INDEX",
            Routes.resumePlayer(sourceId = "dbzy", vodId = 1)
        )
    }
}
