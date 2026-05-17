package com.untr.medeo.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val history: List<String> = emptyList(),
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
    private val networkMonitor: NetworkMonitor,
    private val settingsStore: SettingsStore
) : ViewModel() {
    var uiState by mutableStateOf(SearchUiState())
        private set

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.searchHistory.collect { history ->
                uiState = uiState.copy(history = history)
            }
        }
    }

    fun onQueryChange(query: String) {
        uiState = uiState.copy(query = query)
    }

    fun submitSearch(query: String = uiState.query) {
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            uiState = uiState.copy(
                query = query,
                submittedQuery = "",
                loading = false,
                completedSources = 0,
                totalSources = 0,
                results = emptyList(),
                error = null
            )
            return
        }

        searchJob = viewModelScope.launch {
            if (!networkMonitor.snapshot().online) {
                uiState = uiState.copy(
                    query = query,
                    submittedQuery = trimmed,
                    loading = false,
                    completedSources = 0,
                    totalSources = 0,
                    results = emptyList(),
                    error = "当前无网络连接，无法搜索"
                )
                return@launch
            }

            settingsStore.addSearchHistory(trimmed)
            uiState = uiState.copy(
                query = query,
                submittedQuery = trimmed,
                loading = true,
                completedSources = 0,
                totalSources = 0,
                results = emptyList(),
                error = null
            )
            searchRepository.searchProgress(trimmed).collect { progress ->
                uiState = uiState.copy(
                    query = query,
                    submittedQuery = trimmed,
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

    fun useHistory(query: String) {
        uiState = uiState.copy(query = query)
        submitSearch(query)
    }

    fun clearHistory() {
        viewModelScope.launch {
            settingsStore.clearSearchHistory()
        }
    }

    fun retry() {
        val query = uiState.query
        if (query.isNotBlank()) {
            submitSearch(query)
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
