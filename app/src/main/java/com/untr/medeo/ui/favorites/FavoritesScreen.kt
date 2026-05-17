package com.untr.medeo.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.untr.medeo.R
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.components.LoadingState
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.components.VodListRow

@Composable
fun FavoritesScreen(
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val hasContent = state.items.isNotEmpty() || state.recentItems.isNotEmpty()

    when {
        state.loading -> LoadingState("正在加载收藏", modifier)
        !hasContent -> MessageState("暂无收藏和最近观看", modifier)
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
                if (state.recentItems.isNotEmpty()) {
                    item {
                        SectionHeader("最近观看")
                    }
                    items(state.recentItems, key = { "recent-${it.key}" }) { item ->
                        RecentWatchRow(
                            item = item,
                            onClick = { selected ->
                                viewModel.rememberForDetail(selected)
                                onOpenDetail(selected)
                            },
                            onDelete = viewModel::deleteRecent
                        )
                    }
                }
                if (state.items.isNotEmpty()) {
                    item {
                        SectionHeader("我的收藏")
                    }
                }
                items(state.items, key = { "favorite-${it.key}" }) { item ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentWatchRow(
    item: VodItem,
    onClick: (VodItem) -> Unit,
    onDelete: (VodItem) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "删除记录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        VodListRow(
            item = item,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    )
}
