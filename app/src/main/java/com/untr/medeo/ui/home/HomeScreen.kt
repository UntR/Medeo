package com.untr.medeo.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.untr.medeo.R
import com.untr.medeo.data.model.HotFilter
import com.untr.medeo.data.model.HotListItem
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.ui.adaptive.AdaptiveWidthBox
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.InstantTabItem
import com.untr.medeo.ui.components.InstantTabRow
import com.untr.medeo.ui.components.LoadingState
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.components.rememberMedeoImageRequest
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenDetail: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    LaunchedEffect(viewModel) {
        viewModel.openDetailEvents.collect { item ->
            onOpenDetail(item)
        }
    }

    if (windowClass == MedeoWindowClass.Compact) {
        PhoneHomeContent(
            state = state,
            onOpenSearch = onOpenSearch,
            onSelectCategory = viewModel::selectCategory,
            onSelectType = viewModel::selectType,
            onRefresh = viewModel::refresh,
            onOpenItem = viewModel::openHotItem,
            modifier = modifier
        )
    } else {
        TabletHomeContent(
            state = state,
            onOpenSearch = onOpenSearch,
            onSelectCategory = viewModel::selectCategory,
            onSelectType = viewModel::selectType,
            onRefresh = viewModel::refresh,
            onOpenItem = viewModel::openHotItem,
            windowClass = windowClass,
            modifier = modifier
        )
    }
}

@Composable
private fun PhoneHomeContent(
    state: HomeUiState,
    onOpenSearch: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (HotListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HomeHeader(onOpenSearch = onOpenSearch)

        HotCategoryTabs(
            filters = state.categoryFilters,
            selectedCategory = state.selectedCategory,
            onSelected = onSelectCategory
        )

        HotTypeRow(
            filters = state.typeFilters,
            selectedType = state.selectedType,
            onSelected = onSelectType
        )

        state.lookupMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when {
            state.loading -> LoadingState("正在加载热榜")
            state.error != null -> MessageState(
                message = state.error,
                actionLabel = "重新加载",
                onAction = onRefresh
            )
            else -> HotRankedList(
                items = state.items,
                resolvingItemId = state.resolvingItemId,
                onOpenItem = onOpenItem,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TabletHomeContent(
    state: HomeUiState,
    onOpenSearch: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (HotListItem) -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(onOpenSearch = onOpenSearch)
                HotCategoryTabs(
                    filters = state.categoryFilters,
                    selectedCategory = state.selectedCategory,
                    onSelected = onSelectCategory
                )
                HotTypeRow(
                    filters = state.typeFilters,
                    selectedType = state.selectedType,
                    onSelected = onSelectType
                )

                state.lookupMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }

                when {
                    state.loading -> LoadingState("正在加载热榜", Modifier.weight(1f))
                    state.error != null -> MessageState(
                        message = state.error,
                        modifier = Modifier.weight(1f),
                        actionLabel = "重新加载",
                        onAction = onRefresh
                    )
                    else -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        HotRankedList(
                            items = state.items,
                            resolvingItemId = state.resolvingItemId,
                            onOpenItem = onOpenItem,
                            modifier = Modifier
                                .weight(1.08f)
                                .fillMaxHeight()
                        )
                        HotListSidePanel(
                            items = state.items,
                            resolvingItemId = state.resolvingItemId,
                            onOpenItem = onOpenItem,
                            modifier = Modifier
                                .weight(0.92f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MedeoLogo()
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun MedeoLogo() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Image(
                painter = painterResource(R.drawable.ic_medeo_logo_mark),
                contentDescription = "Medeo",
                modifier = Modifier
                    .padding(6.dp)
                    .size(34.dp)
            )
        }
        Text(
            text = "Medeo",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun HotListSidePanel(
    items: List<HotListItem>,
    resolvingItemId: String?,
    onOpenItem: (HotListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "热榜快览",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            items.take(5).forEachIndexed { index, item ->
                HotCompactRow(
                    item = item.copy(rank = index + 1),
                    resolving = resolvingItemId == item.id,
                    onClick = { onOpenItem(item) }
                )
            }
        }
    }
}

@Composable
private fun HotCompactRow(
    item: HotListItem,
    resolving: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !resolving, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankLabel(rank = item.rank)
        AsyncImage(
            model = rememberMedeoImageRequest(item.posterUrl),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(start = 10.dp)
                .width(52.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.displaySubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (resolving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = item.displayRating,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HotCategoryTabs(
    filters: List<HotFilter>,
    selectedCategory: String,
    onSelected: (String) -> Unit
) {
    val selectedIndex = filters.indexOfFirst { it.category == selectedCategory }
        .takeIf { it >= 0 }
        ?: 0
    InstantTabRow(
        items = filters.map { filter ->
            InstantTabItem(id = filter.category, label = filter.title)
        },
        selectedIndex = selectedIndex,
        onSelected = { index ->
            filters.getOrNull(index)?.let { filter -> onSelected(filter.category) }
        },
        contentPadding = PaddingValues(horizontal = 16.dp)
    )
}

@Composable
private fun HotTypeRow(
    filters: List<HotFilter>,
    selectedType: String,
    onSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(filters, key = { it.type }) { filter ->
            HotTypeChip(
                text = filter.title,
                selected = filter.type == selectedType,
                onClick = { onSelected(filter.type) }
            )
        }
    }
}

@Composable
private fun HotTypeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colorScheme.primary else colorScheme.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun HotRankedList(
    items: List<HotListItem>,
    resolvingItemId: String?,
    onOpenItem: (HotListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            HotListRow(
                item = item.copy(rank = index + 1),
                resolving = resolvingItemId == item.id,
                onClick = { onOpenItem(item) }
            )
        }
    }
}

@Composable
private fun HotListRow(
    item: HotListItem,
    resolving: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !resolving, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankLabel(rank = item.rank)
            AsyncImage(
                model = rememberMedeoImageRequest(item.posterUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(70.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    if (item.isNew) {
                        InlineBadge("新")
                    }
                    item.episodesInfo?.let { info -> InlineBadge(info) }
                    item.ratingCount?.let { count ->
                        Text(
                            text = "${count.toRatingCountText()}评价",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(56.dp)
            ) {
                if (resolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = item.displayRating,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "豆瓣",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RankLabel(rank: Int) {
    val color = when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2, 3 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (rank <= 3) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InlineBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

private fun Int.toRatingCountText(): String =
    if (this >= 10000) {
        String.format(Locale.US, "%.1f万", this / 10000.0)
    } else {
        toString()
    }
