package com.czpn7.ying.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.data.model.AggregatedResult
import com.czpn7.ying.data.net.NetworkMonitor
import com.czpn7.ying.data.repo.DetailSelectionStore
import com.czpn7.ying.data.repo.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val results: List<AggregatedResult> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    var uiState by mutableStateOf(SearchUiState())
        private set

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        uiState = uiState.copy(query = query)
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            uiState = SearchUiState(query = query)
            return
        }

        searchJob = viewModelScope.launch {
            delay(500)
            if (!networkMonitor.snapshot().online) {
                uiState = SearchUiState(
                    query = query,
                    error = "当前无网络连接，无法搜索"
                )
                return@launch
            }

            uiState = SearchUiState(query = query, loading = true)
            searchRepository.searchProgress(trimmed).collect { progress ->
                uiState = SearchUiState(
                    query = query,
                    loading = progress.loading,
                    completedSources = progress.completedSources,
                    totalSources = progress.totalSources,
                    results = progress.results,
                    error = when {
                        progress.loading -> null
                        progress.totalSources == 0 -> "没有启用数据源"
                        progress.failedSources == progress.totalSources -> "所有数据源请求失败，请检查网络或稍后重试"
                        progress.results.isEmpty() -> "没有找到结果"
                        else -> null
                    }
                )
            }
        }
    }

    fun retry() {
        val query = uiState.query
        if (query.isNotBlank()) {
            onQueryChange(query)
        }
    }

    fun rememberForDetail(item: VodItem) {
        val result = uiState.results.firstOrNull { it.primary.key == item.key }
        if (result != null) {
            detailSelectionStore.remember(result)
        } else {
            detailSelectionStore.remember(item)
        }
    }
}
