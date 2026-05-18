package com.untr.medeo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.local.AppSettings
import com.untr.medeo.data.local.AppThemeMode
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.repo.CacheUsage
import com.untr.medeo.ui.adaptive.AdaptiveWidthBox
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.LoadingState
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val settings = state.settings

    if (state.loading || settings == null) {
        LoadingState("正在加载设置", modifier)
        return
    }

    if (windowClass == MedeoWindowClass.Expanded) {
        TabletSettingsContent(
            state = state,
            settings = settings,
            viewModel = viewModel,
            windowClass = windowClass,
            modifier = modifier
        )
    } else {
        PhoneSettingsContent(
            state = state,
            settings = settings,
            viewModel = viewModel,
            windowClass = windowClass,
            modifier = modifier
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PhoneSettingsContent(
    state: SettingsUiState,
    settings: AppSettings,
    viewModel: SettingsViewModel,
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
            SettingsList(
                state = state,
                settings = settings,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TabletSettingsContent(
    state: SettingsUiState,
    settings: AppSettings,
    viewModel: SettingsViewModel,
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
                SettingsTitle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            SettingSwitchRow(
                                title = "夜间模式",
                                subtitle = if (settings.themeMode == AppThemeMode.NIGHT) {
                                    "Tokyo Night"
                                } else {
                                    "Daylight"
                                },
                                checked = settings.themeMode == AppThemeMode.NIGHT,
                                onCheckedChange = { enabled ->
                                    viewModel.setThemeMode(if (enabled) AppThemeMode.NIGHT else AppThemeMode.DAY)
                                }
                            )
                        }

                        item {
                            SettingSwitchRow(
                                title = "仅 Wi-Fi 自动播放",
                                subtitle = if (settings.wifiOnlyPlay) "已开启" else "已关闭",
                                checked = settings.wifiOnlyPlay,
                                onCheckedChange = viewModel::setWifiOnlyPlay
                            )
                        }

                        item {
                            CacheCard(
                                settings = settings,
                                usage = state.cacheUsage,
                                clearing = state.clearingCache,
                                message = state.message,
                                onMediaCacheMbChange = viewModel::setMediaCacheMb,
                                onImageCacheMbChange = viewModel::setImageCacheMb,
                                onClear = viewModel::clearCache
                            )
                        }

                        if (SHOW_REMOTE_SOURCE_SETTINGS) {
                            item {
                                SourceManifestSettings(
                                    url = state.sourceManifestUrlDraft,
                                    updatedAt = settings.remoteSourceManifestUpdatedAt,
                                    refreshing = state.refreshingSources,
                                    onUrlChange = viewModel::setSourceManifestUrlDraft,
                                    onSave = viewModel::saveSourceManifestUrl,
                                    onRefresh = viewModel::refreshRemoteSources,
                                    onClear = viewModel::clearRemoteSources
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = "数据源",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp)
                            )
                        }

                        items(state.sources, key = { it.id }) { source ->
                            SourceRow(
                                source = source,
                                checked = source.id in settings.enabledSourceIds,
                                onCheckedChange = { enabled ->
                                    viewModel.setSourceEnabled(source.id, enabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SettingsList(
    state: SettingsUiState,
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsTitle()
        }

        item {
            SettingSwitchRow(
                title = "夜间模式",
                subtitle = if (settings.themeMode == AppThemeMode.NIGHT) {
                    "Tokyo Night"
                } else {
                    "Daylight"
                },
                checked = settings.themeMode == AppThemeMode.NIGHT,
                onCheckedChange = { enabled ->
                    viewModel.setThemeMode(if (enabled) AppThemeMode.NIGHT else AppThemeMode.DAY)
                }
            )
        }

        item {
            SettingSwitchRow(
                title = "仅 Wi-Fi 自动播放",
                subtitle = if (settings.wifiOnlyPlay) "已开启" else "已关闭",
                checked = settings.wifiOnlyPlay,
                onCheckedChange = viewModel::setWifiOnlyPlay
            )
        }

        item {
            CacheCard(
                settings = settings,
                usage = state.cacheUsage,
                clearing = state.clearingCache,
                message = state.message,
                onMediaCacheMbChange = viewModel::setMediaCacheMb,
                onImageCacheMbChange = viewModel::setImageCacheMb,
                onClear = viewModel::clearCache
            )
        }

        if (SHOW_REMOTE_SOURCE_SETTINGS) {
            item {
                SourceManifestSettings(
                    url = state.sourceManifestUrlDraft,
                    updatedAt = settings.remoteSourceManifestUpdatedAt,
                    refreshing = state.refreshingSources,
                    onUrlChange = viewModel::setSourceManifestUrlDraft,
                    onSave = viewModel::saveSourceManifestUrl,
                    onRefresh = viewModel::refreshRemoteSources,
                    onClear = viewModel::clearRemoteSources
                )
            }
        }

        item {
            Text(
                text = "数据源",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)
            )
        }

        items(state.sources, key = { it.id }) { source ->
            SourceRow(
                source = source,
                checked = source.id in settings.enabledSourceIds,
                onCheckedChange = { enabled ->
                    viewModel.setSourceEnabled(source.id, enabled)
                }
            )
        }
    }
}

@Composable
private fun SettingsTitle() {
    Text(
        text = "设置",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

private const val SHOW_REMOTE_SOURCE_SETTINGS = false

@Composable
private fun SourceManifestSettings(
    url: String,
    updatedAt: Long?,
    refreshing: Boolean,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    SettingSectionCard {
        Text(
            text = "云端源",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            label = { Text("源清单 URL") },
            placeholder = { Text("https://your-vps.example/sources.json") }
        )
        Text(
            text = if (updatedAt == null) "未加载云端源" else "已缓存云端源",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "更新中" else "更新")
            }
            TextButton(onClick = onSave, enabled = !refreshing) {
                Text("保存")
            }
            TextButton(onClick = onClear, enabled = !refreshing) {
                Text("清除缓存")
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: VodSource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingSwitchRow(
        title = source.name,
        subtitle = null,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun CacheCard(
    settings: AppSettings,
    usage: CacheUsage?,
    clearing: Boolean,
    message: String?,
    onMediaCacheMbChange: (Int) -> Unit,
    onImageCacheMbChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    SettingSectionCard {
        Text(
            text = "缓存",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (usage == null) {
                "正在统计缓存占用"
            } else {
                "当前占用 ${usage.totalBytes.toReadableSize()}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
        usage?.let {
            Text(
                text = "媒体 ${it.mediaBytes.toReadableSize()} / 图片 ${it.imageBytes.toReadableSize()} / HTTP ${it.httpBytes.toReadableSize()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = "上限：媒体 ${settings.mediaCacheMb} MB / 图片 ${settings.imageCacheMb} MB / HTTP ${settings.httpCacheMb} MB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        CacheLimitSlider(
            title = "媒体缓存上限",
            valueMb = settings.mediaCacheMb,
            minMb = SettingsStore.MIN_MEDIA_CACHE_MB,
            maxMb = SettingsStore.MAX_MEDIA_CACHE_MB,
            stepMb = 100,
            onValueChange = onMediaCacheMbChange
        )
        CacheLimitSlider(
            title = "图片缓存上限",
            valueMb = settings.imageCacheMb,
            minMb = SettingsStore.MIN_IMAGE_CACHE_MB,
            maxMb = SettingsStore.MAX_IMAGE_CACHE_MB,
            stepMb = 50,
            onValueChange = onImageCacheMbChange
        )
        Text(
            text = "上限调整后重启应用生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Button(
            onClick = onClear,
            enabled = !clearing,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(if (clearing) "清空中" else "清空缓存")
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CacheLimitSlider(
    title: String,
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    stepMb: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$valueMb MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = valueMb.toFloat(),
            onValueChange = { rawValue ->
                onValueChange(rawValue.roundToCacheStep(stepMb).coerceIn(minMb, maxMb))
            },
            valueRange = minMb.toFloat()..maxMb.toFloat(),
            steps = ((maxMb - minMb) / stepMb - 1).coerceAtLeast(0)
        )
    }
}

private fun Float.roundToCacheStep(stepMb: Int): Int =
    (this / stepMb).roundToInt() * stepMb

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

private fun Long.toReadableSize(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 10) {
        "${mb.roundToInt()} MB"
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@Composable
private fun SettingSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            content = content
        )
    }
}
