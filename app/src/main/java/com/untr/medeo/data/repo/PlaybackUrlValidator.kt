package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.di.MediaOkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class PlaybackValidation(
    val playable: Boolean,
    val reason: String? = null
)

data class PlaybackPreflight(
    val playSources: List<PlaySource>,
    val issue: String? = null
)

@Singleton
class PlaybackUrlValidator @Inject constructor(
    @MediaOkHttpClient
    private val client: OkHttpClient
) {
    private val validationClient = client.newBuilder()
        .callTimeout(PLAYBACK_PREFLIGHT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun preflight(playSources: List<PlaySource>): PlaybackPreflight {
        if (playSources.isEmpty()) return PlaybackPreflight(emptyList())

        val prioritized = prioritizeKnownMediaLines(playSources)
        if (prioritized.first().episodes.firstOrNull()?.hasExplicitMediaExtension() == true) {
            return PlaybackPreflight(prioritized)
        }

        val validations = coroutineScope {
            prioritized.map { source ->
                async {
                    val episode = source.episodes.firstOrNull()
                    if (episode == null) {
                        PlaybackValidation(false, "线路没有可播放剧集")
                    } else {
                        validate(episode.url)
                    }
                }
            }.awaitAll()
        }
        return applyPlaybackValidations(prioritized, validations)
    }

    suspend fun isPlayable(url: String): Boolean = withContext(Dispatchers.IO) {
        validate(url).playable
    }

    suspend fun validate(url: String): PlaybackValidation = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, */*")
                .header("Referer", refererFor(url))
                .build()

            validationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.peekBody(512).string()
                    val reason = when {
                        response.code == 403 && body.contains("region", ignoreCase = true) ->
                            "HTTP 403，源站地区限制"
                        response.code == 403 -> "HTTP 403，源站拒绝访问"
                        response.code == 404 -> "HTTP 404，播放地址已失效"
                        else -> "HTTP ${response.code}"
                    }
                    return@withContext PlaybackValidation(false, reason)
                }

                return@withContext evaluateSuccessfulPlaybackResponse(
                    url = url,
                    contentType = response.header("Content-Type"),
                    bodyPrefix = response.peekBody(512).string()
                )
            }
        }.getOrElse { error ->
            Log.w(
                "PlaybackUrlValidator",
                "Playback URL validation failed: ${url.safeUrlForLog()}, error=${error::class.java.simpleName}"
            )
            PlaybackValidation(false, "连接失败或响应超时")
        }
    }

    private fun refererFor(url: String): String {
        val parsed = url.toHttpUrlOrNull()
        return if (parsed != null) {
            "${parsed.scheme}://${parsed.host}/"
        } else {
            "https://localhost/"
        }
    }
}

internal fun evaluateSuccessfulPlaybackResponse(
    url: String,
    contentType: String?,
    bodyPrefix: String
): PlaybackValidation {
    val normalizedContentType = contentType.orEmpty()
        .substringBefore(";")
        .trim()
        .lowercase()
    val normalizedBody = bodyPrefix.trimStart()
        .removePrefix("\uFEFF")
        .trimStart()
        .lowercase()

    val looksLikeHls =
        "mpegurl" in normalizedContentType ||
            "vnd.apple" in normalizedContentType ||
            normalizedBody.startsWith("#extm3u")
    if (looksLikeHls) return PlaybackValidation(true)

    if (
        normalizedContentType == "text/html" ||
        normalizedContentType == "application/xhtml+xml" ||
        normalizedBody.looksLikeHtmlDocument()
    ) {
        return PlaybackValidation(false, "返回内容是网页，不是媒体流")
    }
    if (
        normalizedContentType.startsWith("text/") ||
        "json" in normalizedContentType ||
        "xml" in normalizedContentType
    ) {
        return PlaybackValidation(false, "返回内容类型不是媒体流")
    }

    val normalizedUrl = url.substringBefore("#").substringBefore("?").lowercase()
    if (normalizedUrl.endsWith(".m3u8")) {
        return PlaybackValidation(false, "返回内容不是 HLS 播放列表")
    }

    return PlaybackValidation(true)
}

internal fun prioritizeKnownMediaLines(playSources: List<PlaySource>): List<PlaySource> =
    playSources.sortedByDescending { source ->
        source.episodes.firstOrNull()?.hasExplicitMediaExtension() == true
    }

internal fun applyPlaybackValidations(
    playSources: List<PlaySource>,
    validations: List<PlaybackValidation>
): PlaybackPreflight {
    val failedCount = validations.count { validation -> !validation.playable }
    val playableSources = playSources.filterIndexed { index, _ ->
        validations.getOrNull(index)?.playable != false
    }
    val issue = when {
        failedCount == 0 -> null
        playableSources.isEmpty() -> "所有候选线路均未通过媒体预检"
        else -> "已排除 $failedCount 条未通过媒体预检的线路"
    }
    return PlaybackPreflight(playSources = playableSources, issue = issue)
}

private fun String.looksLikeHtmlDocument(): Boolean =
    startsWith("<!doctype html") ||
        startsWith("<html") ||
        startsWith("<head") ||
        startsWith("<body")

private const val PLAYBACK_PREFLIGHT_TIMEOUT_MS = 4_000L
private fun String.safeUrlForLog(): String =
    toHttpUrlOrNull()?.let { parsed ->
        "${parsed.scheme}://${parsed.host}${parsed.encodedPath}"
    } ?: "<invalid-url>"
