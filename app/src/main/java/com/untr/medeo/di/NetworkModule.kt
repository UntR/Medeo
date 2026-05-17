package com.untr.medeo.di

import android.content.Context
import com.untr.medeo.BuildConfig
import com.untr.medeo.data.api.DoubanHotApi
import com.untr.medeo.data.local.CacheConfigStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CmsOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DoubanOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideOkHttpCache(
        @ApplicationContext context: Context,
        cacheConfigStore: CacheConfigStore
    ): Cache = Cache(
        directory = File(context.cacheDir, "http"),
        maxSize = cacheConfigStore.snapshot().httpBytes
    )

    @Provides
    @Singleton
    @CmsOkHttpClient
    fun provideCmsOkHttpClient(cache: Cache): OkHttpClient =
        baseClientBuilder()
            .cache(cache)
            .addInterceptor(loggingInterceptor())
            .build()

    @Provides
    @Singleton
    @DoubanOkHttpClient
    fun provideDoubanOkHttpClient(cache: Cache): OkHttpClient =
        baseClientBuilder()
            .cache(cache)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Medeo/v0.1"
                    )
                    .header("Accept", "*/*")
                    .header("Referer", "https://movie.douban.com/explore")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor())
            .build()

    @Provides
    @Singleton
    @MediaOkHttpClient
    fun provideMediaOkHttpClient(): OkHttpClient =
        baseClientBuilder()
            .addInterceptor(loggingInterceptor())
            .build()

    @Provides
    @Singleton
    fun provideDoubanHotApi(
        @DoubanOkHttpClient client: OkHttpClient,
        moshi: Moshi
    ): DoubanHotApi =
        Retrofit.Builder()
            .baseUrl("https://m.douban.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(DoubanHotApi::class.java)

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
}
