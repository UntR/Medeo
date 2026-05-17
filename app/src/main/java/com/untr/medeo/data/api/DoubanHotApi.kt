package com.untr.medeo.data.api

import com.untr.medeo.data.api.dto.DoubanHotResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface DoubanHotApi {
    @GET("rexxar/api/v2/subject/recent_hot/movie")
    suspend fun recentHotMovie(
        @Query("start") start: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("category") category: String,
        @Query("type") type: String
    ): DoubanHotResponse
}
