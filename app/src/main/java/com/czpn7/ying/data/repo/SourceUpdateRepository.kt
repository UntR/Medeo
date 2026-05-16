package com.czpn7.ying.data.repo

import com.czpn7.ying.data.api.RemoteSourceManifest
import com.czpn7.ying.data.local.SettingsStore
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SourceUpdateResult(
    val success: Boolean,
    val message: String
)

@Singleton
class SourceUpdateRepository @Inject constructor(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val settingsStore: SettingsStore
) {
    private val manifestAdapter = moshi.adapter(RemoteSourceManifest::class.java)

    suspend fun refresh(url: String? = null): SourceUpdateResult =
        withContext(Dispatchers.IO) {
            val manifestUrl = (url ?: settingsStore.sourceManifestUrl()).trim()
            if (manifestUrl.isBlank()) {
                return@withContext SourceUpdateResult(false, "未配置云端源地址")
            }
            if (manifestUrl.toHttpUrlOrNull() == null) {
                return@withContext SourceUpdateResult(false, "云端源地址格式无效")
            }

            runCatching {
                val request = Request.Builder()
                    .url(manifestUrl)
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext SourceUpdateResult(
                            success = false,
                            message = "云端源请求失败: HTTP ${response.code}"
                        )
                    }

                    val body = response.body?.string()
                        ?: return@withContext SourceUpdateResult(false, "云端源响应为空")
                    val manifest = manifestAdapter.fromJson(body)
                        ?: return@withContext SourceUpdateResult(false, "云端源 JSON 为空")
                    val validSources = manifest.sources.mapNotNull { source -> source.toVodSource() }
                    if (validSources.isEmpty()) {
                        return@withContext SourceUpdateResult(false, "云端源没有有效数据源")
                    }

                    settingsStore.setSourceManifestUrl(manifestUrl)
                    settingsStore.setRemoteSourceManifest(body, System.currentTimeMillis())
                    settingsStore.addEnabledSources(
                        validSources
                            .filter { source -> source.defaultEnabled }
                            .mapTo(linkedSetOf()) { source -> source.id }
                    )

                    SourceUpdateResult(true, "云端源已更新：${validSources.size} 个有效源")
                }
            }.getOrElse { error ->
                SourceUpdateResult(
                    success = false,
                    message = "云端源更新失败: ${error.message ?: error::class.java.simpleName}"
                )
            }
        }

    suspend fun clearRemoteSources() {
        settingsStore.clearRemoteSourceManifest()
    }
}
