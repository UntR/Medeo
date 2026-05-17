package com.untr.medeo.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.net.NetworkSnapshot
import com.untr.medeo.data.repo.DetailRepository
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val details: List<VodDetail> = emptyList(),
    val error: String? = null,
    val wifiOnlyPlay: Boolean = false,
    val networkSnapshot: NetworkSnapshot = NetworkSnapshot.Offline
) {
    fun detail(index: Int): VodDetail? = details.getOrNull(index)
    fun playSource(detailIndex: Int, playSourceIndex: Int): PlaySource? =
        detail(detailIndex)?.playSources?.getOrNull(playSourceIndex)
    fun episode(detailIndex: Int, playSourceIndex: Int, episodeIndex: Int): Episode? =
        playSource(detailIndex, playSourceIndex)?.episodes?.getOrNull(episodeIndex)
}

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRepository: DetailRepository,
    private val detailSelectionStore: DetailSelectionStore,
    private val progressRepository: ProgressRepository,
    private val sourceCatalog: SourceCatalog,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
    val mediaSourceFactory: MediaSource.Factory
) : ViewModel() {
    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val vodId: Long = savedStateHandle.get<String>("vodId")?.toLongOrNull() ?: -1L

    var playSourceIndex by mutableIntStateOf(
        savedStateHandle.get<String>("playSourceIndex")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    )
        private set

    var episodeIndex by mutableIntStateOf(
        savedStateHandle.get<String>("episodeIndex")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    )
        private set

    var detailIndex by mutableIntStateOf(0)
        private set

    var uiState by mutableStateOf(PlayerUiState())
        private set

    var savedProgressByKey by mutableStateOf<Map<String, WatchProgress>>(emptyMap())
        private set

    init {
        load()
        observeProgress()
    }

    fun selectDetail(index: Int, keepEpisode: Boolean = true) {
        val targetDetail = uiState.detail(index) ?: return
        val nextPlaySourceIndex = playSourceIndex.coerceAtMost(targetDetail.playSources.lastIndex)
        val targetSource = targetDetail.playSources.getOrNull(nextPlaySourceIndex)
        val nextEpisodeIndex = if (keepEpisode) {
            episodeIndex.coerceAtMost(targetSource?.episodes?.lastIndex ?: 0)
        } else {
            0
        }
        detailIndex = index
        playSourceIndex = nextPlaySourceIndex
        episodeIndex = nextEpisodeIndex
    }

    fun selectPlaySource(index: Int, keepEpisode: Boolean = false) {
        val targetSource = uiState.playSource(detailIndex, index) ?: return
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
        val source = uiState.playSource(detailIndex, playSourceIndex) ?: return
        if (episodeIndex < source.episodes.lastIndex) episodeIndex += 1
    }

    fun nextSourceOrLine() {
        val detail = currentDetail() ?: return
        if (playSourceIndex < detail.playSources.lastIndex) {
            selectPlaySource(playSourceIndex + 1, keepEpisode = true)
            return
        }
        if (detailIndex < uiState.details.lastIndex) {
            selectDetail(detailIndex + 1, keepEpisode = true)
            return
        }
        if (uiState.details.size > 1) {
            selectDetail(0, keepEpisode = true)
        } else if (detail.playSources.size > 1) {
            selectPlaySource(0, keepEpisode = true)
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val detail = currentDetail() ?: return
        val playSource = uiState.playSource(detailIndex, playSourceIndex) ?: return
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
        val detail = currentDetail() ?: return 0L
        val progress = savedProgressByKey[detail.item.key] ?: return 0L
        val playSource = uiState.playSource(detailIndex, playSourceIndex) ?: return 0L
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
            if (sourceId.isBlank() || vodId <= 0L) {
                uiState = PlayerUiState(
                    loading = false,
                    error = "播放参数无效",
                    wifiOnlyPlay = settings.wifiOnlyPlay,
                    networkSnapshot = network
                )
                return@launch
            }
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

            val candidates = detailSelectionStore.candidates(sourceId, vodId)
                .ifEmpty {
                    listOf(
                        VodItem(
                            sourceId = source.id,
                            sourceName = source.name,
                            vodId = vodId,
                            name = "",
                            pic = null,
                            year = null,
                            area = null,
                            typeName = null,
                            remarks = null
                        )
                    )
                }
            val details = detailRepository.details(candidates)
            detailIndex = details.indexOfFirst { detail ->
                detail.item.sourceId == sourceId && detail.item.vodId == vodId
            }.takeIf { it >= 0 } ?: 0
            val selectedDetail = details.getOrNull(detailIndex)
            playSourceIndex = playSourceIndex.coerceAtMost(selectedDetail?.playSources?.lastIndex ?: 0)
            episodeIndex = episodeIndex.coerceAtMost(
                selectedDetail?.playSources?.getOrNull(playSourceIndex)?.episodes?.lastIndex ?: 0
            )
            uiState = PlayerUiState(
                loading = false,
                details = details,
                error = if (details.isEmpty()) "播放信息加载失败" else null,
                wifiOnlyPlay = settings.wifiOnlyPlay,
                networkSnapshot = network
            )
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.observeAllByKey().collect { progressByKey ->
                savedProgressByKey = progressByKey
            }
        }
    }

    private fun currentDetail(): VodDetail? = uiState.detail(detailIndex)
}
