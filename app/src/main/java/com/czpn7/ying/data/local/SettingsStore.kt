package com.czpn7.ying.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.czpn7.ying.data.api.DEFAULT_ENABLED_SOURCE_IDS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val enabledSourceIds: Set<String>,
    val mediaCacheMb: Int,
    val imageCacheMb: Int,
    val httpCacheMb: Int,
    val wifiOnlyPlay: Boolean,
    val sourceManifestUrl: String,
    val remoteSourceManifestJson: String?,
    val remoteSourceManifestUpdatedAt: Long?,
    val disclaimerAccepted: Boolean,
    val themeMode: AppThemeMode
)

enum class AppThemeMode {
    DAY,
    NIGHT;

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DAY
    }
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { preferences ->
            AppSettings(
                enabledSourceIds = preferences[Keys.ENABLED_SOURCES] ?: DEFAULT_ENABLED_SOURCE_IDS,
                mediaCacheMb = preferences[Keys.MEDIA_CACHE_MB] ?: DEFAULT_MEDIA_CACHE_MB,
                imageCacheMb = preferences[Keys.IMAGE_CACHE_MB] ?: DEFAULT_IMAGE_CACHE_MB,
                httpCacheMb = preferences[Keys.HTTP_CACHE_MB] ?: DEFAULT_HTTP_CACHE_MB,
                wifiOnlyPlay = preferences[Keys.WIFI_ONLY_PLAY] ?: false,
                sourceManifestUrl = preferences[Keys.SOURCE_MANIFEST_URL].orEmpty(),
                remoteSourceManifestJson = preferences[Keys.REMOTE_SOURCE_MANIFEST_JSON],
                remoteSourceManifestUpdatedAt = preferences[Keys.REMOTE_SOURCE_MANIFEST_UPDATED_AT],
                disclaimerAccepted = preferences[Keys.DISCLAIMER_ACCEPTED] ?: false,
                themeMode = AppThemeMode.fromStoredValue(preferences[Keys.THEME_MODE])
            )
        }

    suspend fun ensureSourceListVersion() {
        context.settingsDataStore.edit { preferences ->
            val version = preferences[Keys.SOURCE_LIST_VERSION] ?: 0
            if (version < CURRENT_SOURCE_LIST_VERSION) {
                preferences[Keys.ENABLED_SOURCES] = DEFAULT_ENABLED_SOURCE_IDS
                preferences[Keys.SOURCE_LIST_VERSION] = CURRENT_SOURCE_LIST_VERSION
            }
        }
    }

    suspend fun enabledSourceIds(): Set<String> =
        settings.map { it.enabledSourceIds }.first()

    suspend fun mediaCacheBytes(): Long =
        readInt(Keys.MEDIA_CACHE_MB, DEFAULT_MEDIA_CACHE_MB).toLong() * BYTES_PER_MB

    suspend fun imageCacheBytes(): Long =
        readInt(Keys.IMAGE_CACHE_MB, DEFAULT_IMAGE_CACHE_MB).toLong() * BYTES_PER_MB

    suspend fun httpCacheBytes(): Long =
        readInt(Keys.HTTP_CACHE_MB, DEFAULT_HTTP_CACHE_MB).toLong() * BYTES_PER_MB

    suspend fun wifiOnlyPlay(): Boolean =
        settings.map { it.wifiOnlyPlay }.first()

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.ENABLED_SOURCES] ?: DEFAULT_ENABLED_SOURCE_IDS
            preferences[Keys.ENABLED_SOURCES] = if (enabled) {
                current + sourceId
            } else {
                current - sourceId
            }
        }
    }

    suspend fun addEnabledSources(sourceIds: Set<String>) {
        if (sourceIds.isEmpty()) return
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.ENABLED_SOURCES] ?: DEFAULT_ENABLED_SOURCE_IDS
            preferences[Keys.ENABLED_SOURCES] = current + sourceIds
        }
    }

    suspend fun setWifiOnlyPlay(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.WIFI_ONLY_PLAY] = enabled
        }
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DISCLAIMER_ACCEPTED] = accepted
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun sourceManifestUrl(): String =
        settings.map { it.sourceManifestUrl }.first()

    suspend fun setSourceManifestUrl(url: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SOURCE_MANIFEST_URL] = url.trim()
        }
    }

    suspend fun setRemoteSourceManifest(json: String, updatedAt: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.REMOTE_SOURCE_MANIFEST_JSON] = json
            preferences[Keys.REMOTE_SOURCE_MANIFEST_UPDATED_AT] = updatedAt
        }
    }

    suspend fun clearRemoteSourceManifest() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.REMOTE_SOURCE_MANIFEST_JSON)
            preferences.remove(Keys.REMOTE_SOURCE_MANIFEST_UPDATED_AT)
        }
    }

    private suspend fun readInt(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        defaultValue: Int
    ): Int = context.settingsDataStore.data
        .map { preferences -> preferences[key] ?: defaultValue }
        .first()

    private object Keys {
        val ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val MEDIA_CACHE_MB = intPreferencesKey("media_cache_mb")
        val IMAGE_CACHE_MB = intPreferencesKey("image_cache_mb")
        val HTTP_CACHE_MB = intPreferencesKey("http_cache_mb")
        val WIFI_ONLY_PLAY = booleanPreferencesKey("wifi_only_play")
        val CATEGORIES_CACHE_JSON = stringPreferencesKey("categories_cache_json")
        val SOURCE_LIST_VERSION = intPreferencesKey("source_list_version")
        val SOURCE_MANIFEST_URL = stringPreferencesKey("source_manifest_url")
        val REMOTE_SOURCE_MANIFEST_JSON = stringPreferencesKey("remote_source_manifest_json")
        val REMOTE_SOURCE_MANIFEST_UPDATED_AT = longPreferencesKey("remote_source_manifest_updated_at")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    companion object {
        const val CURRENT_SOURCE_LIST_VERSION = 2
        const val DEFAULT_MEDIA_CACHE_MB = 500
        const val DEFAULT_IMAGE_CACHE_MB = 200
        const val DEFAULT_HTTP_CACHE_MB = 50
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
