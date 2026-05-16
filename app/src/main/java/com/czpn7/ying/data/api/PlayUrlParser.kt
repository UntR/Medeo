package com.czpn7.ying.data.api

import com.czpn7.ying.data.model.Episode
import com.czpn7.ying.data.model.PlaySource

object PlayUrlParser {
    private const val SOURCE_SEPARATOR = "\$\$\$"
    private const val EPISODE_SEPARATOR = "#"
    private const val NAME_URL_SEPARATOR = "\$"

    fun parse(playFrom: String?, playUrl: String?): List<PlaySource> {
        if (playUrl.isNullOrBlank()) return emptyList()

        val sourceNames = playFrom.orEmpty().split(SOURCE_SEPARATOR)
        val sourceChunks = playUrl.split(SOURCE_SEPARATOR)

        return sourceChunks.mapIndexed { index, chunk ->
            val sourceName = sourceNames
                .getOrNull(index)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "线路${index + 1}"

            val episodes = chunk.split(EPISODE_SEPARATOR).mapNotNull { rawEpisode ->
                val parts = rawEpisode.split(NAME_URL_SEPARATOR, limit = 2)
                val url = parts.getOrNull(1)?.trim()
                if (parts.size != 2 || url.isNullOrBlank()) {
                    null
                } else {
                    Episode(
                        name = parts[0].trim().ifBlank { "未命名" },
                        url = url
                    )
                }
            }

            PlaySource(sourceName, episodes)
        }.filter { it.episodes.isNotEmpty() }
    }
}
