package com.untr.medeo.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
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
        cacheProvider: PlayerCacheProvider
    ): MediaSource.Factory {
        val upstreamFactory = OkHttpDataSource.Factory(client)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cacheProvider.cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory)
    }
}
