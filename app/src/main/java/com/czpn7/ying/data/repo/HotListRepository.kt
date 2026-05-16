package com.czpn7.ying.data.repo

import android.util.Log
import com.czpn7.ying.data.api.DoubanHotApi
import com.czpn7.ying.data.api.dto.DoubanHotItemDto
import com.czpn7.ying.data.api.dto.DoubanHotResponse
import com.czpn7.ying.data.api.dto.DoubanHotTagDto
import com.czpn7.ying.data.model.DEFAULT_HOT_CATEGORY
import com.czpn7.ying.data.model.DEFAULT_HOT_CATEGORY_FILTERS
import com.czpn7.ying.data.model.DEFAULT_HOT_TYPE
import com.czpn7.ying.data.model.DEFAULT_HOT_TYPE_FILTERS
import com.czpn7.ying.data.model.HotFilter
import com.czpn7.ying.data.model.HotListItem
import com.czpn7.ying.data.model.HotListResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HotListRepository @Inject constructor(
    private val api: DoubanHotApi
) {
    private var lastSuccessfulResult: HotListResult? = null

    suspend fun recentHot(
        category: String = DEFAULT_HOT_CATEGORY,
        type: String = DEFAULT_HOT_TYPE,
        limit: Int = HOT_LIST_LIMIT
    ): HotListResult = withContext(Dispatchers.IO) {
        runCatching {
            api.recentHotMovie(
                start = 0,
                limit = limit,
                category = category,
                type = type
            ).toDomain(category, type)
        }.onSuccess { result ->
            lastSuccessfulResult = result
        }.getOrElse { error ->
            Log.w("HotListRepository", "Douban hot list failed", error)
            lastSuccessfulResult ?: HotListResult(
                items = emptyList(),
                categoryFilters = DEFAULT_HOT_CATEGORY_FILTERS,
                typeFilters = DEFAULT_HOT_TYPE_FILTERS
            )
        }
    }

    private fun DoubanHotResponse.toDomain(
        selectedCategory: String,
        selectedType: String
    ): HotListResult {
        val categories = tags.toCategoryFilters().ifEmpty { DEFAULT_HOT_CATEGORY_FILTERS }
        val typeFilters = tags
            .firstOrNull { it.category == selectedCategory }
            ?.types
            ?.map { type -> HotFilter(type.title, selectedCategory, type.type) }
            ?.ifEmpty { null }
            ?: DEFAULT_HOT_TYPE_FILTERS.map { it.copy(category = selectedCategory) }

        return HotListResult(
            items = items.mapIndexed { index, item -> item.toDomain(index + 1) },
            categoryFilters = categories,
            typeFilters = typeFilters.ensureSelectedType(selectedType, selectedCategory)
        )
    }

    private fun List<DoubanHotTagDto>.toCategoryFilters(): List<HotFilter> =
        map { tag ->
            HotFilter(
                title = tag.title
                    .removeSuffix("电影")
                    .replace("豆瓣高分", "高分")
                    .replace("冷门佳片", "冷门"),
                category = tag.category
            )
        }.distinctBy { it.category }

    private fun List<HotFilter>.ensureSelectedType(
        selectedType: String,
        selectedCategory: String
    ): List<HotFilter> =
        if (any { it.type == selectedType }) {
            this
        } else {
            listOf(HotFilter(title = selectedType, category = selectedCategory, type = selectedType)) + this
        }

    private fun DoubanHotItemDto.toDomain(rank: Int): HotListItem {
        val subtitleParts = cardSubtitle.orEmpty()
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val year = subtitleParts.firstOrNull { YEAR_PATTERN.matches(it) }
        val subtitle = subtitleParts
            .filterNot { it == year }
            .take(4)
            .joinToString(" / ")

        return HotListItem(
            id = id,
            rank = rank,
            title = title,
            posterUrl = pic?.large ?: pic?.normal,
            rating = rating?.value,
            ratingCount = rating?.count?.takeIf { it > 0 },
            subtitle = subtitle.takeIf { it.isNotBlank() },
            year = year,
            isNew = isNew,
            episodesInfo = episodesInfo?.takeIf { it.isNotBlank() },
            doubanUri = uri
        )
    }

    private companion object {
        const val HOT_LIST_LIMIT = 30
        val YEAR_PATTERN = Regex("\\d{4}")
    }
}
