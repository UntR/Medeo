package com.czpn7.ying.di

import android.content.Context
import com.czpn7.ying.BuildConfig
import com.czpn7.ying.data.api.DoubanHotApi
import com.czpn7.ying.data.local.SettingsStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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
        @ApplicationContext context: Context
    ): Cache = Cache(
        directory = File(context.cacheDir, "http"),
        maxSize = SettingsStore.DEFAULT_HTTP_CACHE_MB.toLong() * SettingsStore.BYTES_PER_MB
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(cache: Cache): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url
                val referer = if (url.host.endsWith("douban.com")) {
                    "https://movie.douban.com/explore"
                } else {
                    "${url.scheme}://${url.host}/"
                }
                val request = chain.request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 medeo/n0.1"
                    )
                    .header("Accept", "*/*")
                    .header("Referer", referer)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideDoubanHotApi(client: OkHttpClient, moshi: Moshi): DoubanHotApi =
        Retrofit.Builder()
            .baseUrl("https://m.douban.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(DoubanHotApi::class.java)
}
