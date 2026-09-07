package com.untr.medeo.data.model

/** Only whole, ordinary episode labels qualify for numeric matching. */
fun episodeNumber(name: String): Int? {
    val patterns = listOf(
        Regex("第\\s*(\\d+)\\s*[集话期]", RegexOption.IGNORE_CASE),
        Regex("(?:EP?|集)\\s*(\\d+)", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,4})")
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull()
    }
}

fun matchingEpisodeIndex(episodes: List<Episode>, name: String): Int? {
    val normalized = normalize(name)
    if (normalized.isBlank()) return null
    val exact = episodes.indices.filter { normalize(episodes[it].name) == normalized }
    if (exact.isNotEmpty()) return exact.singleOrNull()
    val number = episodeNumber(name) ?: return null
    return episodes.indices.filter { episodeNumber(episodes[it].name) == number }.singleOrNull()
}

fun adjacentEpisodeIndex(episodes: List<Episode>, index: Int, offset: Int): Int? {
    val number = episodes.getOrNull(index)?.name?.let(::episodeNumber) ?: return null
    return episodes.indices.filter {
        episodeNumber(episodes[it].name)?.toLong() == number.toLong() + offset
    }.singleOrNull()
}

fun VodDetail.episodeSummary(lineIndex: Int? = null): String {
    val lineCount = "${playSources.size} 条线路"
    val source = lineIndex?.let(playSources::getOrNull) ?: return lineCount
    return "${source.episodes.size} 集 · $lineCount"
}
