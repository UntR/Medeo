package com.untr.medeo.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CacheConfig(
    val mediaBytes: Long = SettingsStore.DEFAULT_MEDIA_CACHE_MB.toLong() * SettingsStore.BYTES_PER_MB,
    val imageBytes: Long = SettingsStore.DEFAULT_IMAGE_CACHE_MB.toLong() * SettingsStore.BYTES_PER_MB,
    val httpBytes: Long = SettingsStore.DEFAULT_HTTP_CACHE_MB.toLong() * SettingsStore.BYTES_PER_MB
)

@Singleton
class CacheConfigStore @Inject constructor(
    private val settingsStore: SettingsStore
) {
    @Volatile
    private var currentConfig = CacheConfig()
    private var collectJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (collectJob != null) return
        collectJob = scope.launch {
            settingsStore.settings.collectLatest { settings ->
                currentConfig = CacheConfig(
                    mediaBytes = settings.mediaCacheMb.toLong() * SettingsStore.BYTES_PER_MB,
                    imageBytes = settings.imageCacheMb.toLong() * SettingsStore.BYTES_PER_MB,
                    httpBytes = settings.httpCacheMb.toLong() * SettingsStore.BYTES_PER_MB
                )
            }
        }
    }

    fun snapshot(): CacheConfig = currentConfig
}
