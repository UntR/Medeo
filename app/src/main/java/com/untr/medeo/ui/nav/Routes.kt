package com.untr.medeo.ui.nav

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    const val DETAIL_PATTERN = "detail/{sourceId}/{vodId}"
    const val PLAYER_PATTERN = "player/{sourceId}/{vodId}/{playSourceIndex}/{episodeIndex}"

    fun detail(sourceId: String, vodId: Long): String = "detail/$sourceId/$vodId"

    fun player(
        sourceId: String,
        vodId: Long,
        playSourceIndex: Int,
        episodeIndex: Int
    ): String = "player/$sourceId/$vodId/$playSourceIndex/$episodeIndex"
}
