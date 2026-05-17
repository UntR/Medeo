package com.untr.medeo.data.model

import android.text.Html
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.api.dto.VodItemDto
import java.net.URL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false

        val httpUrl = trimmed.toHttpUrlOrNull()
        val fallbackUrl = if (httpUrl == null) runCatching { URL(trimmed) }.getOrNull() else null
        val scheme = httpUrl?.scheme ?: fallbackUrl?.protocol?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        val host = (httpUrl?.host ?: fallbackUrl?.host).orEmpty().lowercase()
        if (host.isBlank() || nonMediaHosts.any { marker -> marker in host }) return false

        val path = httpUrl?.encodedPath ?: fallbackUrl?.path.orEmpty()
        val normalizedLowerPath = path.lowercase()
        val normalizedPath = normalizedLowerPath.ifBlank { "/" }
        val filename = normalizedPath.substringAfterLast("/")
        val extension = filename.substringAfterLast(".", missingDelimiterValue = "")

        if (extension in directMediaExtensions) return true
        if (extension in nonMediaExtensions) return false
        if (nonMediaPathMarkers.any { marker -> marker in normalizedPath }) return false

        // Many CMS sources use signed or extensionless media URLs. Keep those as candidates
        // and let the playback validator/player decide instead of dropping the whole source.
        return true
    }
}

data class AggregatedResult(
    val dedupKey: String,
    val primary: VodItem,
    val perSource: List<VodItem>
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

private val directMediaExtensions = setOf(
    "m3u8",
    "mp4",
    "mkv",
    "flv",
    "mov",
    "ts",
    "webm"
)

private val nonMediaExtensions = setOf(
    "html",
    "htm",
    "shtml",
    "xml",
    "json",
    "txt",
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "apk",
    "zip",
    "rar",
    "7z"
)

private val nonMediaHosts = listOf(
    "pan.baidu.com",
    "aliyundrive.com",
    "alipan.com",
    "quark.cn",
    "drive.uc.cn",
    "115.com",
    "lanzou"
)

private val nonMediaPathMarkers = listOf(
    "/share/",
    "/voddetail/",
    "/vodplay/"
)
