package com.czpn7.ying.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.czpn7.ying.data.local.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerCacheProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(
                SettingsStore.DEFAULT_MEDIA_CACHE_MB.toLong() * SettingsStore.BYTES_PER_MB
            ),
            StandaloneDatabaseProvider(context)
        )
    }

    fun clear() {
        cache.keys.forEach { key -> cache.removeResource(key) }
    }
}
