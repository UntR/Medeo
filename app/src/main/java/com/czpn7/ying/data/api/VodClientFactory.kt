package com.czpn7.ying.data.api

import com.squareup.moshi.Moshi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Singleton
class VodClientFactory @Inject constructor(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    private val cache = ConcurrentHashMap<String, VodApi>()

    fun get(source: VodSource): VodApi = cache.getOrPut("${source.id}|${source.baseUrl}") {
        Retrofit.Builder()
            .baseUrl(source.baseUrl.normalizedBaseUrl())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(VodApi::class.java)
    }
}

private fun String.normalizedBaseUrl(): String =
    if (endsWith("/")) this else "$this/"
