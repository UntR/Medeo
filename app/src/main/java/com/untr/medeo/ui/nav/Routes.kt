package com.untr.medeo.ui.nav

import com.untr.medeo.player.RESUME_PLAYBACK_INDEX
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_PATTERN = "search?query={query}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    const val DETAIL_PATTERN = "detail/{sourceId}/{vodId}"
    const val PLAYER_PATTERN = "player/{sourceId}/{vodId}/{playSourceIndex}/{episodeIndex}"

    fun search(query: String? = null): String {
        val cleanQuery = query?.trim().orEmpty()
        if (cleanQuery.isBlank()) return SEARCH
        val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        return "$SEARCH?query=$encoded"
    }

    fun detail(sourceId: String, vodId: Long): String = "detail/$sourceId/$vodId"

    fun player(
        sourceId: String,
        vodId: Long,
        playSourceIndex: Int,
        episodeIndex: Int
    ): String = "player/$sourceId/$vodId/$playSourceIndex/$episodeIndex"

    fun resumePlayer(sourceId: String, vodId: Long): String =
        player(sourceId, vodId, RESUME_PLAYBACK_INDEX, RESUME_PLAYBACK_INDEX)
}
