package com.untr.medeo.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.untr.medeo.data.diagnostics.DiagnosticLogger
import com.untr.medeo.player.PlayerCacheProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideMediaSourceFactory(
        @ApplicationContext context: Context,
        @MediaOkHttpClient
        client: OkHttpClient,
        cacheProvider: PlayerCacheProvider,
        diagnosticLogger: DiagnosticLogger
    ): MediaSource.Factory {
        val upstreamFactory = OkHttpDataSource.Factory(client)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cacheProvider.cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(
                object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        if (!diagnosticLogger.isEnabled) return
                        diagnosticLogger.log(
                            event = "cache_read",
                            fields = mapOf(
                                "cache_size_bytes" to cacheSizeBytes,
                                "cached_bytes_read" to cachedBytesRead
                            )
                        )
                    }

                    override fun onCacheIgnored(reason: Int) {
                        if (!diagnosticLogger.isEnabled) return
                        diagnosticLogger.log(
                            event = "cache_ignored",
                            fields = mapOf("reason" to reason.cacheIgnoreReason())
                        )
                    }
                }
            )

        return DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory)
    }
}

@UnstableApi
private fun Int.cacheIgnoreReason(): String = when (this) {
    CacheDataSource.CACHE_IGNORED_REASON_ERROR -> "cache_error"
    CacheDataSource.CACHE_IGNORED_REASON_UNSET_LENGTH -> "unset_length"
    else -> "unknown_$this"
}
