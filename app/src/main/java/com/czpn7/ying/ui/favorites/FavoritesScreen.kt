package com.czpn7.ying.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.ui.components.LoadingState
import com.czpn7.ying.ui.components.MessageState
import com.czpn7.ying.ui.components.VodListRow

@Composable
fun FavoritesScreen(
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    when {
        state.loading -> LoadingState("正在加载收藏", modifier)
        state.items.isEmpty() -> MessageState("暂无收藏", modifier)
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "收藏",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(state.items, key = { it.key }) { item ->
                    VodListRow(
                        item = item,
                        onClick = {
                            viewModel.rememberForDetail(item)
                            onOpenDetail(item)
                        }
                    )
                }
            }
        }
    }
}
