package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.di.MediaOkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class PlaybackValidation(
    val playable: Boolean,
    val reason: String? = null
)

@Singleton
class PlaybackUrlValidator @Inject constructor(
    @MediaOkHttpClient
    private val client: OkHttpClient
) {
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

            client.newCall(request).execute().use { response ->
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

                val normalized = url.substringBefore("?").lowercase()
                if (!normalized.endsWith(".m3u8")) {
                    return@withContext PlaybackValidation(true)
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if ("mpegurl" in contentType || "vnd.apple" in contentType) {
                    return@withContext PlaybackValidation(true)
                }

                val looksLikeHls = response.peekBody(512).string().trimStart().startsWith("#EXTM3U")
                if (looksLikeHls) {
                    PlaybackValidation(true)
                } else {
                    PlaybackValidation(false, "返回内容不是 HLS 播放列表")
                }
            }
        }.getOrElse { error ->
            Log.w("PlaybackUrlValidator", "Playback URL validation failed: ${url.safeUrlForLog()}", error)
            PlaybackValidation(false, error.message ?: error::class.java.simpleName)
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

private fun String.safeUrlForLog(): String =
    toHttpUrlOrNull()?.let { parsed ->
        "${parsed.scheme}://${parsed.host}${parsed.encodedPath}"
    } ?: "<invalid-url>"
