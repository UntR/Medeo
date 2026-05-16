package com.czpn7.ying.data.repo

import android.util.Log
import com.czpn7.ying.data.api.BUILTIN_SOURCES
import com.czpn7.ying.data.api.PlayUrlParser
import com.czpn7.ying.data.api.VodClientFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class M1SmokeRunner @Inject constructor(
    private val factory: VodClientFactory
) {
    suspend fun run(): String = withContext(Dispatchers.IO) {
        val source = BUILTIN_SOURCES.first { it.id == "dbzy" }
        val searchResponse = factory.get(source).list(wd = "庆余年", pg = 1)
        val first = searchResponse.list.firstOrNull()
            ?: return@withContext "${source.name} 搜索成功，但没有返回结果"

        val detail = factory.get(source)
            .detail(ids = first.vodId.toString())
            .list
            .firstOrNull()
            ?: return@withContext "ffzy 找到 ${first.vodName}，但详情为空"

        val playSources = PlayUrlParser.parse(detail.vodPlayFrom, detail.vodPlayUrl)
        Log.i("M1Smoke", "PlaySource[] for ${detail.vodName}: $playSources")

        "${source.name}: ${detail.vodName}, ${playSources.size} 个播放源"
    }
}
