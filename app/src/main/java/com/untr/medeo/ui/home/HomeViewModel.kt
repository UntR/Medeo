package com.untr.medeo.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.model.DEFAULT_HOT_CATEGORY
import com.untr.medeo.data.model.DEFAULT_HOT_CATEGORY_FILTERS
import com.untr.medeo.data.model.DEFAULT_HOT_TYPE
import com.untr.medeo.data.model.DEFAULT_HOT_TYPE_FILTERS
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.bestHotListMatchFor
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
    val items: List<HotListItem> = emptyList(),
    val categoryFilters: List<HotFilter> = DEFAULT_HOT_CATEGORY_FILTERS,
    val typeFilters: List<HotFilter> = DEFAULT_HOT_TYPE_FILTERS,
    val selectedCategory: String = DEFAULT_HOT_CATEGORY,
    val selectedType: String = DEFAULT_HOT_TYPE,
    val resolvingItemId: String? = null,
    val lookupMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hotListRepository: HotListRepository,
    private val searchRepository: SearchRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    var uiState by mutableStateOf(HomeUiState())
        private set

    private val _openDetailEvents = MutableSharedFlow<VodItem>(extraBufferCapacity = 1)
    val openDetailEvents = _openDetailEvents.asSharedFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh(
        category: String = uiState.selectedCategory,
        type: String = uiState.selectedType
    ) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val network = networkMonitor.snapshot()
            if (!network.online) {
                uiState = uiState.copy(
                    loading = false,
                    items = emptyList(),
                    selectedCategory = category,
                    selectedType = type,
                    resolvingItemId = null,
                    lookupMessage = null,
                    error = "当前无网络连接，无法加载热榜"
                )
                return@launch
            }

            uiState = uiState.copy(
                loading = true,
                selectedCategory = category,
                selectedType = type,
                lookupMessage = null,
                error = null
            )
            val result = hotListRepository.recentHot(category = category, type = type)
            uiState = HomeUiState(
                loading = false,
                items = result.items,
                categoryFilters = result.categoryFilters.ifEmpty { uiState.categoryFilters },
                typeFilters = result.typeFilters.ifEmpty { uiState.typeFilters },
                selectedCategory = category,
                selectedType = type,
                error = if (result.items.isEmpty()) "暂时没有加载到热榜内容" else null
            )
        }
    }

    fun selectCategory(category: String) {
        if (category != uiState.selectedCategory) {
            refresh(category = category, type = DEFAULT_HOT_TYPE)
        }
    }

    fun selectType(type: String) {
        if (type != uiState.selectedType) {
            refresh(category = uiState.selectedCategory, type = type)
        }
    }

    fun openHotItem(item: HotListItem) {
        if (uiState.resolvingItemId != null) return

        viewModelScope.launch {
            if (!networkMonitor.snapshot().online) {
                uiState = uiState.copy(
                    resolvingItemId = null,
                    lookupMessage = "当前无网络连接，无法匹配可播放源"
                )
                return@launch
            }

            uiState = uiState.copy(
                resolvingItemId = item.id,
                lookupMessage = "正在匹配《${item.title}》的可播放源"
            )

            val matched = searchRepository.search(item.title)
                .bestHotListMatchFor(item)

            if (matched == null) {
                uiState = uiState.copy(
                    resolvingItemId = null,
                    lookupMessage = "可播放源暂时没有可靠匹配到《${item.title}》，请使用搜索页手动确认"
                )
                return@launch
            }

            detailSelectionStore.remember(matched)
            uiState = uiState.copy(
                resolvingItemId = null,
                lookupMessage = null
            )
            _openDetailEvents.emit(matched.primary)
        }
    }
}
