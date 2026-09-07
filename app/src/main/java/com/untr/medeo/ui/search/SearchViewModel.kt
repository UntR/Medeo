package com.untr.medeo.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
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
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val failedSources: Int = 0,
    val results: List<AggregatedResult> = emptyList(),
    val requiresSourceSetup: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val networkMonitor: NetworkMonitor,
    private val settingsStore: SettingsStore
) : ViewModel() {
    var uiState by mutableStateOf(SearchUiState())
        private set

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        val initialQuery = savedStateHandle.get<String>("query").orEmpty()
        viewModelScope.launch {
            settingsStore.searchHistory.collect { history ->
                uiState = uiState.copy(history = history)
            }
        }
        if (initialQuery.isNotBlank()) {
            uiState = uiState.copy(query = initialQuery)
            submitSearch(initialQuery)
        }
    }

    fun onQueryChange(query: String) {
        uiState = uiState.copy(query = query)
    }

    fun submitSearch(query: String = uiState.query) {
        searchJob?.cancel()
        loadMoreJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            uiState = uiState.copy(
                query = query,
                submittedQuery = "",
                loading = false,
                loadingMore = false,
                hasMore = false,
                completedSources = 0,
                totalSources = 0,
                failedSources = 0,
                results = emptyList(),
                requiresSourceSetup = false,
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
                    loadingMore = false,
                    hasMore = false,
                    completedSources = 0,
                    totalSources = 0,
                    failedSources = 0,
                    results = emptyList(),
                    requiresSourceSetup = false,
                    error = "当前无网络连接，无法搜索"
                )
                return@launch
            }

            settingsStore.addSearchHistory(trimmed)
            uiState = uiState.copy(
                query = query,
                submittedQuery = trimmed,
                loading = true,
                loadingMore = false,
                hasMore = false,
                completedSources = 0,
                totalSources = 0,
                failedSources = 0,
                results = emptyList(),
                requiresSourceSetup = false,
                error = null
            )
            searchRepository.searchProgress(trimmed).collect { progress ->
                uiState = uiState.copy(
                    query = query,
                    submittedQuery = trimmed,
                    loading = progress.loading,
                    loadingMore = progress.loadingMore,
                    hasMore = progress.hasMore,
                    completedSources = progress.completedSources,
                    totalSources = progress.totalSources,
                    failedSources = progress.failedSources,
                    results = progress.results,
                    requiresSourceSetup = !progress.loading && progress.totalSources == 0,
                    error = when {
                        progress.loading || progress.loadingMore -> null
                        progress.totalSources == 0 -> "尚未启用数据源"
                        progress.results.isEmpty() && progress.failedSources == progress.totalSources -> "所有数据源请求失败，请检查网络或稍后重试"
                        progress.results.isEmpty() -> "没有找到结果"
                        else -> null
                    }
                )
            }
        }
    }

    fun loadMore() {
        val trimmed = uiState.submittedQuery.trim()
        if (
            trimmed.isBlank() ||
            uiState.loading ||
            uiState.loadingMore ||
            !uiState.hasMore
        ) {
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            if (!networkMonitor.snapshot().online) {
                uiState = uiState.copy(loadingMore = false)
                return@launch
            }

            searchRepository.searchProgress(trimmed, loadMore = true).collect { progress ->
                uiState = uiState.copy(
                    loading = false,
                    loadingMore = progress.loadingMore,
                    hasMore = progress.hasMore,
                    completedSources = progress.completedSources,
                    totalSources = progress.totalSources,
                    failedSources = progress.failedSources,
                    results = progress.results,
                    error = when {
                        progress.loadingMore -> null
                        progress.results.isEmpty() && progress.failedSources == progress.totalSources -> "所有数据源请求失败，请检查网络或稍后重试"
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
        val result = uiState.results.matchingResultFor(item)
        if (result != null) {
            detailSelectionStore.remember(result)
        } else {
            detailSelectionStore.remember(item)
        }
    }
}

internal fun List<AggregatedResult>.matchingResultFor(item: VodItem): AggregatedResult? =
    firstOrNull { result -> result.perSource.any { candidate -> candidate.key == item.key } }

internal fun SearchUiState.sourceStatusLabel(): String {
    val successes = (completedSources - failedSources).coerceAtLeast(0)
    val phase = when {
        loadingMore -> "加载更多"
        loading -> "正在搜索"
        else -> "搜索完成"
    }
    val counts = "$successes 个源成功、$failedSources 个失败"
    return if (loading || loadingMore) "$phase：$completedSources/$totalSources 个源已处理，$counts"
    else "$phase，$counts" + if (failedSources > 0) "，结果可能不完整" else ""
}
