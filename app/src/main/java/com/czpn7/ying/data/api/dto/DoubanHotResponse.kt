package com.czpn7.ying.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DoubanHotResponse(
    val category: String = "",
    val type: String = "",
    val total: Int = 0,
    val items: List<DoubanHotItemDto> = emptyList(),
    val tags: List<DoubanHotTagDto> = emptyList(),
    @Json(name = "recommend_tags") val recommendTags: List<DoubanRecommendTagDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DoubanHotItemDto(
    val id: String,
    val title: String,
    val pic: DoubanHotPicDto? = null,
    val rating: DoubanHotRatingDto? = null,
    val uri: String? = null,
    @Json(name = "card_subtitle") val cardSubtitle: String? = null,
    @Json(name = "is_new") val isNew: Boolean = false,
    @Json(name = "episodes_info") val episodesInfo: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class DoubanHotPicDto(
    val large: String? = null,
    val normal: String? = null
)

@JsonClass(generateAdapter = true)
data class DoubanHotRatingDto(
    val count: Int = 0,
    val max: Int = 10,
    @Json(name = "star_count") val starCount: Double? = null,
    val value: Double? = null
)

@JsonClass(generateAdapter = true)
data class DoubanHotTagDto(
    val title: String,
    val category: String,
    val selected: Boolean = false,
    val types: List<DoubanHotTypeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DoubanHotTypeDto(
    val title: String,
    val type: String,
    val selected: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DoubanRecommendTagDto(
    val title: String,
    val category: String,
    val type: String,
    val selected: Boolean = false
)
