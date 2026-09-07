package com.untr.medeo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.episodeNumber
import kotlinx.coroutines.launch

internal fun visibleEpisodes(episodes: List<Episode>, query: String, reversed: Boolean): List<IndexedValue<Episode>> {
    val cleanQuery = query.trim()
    val number = cleanQuery.toIntOrNull()
    val indexed = episodes.withIndex().toList()
    val ordered = if (episodes.all { episodeNumber(it.name) != null }) {
        indexed.sortedBy { episodeNumber(it.value.name) }
    } else indexed
    val filtered = ordered.filter {
        if (number != null) episodeNumber(it.value.name) == number || it.value.name.contains(cleanQuery)
        else it.value.name.contains(cleanQuery, ignoreCase = true)
    }
    return if (reversed) filtered.reversed() else filtered
}

@Composable
internal fun EpisodePicker(
    episodes: List<Episode>,
    selectedIndex: Int?,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable(episodes) { mutableStateOf("") }
    var reversed by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visible = visibleEpisodes(episodes, query, reversed)
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("查找集名 / 集号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { reversed = !reversed }) {
                val numbered = episodes.all { episodeNumber(it.name) != null }
                Text(if (numbered) { if (reversed) "倒序" else "正序" } else { if (reversed) "反向排列" else "原顺序" })
            }
            if (selectedIndex != null && selectedIndex in episodes.indices) {
                TextButton(onClick = {
                    query = ""
                    val index = visibleEpisodes(episodes, "", reversed).indexOfFirst { it.index == selectedIndex }
                    scope.launch { listState.scrollToItem(index.coerceAtLeast(0)) }
                }) { Text("定位当前集") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visible.isEmpty()) item { Text("没有匹配的集数", Modifier.padding(12.dp)) }
            items(visible, key = { it.index }) { indexed ->
                EpisodeListRow(
                    episode = indexed.value,
                    index = indexed.index,
                    selected = indexed.index == selectedIndex,
                    onClick = { onSelectEpisode(indexed.index) }
                )
            }
        }
    }
}
