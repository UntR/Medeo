package com.czpn7.ying.data.repo

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.czpn7.ying.player.PlayerCacheProvider
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
        listOf("img", "image_cache", "coil").forEach { name ->
            File(context.cacheDir, name).deleteRecursively()
        }
    }

    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) {
        val mediaDir = File(context.cacheDir, "media")
        val httpDir = File(context.cacheDir, "http")
        val imageBytes = listOf("img", "image_cache", "coil")
            .sumOf { name -> File(context.cacheDir, name).safeSize() }

        CacheUsage(
            mediaBytes = runCatching { playerCacheProvider.cache.cacheSpace }
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
