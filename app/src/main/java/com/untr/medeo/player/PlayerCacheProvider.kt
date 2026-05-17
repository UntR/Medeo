package com.untr.medeo.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.untr.medeo.data.local.CacheConfigStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerCacheProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheConfigStore: CacheConfigStore
) {
    private val mediaDir = File(context.cacheDir, "media")
    private val databaseProvider by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        StandaloneDatabaseProvider(context)
    }
    private val cacheDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            mediaDir,
            LeastRecentlyUsedCacheEvictor(cacheConfigStore.snapshot().mediaBytes),
            databaseProvider
        )
    }

    val cache: SimpleCache
        get() = cacheDelegate.value

    fun clear() {
        if (!cacheDelegate.isInitialized()) {
            SimpleCache.delete(mediaDir, databaseProvider)
            mediaDir.deleteRecursively()
            return
        }

        val activeCache = cache
        synchronized(activeCache) {
            activeCache.keys.toList().forEach { key -> activeCache.removeResource(key) }
        }
    }

    fun cacheSpaceOrDirectorySize(): Long {
        if (cacheDelegate.isInitialized()) {
            return cache.cacheSpace
        }
        return mediaDir.safeSize()
    }

    private fun File.safeSize(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles().orEmpty().sumOf { it.safeSize() }
    }
}
