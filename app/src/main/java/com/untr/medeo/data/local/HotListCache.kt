package com.untr.medeo.data.local

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotContentType
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.HotListResult

@JsonClass(generateAdapter = true)
data class HotListCachePayload(
    val version: Int = HOT_LIST_CACHE_VERSION,
    val ts: Long,
    val category: String,
    val type: String,
    val items: List<HotListCacheItem> = emptyList(),
    val categoryFilters: List<HotFilterCacheItem> = emptyList(),
    val typeFilters: List<HotFilterCacheItem> = emptyList()
) {
    fun matches(category: String, type: String): Boolean =
        this.category == category && this.type == type

    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - ts <= HOT_LIST_CACHE_TTL_MS
}

@JsonClass(generateAdapter = true)
data class HotListCacheCollection(
    @Json(name = "MOVIE") val movie: HotListCachePayload? = null,
    @Json(name = "TV") val tv: HotListCachePayload? = null
) {
    fun cacheFor(contentType: HotContentType): HotListCachePayload? = when (contentType) {
        HotContentType.MOVIE -> movie
        HotContentType.TV -> tv
    }

    fun withCache(
        contentType: HotContentType,
        payload: HotListCachePayload
    ): HotListCacheCollection = when (contentType) {
        HotContentType.MOVIE -> copy(movie = payload)
        HotContentType.TV -> copy(tv = payload)
    }

    companion object {
        fun fromLegacyMovie(payload: HotListCachePayload): HotListCacheCollection =
            HotListCacheCollection(movie = payload)
    }
}

@JsonClass(generateAdapter = true)
data class HotListCacheItem(
    val id: String,
    val rank: Int,
    val title: String,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val subtitle: String? = null,
    val year: String? = null,
    val isNew: Boolean = false,
    val episodesInfo: String? = null,
    val doubanUri: String? = null
)

@JsonClass(generateAdapter = true)
data class HotFilterCacheItem(
    val title: String,
    val category: String,
    val type: String
)

fun HotListResult.toCachePayload(
    category: String,
    type: String,
    nowMs: Long = System.currentTimeMillis()
): HotListCachePayload =
    HotListCachePayload(
        ts = nowMs,
        category = category,
        type = type,
        items = items.map { item ->
            HotListCacheItem(
                id = item.id,
                rank = item.rank,
                title = item.title,
                posterUrl = item.posterUrl,
                rating = item.rating,
                ratingCount = item.ratingCount,
                subtitle = item.subtitle,
                year = item.year,
                isNew = item.isNew,
                episodesInfo = item.episodesInfo,
                doubanUri = item.doubanUri
            )
        },
        categoryFilters = categoryFilters.map { it.toCacheItem() },
        typeFilters = typeFilters.map { it.toCacheItem() }
    )

fun HotListCachePayload.toHotListResult(): HotListResult =
    HotListResult(
        items = items.map { item ->
            HotListItem(
                id = item.id,
                rank = item.rank,
                title = item.title,
                posterUrl = item.posterUrl,
                rating = item.rating,
                ratingCount = item.ratingCount,
                subtitle = item.subtitle,
                year = item.year,
                isNew = item.isNew,
                episodesInfo = item.episodesInfo,
                doubanUri = item.doubanUri
            )
        },
        categoryFilters = categoryFilters.map { it.toHotFilter() },
        typeFilters = typeFilters.map { it.toHotFilter() }
    )

private fun HotFilter.toCacheItem(): HotFilterCacheItem =
    HotFilterCacheItem(
        title = title,
        category = category,
        type = type
    )

private fun HotFilterCacheItem.toHotFilter(): HotFilter =
    HotFilter(
        title = title,
        category = category,
        type = type
    )

const val HOT_LIST_CACHE_TTL_MS: Long = 7L * 24L * 60L * 60L * 1000L
private const val HOT_LIST_CACHE_VERSION = 2

interface HotListCacheStorage {
    suspend fun hotListCache(contentType: HotContentType): HotListCachePayload?

    suspend fun setHotListCache(
        contentType: HotContentType,
        payload: HotListCachePayload
    )
}

internal fun decodeHotListCacheCollection(
    raw: String?,
    collectionAdapter: JsonAdapter<HotListCacheCollection>,
    legacyAdapter: JsonAdapter<HotListCachePayload>
): HotListCacheCollection {
    val json = raw?.takeIf { it.isNotBlank() } ?: return HotListCacheCollection()
    val collection = runCatching { collectionAdapter.fromJson(json) }.getOrNull()
    if (collection?.movie != null || collection?.tv != null) return collection

    val legacyMovie = runCatching { legacyAdapter.fromJson(json) }.getOrNull()
    return legacyMovie?.let(HotListCacheCollection::fromLegacyMovie) ?: HotListCacheCollection()
}
