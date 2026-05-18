package com.untr.medeo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.untr.medeo.R
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.adaptive.AdaptiveWidthBox
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.components.VodListRow
import com.untr.medeo.ui.components.rememberMedeoImageRequest

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val items = state.results.map { it.primary }
    val openDetail: (VodItem) -> Unit = { item ->
        viewModel.rememberForDetail(item)
        onOpenDetail(item)
    }

    if (windowClass == MedeoWindowClass.Expanded) {
        TabletSearchContent(
            state = state,
            items = items,
            onBack = onBack,
            onOpenDetail = openDetail,
            onQueryChange = viewModel::onQueryChange,
            onSubmitSearch = { viewModel.submitSearch() },
            onRetry = viewModel::retry,
            onHistoryClick = viewModel::useHistory,
            onClearHistory = viewModel::clearHistory,
            windowClass = windowClass,
            modifier = modifier
        )
    } else {
        PhoneSearchContent(
            state = state,
            items = items,
            onBack = onBack,
            onOpenDetail = openDetail,
            onQueryChange = viewModel::onQueryChange,
            onSubmitSearch = { viewModel.submitSearch() },
            onRetry = viewModel::retry,
            onHistoryClick = viewModel::useHistory,
            onClearHistory = viewModel::clearHistory,
            windowClass = windowClass,
            modifier = modifier
        )
    }
}

@Composable
private fun PhoneSearchContent(
    state: SearchUiState,
    items: List<VodItem>,
    onBack: () -> Unit,
    onOpenDetail: (VodItem) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRetry: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
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
                SearchTopBar(onBack = onBack)
                SearchField(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSubmitSearch = onSubmitSearch
                )
                SearchProgress(state = state)
                SearchBody(
                    state = state,
                    items = items,
                    onOpenDetail = onOpenDetail,
                    onRetry = onRetry,
                    onHistoryClick = onHistoryClick,
                    onClearHistory = onClearHistory,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TabletSearchContent(
    state: SearchUiState,
    items: List<VodItem>,
    onBack: () -> Unit,
    onOpenDetail: (VodItem) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRetry: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.12f)
                        .fillMaxHeight()
                ) {
                    SearchTopBar(onBack = onBack)
                    SearchField(
                        state = state,
                        onQueryChange = onQueryChange,
                        onSubmitSearch = onSubmitSearch
                    )
                    SearchProgress(state = state)
                    SearchBody(
                        state = state,
                        items = items,
                        onOpenDetail = onOpenDetail,
                        onRetry = onRetry,
                        onHistoryClick = onHistoryClick,
                        onClearHistory = onClearHistory,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SearchPreviewPanel(
                    item = items.firstOrNull(),
                    submittedQuery = state.submittedQuery,
                    resultCount = items.size,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier
                        .weight(0.88f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回"
            )
        }
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SearchField(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text("搜索片名") },
        trailingIcon = {
            IconButton(
                onClick = onSubmitSearch,
                enabled = state.query.isNotBlank() && !state.loading
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "搜索"
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchProgress(state: SearchUiState) {
    if (state.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (state.totalSources > 0) {
        Text(
            text = "${state.completedSources}/${state.totalSources} 源已返回",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SearchBody(
    state: SearchUiState,
    items: List<VodItem>,
    onOpenDetail: (VodItem) -> Unit,
    onRetry: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.error != null -> MessageState(
            message = state.error,
            modifier = modifier,
            actionLabel = "重试",
            onAction = onRetry
        )
        items.isNotEmpty() -> SearchResultList(
            items = items,
            onOpenDetail = onOpenDetail,
            modifier = modifier
        )
        state.submittedQuery.isBlank() && state.history.isNotEmpty() -> SearchHistoryList(
            history = state.history,
            onHistoryClick = onHistoryClick,
            onClear = onClearHistory,
            modifier = modifier
        )
    }
}

@Composable
private fun SearchPreviewPanel(
    item: VodItem?,
    submittedQuery: String,
    resultCount: Int,
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
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (submittedQuery.isBlank()) "搜索预览" else "“$submittedQuery”",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (resultCount > 0) "$resultCount 个聚合结果" else "等待搜索结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item?.let {
                AsyncImage(
                    model = rememberMedeoImageRequest(it.pic),
                    contentDescription = it.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(it.year, it.typeName, it.sourceName).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!it.remarks.isNullOrBlank()) {
                    Text(
                        text = it.remarks,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = { onOpenDetail(it) }) {
                    Text("打开详情")
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryList(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClear) {
                    Text("清空")
                }
            }
        }
        items(history, key = { it }) { query ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onHistoryClick(query) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp
            ) {
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    items: List<VodItem>,
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(items, key = { it.key }) { item ->
            VodListRow(
                item = item,
                onClick = onOpenDetail
            )
        }
    }
}
