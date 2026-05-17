package com.untr.medeo.data.model

fun List<AggregatedResult>.bestHotListMatchFor(item: HotListItem): AggregatedResult? {
    val hotTitle = normalize(item.title)
    if (hotTitle.isBlank()) return null

    return asSequence()
        .mapNotNull { result ->
            val sourceTitle = normalize(result.primary.name)
            if (sourceTitle.isBlank()) return@mapNotNull null

            val titleScore = when {
                sourceTitle == hotTitle -> 100
                sourceTitle.contains(hotTitle) && hotTitle.length >= 2 -> 70
                hotTitle.contains(sourceTitle) && sourceTitle.length >= 2 -> 65
                else -> 0
            }
            if (titleScore == 0) return@mapNotNull null

            val hotYear = item.year
            val sourceYear = result.primary.year
            if (!hotYear.isNullOrBlank() && !sourceYear.isNullOrBlank() && hotYear != sourceYear) {
                return@mapNotNull null
            }

            val yearScore = if (!hotYear.isNullOrBlank() && hotYear == sourceYear) 20 else 0
            val score = titleScore + yearScore
            result to score
        }
        .filter { (_, score) -> score >= MIN_HOT_MATCH_SCORE }
        .maxByOrNull { (_, score) -> score }
        ?.first
}

private const val MIN_HOT_MATCH_SCORE = 80
