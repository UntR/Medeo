package com.czpn7.ying.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import com.czpn7.ying.data.api.SourceCatalog
import com.czpn7.ying.data.local.SettingsStore
import com.czpn7.ying.data.local.WatchProgress
import com.czpn7.ying.data.model.Episode
import com.czpn7.ying.data.model.PlaySource
import com.czpn7.ying.data.model.VodDetail
import com.czpn7.ying.data.net.NetworkMonitor
import com.czpn7.ying.data.net.NetworkSnapshot
import com.czpn7.ying.data.repo.DetailRepository
import com.czpn7.ying.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val detail: VodDetail? = null,
    val error: String? = null,
    val wifiOnlyPlay: Boolean = false,
    val networkSnapshot: NetworkSnapshot = NetworkSnapshot.Offline
) {
    fun playSource(index: Int): PlaySource? = detail?.playSources?.getOrNull(index)
    fun episode(playSourceIndex: Int, episodeIndex: Int): Episode? =
        playSource(playSourceIndex)?.episodes?.getOrNull(episodeIndex)
}

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRepository: DetailRepository,
    private val progressRepository: ProgressRepository,
    private val sourceCatalog: SourceCatalog,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
    val mediaSourceFactory: MediaSource.Factory
) : ViewModel() {
    private val sourceId: String = checkNotNull(savedStateHandle["sourceId"])
    private val vodId: Long = checkNotNull<String>(savedStateHandle["vodId"]).toLong()

    var playSourceIndex by mutableIntStateOf(
        checkNotNull<String>(savedStateHandle["playSourceIndex"]).toInt()
    )
        private set

    var episodeIndex by mutableIntStateOf(
        checkNotNull<String>(savedStateHandle["episodeIndex"]).toInt()
    )
        private set

    var uiState by mutableStateOf(PlayerUiState())
        private set

    var savedProgress by mutableStateOf<WatchProgress?>(null)
        private set

    init {
        load()
        observeProgress()
    }

    fun selectPlaySource(index: Int, keepEpisode: Boolean = false) {
        val targetSource = uiState.playSource(index) ?: return
        val nextEpisodeIndex = if (keepEpisode) {
            episodeIndex.coerceAtMost(targetSource.episodes.lastIndex)
        } else {
            0
        }
        playSourceIndex = index
        episodeIndex = nextEpisodeIndex
    }

    fun selectEpisode(index: Int) {
        episodeIndex = index
    }

    fun previousEpisode() {
        if (episodeIndex > 0) episodeIndex -= 1
    }

    fun nextEpisode() {
        val source = uiState.playSource(playSourceIndex) ?: return
        if (episodeIndex < source.episodes.lastIndex) episodeIndex += 1
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val detail = uiState.detail ?: return
        val playSource = uiState.playSource(playSourceIndex) ?: return
        val episode = playSource.episodes.getOrNull(episodeIndex) ?: return

        viewModelScope.launch {
            progressRepository.save(
                detail = detail,
                playSourceName = playSource.name,
                episodeIndex = episodeIndex,
                episodeName = episode.name,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    fun resumePositionForCurrentEpisode(): Long {
        val progress = savedProgress ?: return 0L
        val playSource = uiState.playSource(playSourceIndex) ?: return 0L
        return if (
            progress.playSourceName == playSource.name &&
            progress.episodeIndex == episodeIndex
        ) {
            progress.positionMs
        } else {
            0L
        }
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val network = networkMonitor.snapshot()
            val source = sourceCatalog.sourceById(sourceId)
            if (source == null) {
                uiState = PlayerUiState(
                    loading = false,
                    error = "未知数据源",
                    wifiOnlyPlay = settings.wifiOnlyPlay,
                    networkSnapshot = network
                )
                return@launch
            }
            if (!network.online) {
                uiState = PlayerUiState(
                    loading = false,
                    error = "当前无网络连接，无法加载播放信息",
                    wifiOnlyPlay = settings.wifiOnlyPlay,
                    networkSnapshot = network
                )
                return@launch
            }

            val detail = detailRepository.detail(source, vodId)
            uiState = PlayerUiState(
                loading = false,
                detail = detail,
                error = if (detail == null) "播放信息加载失败" else null,
                wifiOnlyPlay = settings.wifiOnlyPlay,
                networkSnapshot = network
            )
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.observeProgress("$sourceId|$vodId").collect { progress ->
                savedProgress = progress
            }
        }
    }
}
