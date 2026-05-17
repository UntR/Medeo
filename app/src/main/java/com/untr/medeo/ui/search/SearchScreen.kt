package com.untr.medeo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.untr.medeo.R
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.components.VodListRow

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val items = state.results.map { it.primary }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            label = { Text("搜索片名") },
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.submitSearch() },
                    enabled = state.query.isNotBlank() && !state.loading
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = "搜索"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
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

        when {
            state.error != null -> MessageState(
                message = state.error,
                actionLabel = "重试",
                onAction = viewModel::retry
            )
            items.isNotEmpty() -> SearchResultList(
                items = items,
                onOpenDetail = { item ->
                    viewModel.rememberForDetail(item)
                    onOpenDetail(item)
                },
                modifier = Modifier.fillMaxSize()
            )
            state.submittedQuery.isBlank() && state.history.isNotEmpty() -> SearchHistoryList(
                history = state.history,
                onHistoryClick = viewModel::useHistory,
                onClear = viewModel::clearHistory,
                modifier = Modifier.fillMaxSize()
            )
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
