package com.untr.medeo.data.api

import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource

object PlayUrlParser {
    private const val SOURCE_SEPARATOR = "\$\$\$"
    private const val EPISODE_SEPARATOR = "#"
    private val NAME_URL_SEPARATORS = listOf("\$\$", "\$", "|")
    private val HTTP_URL_PATTERN = Regex("https?://", RegexOption.IGNORE_CASE)

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

            val episodes = chunk.split(EPISODE_SEPARATOR).mapNotNull(::parseEpisode)

            PlaySource(sourceName, episodes)
        }.filter { it.episodes.isNotEmpty() }
    }

    private fun parseEpisode(rawEpisode: String): Episode? {
        val raw = rawEpisode.trim()
        if (raw.isBlank()) return null

        HTTP_URL_PATTERN.find(raw)?.let { match ->
            val name = raw.substring(0, match.range.first)
                .trim()
                .trimEnd('$', '|')
                .ifBlank { "未命名" }
            val url = raw.substring(match.range.first).trim()
            return Episode(name = name, url = url)
        }

        NAME_URL_SEPARATORS.forEach { separator ->
            val parts = raw.split(separator, limit = 2)
            val url = parts.getOrNull(1)?.trim()
            if (parts.size == 2 && !url.isNullOrBlank()) {
                return Episode(
                    name = parts[0].trim().ifBlank { "未命名" },
                    url = url
                )
            }
        }

        return null
    }
}
