package com.untr.medeo.ui.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.FavoriteRepository
import com.untr.medeo.data.repo.ProgressRepository
import com.untr.medeo.data.repo.toVodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val items: List<VodItem> = emptyList(),
    val recentItems: List<VodItem> = emptyList()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val progressRepository: ProgressRepository,
    private val detailSelectionStore: DetailSelectionStore
) : ViewModel() {
    var uiState by mutableStateOf(FavoritesUiState())
        private set

    init {
        viewModelScope.launch {
            combine(
                favoriteRepository.observeFavorites(),
                progressRepository.observeAllByKey()
            ) { favorites, progressByKey ->
                uiState = FavoritesUiState(
                    loading = false,
                    items = favorites.map { it.toVodItem() },
                    recentItems = progressByKey.values
                        .sortedByDescending { it.updatedAt }
                        .take(RECENT_WATCH_LIMIT)
                        .map { it.toVodItem() }
                )
            }.collect {}
        }
    }

    fun rememberForDetail(item: VodItem) {
        detailSelectionStore.remember(item)
    }

    fun deleteRecent(item: VodItem) {
        viewModelScope.launch {
            progressRepository.delete(item.key)
        }
    }

    private companion object {
        const val RECENT_WATCH_LIMIT = 10
    }
}
