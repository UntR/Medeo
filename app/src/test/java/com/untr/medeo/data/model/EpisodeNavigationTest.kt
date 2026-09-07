package com.untr.medeo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeNavigationTest {
    @Test fun matchesEpisodeAcrossMissingEpisodesAndInsertedPreview() {
        assertEquals(3, matchingEpisodeIndex(episodes("预告", "EP01", "EP02", "EP03"), "第03集"))
        assertEquals(1, matchingEpisodeIndex(episodes("EP01", "EP03"), "第3集"))
    }

    @Test fun matchesReversedCatalogueAndFindsChronologicalNext() {
        val episodes = episodes("EP03", "EP02", "EP01")
        assertEquals(1, matchingEpisodeIndex(episodes, "第2集"))
        assertEquals(0, adjacentEpisodeIndex(episodes, 1, 1))
        assertEquals(2, adjacentEpisodeIndex(episodes, 1, -1))
    }

    @Test fun neverFallsBackToIndexWhenTargetEpisodeIsMissing() {
        assertNull(matchingEpisodeIndex(episodes("EP01", "EP02"), "EP03"))
        assertNull(adjacentEpisodeIndex(episodes("EP01", "EP03"), 0, 1))
    }

    @Test fun rejectsAmbiguousNamesAndNumbers() {
        assertNull(matchingEpisodeIndex(episodes("EP01", "EP01"), "EP01"))
        assertNull(matchingEpisodeIndex(episodes("EP01", "第1集"), "E1"))
    }

    @Test fun doesNotConfusePreviewsSpecialsPartsOrDatesWithOrdinaryEpisodes() {
        listOf("第1集预告", "第1集上", "第1集下", "特别篇01", "20260907", "S02E01").forEach {
            assertNull("Unexpected number for $it", episodeNumber(it))
            assertNull(matchingEpisodeIndex(episodes(it), "EP01"))
        }
        assertEquals(0, matchingEpisodeIndex(episodes("特别篇·重聚（上）"), "特别篇 重聚(上)"))
    }

    @Test fun movieAndLastEpisodeNeverWrapAround() {
        assertNull(adjacentEpisodeIndex(episodes("正片"), 0, 1))
        assertNull(adjacentEpisodeIndex(episodes("EP01", "EP02"), 1, 1))
        assertNull(adjacentEpisodeIndex(episodes("EP01"), -1, 1))
    }

    @Test fun repeatedLinesAreCountedSeparately() {
        val detail = VodDetail(VodItem("a", "源A", 1, "测试剧", null, "2026", null, "国产剧", null),
            null, null, null, listOf(PlaySource("A", episodes(*Array(12) { "EP${it+1}" })),
                PlaySource("B", episodes(*Array(12) { "EP${it+1}" }))))
        assertEquals("12 集 · 2 条线路", detail.episodeSummary(0))
        assertEquals("12 集 · 2 条线路", detail.episodeSummary(1))
        assertEquals("2 条线路", detail.episodeSummary())
    }

    private fun episodes(vararg names: String) = names.map { Episode(it, "https://example.com/$it.m3u8") }
}
