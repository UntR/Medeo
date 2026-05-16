package com.czpn7.ying.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.czpn7.ying.R
import coil3.compose.AsyncImage
import com.czpn7.ying.data.model.PlaySource
import com.czpn7.ying.data.model.VodDetail
import com.czpn7.ying.data.local.WatchProgress
import com.czpn7.ying.ui.components.EpisodeListRow
import com.czpn7.ying.ui.components.InstantTabItem
import com.czpn7.ying.ui.components.InstantTabRow
import com.czpn7.ying.ui.components.LoadingState
import com.czpn7.ying.ui.components.MessageState
import com.czpn7.ying.ui.components.rememberYingImageRequest

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onPlay: (sourceId: String, vodId: Long, playSourceIndex: Int, episodeIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    when {
        state.loading -> LoadingState("正在加载详情", modifier)
        state.error != null -> MessageState(
            message = state.error,
            modifier = modifier,
            actionLabel = "重试",
            onAction = viewModel::load
        )
        state.details.isNotEmpty() -> DetailContent(
            details = state.details,
            favoriteKeys = state.favoriteKeys,
            progressByKey = state.progressByKey,
            onBack = onBack,
            onPlay = onPlay,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = modifier
        )
    }
}

@Composable
private fun DetailContent(
    details: List<VodDetail>,
    favoriteKeys: Set<String>,
    progressByKey: Map<String, WatchProgress>,
    onBack: () -> Unit,
    onPlay: (sourceId: String, vodId: Long, playSourceIndex: Int, episodeIndex: Int) -> Unit,
    onToggleFavorite: (VodDetail) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDetailIndex by remember(details) { mutableIntStateOf(0) }
    var selectedLineIndex by remember(selectedDetailIndex) { mutableIntStateOf(0) }
    val detail = details.getOrNull(selectedDetailIndex) ?: return
    val playSource = detail.playSources.getOrNull(selectedLineIndex)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DetailToolbar(
                favorite = detail.item.key in favoriteKeys,
                onBack = onBack,
                onToggleFavorite = { onToggleFavorite(detail) }
            )
        }

        item {
            DetailHeader(detail)
        }

        progressByKey[detail.item.key]?.let { progress ->
            item {
                ContinueWatchingButton(
                    detail = detail,
                    progress = progress,
                    onPlay = onPlay
                )
            }
        }

        detail.playbackIssue?.let { issue ->
            item {
                IssueCard(issue)
            }
        }

        item {
            SummaryCard(detail.content.orEmpty().ifBlank { "暂无简介" })
        }

        if (detail.playSources.isNotEmpty()) {
            if (details.size > 1) {
                item {
                    SourceTabs(
                        details = details,
                        selectedIndex = selectedDetailIndex,
                        onSelected = {
                            selectedDetailIndex = it
                            selectedLineIndex = 0
                        }
                    )
                }
            }

            if (detail.playSources.size > 1) {
                item {
                    LineTabs(
                        playSources = detail.playSources,
                        selectedIndex = selectedLineIndex,
                        onSelected = { selectedLineIndex = it }
                    )
                }
            }

            playSource?.let { source ->
                itemsIndexed(source.episodes) { episodeIndex, episode ->
                    EpisodeListRow(
                        episode = episode,
                        index = episodeIndex,
                        selected = false,
                        onClick = {
                            onPlay(
                                detail.item.sourceId,
                                detail.item.vodId,
                                selectedLineIndex,
                                episodeIndex
                            )
                        }
                    )
                }
            }
        } else {
            item {
                Text("暂无可播放线路")
            }
        }
    }
}

@Composable
private fun DetailToolbar(
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "详情",
            style = MaterialTheme.typography.titleLarge
        )
        Surface(
            shape = CircleShape,
            color = if (favorite) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shadowElevation = 1.dp
        ) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    painter = painterResource(
                        if (favorite) R.drawable.ic_nav_favorite else R.drawable.ic_favorite_border
                    ),
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingButton(
    detail: VodDetail,
    progress: WatchProgress,
    onPlay: (sourceId: String, vodId: Long, playSourceIndex: Int, episodeIndex: Int) -> Unit
) {
    val playSourceIndex = detail.playSources
        .indexOfFirst { it.name == progress.playSourceName }
        .takeIf { it >= 0 }
        ?: 0
    val episodeIndex = progress.episodeIndex.coerceAtLeast(0)

    Button(
        onClick = {
            onPlay(
                detail.item.sourceId,
                detail.item.vodId,
                playSourceIndex,
                episodeIndex
            )
        },
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("继续观看 ${progress.episodeName} ${formatPosition(progress.positionMs)}")
    }
}

private fun formatPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun DetailHeader(detail: VodDetail) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(126.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = rememberYingImageRequest(detail.item.pic),
                    contentDescription = detail.item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(detail.item.year, detail.item.area, detail.item.typeName)
                        .take(3)
                        .forEach { value -> MetaPill(value) }
                }
                Text(
                    text = detail.item.sourceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                DetailLine("导演", detail.director)
                DetailLine("主演", detail.actor)
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SummaryCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "简介",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun IssueCard(issue: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = "当前源预检提示：$issue",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SourceTabs(
    details: List<VodDetail>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "数据源",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        InstantTabRow(
            items = details.mapIndexed { index, detail ->
                val episodeCount = detail.playSources.sumOf { it.episodes.size }
                val issueMark = if (detail.playbackIssue == null) "" else " !"
                InstantTabItem(
                    id = "${detail.item.sourceId}-${detail.item.vodId}-$index",
                    label = "${detail.item.sourceName}${issueMark} ${episodeCount}集"
                )
            },
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            contentPadding = PaddingValues(horizontal = 2.dp)
        )
    }
}

@Composable
private fun LineTabs(
    playSources: List<PlaySource>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "线路",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        InstantTabRow(
            items = playSources.mapIndexed { index, source ->
                InstantTabItem(
                    id = "line-$index-${source.name}",
                    label = source.name
                )
            },
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            contentPadding = PaddingValues(horizontal = 2.dp)
        )
    }
}
