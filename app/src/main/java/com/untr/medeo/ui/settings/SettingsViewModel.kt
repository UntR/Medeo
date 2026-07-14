package com.untr.medeo.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.diagnostics.DiagnosticLogger
import com.untr.medeo.data.diagnostics.DiagnosticLogStatus
import com.untr.medeo.data.local.AppThemeMode
import com.untr.medeo.data.local.AppSettings
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.local.SourceHealthRecord
import com.untr.medeo.data.repo.CacheManager
import com.untr.medeo.data.repo.CacheUsage
import com.untr.medeo.data.repo.SourceUpdateRepository
import com.untr.medeo.data.repo.SourceHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings? = null,
    val sources: List<VodSource> = emptyList(),
    val sourceManifestUrlDraft: String = "",
    val cacheUsage: CacheUsage? = null,
    val refreshingSources: Boolean = false,
    val sourceHealth: Map<String, SourceHealthRecord> = emptyMap(),
    val testingSourceIds: Set<String> = emptySet(),
    val sourceHealthMessage: String? = null,
    val clearingCache: Boolean = false,
    val diagnosticLogStatus: DiagnosticLogStatus = DiagnosticLogStatus(),
    val diagnosticMessage: String? = null,
    val message: String? = null
)

@UnstableApi
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val cacheManager: CacheManager,
    private val sourceCatalog: SourceCatalog,
    private val sourceUpdateRepository: SourceUpdateRepository,
    private val sourceHealthRepository: SourceHealthRepository,
    private val diagnosticLogger: DiagnosticLogger
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
        viewModelScope.launch {
            settingsStore.sourceHealth.collect { sourceHealth ->
                uiState = uiState.copy(sourceHealth = sourceHealth)
            }
        }
        viewModelScope.launch {
            diagnosticLogger.status.collect { status ->
                uiState = uiState.copy(diagnosticLogStatus = status)
            }
        }
        refreshCacheUsage()
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setSourceEnabled(sourceId, enabled)
        }
    }

    fun testSource(source: VodSource) {
        if (source.id in uiState.testingSourceIds) return
        uiState = uiState.copy(
            testingSourceIds = uiState.testingSourceIds + source.id,
            sourceHealthMessage = null
        )
        viewModelScope.launch {
            runCatching { sourceHealthRepository.test(source) }
                .onSuccess { record ->
                    uiState = uiState.copy(
                        sourceHealth = uiState.sourceHealth + (source.id to record)
                    )
                }
                .onFailure {
                    uiState = uiState.copy(sourceHealthMessage = "数据源测试失败，请稍后重试")
                }
            uiState = uiState.copy(
                testingSourceIds = uiState.testingSourceIds - source.id
            )
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

    fun setDiagnosticLogging(enabled: Boolean) {
        if (enabled) {
            diagnosticLogger.start()
        } else {
            diagnosticLogger.stop()
        }
        uiState = uiState.copy(
            diagnosticMessage = if (enabled) "诊断日志已开启" else "诊断日志已关闭"
        )
    }

    fun exportDiagnosticLog(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = diagnosticLogger.export()
            if (file == null) {
                uiState = uiState.copy(diagnosticMessage = "没有可导出的诊断日志")
            } else {
                uiState = uiState.copy(diagnosticMessage = null)
                onReady(file)
            }
        }
    }

    fun clearDiagnosticLog() {
        viewModelScope.launch {
            diagnosticLogger.stop()
            diagnosticLogger.clear()
            uiState = uiState.copy(diagnosticMessage = "诊断日志已关闭并清除")
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
