package com.untr.medeo.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.local.AppThemeMode
import com.untr.medeo.data.local.AppSettings
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.repo.CacheManager
import com.untr.medeo.data.repo.CacheUsage
import com.untr.medeo.data.repo.SourceUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings? = null,
    val sources: List<VodSource> = emptyList(),
    val sourceManifestUrlDraft: String = "",
    val cacheUsage: CacheUsage? = null,
    val refreshingSources: Boolean = false,
    val clearingCache: Boolean = false,
    val message: String? = null
)

@UnstableApi
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val cacheManager: CacheManager,
    private val sourceCatalog: SourceCatalog,
    private val sourceUpdateRepository: SourceUpdateRepository
) : ViewModel() {
    var uiState by mutableStateOf(SettingsUiState())
        private set
    private var sourceManifestUrlDirty = false

    init {
        viewModelScope.launch {
            settingsStore.ensureSourceListVersion()
            settingsStore.settings.collect { settings ->
                val sourceManifestUrlDraft = if (sourceManifestUrlDirty) {
                    uiState.sourceManifestUrlDraft
                } else {
                    settings.sourceManifestUrl
                }
                uiState = uiState.copy(
                    loading = false,
                    settings = settings,
                    sourceManifestUrlDraft = sourceManifestUrlDraft
                )
            }
        }
        viewModelScope.launch {
            sourceCatalog.sources.collect { sources ->
                uiState = uiState.copy(sources = sources)
            }
        }
        refreshCacheUsage()
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setSourceEnabled(sourceId, enabled)
        }
    }

    fun setWifiOnlyPlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setWifiOnlyPlay(enabled)
        }
    }

    fun setMediaCacheMb(valueMb: Int) {
        viewModelScope.launch {
            settingsStore.setMediaCacheMb(valueMb)
        }
    }

    fun setImageCacheMb(valueMb: Int) {
        viewModelScope.launch {
            settingsStore.setImageCacheMb(valueMb)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
        }
    }

    fun setSourceManifestUrlDraft(url: String) {
        sourceManifestUrlDirty = true
        uiState = uiState.copy(sourceManifestUrlDraft = url)
    }

    fun saveSourceManifestUrl() {
        viewModelScope.launch {
            val url = uiState.sourceManifestUrlDraft.trim()
            settingsStore.setSourceManifestUrl(url)
            sourceManifestUrlDirty = false
            uiState = uiState.copy(message = if (url.isBlank()) "云端源地址已清空" else "云端源地址已保存")
        }
    }

    fun refreshRemoteSources() {
        viewModelScope.launch {
            val url = uiState.sourceManifestUrlDraft.trim()
            uiState = uiState.copy(refreshingSources = true, message = null)
            settingsStore.setSourceManifestUrl(url)
            sourceManifestUrlDirty = false
            val result = sourceUpdateRepository.refresh(url)
            uiState = uiState.copy(
                refreshingSources = false,
                message = result.message
            )
        }
    }

    fun clearRemoteSources() {
        viewModelScope.launch {
            sourceUpdateRepository.clearRemoteSources()
            uiState = uiState.copy(message = "云端源缓存已清除")
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            uiState = uiState.copy(clearingCache = true, message = null)
            runCatching { cacheManager.clearAll() }
                .onSuccess {
                    val usage = runCatching { cacheManager.usage() }.getOrNull()
                    uiState = uiState.copy(
                        clearingCache = false,
                        cacheUsage = usage,
                        message = "缓存已清空"
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        clearingCache = false,
                        message = "清空失败: ${error.message ?: error::class.java.simpleName}"
                    )
                }
        }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch {
            runCatching { cacheManager.usage() }
                .onSuccess { usage ->
                    uiState = uiState.copy(cacheUsage = usage)
                }
        }
    }
}
