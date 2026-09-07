package com.untr.medeo.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.untr.medeo.R
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.adaptive.AdaptiveWidthBox
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.InstantTabItem
import com.untr.medeo.ui.components.InstantTabRow
import com.untr.medeo.ui.components.VodListRow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FavoritesScreen(
    onOpenDetail: (VodItem) -> Unit,
    onContinueRecent: (VodItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    initiallyShowHistory: Boolean = false,
    onBack: (() -> Unit)? = null,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    var showHistory by rememberSaveable { mutableStateOf(initiallyShowHistory) }
    var query by rememberSaveable { mutableStateOf("") }
    var clearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is FavoritesNavigationEvent.OpenDetail -> onOpenDetail(event.item)
                is FavoritesNavigationEvent.ContinueRecent -> onContinueRecent(event.item)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collectLatest { item ->
            snackbarHost.currentSnackbarData?.dismiss()
            if (snackbarHost.showSnackbar(
                    message = "已删除 ${item.item.name} 的观看记录",
                    actionLabel = "撤销",
                    withDismissAction = true
                ) == SnackbarResult.ActionPerformed
            ) viewModel.undoDelete(item)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        AdaptiveWidthBox(windowClass = windowClass, modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                        }
                    }
                    Text("收藏与记录", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp))
                }
                InstantTabRow(
                    items = listOf(InstantTabItem("favorites", "我的收藏"), InstantTabItem("history", "观看记录")),
                    selectedIndex = if (showHistory) 1 else 0,
                    onSelected = { showHistory = it == 1 }
                )
                if (state.loading || state.resolvingContentKey != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (showHistory) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索观看记录中的片名") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    state.recoveryMessage?.let { message ->
                        item {
                            Column {
                                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state.requiresSourceSetup) {
                                    TextButton(onClick = onOpenSettings) { Text("前往设置") }
                                } else if (state.failedFavorite != null) {
                                    TextButton(onClick = viewModel::deleteFailedFavorite) { Text("删除收藏") }
                                }
                                Text("可再次点击条目重试", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (showHistory) {
                        val visibleItems = filterWatchHistory(state.recentItems, query)
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${state.recentItems.size} 条观看记录", modifier = Modifier.weight(1f))
                                if (state.recentItems.isNotEmpty()) {
                                    TextButton(onClick = { clearConfirmationVisible = true }) { Text("清空全部") }
                                }
                            }
                        }
                        if (!state.loading && visibleItems.isEmpty()) {
                            item { Text(if (query.isBlank()) "暂无观看记录" else "没有匹配的观看记录") }
                        }
                        items(visibleItems, key = { it.contentKey }) { item ->
                            RecentWatchRow(item, viewModel::openRecentDetail, viewModel::continueRecent, viewModel::deleteRecent)
                        }
                    } else {
                        if (!state.loading && state.items.isEmpty()) item { Text("暂无收藏，可在详情页加入收藏") }
                        items(state.items, key = { it.key }) { item ->
                            VodListRow(item = item, onClick = viewModel::openFavorite)
                        }
                    }
                }
            }
        }
    }
    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text("清空全部观看记录？") },
            text = { Text("这只会删除本机的全部观看进度（包含搜索未显示的记录），不会删除收藏或缓存。") },
            confirmButton = {
                TextButton(onClick = { clearConfirmationVisible = false; viewModel.clearRecent() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { clearConfirmationVisible = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentWatchRow(
    item: RecentWatchItem,
    onClick: (RecentWatchItem) -> Unit,
    onContinue: (RecentWatchItem) -> Unit,
    onDelete: (RecentWatchItem) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete(item)
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clearAndSetSemantics {}.clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer).padding(22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Text("删除记录")
                }
            }
        }
    ) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)) {
            VodListRow(item = item.item, onClick = { onClick(item) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDelete(item) }) { Text("删除记录") }
                TextButton(onClick = { onContinue(item) }) { Text(item.actionLabel) }
            }
        }
    }
}
