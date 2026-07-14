package com.untr.medeo.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.untr.medeo.R
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.adaptive.AdaptiveWidthBox
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.LoadingState
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.components.VodListRow

@Composable
fun FavoritesScreen(
    onOpenDetail: (VodItem) -> Unit,
    onContinueRecent: (VodItem, Int?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val hasContent = state.items.isNotEmpty() || state.recentItems.isNotEmpty()
    var clearRecentConfirmationVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is FavoritesNavigationEvent.OpenDetail -> onOpenDetail(event.item)
                is FavoritesNavigationEvent.ContinueRecent -> {
                    onContinueRecent(event.item, event.nextEpisodeIndex)
                }
            }
        }
    }

    when {
        state.loading -> LoadingState("正在加载收藏", modifier)
        !hasContent -> MessageState("暂无收藏和最近观看", modifier)
        else -> FavoritesContent(
            state = state,
            onOpenFavorite = viewModel::openFavorite,
            onOpenRecentDetail = viewModel::openRecentDetail,
            onContinueRecent = viewModel::continueRecent,
            onDeleteRecent = viewModel::deleteRecent,
            onRequestClearRecent = { clearRecentConfirmationVisible = true },
            onOpenSettings = onOpenSettings,
            onDeleteFailedFavorite = viewModel::deleteFailedFavorite,
            windowClass = windowClass,
            modifier = modifier
        )
    }

    if (clearRecentConfirmationVisible && state.recentItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { clearRecentConfirmationVisible = false },
            title = { Text("清空最近观看？") },
            text = { Text("这只会删除本机的观看进度，不会删除收藏或缓存。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearRecentConfirmationVisible = false
                        viewModel.clearRecent()
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearRecentConfirmationVisible = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FavoritesContent(
    state: FavoritesUiState,
    onOpenFavorite: (VodItem) -> Unit,
    onOpenRecentDetail: (RecentWatchItem) -> Unit,
    onContinueRecent: (RecentWatchItem) -> Unit,
    onDeleteRecent: (RecentWatchItem) -> Unit,
    onRequestClearRecent: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteFailedFavorite: () -> Unit,
    windowClass: MedeoWindowClass,
    modifier: Modifier = Modifier
) {
    if (windowClass == MedeoWindowClass.Expanded) {
        TabletFavoritesContent(
            state = state,
            onOpenFavorite = onOpenFavorite,
            onOpenRecentDetail = onOpenRecentDetail,
            onContinueRecent = onContinueRecent,
            onDeleteRecent = onDeleteRecent,
            onRequestClearRecent = onRequestClearRecent,
            onOpenSettings = onOpenSettings,
            onDeleteFailedFavorite = onDeleteFailedFavorite,
            windowClass = windowClass,
            modifier = modifier
        )
    } else {
        PhoneFavoritesContent(
            state = state,
            onOpenFavorite = onOpenFavorite,
            onOpenRecentDetail = onOpenRecentDetail,
            onContinueRecent = onContinueRecent,
            onDeleteRecent = onDeleteRecent,
            onRequestClearRecent = onRequestClearRecent,
            onOpenSettings = onOpenSettings,
            onDeleteFailedFavorite = onDeleteFailedFavorite,
            windowClass = windowClass,
            modifier = modifier
        )
    }
}

@Composable
private fun PhoneFavoritesContent(
    state: FavoritesUiState,
    onOpenFavorite: (VodItem) -> Unit,
    onOpenRecentDetail: (RecentWatchItem) -> Unit,
    onContinueRecent: (RecentWatchItem) -> Unit,
    onDeleteRecent: (RecentWatchItem) -> Unit,
    onRequestClearRecent: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteFailedFavorite: () -> Unit,
    windowClass: MedeoWindowClass,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AdaptiveWidthBox(
            windowClass = windowClass,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FavoritesTitle()
                if (state.resolvingContentKey != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                RecoveryNotice(
                    message = state.recoveryMessage,
                    requiresSourceSetup = state.requiresSourceSetup,
                    canDeleteFavorite = state.failedFavorite != null,
                    onOpenSettings = onOpenSettings,
                    onDeleteFavorite = onDeleteFailedFavorite
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (state.recentItems.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "最近观看",
                                actionLabel = "清空",
                                onAction = onRequestClearRecent
                            )
                        }
                        items(state.recentItems, key = { "recent-${it.contentKey}" }) { item ->
                            RecentWatchRow(
                                item = item,
                                onClick = onOpenRecentDetail,
                                onContinue = onContinueRecent,
                                onDelete = onDeleteRecent
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
                            onClick = onOpenFavorite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletFavoritesContent(
    state: FavoritesUiState,
    onOpenFavorite: (VodItem) -> Unit,
    onOpenRecentDetail: (RecentWatchItem) -> Unit,
    onContinueRecent: (RecentWatchItem) -> Unit,
    onDeleteRecent: (RecentWatchItem) -> Unit,
    onRequestClearRecent: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteFailedFavorite: () -> Unit,
    windowClass: MedeoWindowClass,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AdaptiveWidthBox(
            windowClass = windowClass,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FavoritesTitle()
                if (state.resolvingContentKey != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                RecoveryNotice(
                    message = state.recoveryMessage,
                    requiresSourceSetup = state.requiresSourceSetup,
                    canDeleteFavorite = state.failedFavorite != null,
                    onOpenSettings = onOpenSettings,
                    onDeleteFavorite = onDeleteFailedFavorite
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (state.recentItems.isNotEmpty()) {
                        RecentPanel(
                            items = state.recentItems,
                            onOpenDetail = onOpenRecentDetail,
                            onContinueRecent = onContinueRecent,
                            onDeleteRecent = onDeleteRecent,
                            onRequestClearRecent = onRequestClearRecent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    if (state.items.isNotEmpty()) {
                        FavoritePanel(
                            items = state.items,
                            onOpenDetail = onOpenFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTitle() {
    Text(
        text = "收藏",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
    )
}

@Composable
private fun RecentPanel(
    items: List<RecentWatchItem>,
    onOpenDetail: (RecentWatchItem) -> Unit,
    onContinueRecent: (RecentWatchItem) -> Unit,
    onDeleteRecent: (RecentWatchItem) -> Unit,
    onRequestClearRecent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                SectionHeader(
                    title = "最近观看",
                    actionLabel = "清空",
                    onAction = onRequestClearRecent
                )
            }
            items(items, key = { "recent-panel-${it.contentKey}" }) { item ->
                RecentWatchRow(
                    item = item,
                    onClick = onOpenDetail,
                    onContinue = onContinueRecent,
                    onDelete = onDeleteRecent
                )
            }
        }
    }
}

@Composable
private fun FavoritePanel(
    items: List<VodItem>,
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                SectionHeader("我的收藏")
            }
            items(items, key = { "favorite-panel-${it.key}" }) { item ->
                VodListRow(
                    item = item,
                    onClick = onOpenDetail
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentWatchRow(
    item: RecentWatchItem,
    onClick: (RecentWatchItem) -> Unit,
    onContinue: (RecentWatchItem) -> Unit,
    onDelete: (RecentWatchItem) -> Unit
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
        Column {
            VodListRow(
                item = item.item,
                onClick = { onClick(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (item.finished) 0.62f else 1f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.finished) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "看完",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                TextButton(onClick = { onContinue(item) }) {
                    Text(item.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun RecoveryNotice(
    message: String?,
    requiresSourceSetup: Boolean,
    canDeleteFavorite: Boolean,
    onOpenSettings: () -> Unit,
    onDeleteFavorite: () -> Unit
) {
    if (message == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                requiresSourceSetup -> message
                canDeleteFavorite -> "$message。可重试或删除该收藏"
                else -> "$message。可重试或左滑删除记录"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        when {
            requiresSourceSetup -> TextButton(onClick = onOpenSettings) {
                Text("前往设置")
            }
            canDeleteFavorite -> TextButton(onClick = onDeleteFavorite) {
                Text("删除收藏")
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
