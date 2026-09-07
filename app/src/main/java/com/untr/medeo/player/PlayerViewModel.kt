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
import com.untr.medeo.data.diagnostics.DiagnosticLogger
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.matchingEpisodeIndex
import com.untr.medeo.data.model.adjacentEpisodeIndex
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
    fun hasNextEpisode(detailIndex: Int, playSourceIndex: Int, episodeIndex: Int): Boolean {
        val source = playSource(detailIndex, playSourceIndex) ?: return false
        return adjacentEpisodeIndex(source.episodes, episodeIndex, 1) != null
    }
}

internal const val RESUME_PLAYBACK_INDEX = -1

internal data class PlaybackSelection(
    val playSourceIndex: Int,
    val episodeIndex: Int
)

internal fun resolvePlaybackSelection(
    detail: VodDetail?,
    requestedPlaySourceIndex: Int,
    requestedEpisodeIndex: Int,
    progress: WatchProgress?
): PlaybackSelection {
    val playSources = detail?.playSources.orEmpty()
    if (playSources.isEmpty()) return PlaybackSelection(0, 0)

    if (
        progress != null &&
        (requestedPlaySourceIndex == RESUME_PLAYBACK_INDEX || requestedEpisodeIndex == RESUME_PLAYBACK_INDEX)
    ) {
        val progressPlaySourceIndex = playSources
            .indexOfFirst { source -> source.name == progress.playSourceName }
            .takeIf { it >= 0 }
            ?: 0
        val safePlaySourceIndex = if (requestedPlaySourceIndex == RESUME_PLAYBACK_INDEX) {
            progressPlaySourceIndex
        } else {
            requestedPlaySourceIndex
                .coerceAtLeast(0)
                .coerceAtMost(playSources.lastIndex)
        }
        val progressSource = playSources[safePlaySourceIndex]
        val targetEpisodeIndex = if (requestedEpisodeIndex == RESUME_PLAYBACK_INDEX) {
            resolveProgressEpisodeIndex(progressSource.episodes, progress)
        } else {
            requestedEpisodeIndex
        }
        return PlaybackSelection(
            playSourceIndex = safePlaySourceIndex,
            episodeIndex = targetEpisodeIndex.coerceIn(0, progressSource.episodes.lastIndex)
        )
    }

    val safePlaySourceIndex = requestedPlaySourceIndex
        .coerceAtLeast(0)
        .coerceAtMost(playSources.lastIndex)
    val safeEpisodeIndex = requestedEpisodeIndex
        .coerceAtLeast(0)
        .coerceAtMost(playSources[safePlaySourceIndex].episodes.lastIndex)

    return PlaybackSelection(safePlaySourceIndex, safeEpisodeIndex)
}

internal fun resolveProgressEpisodeIndex(
    episodes: List<Episode>,
    progress: WatchProgress
): Int {
    if (episodes.isEmpty()) return 0
    return matchingEpisodeIndex(episodes, progress.episodeName)
        ?: progress.episodeIndex.coerceIn(0, episodes.lastIndex)
}

