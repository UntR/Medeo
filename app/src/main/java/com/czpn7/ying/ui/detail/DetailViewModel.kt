package com.czpn7.ying.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.czpn7.ying.data.api.SourceCatalog
import com.czpn7.ying.data.model.VodDetail
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.data.local.WatchProgress
import com.czpn7.ying.data.net.NetworkMonitor
import com.czpn7.ying.data.repo.DetailRepository
import com.czpn7.ying.data.repo.DetailSelectionStore
import com.czpn7.ying.data.repo.FavoriteRepository
import com.czpn7.ying.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val details: List<VodDetail> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val progressByKey: Map<String, WatchProgress> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRepository: DetailRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val favoriteRepository: FavoriteRepository,
    private val progressRepository: ProgressRepository,
    private val sourceCatalog: SourceCatalog,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    val sourceId: String = checkNotNull(savedStateHandle["sourceId"])
    val vodId: Long = checkNotNull<String>(savedStateHandle["vodId"]).toLong()

    var uiState by mutableStateOf(DetailUiState())
        private set

    init {
        load()
        observeFavorites()
        observeProgress()
    }

    fun load() {
        viewModelScope.launch {
            uiState = DetailUiState(loading = true)
            val source = sourceCatalog.sourceById(sourceId)
            if (source == null) {
                uiState = DetailUiState(loading = false, error = "未知数据源")
                return@launch
            }
            if (!networkMonitor.snapshot().online) {
                uiState = DetailUiState(loading = false, error = "当前无网络连接，无法加载详情")
                return@launch
            }

            val candidates = detailSelectionStore.candidates(sourceId, vodId)
                .ifEmpty {
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
            val details = detailRepository.details(candidates)
            uiState = DetailUiState(
                loading = false,
                details = details,
                error = if (details.isEmpty()) "详情加载失败或暂无可播放线路" else null
            )
        }
    }

    fun toggleFavorite(detail: VodDetail) {
        viewModelScope.launch {
            val isFavorite = detail.item.key in uiState.favoriteKeys
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
