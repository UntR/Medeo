package com.czpn7.ying.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VodListResponse(
    val code: Int,
    val msg: String? = null,
    val page: Int = 1,
    val pagecount: Int = 1,
    val total: Int = 0,
    val list: List<VodItemDto> = emptyList(),
    @Json(name = "class") val classes: List<ClassDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VodItemDto(
    @Json(name = "vod_id") val vodId: Long,
    @Json(name = "vod_name") val vodName: String,
    @Json(name = "vod_pic") val vodPic: String? = null,
    @Json(name = "vod_remarks") val vodRemarks: String? = null,
    @Json(name = "vod_year") val vodYear: String? = null,
    @Json(name = "vod_area") val vodArea: String? = null,
    @Json(name = "type_name") val typeName: String? = null,
    @Json(name = "vod_content") val vodContent: String? = null,
    @Json(name = "vod_actor") val vodActor: String? = null,
    @Json(name = "vod_director") val vodDirector: String? = null,
    @Json(name = "vod_play_from") val vodPlayFrom: String? = null,
    @Json(name = "vod_play_url") val vodPlayUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassDto(
    @Json(name = "type_id") val typeId: Int,
    @Json(name = "type_name") val typeName: String
)
