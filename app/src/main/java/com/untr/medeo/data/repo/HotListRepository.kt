package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.api.DoubanHotApi
import com.untr.medeo.data.api.dto.DoubanHotItemDto
import com.untr.medeo.data.api.dto.DoubanHotResponse
import com.untr.medeo.data.api.dto.DoubanHotTagDto
import com.untr.medeo.data.model.DEFAULT_HOT_CATEGORY
import com.untr.medeo.data.model.DEFAULT_HOT_CATEGORY_FILTERS
import com.untr.medeo.data.model.DEFAULT_HOT_TYPE
import com.untr.medeo.data.model.DEFAULT_HOT_TYPE_FILTERS
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.HotListResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HotListRepository @Inject constructor(
    private val api: DoubanHotApi
) {
    private val lastSuccessfulResults = ConcurrentHashMap<HotListCacheKey, HotListResult>()

    suspend fun recentHot(
        category: String = DEFAULT_HOT_CATEGORY,
        type: String = DEFAULT_HOT_TYPE,
        limit: Int = HOT_LIST_LIMIT
    ): HotListResult = withContext(Dispatchers.IO) {
        val cacheKey = HotListCacheKey(category, type)
        runCatching {
            api.recentHotMovie(
                start = 0,
                limit = limit,
                category = category,
                type = type
            ).toDomain(category, type)
        }.onSuccess { result ->
            lastSuccessfulResults[cacheKey] = result
        }.getOrElse { error ->
            Log.w("HotListRepository", "Douban hot list failed", error)
            lastSuccessfulResults[cacheKey] ?: HotListResult(
                items = emptyList(),
                categoryFilters = DEFAULT_HOT_CATEGORY_FILTERS,
                typeFilters = DEFAULT_HOT_TYPE_FILTERS.map { it.copy(category = category) }
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

private data class HotListCacheKey(
    val category: String,
    val type: String
)