internal data class PendingPlaybackSelection(val detailIndex: Int, val playSourceIndex: Int)


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
    private val diagnosticLogger: DiagnosticLogger,
    val mediaSourceFactory: MediaSource.Factory
) : ViewModel() {
    private val sourceId: String = savedStateHandle["sourceId"] ?: ""
    private val vodId: Long = savedStateHandle.get<String>("vodId")?.toLongOrNull() ?: -1L
    private val requestedPlaySourceIndex =
        savedStateHandle.get<String>("playSourceIndex")?.toIntOrNull() ?: 0
    private val requestedEpisodeIndex =
        savedStateHandle.get<String>("episodeIndex")?.toIntOrNull() ?: 0

    var playSourceIndex by mutableIntStateOf(
        requestedPlaySourceIndex.coerceAtLeast(0)
    )
        private set

    var episodeIndex by mutableIntStateOf(
        requestedEpisodeIndex.coerceAtLeast(0)
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

    internal var pendingSelection by mutableStateOf<PendingPlaybackSelection?>(null)
        private set

    fun selectDetail(index: Int) {
        val target = uiState.detail(index) ?: return
        val currentLine = uiState.playSource(detailIndex, playSourceIndex)?.name
        val line = target.playSources.indexOfFirst { it.name == currentLine }.takeIf { it >= 0 } ?: 0
        requestSelection(index, line)
    }

    fun selectPlaySource(index: Int) {
        requestSelection(detailIndex, index)
    }

    private fun requestSelection(targetDetailIndex: Int, targetLineIndex: Int) {
        if (targetDetailIndex == detailIndex && targetLineIndex == playSourceIndex) return
        val target = uiState.playSource(targetDetailIndex, targetLineIndex) ?: return
        val current = uiState.episode(detailIndex, playSourceIndex, episodeIndex) ?: return
        val matched = matchingEpisodeIndex(target.episodes, current.name)
        if (matched == null) {
            pendingSelection = PendingPlaybackSelection(targetDetailIndex, targetLineIndex)
        } else {
            applySelection(targetDetailIndex, targetLineIndex, matched)
        }
    }

    internal fun confirmPendingEpisode(index: Int) {
        val pending = pendingSelection ?: return
        applySelection(pending.detailIndex, pending.playSourceIndex, index)
    }

    fun cancelPendingSelection() {
        pendingSelection = null
    }

    private fun applySelection(targetDetailIndex: Int, targetLineIndex: Int, targetEpisodeIndex: Int) {
        if (uiState.episode(targetDetailIndex, targetLineIndex, targetEpisodeIndex) == null) return
        detailIndex = targetDetailIndex
        playSourceIndex = targetLineIndex
        episodeIndex = targetEpisodeIndex
        pendingSelection = null
    }

    fun selectEpisode(index: Int) {
        applySelection(detailIndex, playSourceIndex, index)
    }

    fun previousEpisode() {
        val source = uiState.playSource(detailIndex, playSourceIndex) ?: return
        adjacentEpisodeIndex(source.episodes, episodeIndex, -1)?.let(::selectEpisode)
    }

    fun nextEpisode() {
        val source = uiState.playSource(detailIndex, playSourceIndex) ?: return
        adjacentEpisodeIndex(source.episodes, episodeIndex, 1)?.let(::selectEpisode)
    }

    fun hasAlternativeSource(): Boolean = uiState.details.sumOf { it.playSources.size } > 1

    fun currentNetworkSnapshot(): NetworkSnapshot = networkMonitor.snapshot()

    fun isDiagnosticLoggingEnabled(): Boolean = diagnosticLogger.isEnabled

    fun logDiagnostic(event: String, fields: Map<String, Any?> = emptyMap()) {
        diagnosticLogger.log(event, fields)
    }

    fun nextSourceOrLine() {
        val detail = currentDetail() ?: return
        if (playSourceIndex < detail.playSources.lastIndex) {
            selectPlaySource(playSourceIndex + 1)
            return
        }
        if (detailIndex < uiState.details.lastIndex) {
            selectDetail(detailIndex + 1)
            return
        }
        if (uiState.details.size > 1) {
            selectDetail(0)
        } else if (detail.playSources.size > 1) {
            selectPlaySource(0)
        }
    }

    private var sessionProgress: WatchProgress? = null

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val detail = currentDetail() ?: return
        val playSource = uiState.playSource(detailIndex, playSourceIndex) ?: return
        val episode = playSource.episodes.getOrNull(episodeIndex) ?: return

        val savedEpisodeIndex = episodeIndex
        val snapshot = WatchProgress(
            contentKey = detail.item.contentKey,
            name = detail.item.name,
            pic = detail.item.pic,
            year = detail.item.year,
            preferredSourceId = detail.item.sourceId,
            preferredVodId = detail.item.vodId,
            preferredSourceName = detail.item.sourceName,
            playSourceName = playSource.name,
            episodeIndex = savedEpisodeIndex,
            episodeName = episode.name,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        sessionProgress = snapshot
        savedProgressByKey = savedProgressByKey + (detail.item.contentKey to snapshot)
        viewModelScope.launch {
            progressRepository.save(
                detail = detail,
                playSourceName = playSource.name,
                episodeIndex = savedEpisodeIndex,
                episodeName = episode.name,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    fun resumePositionForCurrentEpisode(): Long {
        val detail = currentDetail() ?: return 0L
        val progress = sessionProgress?.takeIf { it.contentKey == detail.item.contentKey }
            ?: savedProgressByKey[detail.item.contentKey]
            ?: savedProgressByKey[detail.item.key]
            ?: return 0L
        val playSource = uiState.playSource(detailIndex, playSourceIndex) ?: return 0L
        return resumePositionForSelection(playSource, episodeIndex, progress)
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
                .distinctBy { it.key }
            val selectedItem = candidates.firstOrNull { item ->
                item.sourceId == sourceId && item.vodId == vodId
            } ?: candidates.first()
            val selectedDetail = detailRepository.detail(selectedItem)
                ?.takeIf { detail -> detail.playSources.isNotEmpty() }
            val shouldResume =
                requestedPlaySourceIndex == RESUME_PLAYBACK_INDEX ||
                requestedEpisodeIndex == RESUME_PLAYBACK_INDEX
            if (selectedDetail != null) {
                val progress = if (shouldResume) {
                    progressRepository.observeProgress(selectedDetail.item).first()
                } else {
                    null
                }
                detailIndex = 0
                val selection = resolvePlaybackSelection(
                    detail = selectedDetail,
                    requestedPlaySourceIndex = requestedPlaySourceIndex,
                    requestedEpisodeIndex = requestedEpisodeIndex,
                    progress = progress
                )
                playSourceIndex = selection.playSourceIndex
                episodeIndex = selection.episodeIndex
                uiState = PlayerUiState(
                    loading = false,
                    details = listOf(selectedDetail),
                    error = null,
                    wifiOnlyPlay = settings.wifiOnlyPlay,
                    networkSnapshot = network
                )
                loadRemainingDetails(selectedKey = selectedItem.key, candidates = candidates)
                return@launch
            }

            val details = detailRepository.details(candidates.filterNot { it.key == selectedItem.key })
            detailIndex = details.indexOfFirst { detail ->
                detail.item.sourceId == sourceId && detail.item.vodId == vodId
            }.takeIf { it >= 0 } ?: 0
            val fallbackDetail = details.getOrNull(detailIndex)
            val progress = if (shouldResume && fallbackDetail != null) {
                progressRepository.observeProgress(fallbackDetail.item).first()
            } else {
                null
            }
            val selection = resolvePlaybackSelection(
                detail = fallbackDetail,
                requestedPlaySourceIndex = requestedPlaySourceIndex,
                requestedEpisodeIndex = requestedEpisodeIndex,
                progress = progress
            )
            playSourceIndex = selection.playSourceIndex
            episodeIndex = selection.episodeIndex
            uiState = PlayerUiState(
                loading = false,
                details = details,
                error = if (details.isEmpty()) "播放信息加载失败" else null,
                wifiOnlyPlay = settings.wifiOnlyPlay,
                networkSnapshot = network
            )
        }
    }

    private fun loadRemainingDetails(
        selectedKey: String,
        candidates: List<VodItem>
    ) {
        val remaining = candidates.filterNot { it.key == selectedKey }
        if (remaining.isEmpty()) return

        viewModelScope.launch {
            val incoming = detailRepository.details(remaining)
            if (incoming.isEmpty()) return@launch

            val currentKey = currentDetail()?.item?.key
            val merged = mergePlaybackDetails(
                current = uiState.details,
                incoming = incoming
            )
            if (merged.isEmpty()) return@launch
            uiState = uiState.copy(details = merged)
            detailIndex = merged.indexOfFirst { detail -> detail.item.key == currentKey }
                .takeIf { it >= 0 }
                ?: detailIndex.coerceAtMost(merged.lastIndex)
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

internal fun mergePlaybackDetails(
    current: List<VodDetail>,
    incoming: List<VodDetail>
): List<VodDetail> =
    (current + incoming)
        .filter { detail -> detail.playSources.isNotEmpty() }
        .distinctBy { detail -> detail.item.key }

internal fun resumePositionForSelection(
    playSource: PlaySource,
    episodeIndex: Int,
    progress: WatchProgress
): Long =
    if (matchingEpisodeIndex(playSource.episodes, progress.episodeName) == episodeIndex) {
        progress.positionMs.coerceAtLeast(0L)
    } else {
        0L
    }
