package com.untr.medeo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

private const val IMAGE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Medeo/n0.1"

@Composable
fun rememberMedeoImageRequest(url: String?): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(imageRequestHeaders(url))
            .build()
    }
}

private fun imageRequestHeaders(url: String?): NetworkHeaders {
    val referer = when {
        url.isNullOrBlank() -> "https://movie.douban.com/"
        url.contains("doubanio.com") || url.contains("douban.com") -> "https://movie.douban.com/"
        else -> url.toHostReferer()
    }
    return NetworkHeaders.Builder()
        .set("User-Agent", IMAGE_USER_AGENT)
        .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        .set("Referer", referer)
        .build()
}

private fun String.toHostReferer(): String =
    runCatching {
        val uri = java.net.URI(this)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return@runCatching this
        "$scheme://$host/"
    }.getOrDefault(this)
