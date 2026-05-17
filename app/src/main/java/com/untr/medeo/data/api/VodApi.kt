package com.untr.medeo.data.api

import com.untr.medeo.data.api.dto.VodListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface VodApi {
    @GET(".")
    suspend fun list(
        @Query("ac") ac: String = "list",
        @Query("wd") wd: String? = null,
        @Query("pg") pg: Int = 1,
        @Query("t") typeId: Int? = null,
        @Query("h") hours: Int? = null,
        @Query("at") at: String = "json"
    ): VodListResponse

    @GET(".")
    suspend fun detail(
        @Query("ac") ac: String = "detail",
        @Query("ids") ids: String,
        @Query("at") at: String = "json"
    ): VodListResponse
}
