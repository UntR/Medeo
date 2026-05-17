package com.untr.medeo.data.model

import java.util.Locale

data class HotListItem(
    val id: String,
    val rank: Int,
    val title: String,
    val posterUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val subtitle: String?,
    val year: String?,
    val isNew: Boolean,
    val episodesInfo: String?,
    val doubanUri: String?
) {
    val displayRating: String
        get() = rating?.takeIf { it > 0.0 }?.let {
            String.format(Locale.US, "%.1f", it)
        } ?: "暂无评分"

    val displaySubtitle: String
        get() = listOfNotNull(
            year,
            subtitle?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" / ").ifBlank { "暂无条目信息" }
}

data class HotFilter(
    val title: String,
    val category: String,
    val type: String = DEFAULT_HOT_TYPE
)

data class HotListResult(
    val items: List<HotListItem>,
    val categoryFilters: List<HotFilter>,
    val typeFilters: List<HotFilter>
)

const val DEFAULT_HOT_CATEGORY = "热门"
const val DEFAULT_HOT_TYPE = "全部"

val DEFAULT_HOT_CATEGORY_FILTERS = listOf(
    HotFilter(title = "热门", category = "热门"),
    HotFilter(title = "最新", category = "最新"),
    HotFilter(title = "高分", category = "豆瓣高分"),
    HotFilter(title = "冷门", category = "冷门佳片")
)

val DEFAULT_HOT_TYPE_FILTERS = listOf(
    HotFilter(title = "全部", category = DEFAULT_HOT_CATEGORY, type = "全部"),
    HotFilter(title = "华语", category = DEFAULT_HOT_CATEGORY, type = "华语"),
    HotFilter(title = "欧美", category = DEFAULT_HOT_CATEGORY, type = "欧美"),
    HotFilter(title = "韩国", category = DEFAULT_HOT_CATEGORY, type = "韩国"),
    HotFilter(title = "日本", category = DEFAULT_HOT_CATEGORY, type = "日本")
)
