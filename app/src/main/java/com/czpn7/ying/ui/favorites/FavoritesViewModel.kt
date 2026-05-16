package com.czpn7.ying.ui.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.data.repo.DetailSelectionStore
import com.czpn7.ying.data.repo.FavoriteRepository
import com.czpn7.ying.data.repo.toVodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val items: List<VodItem> = emptyList()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val detailSelectionStore: DetailSelectionStore
) : ViewModel() {
    var uiState by mutableStateOf(FavoritesUiState())
        private set

    init {
        viewModelScope.launch {
            favoriteRepository.observeFavorites().collect { favorites ->
                uiState = FavoritesUiState(
                    loading = false,
                    items = favorites.map { it.toVodItem() }
                )
            }
        }
    }

    fun rememberForDetail(item: VodItem) {
        detailSelectionStore.remember(item)
    }
}
