package com.untr.medeo.ui.review

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.repo.SearchRepository
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchSourceStatusTest {
    @Test fun paginationKeepsFailedSourcesAndPreviouslyReturnedResults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val moshi = Moshi.Builder().build()
        val catalog = SourceCatalog(SettingsStore(context, moshi), moshi)
        val sources = catalog.enabledSources()
        assertTrue(sources.size > 1)
        val successfulHost = sources.first().baseUrl.toHttpUrl().host
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val page = request.url.queryParameter("pg")!!.toInt()
            val payload = if (request.url.host == successfulHost) {
                """{"code":1,"page":$page,"pagecount":2,"list":[{"vod_id":$page,"vod_name":"测试$page","vod_year":"2026"}]}"""
            } else """{"code":0,"list":[]}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = SearchRepository(VodClientFactory(client, moshi), catalog)
        val initial = repository.searchProgress("测试").last()
        assertEquals(sources.size - 1, initial.failedSources)
        assertEquals(1, initial.results.size)
        val updates = repository.searchProgress("测试", loadMore = true).toList()
        val final = updates.last()
        assertEquals(sources.size, final.totalSources)
        assertEquals(sources.size, final.completedSources)
        assertEquals(sources.size - 1, final.failedSources)
        assertEquals(2, final.results.size)
        assertTrue(updates.all { it.failedSources <= it.completedSources && it.completedSources <= it.totalSources })
    }
}
