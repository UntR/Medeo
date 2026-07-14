package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.api.DoubanHotApi
import com.untr.medeo.data.api.dto.DoubanHotItemDto
import com.untr.medeo.data.api.dto.DoubanHotResponse
import com.untr.medeo.data.api.dto.DoubanHotTagDto
import com.untr.medeo.data.local.HotListCacheStorage
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotContentType
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.HotListResult
import com.untr.medeo.data.model.defaultCategoryFilters
import com.untr.medeo.data.model.defaultTypeFilters
import com.untr.medeo.data.local.toCachePayload
import com.untr.medeo.data.local.toHotListResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HotListRepository @Inject constructor(
    private val api: DoubanHotApi,
    private val cacheStorage: HotListCacheStorage
) {
    private val lastSuccessfulResults = ConcurrentHashMap<HotListCacheKey, CachedHotListResult>()

    suspend fun cachedRecentHot(
        contentType: HotContentType = HotContentType.MOVIE,
        category: String = contentType.defaultCategory,
        type: String = contentType.defaultType,
        freshOnly: Boolean = true
    ): HotListResult? = withContext(Dispatchers.IO) {
        val cacheKey = HotListCacheKey(contentType, category, type)
        lastSuccessfulResults[cacheKey]
            ?.takeIf { !freshOnly || it.isFresh() }
            ?.let { return@withContext it.result }

        val payload = cacheStorage.hotListCache(contentType)
            ?.takeIf { it.matches(category, type) }
            ?.takeIf { !freshOnly || it.isFresh() }
            ?: return@withContext null

        payload.toHotListResult().also { result ->
            lastSuccessfulResults[cacheKey] = CachedHotListResult(result, payload.ts)
        }
    }

    suspend fun cachedSelection(
        contentType: HotContentType,
        freshOnly: Boolean = true
    ): CachedHotListSelection? = withContext(Dispatchers.IO) {
        val payload = cacheStorage.hotListCache(contentType)
            ?.takeIf { !freshOnly || it.isFresh() }
            ?: return@withContext null
        val result = payload.toHotListResult()
        val cacheKey = HotListCacheKey(contentType, payload.category, payload.type)
        lastSuccessfulResults[cacheKey] = CachedHotListResult(result, payload.ts)
        CachedHotListSelection(payload.category, payload.type, result)
    }

    suspend fun recentHot(
        contentType: HotContentType = HotContentType.MOVIE,
        category: String = contentType.defaultCategory,
        type: String = contentType.defaultType,
        limit: Int = HOT_LIST_LIMIT
    ): HotListResult = withContext(Dispatchers.IO) {
        val cacheKey = HotListCacheKey(contentType, category, type)
        runCatching {
            when (contentType) {
                HotContentType.MOVIE -> api.recentHotMovie(
                    start = 0,
                    limit = limit,
                    category = category,
                    type = type
                )
                HotContentType.TV -> api.recentHotTv(
                    start = 0,
                    limit = limit,
                    category = category,
                    type = type
                )
            }.toDomain(contentType, category, type)
        }.onSuccess { result ->
            val payload = result.toCachePayload(category = category, type = type)
            lastSuccessfulResults[cacheKey] = CachedHotListResult(result, payload.ts)
            cacheStorage.setHotListCache(contentType, payload)
        }.getOrElse { error ->
            Log.w("HotListRepository", "Douban hot list failed", error)
            cachedRecentHot(
                contentType = contentType,
                category = category,
                type = type,
                freshOnly = false
            )
                ?: HotListResult(
                items = emptyList(),
                categoryFilters = contentType.defaultCategoryFilters(),
                typeFilters = contentType.defaultTypeFilters(category)
            )
        }
    }

    private fun DoubanHotResponse.toDomain(
        contentType: HotContentType,
        selectedCategory: String,
        selectedType: String
    ): HotListResult {
        val categories = tags.toCategoryFilters(contentType)
            .ifEmpty { contentType.defaultCategoryFilters() }
        val typeFilters = tags
            .firstOrNull { it.category == selectedCategory }
            ?.types
            ?.map { type -> HotFilter(type.title, selectedCategory, type.type) }
            ?.ifEmpty { null }
            ?: contentType.defaultTypeFilters(selectedCategory)

        return HotListResult(
            items = items.mapIndexed { index, item -> item.toDomain(index + 1) },
            categoryFilters = categories,
            typeFilters = typeFilters.ensureSelectedType(selectedType, selectedCategory)
        )
    }

    private fun List<DoubanHotTagDto>.toCategoryFilters(
        contentType: HotContentType
    ): List<HotFilter> =
        map { tag ->
            HotFilter(
                title = if (contentType == HotContentType.MOVIE) {
                    tag.title
                        .removeSuffix("电影")
                        .replace("豆瓣高分", "高分")
                        .replace("冷门佳片", "冷门")
                } else {
                    tag.title
                },
                category = tag.category,
                type = tag.types.firstOrNull { it.selected }?.type
                    ?: tag.types.firstOrNull()?.type
                    ?: contentType.defaultType
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
    val contentType: HotContentType,
    val category: String,
    val type: String
)

private data class CachedHotListResult(
    val result: HotListResult,
    val cachedAt: Long
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - cachedAt <= com.untr.medeo.data.local.HOT_LIST_CACHE_TTL_MS
}

data class CachedHotListSelection(
    val category: String,
    val type: String,
    val result: HotListResult
)
