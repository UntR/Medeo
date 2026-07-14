package com.untr.medeo.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.repo.ContentRecoveryRepository
import com.untr.medeo.data.repo.ContentRecoveryResult
import com.untr.medeo.data.repo.DetailRepository
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.FavoriteRepository
import com.untr.medeo.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val details: List<VodDetail> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val progressByKey: Map<String, WatchProgress> = emptyMap(),
    val requiresSourceSetup: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRepository: DetailRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val contentRecoveryRepository: ContentRecoveryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val progressRepository: ProgressRepository,
    private val sourceCatalog: SourceCatalog,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    val sourceId: String = savedStateHandle["sourceId"] ?: ""
    val vodId: Long = savedStateHandle.get<String>("vodId")?.toLongOrNull() ?: -1L

    var uiState by mutableStateOf(DetailUiState())
        private set

    init {
        load()
        observeFavorites()
        observeProgress()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(
                loading = true,
                details = emptyList(),
                requiresSourceSetup = false,
                error = null
            )
            if (sourceId.isBlank() || vodId <= 0L) {
                uiState = uiState.copy(loading = false, error = "详情参数无效")
                return@launch
            }
            val enabledSources = sourceCatalog.enabledSources()
            if (enabledSources.isEmpty()) {
                uiState = uiState.copy(
                    loading = false,
                    requiresSourceSetup = true,
                    error = "尚未启用数据源"
                )
                return@launch
            }
            if (!networkMonitor.snapshot().online) {
                uiState = uiState.copy(
                    loading = false,
                    error = "当前无网络连接，无法加载详情"
                )
                return@launch
            }

            val remembered = detailSelectionStore.candidates(sourceId, vodId)
            val candidates = if (remembered.isNotEmpty()) {
                remembered
            } else {
                val source = sourceCatalog.sourceById(sourceId)
                if (source == null) {
                    uiState = uiState.copy(loading = false, error = "未知数据源")
                    return@launch
                }
                listOf(
                    VodItem(
                        sourceId = source.id,
                        sourceName = source.name,
                        vodId = vodId,
                        name = "",
                        pic = null,
                        year = null,
                        area = null,
                        typeName = null,
                        remarks = null
                    )
                )
            }

            val enabledIds = enabledSources.mapTo(hashSetOf()) { source -> source.id }
            val enabledCandidates = candidates.filter { item -> item.sourceId in enabledIds }
            val result = if (candidates.size == 1) {
                contentRecoveryRepository.recover(candidates.first())
            } else {
                val details = detailRepository.details(enabledCandidates)
                if (details.isNotEmpty()) {
                    ContentRecoveryResult.Success(
                        candidates = details.map { detail -> detail.item },
                        details = details,
                        usedFallback = false
                    )
                } else {
                    contentRecoveryRepository.recover(candidates.first())
                }
            }
            when (result) {
                is ContentRecoveryResult.Success -> {
                    detailSelectionStore.remember(result.candidates)
                    uiState = uiState.copy(
                        loading = false,
                        details = result.details
                    )
                }
                ContentRecoveryResult.NoEnabledSources -> {
                    uiState = uiState.copy(
                        loading = false,
                        requiresSourceSetup = true,
                        error = "尚未启用数据源"
                    )
                }
                ContentRecoveryResult.NetworkUnavailable -> {
                    uiState = uiState.copy(
                        loading = false,
                        error = "当前无网络连接，无法加载详情"
                    )
                }
                ContentRecoveryResult.NotFound -> {
                    uiState = uiState.copy(
                        loading = false,
                        error = "详情加载失败，其他启用源也没有精确匹配到该内容"
                    )
                }
            }
        }
    }

    fun toggleFavorite(detail: VodDetail) {
        viewModelScope.launch {
            val isFavorite = detail.item.contentKey in uiState.favoriteKeys ||
                detail.item.key in uiState.favoriteKeys
            favoriteRepository.setFavorite(detail.item, !isFavorite)
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.observeFavoriteKeys().collect { keys ->
                uiState = uiState.copy(favoriteKeys = keys)
            }
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.observeAllByKey().collect { progressByKey ->
                uiState = uiState.copy(progressByKey = progressByKey)
            }
        }
    }
}
