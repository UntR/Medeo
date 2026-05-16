package com.czpn7.ying.data.model

import android.text.Html
import com.czpn7.ying.data.api.VodSource
import com.czpn7.ying.data.api.dto.VodItemDto

data class VodItem(
    val sourceId: String,
    val sourceName: String,
    val vodId: Long,
    val name: String,
    val pic: String?,
    val year: String?,
    val area: String?,
    val typeName: String?,
    val remarks: String?
) {
    val key: String get() = "$sourceId|$vodId"
    val dedupKey: String get() = "${normalize(name)}|${year.orEmpty()}"
}

data class VodDetail(
    val item: VodItem,
    val content: String?,
    val actor: String?,
    val director: String?,
    val playSources: List<PlaySource>,
    val playbackIssue: String? = null
)

data class PlaySource(
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val name: String,
    val url: String
) {
    fun isDirectMedia(): Boolean {
        val normalized = url.substringBefore("?").lowercase()
        return normalized.endsWith(".m3u8") ||
            normalized.endsWith(".mp4") ||
            normalized.endsWith(".mkv") ||
            normalized.endsWith(".flv") ||
            normalized.endsWith(".mov")
    }
}

data class AggregatedResult(
    val dedupKey: String,
    val primary: VodItem,
    val perSource: List<VodItem>
)

data class VodCategory(
    val typeId: Int,
    val name: String
)

fun normalize(value: String): String =
    value.replace(Regex("[\\s·・•\\-_:：『』「」【】《》()（）\\[\\]]+"), "")
        .lowercase()

fun VodItemDto.toDomain(source: VodSource): VodItem =
    VodItem(
        sourceId = source.id,
        sourceName = source.name,
        vodId = vodId,
        name = vodName,
        pic = vodPic?.takeIf { it.isNotBlank() },
        year = vodYear?.takeIf { it.isNotBlank() },
        area = vodArea?.takeIf { it.isNotBlank() },
        typeName = typeName?.takeIf { it.isNotBlank() },
        remarks = vodRemarks?.takeIf { it.isNotBlank() }
    )

fun String.stripHtml(): String =
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
