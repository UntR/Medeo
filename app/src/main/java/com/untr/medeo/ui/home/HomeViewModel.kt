package com.untr.medeo.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotContentType
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.bestHotListMatchFor
import com.untr.medeo.data.model.defaultCategoryFilters
import com.untr.medeo.data.model.defaultTypeFilters
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.repo.ProgressRepository
import com.untr.medeo.data.repo.ContentRecoveryRepository
import com.untr.medeo.data.repo.ContentRecoveryResult
import com.untr.medeo.data.repo.isFinished
import com.untr.medeo.data.repo.toVodItem
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.HotListRepository
import com.untr.medeo.data.repo.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val contentType: HotContentType = HotContentType.MOVIE,
    val items: List<HotListItem> = emptyList(),
    val categoryFilters: List<HotFilter> = HotContentType.MOVIE.defaultCategoryFilters(),
    val typeFilters: List<HotFilter> = HotContentType.MOVIE.defaultTypeFilters(),
    val selectedCategory: String = HotContentType.MOVIE.defaultCategory,
    val selectedType: String = HotContentType.MOVIE.defaultType,
    val resolvingItemId: String? = null,
    val lookupMessage: String? = null,
    val manualSearchQuery: String? = null,
    val requiresSourceSetup: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val recoveryRepository: ContentRecoveryRepository,
    private val hotListRepository: HotListRepository,
    private val searchRepository: SearchRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val sourceCatalog: SourceCatalog,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    var uiState by mutableStateOf(HomeUiState())
        private set

    private val _openDetailEvents = MutableSharedFlow<VodItem>(extraBufferCapacity = 1)
    val openDetailEvents = _openDetailEvents.asSharedFlow()
    var recentProgress by mutableStateOf<WatchProgress?>(null)
        private set
    var recoveringRecent by mutableStateOf(false)
        private set
    private val _continueRecentEvents = MutableSharedFlow<VodItem>(extraBufferCapacity = 1)
    val continueRecentEvents = _continueRecentEvents.asSharedFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            progressRepository.observeAll().collect { recentProgress = it.maxByOrNull { progress -> progress.updatedAt } }
        }
        viewModelScope.launch {
            loadHotList(
                contentType = settingsStore.homeContentType(),
                restoreCachedSelection = true
            )
        }
    }

    fun continueRecent() {
        val progress = recentProgress ?: return
        if (recoveringRecent) return
        viewModelScope.launch {
            recoveringRecent = true
            try {
                when (val result = recoveryRepository.recover(progress.toVodItem())) {
                    is ContentRecoveryResult.Success -> {
                        detailSelectionStore.remember(result.candidates)
                        if (progress.isFinished()) _openDetailEvents.emit(result.primary)
                        else _continueRecentEvents.emit(result.primary)
                    }
                    ContentRecoveryResult.NoEnabledSources -> uiState = uiState.copy(lookupMessage = "尚未启用数据源", requiresSourceSetup = true)
                    ContentRecoveryResult.NetworkUnavailable -> uiState = uiState.copy(lookupMessage = "当前无网络连接，观看记录已保留", requiresSourceSetup = false)
                    ContentRecoveryResult.NotFound -> uiState = uiState.copy(lookupMessage = "暂未找到对应播放源，可再次点击重试，观看记录已保留", requiresSourceSetup = false)
                }
            } finally {
                recoveringRecent = false
            }
        }
    }

    fun refresh() {
        loadHotList(
            contentType = uiState.contentType,
            category = uiState.selectedCategory,
            type = uiState.selectedType
        )
    }

    private fun loadHotList(
        contentType: HotContentType,
        category: String? = null,
        type: String? = null,
        restoreCachedSelection: Boolean = false,
        persistContentType: Boolean = false
    ) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (persistContentType) {
                settingsStore.setHomeContentType(contentType)
            }
            val storedSelection = if (restoreCachedSelection) {
                hotListRepository.cachedSelection(contentType, freshOnly = false)
            } else {
                null
            }
            val selectedCategory = category
                ?: storedSelection?.category
                ?: contentType.defaultCategory
            val selectedType = type
                ?: storedSelection?.type
                ?: contentType.defaultType
            uiState = HomeUiState(
                loading = true,
                contentType = contentType,
                categoryFilters = contentType.defaultCategoryFilters(),
                typeFilters = contentType.defaultTypeFilters(selectedCategory),
                selectedCategory = selectedCategory,
                selectedType = selectedType
            )

            val cached = hotListRepository.cachedRecentHot(
                contentType = contentType,
                category = selectedCategory,
                type = selectedType
            )
            if (cached != null) {
                uiState = uiState.withHotListResult(
                    result = cached,
                    contentType = contentType,
                    category = selectedCategory,
                    type = selectedType,
                    error = null
                )
            }

            val network = networkMonitor.snapshot()
            if (!network.online) {
                val fallback = cached ?: hotListRepository.cachedRecentHot(
                    contentType = contentType,
                    category = selectedCategory,
                    type = selectedType,
                    freshOnly = false
                )
                uiState = if (fallback != null) {
                    uiState.withHotListResult(
                        result = fallback,
                        contentType = contentType,
                        category = selectedCategory,
                        type = selectedType,
                        error = null
                    )
                } else {
                    uiState.copy(
                        loading = false,
                        items = emptyList(),
                        selectedCategory = selectedCategory,
                        selectedType = selectedType,
                        resolvingItemId = null,
                        lookupMessage = null,
                        manualSearchQuery = null,
                        requiresSourceSetup = false,
                        error = "当前无网络连接，无法加载热榜"
                    )
                }
                return@launch
            }

            val result = hotListRepository.recentHot(
                contentType = contentType,
                category = selectedCategory,
                type = selectedType
            )
            uiState = uiState.withHotListResult(
                result = result,
                contentType = contentType,
                category = selectedCategory,
                type = selectedType,
                error = if (result.items.isEmpty()) "暂时没有加载到热榜内容" else null
            )
        }
    }

    fun selectContentType(contentType: HotContentType) {
        if (contentType != uiState.contentType) {
            loadHotList(
                contentType = contentType,
                restoreCachedSelection = true,
                persistContentType = true
            )
        }
    }

    fun selectCategory(category: String) {
        if (category != uiState.selectedCategory) {
            val defaultType = uiState.categoryFilters
                .firstOrNull { it.category == category }
                ?.type
                ?: uiState.contentType.defaultType
            loadHotList(
                contentType = uiState.contentType,
                category = category,
                type = defaultType
            )
        }
    }

    fun selectType(type: String) {
        if (type != uiState.selectedType) {
            loadHotList(
                contentType = uiState.contentType,
                category = uiState.selectedCategory,
                type = type
            )
        }
    }

    fun openHotItem(item: HotListItem) {
        if (uiState.resolvingItemId != null) return

        viewModelScope.launch {
            if (sourceCatalog.enabledSources().isEmpty()) {
                uiState = uiState.copy(
                    resolvingItemId = null,
                    lookupMessage = "尚未启用数据源",
                    manualSearchQuery = null,
                    requiresSourceSetup = true
                )
                return@launch
            }
            if (!networkMonitor.snapshot().online) {
                uiState = uiState.copy(
                    resolvingItemId = null,
                    lookupMessage = "当前无网络连接，无法匹配可播放源",
                    manualSearchQuery = null,
                    requiresSourceSetup = false
                )
                return@launch
            }

            uiState = uiState.copy(
                resolvingItemId = item.id,
                lookupMessage = "正在匹配《${item.title}》的可播放源",
                manualSearchQuery = null,
                requiresSourceSetup = false
            )

            val matched = searchRepository.search(item.title)
                .bestHotListMatchFor(item)

            if (matched == null) {
                uiState = uiState.copy(
                    resolvingItemId = null,
                    lookupMessage = "可播放源暂时没有可靠匹配到《${item.title}》",
                    manualSearchQuery = item.title,
                    requiresSourceSetup = false
                )
                return@launch
            }

            detailSelectionStore.remember(matched)
            uiState = uiState.copy(
                resolvingItemId = null,
                lookupMessage = null,
                manualSearchQuery = null,
                requiresSourceSetup = false
            )
            _openDetailEvents.emit(matched.primary)
        }
    }
}

private fun HomeUiState.withHotListResult(
    result: com.untr.medeo.data.model.HotListResult,
    contentType: HotContentType,
    category: String,
    type: String,
    error: String?
): HomeUiState =
    HomeUiState(
        loading = false,
        contentType = contentType,
        items = result.items,
        categoryFilters = result.categoryFilters.ifEmpty { categoryFilters },
        typeFilters = result.typeFilters.ifEmpty { typeFilters },
        selectedCategory = category,
        selectedType = type,
        error = error
    )
