package com.czpn7.ying.data.api

import com.czpn7.ying.data.local.SettingsStore
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class SourceCatalog @Inject constructor(
    private val settingsStore: SettingsStore,
    moshi: Moshi
) {
    private val manifestAdapter = moshi.adapter(RemoteSourceManifest::class.java)

    val sources: Flow<List<VodSource>> = settingsStore.settings
        .map { settings ->
            val remoteManifestJson = if (REMOTE_SOURCE_MANIFEST_ENABLED) {
                settings.remoteSourceManifestJson
            } else {
                null
            }
            mergeSources(remoteManifestJson)
        }

    suspend fun sources(): List<VodSource> =
        sources.first()

    suspend fun enabledSources(): List<VodSource> {
        settingsStore.ensureSourceListVersion()
        val enabledIds = settingsStore.enabledSourceIds()
        return sources().filter { source -> source.id in enabledIds }
    }

    suspend fun sourceById(sourceId: String): VodSource? =
        sources().firstOrNull { source -> source.id == sourceId }

    fun playbackPriority(sourceId: String): Int {
        val explicitPriority = PLAYBACK_SOURCE_PRIORITY.indexOf(sourceId)
        return if (explicitPriority >= 0) explicitPriority else PLAYBACK_SOURCE_PRIORITY.size
    }

    private fun mergeSources(remoteManifestJson: String?): List<VodSource> {
        val merged = linkedMapOf<String, VodSource>()
        BUILTIN_SOURCES.forEach { source -> merged[source.id] = source }

        parseManifest(remoteManifestJson)
            ?.sources
            ?.mapNotNull { dto -> dto.toVodSource() }
            ?.forEach { source -> merged[source.id] = source }

        return merged.values.toList()
    }

    private fun parseManifest(rawJson: String?): RemoteSourceManifest? {
        val json = rawJson?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { manifestAdapter.fromJson(json) }.getOrNull()
    }
}

@JsonClass(generateAdapter = true)
data class RemoteSourceManifest(
    val version: Int = 1,
    val sources: List<RemoteVodSource> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteVodSource(
    val id: String,
    val name: String,
    @Json(name = "base_url")
    val baseUrl: String? = null,
    @Json(name = "baseUrl")
    val camelBaseUrl: String? = null,
    @Json(name = "default_enabled")
    val defaultEnabled: Boolean? = null,
    @Json(name = "defaultEnabled")
    val camelDefaultEnabled: Boolean? = null,
    @Json(name = "needs_https_bypass")
    val needsHttpsBypass: Boolean? = null,
    @Json(name = "needsHttpsBypass")
    val camelNeedsHttpsBypass: Boolean? = null
) {
    fun toVodSource(): VodSource? {
        val cleanId = id.trim()
        val cleanName = name.trim()
        val cleanBaseUrl = (baseUrl ?: camelBaseUrl).orEmpty().trim()
        if (!SOURCE_ID_PATTERN.matches(cleanId) || cleanName.isBlank()) return null
        if (cleanBaseUrl.toHttpUrlOrNull() == null) return null

        return VodSource(
            id = cleanId,
            name = cleanName,
            baseUrl = cleanBaseUrl,
            defaultEnabled = defaultEnabled ?: camelDefaultEnabled ?: false,
            needsHttpsBypass = needsHttpsBypass ?: camelNeedsHttpsBypass ?: false
        )
    }
}

private val SOURCE_ID_PATTERN = Regex("[A-Za-z0-9_-]{2,32}")

private const val REMOTE_SOURCE_MANIFEST_ENABLED = false
