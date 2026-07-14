package com.untr.medeo.ui.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.repo.ContentRecoveryRepository
import com.untr.medeo.data.repo.ContentRecoveryResult
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.FavoriteRepository
import com.untr.medeo.data.repo.ProgressRepository
import com.untr.medeo.data.repo.isFinished
import com.untr.medeo.data.repo.nextEpisodeIndexIfFinished
import com.untr.medeo.data.repo.toVodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val items: List<VodItem> = emptyList(),
    val recentItems: List<RecentWatchItem> = emptyList(),
    val resolvingContentKey: String? = null,
    val recoveryMessage: String? = null,
    val requiresSourceSetup: Boolean = false,
    val failedFavorite: VodItem? = null
)

data class RecentWatchItem(
    val contentKey: String,
    val item: VodItem,
    val finished: Boolean,
    val nextEpisodeIndex: Int?
) {
    val actionLabel: String get() = if (finished) "看下一集" else "继续播放"
}

sealed interface FavoritesNavigationEvent {
    data class OpenDetail(val item: VodItem) : FavoritesNavigationEvent
    data class ContinueRecent(
        val item: VodItem,
        val nextEpisodeIndex: Int?
    ) : FavoritesNavigationEvent
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val progressRepository: ProgressRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val contentRecoveryRepository: ContentRecoveryRepository
) : ViewModel() {
    var uiState by mutableStateOf(FavoritesUiState())
        private set

    private val _navigationEvents = MutableSharedFlow<FavoritesNavigationEvent>(
        extraBufferCapacity = 1
    )
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                favoriteRepository.observeFavorites(),
                progressRepository.observeAll()
            ) { favorites, progresses ->
                uiState = uiState.copy(
                    loading = false,
                    items = favorites.map { it.toVodItem() },
                    recentItems = progresses
                        .sortedByDescending { it.updatedAt }
                        .take(RECENT_WATCH_LIMIT)
                        .map { progress ->
                            RecentWatchItem(
                                contentKey = progress.contentKey,
                                item = progress.toVodItem(),
                                finished = progress.isFinished(),
                                nextEpisodeIndex = progress.nextEpisodeIndexIfFinished()
                            )
                        }
                )
            }.collect {}
        }
    }

    fun openFavorite(item: VodItem) {
        recover(item, failedFavorite = item) { recovered ->
            FavoritesNavigationEvent.OpenDetail(recovered)
        }
    }

    fun openRecentDetail(item: RecentWatchItem) {
        recover(item.item) { recovered ->
            FavoritesNavigationEvent.OpenDetail(recovered)
        }
    }

    fun continueRecent(item: RecentWatchItem) {
        recover(item.item) { recovered ->
            FavoritesNavigationEvent.ContinueRecent(recovered, item.nextEpisodeIndex)
        }
    }

    fun deleteRecent(item: RecentWatchItem) {
        viewModelScope.launch {
            progressRepository.delete(item.contentKey)
        }
    }

    fun clearRecent() {
        viewModelScope.launch {
            progressRepository.clearAll()
        }
    }

    fun deleteFailedFavorite() {
        val item = uiState.failedFavorite ?: return
        viewModelScope.launch {
            favoriteRepository.setFavorite(item, false)
            uiState = uiState.copy(recoveryMessage = null, failedFavorite = null)
        }
    }

    private fun recover(
        item: VodItem,
        failedFavorite: VodItem? = null,
        event: (VodItem) -> FavoritesNavigationEvent
    ) {
        if (uiState.resolvingContentKey != null) return
        viewModelScope.launch {
            uiState = uiState.copy(
                resolvingContentKey = item.contentKey,
                recoveryMessage = null,
                requiresSourceSetup = false,
                failedFavorite = null
            )
            when (val result = contentRecoveryRepository.recover(item)) {
                is ContentRecoveryResult.Success -> {
                    detailSelectionStore.remember(result.candidates)
                    uiState = uiState.copy(resolvingContentKey = null)
                    _navigationEvents.emit(event(result.primary))
                }
                ContentRecoveryResult.NoEnabledSources -> {
                    uiState = uiState.copy(
                        resolvingContentKey = null,
                        recoveryMessage = "尚未启用数据源",
                        requiresSourceSetup = true,
                        failedFavorite = failedFavorite
                    )
                }
                ContentRecoveryResult.NetworkUnavailable -> {
                    uiState = uiState.copy(
                        resolvingContentKey = null,
                        recoveryMessage = "当前无网络连接，无法恢复该内容",
                        requiresSourceSetup = false,
                        failedFavorite = failedFavorite
                    )
                }
                ContentRecoveryResult.NotFound -> {
                    uiState = uiState.copy(
                        resolvingContentKey = null,
                        recoveryMessage = "原数据源不可用，其他启用源也没有精确匹配到该内容",
                        requiresSourceSetup = false,
                        failedFavorite = failedFavorite
                    )
                }
            }
        }
    }

    private companion object {
        const val RECENT_WATCH_LIMIT = 10
    }
}
