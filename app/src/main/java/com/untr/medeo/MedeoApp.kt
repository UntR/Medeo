package com.untr.medeo

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.untr.medeo.data.local.CacheConfigStore
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class MedeoApp : Application() {
    @Inject
    lateinit var cacheConfigStore: CacheConfigStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        cacheConfigStore.start(applicationScope)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.cacheDir, "img").toOkioPath())
                        .maxSizeBytes(cacheConfigStore.snapshot().imageBytes)
                        .build()
                }
                .build()
        }
    }
}
