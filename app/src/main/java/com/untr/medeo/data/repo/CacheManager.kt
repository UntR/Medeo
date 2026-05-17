package com.untr.medeo.data.repo

import android.content.Context
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import com.untr.medeo.player.PlayerCacheProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache

data class CacheUsage(
    val mediaBytes: Long,
    val imageBytes: Long,
    val httpBytes: Long
) {
    val totalBytes: Long = mediaBytes + imageBytes + httpBytes
}

@UnstableApi
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerCacheProvider: PlayerCacheProvider,
    private val okHttpCache: Cache
) {
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        playerCacheProvider.clear()
        okHttpCache.evictAll()
        runCatching {
            SingletonImageLoader.get(context).apply {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
        // Keep legacy names here so users upgrading from earlier internal builds can
        // clear stale image cache directories created before Coil was pinned to img.
        listOf("img", "image_cache", "coil").forEach { name ->
            File(context.cacheDir, name).deleteRecursively()
        }
    }

    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) {
        val mediaDir = File(context.cacheDir, "media")
        val httpDir = File(context.cacheDir, "http")
        val imageBytes = runCatching {
            SingletonImageLoader.get(context).diskCache?.size ?: 0L
        }.getOrElse {
            listOf("img", "image_cache", "coil")
                .sumOf { name -> File(context.cacheDir, name).safeSize() }
        }

        CacheUsage(
            mediaBytes = runCatching { playerCacheProvider.cacheSpaceOrDirectorySize() }
                .getOrElse { mediaDir.safeSize() },
            imageBytes = imageBytes,
            httpBytes = runCatching { okHttpCache.size() }
                .getOrElse { httpDir.safeSize() }
        )
    }

    private fun File.safeSize(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles().orEmpty().sumOf { it.safeSize() }
    }
}
