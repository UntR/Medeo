package com.untr.medeo.player

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.adaptive.rememberMedeoWindowClass
import com.untr.medeo.ui.components.EpisodeListRow
import com.untr.medeo.ui.components.InstantTabItem
import com.untr.medeo.ui.components.InstantTabRow
import com.untr.medeo.ui.components.LoadingState
import com.untr.medeo.ui.components.MessageState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEK_STEP_MS = 10_000L
private const val LONG_PRESS_SPEED = 2f

@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowClass: MedeoWindowClass = rememberMedeoWindowClass(),
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val episode = state.episode(viewModel.detailIndex, viewModel.playSourceIndex, viewModel.episodeIndex)

    when {
        state.loading -> LoadingState("正在加载播放信息", modifier)
        state.error != null -> MessageState(state.error, modifier)
        episode == null -> MessageState("当前集数不可播放", modifier)
        else -> PlayerContent(
            viewModel = viewModel,
            episodeUrl = episode.url,
            autoPlayBlocked = state.wifiOnlyPlay && !state.networkSnapshot.wifiLike,
            onBack = onBack,
            windowClass = windowClass,
            modifier = modifier
        )
    }
}

@UnstableApi
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun PlayerContent(
    viewModel: PlayerViewModel,
    episodeUrl: String,
    autoPlayBlocked: Boolean,
    onBack: () -> Unit,
    windowClass: MedeoWindowClass,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val details = viewModel.uiState.details
    val detail = viewModel.uiState.detail(viewModel.detailIndex)
    val playSource = viewModel.uiState.playSource(viewModel.detailIndex, viewModel.playSourceIndex)
    val episode = viewModel.uiState.episode(viewModel.detailIndex, viewModel.playSourceIndex, viewModel.episodeIndex)

    var playbackError by remember(episodeUrl) { mutableStateOf<String?>(null) }
    var resumeApplied by remember(episodeUrl) { mutableStateOf(false) }
    var isPlaying by remember(episodeUrl) { mutableStateOf(false) }
    var playbackState by remember(episodeUrl) { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackSpeed by remember(episodeUrl) { mutableFloatStateOf(1f) }
    var currentPosition by remember(episodeUrl) { mutableLongStateOf(0L) }
    var duration by remember(episodeUrl) { mutableLongStateOf(0L) }
    var bufferedPosition by remember(episodeUrl) { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var drawerVisible by remember { mutableStateOf(false) }
    var immersiveRequested by rememberSaveable { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var gestureMessage by remember { mutableStateOf<String?>(null) }
    var autoPlayNoticeVisible by remember(episodeUrl, autoPlayBlocked) { mutableStateOf(autoPlayBlocked) }
    var longPressBoosting by remember { mutableStateOf(false) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var brightnessLevel by remember(activity) {
        mutableFloatStateOf(activity?.currentScreenBrightness() ?: 0.5f)
    }
    var volumeLevel by remember(context) { mutableFloatStateOf(context.currentMusicVolumeFraction()) }
    val resizeOption = resizeOptions.first { it.mode == resizeMode }
    val title = detail?.item?.name.orEmpty()
    val sourceLabel = detail?.item?.sourceName.orEmpty()
    val subtitle = listOfNotNull(
        sourceLabel.takeIf { it.isNotBlank() },
        episode?.name?.takeIf { it.isNotBlank() }
    ).joinToString(" / ")
    val useFullscreen = immersiveRequested || (isLandscape && !windowClass.usesWideLayout)
    val useTheaterLayout = windowClass.usesWideLayout && !useFullscreen

    val player = remember(episodeUrl, autoPlayBlocked) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,
                90_000,
                1_500,
                5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(viewModel.mediaSourceFactory)
            .setLoadControl(loadControl)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(episodeUrl))
                prepare()
                playWhenReady = !autoPlayBlocked
            }
    }

    fun saveProgress() {
        viewModel.saveProgress(player.currentPosition, player.duration)
    }

    fun retryPlayback() {
        playbackError = null
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(episodeUrl))
        player.prepare()
        player.playWhenReady = true
        controlsVisible = true
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            autoPlayNoticeVisible = false
            player.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val safeDuration = player.duration.takeIf { it.isFiniteDuration() } ?: Long.MAX_VALUE
        val nextPosition = (player.currentPosition + deltaMs).coerceIn(0L, safeDuration)
        player.seekTo(nextPosition)
        currentPosition = nextPosition
        gestureMessage = if (deltaMs > 0) "快进 ${deltaMs / 1000}s" else "快退 ${abs(deltaMs) / 1000}s"
    }

    fun switchToNextLine() {
        saveProgress()
        playbackError = null
        viewModel.nextSourceOrLine()
        controlsVisible = true
    }

    LaunchedEffect(activity, useFullscreen) {
        if (activity != null) {
            if (useFullscreen) {
                activity.enterImmersive()
            } else {
                activity.exitImmersive()
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.exitImmersive()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(player, viewModel.savedProgressByKey, viewModel.detailIndex, episodeUrl) {
        if (!resumeApplied) {
            val resumePositionMs = viewModel.resumePositionForCurrentEpisode()
            if (resumePositionMs > 1000L) {
                player.seekTo(resumePositionMs)
                resumeApplied = true
            }
        }
    }

    LaunchedEffect(player, episodeUrl) {
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it.isFiniteDuration() } ?: 0L
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(player, episodeUrl) {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }

    LaunchedEffect(controlsVisible, controlsLocked, drawerVisible, speedMenuExpanded, isPlaying, playbackError) {
        if (controlsVisible && !controlsLocked && !drawerVisible && !speedMenuExpanded && isPlaying && playbackError == null) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureMessage, longPressBoosting) {
        if (gestureMessage != null && !longPressBoosting) {
            delay(900)
            gestureMessage = null
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                isPlaying = isPlayingValue
            }

            override fun onPlaybackStateChanged(playbackStateValue: Int) {
                playbackState = playbackStateValue
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.toUserMessage()
                controlsVisible = true
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        playbackState = player.playbackState
        playbackSpeed = player.playbackParameters.speed
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (useFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (useFullscreen) {
            PlayerSurface(
                player = player,
                title = title,
                subtitle = subtitle,
                playbackError = playbackError,
                playbackNotice = if (autoPlayNoticeVisible) "当前非 Wi-Fi，已暂停自动播放" else null,
                isPlaying = isPlaying,
                playbackState = playbackState,
                playbackSpeed = playbackSpeed,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                controlsVisible = controlsVisible,
                controlsLocked = controlsLocked,
                speedMenuExpanded = speedMenuExpanded,
                resizeMode = resizeMode,
                resizeLabel = resizeOption.label,
                gestureMessage = gestureMessage,
                isLandscape = true,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlay = ::togglePlayback,
                onSeekTo = { player.seekTo(it) },
                onSeekBy = ::seekBy,
                onBack = {
                    saveProgress()
                    onBack()
                },
                onRotate = {
                    immersiveRequested = false
                    activity?.requestedOrientation = if (windowClass.usesWideLayout) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                },
                onPrevious = {
                    saveProgress()
                    playbackError = null
                    viewModel.previousEpisode()
                },
                onNext = {
                    saveProgress()
                    playbackError = null
                    viewModel.nextEpisode()
                },
                onRetry = ::retryPlayback,
                onNextLine = ::switchToNextLine,
                onShowEpisodes = { drawerVisible = true },
                onToggleLock = {
                    val nextLocked = !controlsLocked
                    controlsLocked = nextLocked
                    controlsVisible = !nextLocked
                },
                onCycleResize = { resizeMode = resizeOptions.nextAfter(resizeMode).mode },
                onSpeedMenuExpandedChange = { speedMenuExpanded = it },
                onSelectSpeed = {
                    player.setPlaybackSpeed(it)
                    speedMenuExpanded = false
                    controlsVisible = true
                },
                onBrightnessDelta = { delta ->
                    brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                    activity?.setScreenBrightness(brightnessLevel)
                    gestureMessage = "亮度 ${(brightnessLevel * 100).roundToInt()}%"
                },
                onVolumeDelta = { delta ->
                    volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                    context.setMusicVolumeFraction(volumeLevel)
                    gestureMessage = "音量 ${(volumeLevel * 100).roundToInt()}%"
                },
                onLongPressBoost = { active, originalSpeed ->
                    longPressBoosting = active
                    if (active) {
                        player.setPlaybackSpeed(LONG_PRESS_SPEED)
                        gestureMessage = "${formatSpeed(LONG_PRESS_SPEED)} 快速播放"
                    } else {
                        player.setPlaybackSpeed(originalSpeed)
                        gestureMessage = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (useTheaterLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    PlayerSurface(
                        player = player,
                        title = title,
                        subtitle = subtitle,
                        playbackError = playbackError,
                        playbackNotice = if (autoPlayNoticeVisible) "当前非 Wi-Fi，已暂停自动播放" else null,
                        isPlaying = isPlaying,
                        playbackState = playbackState,
                        playbackSpeed = playbackSpeed,
                        currentPosition = currentPosition,
                        duration = duration,
                        bufferedPosition = bufferedPosition,
                        controlsVisible = controlsVisible,
                        controlsLocked = controlsLocked,
                        speedMenuExpanded = speedMenuExpanded,
                        resizeMode = resizeMode,
                        resizeLabel = resizeOption.label,
                        gestureMessage = gestureMessage,
                        isLandscape = false,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onTogglePlay = ::togglePlayback,
                        onSeekTo = { player.seekTo(it) },
                        onSeekBy = ::seekBy,
                        onBack = {
                            saveProgress()
                            onBack()
                        },
                        onRotate = {
                            immersiveRequested = true
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onPrevious = {
                            saveProgress()
                            playbackError = null
                            viewModel.previousEpisode()
                        },
                        onNext = {
                            saveProgress()
                            playbackError = null
                            viewModel.nextEpisode()
                        },
                        onRetry = ::retryPlayback,
                        onNextLine = ::switchToNextLine,
                        onShowEpisodes = { drawerVisible = true },
                        onToggleLock = {
                            val nextLocked = !controlsLocked
                            controlsLocked = nextLocked
                            controlsVisible = !nextLocked
                        },
                        onCycleResize = { resizeMode = resizeOptions.nextAfter(resizeMode).mode },
                        onSpeedMenuExpandedChange = { speedMenuExpanded = it },
                        onSelectSpeed = {
                            player.setPlaybackSpeed(it)
                            speedMenuExpanded = false
                            controlsVisible = true
                        },
                        onBrightnessDelta = { delta ->
                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                            activity?.setScreenBrightness(brightnessLevel)
                            gestureMessage = "亮度 ${(brightnessLevel * 100).roundToInt()}%"
                        },
                        onVolumeDelta = { delta ->
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                            context.setMusicVolumeFraction(volumeLevel)
                            gestureMessage = "音量 ${(volumeLevel * 100).roundToInt()}%"
                        },
                        onLongPressBoost = { active, originalSpeed ->
                            longPressBoosting = active
                            if (active) {
                                player.setPlaybackSpeed(LONG_PRESS_SPEED)
                                gestureMessage = "${formatSpeed(LONG_PRESS_SPEED)} 快速播放"
                            } else {
                                player.setPlaybackSpeed(originalSpeed)
                                gestureMessage = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }

                detail?.let {
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 320.dp, max = 390.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 1.dp,
                        tonalElevation = 1.dp
                    ) {
                        PlayerQueuePanel(
                            details = details,
                            selectedDetailIndex = viewModel.detailIndex,
                            selectedPlaySourceIndex = viewModel.playSourceIndex,
                            selectedEpisodeIndex = viewModel.episodeIndex,
                            onOpenDrawer = { drawerVisible = true },
                            onSelectDetail = { index ->
                                saveProgress()
                                playbackError = null
                                viewModel.selectDetail(index, keepEpisode = true)
                            },
                            onSelectPlaySource = { index ->
                                saveProgress()
                                playbackError = null
                                viewModel.selectPlaySource(index)
                            },
                            onSelectEpisode = { index ->
                                saveProgress()
                                playbackError = null
                                viewModel.selectEpisode(index)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerSurface(
                    player = player,
                    title = title,
                    subtitle = subtitle,
                    playbackError = playbackError,
                    playbackNotice = if (autoPlayNoticeVisible) "当前非 Wi-Fi，已暂停自动播放" else null,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    playbackSpeed = playbackSpeed,
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    controlsVisible = controlsVisible,
                    controlsLocked = controlsLocked,
                    speedMenuExpanded = speedMenuExpanded,
                    resizeMode = resizeMode,
                    resizeLabel = resizeOption.label,
                    gestureMessage = gestureMessage,
                    isLandscape = false,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onTogglePlay = ::togglePlayback,
                    onSeekTo = { player.seekTo(it) },
                    onSeekBy = ::seekBy,
                    onBack = {
                        saveProgress()
                        onBack()
                    },
                    onRotate = {
                        immersiveRequested = true
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onPrevious = {
                        saveProgress()
                        playbackError = null
                        viewModel.previousEpisode()
                    },
                    onNext = {
                        saveProgress()
                        playbackError = null
                        viewModel.nextEpisode()
                    },
                    onRetry = ::retryPlayback,
                    onNextLine = ::switchToNextLine,
                    onShowEpisodes = { drawerVisible = true },
                    onToggleLock = {
                        val nextLocked = !controlsLocked
                        controlsLocked = nextLocked
                        controlsVisible = !nextLocked
                    },
                    onCycleResize = { resizeMode = resizeOptions.nextAfter(resizeMode).mode },
                    onSpeedMenuExpandedChange = { speedMenuExpanded = it },
                    onSelectSpeed = {
                        player.setPlaybackSpeed(it)
                        speedMenuExpanded = false
                        controlsVisible = true
                    },
                    onBrightnessDelta = { delta ->
                        brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                        activity?.setScreenBrightness(brightnessLevel)
                        gestureMessage = "亮度 ${(brightnessLevel * 100).roundToInt()}%"
                    },
                    onVolumeDelta = { delta ->
                        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                        context.setMusicVolumeFraction(volumeLevel)
                        gestureMessage = "音量 ${(volumeLevel * 100).roundToInt()}%"
                    },
                    onLongPressBoost = { active, originalSpeed ->
                        longPressBoosting = active
                        if (active) {
                            player.setPlaybackSpeed(LONG_PRESS_SPEED)
                            gestureMessage = "${formatSpeed(LONG_PRESS_SPEED)} 快速播放"
                        } else {
                            player.setPlaybackSpeed(originalSpeed)
                            gestureMessage = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                detail?.let {
                    PlayerQueuePanel(
                        details = details,
                        selectedDetailIndex = viewModel.detailIndex,
                        selectedPlaySourceIndex = viewModel.playSourceIndex,
                        selectedEpisodeIndex = viewModel.episodeIndex,
                        onOpenDrawer = { drawerVisible = true },
                        onSelectDetail = { index ->
                            saveProgress()
                            playbackError = null
                            viewModel.selectDetail(index, keepEpisode = true)
                        },
                        onSelectPlaySource = { index ->
                            saveProgress()
                            playbackError = null
                            viewModel.selectPlaySource(index)
                        },
                        onSelectEpisode = { index ->
                            saveProgress()
                            playbackError = null
                            viewModel.selectEpisode(index)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (drawerVisible && detail != null) {
            EpisodeDrawer(
                details = details,
                selectedDetailIndex = viewModel.detailIndex,
                selectedPlaySourceIndex = viewModel.playSourceIndex,
                selectedEpisodeIndex = viewModel.episodeIndex,
                isLandscape = useFullscreen,
                onDismiss = { drawerVisible = false },
                onSelectDetail = { index ->
                    saveProgress()
                    playbackError = null
                    viewModel.selectDetail(index, keepEpisode = true)
                },
                onSelectPlaySource = { index ->
                    saveProgress()
                    playbackError = null
                    viewModel.selectPlaySource(index, keepEpisode = true)
                },
                onSelectEpisode = { index ->
                    saveProgress()
                    playbackError = null
                    viewModel.selectEpisode(index)
                }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    title: String,
    subtitle: String,
    playbackError: String?,
    playbackNotice: String?,
    isPlaying: Boolean,
    playbackState: Int,
    playbackSpeed: Float,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    controlsVisible: Boolean,
    controlsLocked: Boolean,
    speedMenuExpanded: Boolean,
    resizeMode: Int,
    resizeLabel: String,
    gestureMessage: String?,
    isLandscape: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onNextLine: () -> Unit,
    onShowEpisodes: () -> Unit,
    onToggleLock: () -> Unit,
    onCycleResize: () -> Unit,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onLongPressBoost: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val showControls = (controlsVisible || !isPlaying || playbackError != null || speedMenuExpanded) && !controlsLocked
    val safeDuration = duration.takeIf { it.isFiniteDuration() } ?: 0L
    val safePosition = currentPosition.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
    val bufferedPercent = if (safeDuration > 0L) {
        ((bufferedPosition.coerceIn(0L, safeDuration) * 100f) / safeDuration).roundToInt()
    } else {
        0
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .playerGestures(
                controlsLocked = controlsLocked,
                playbackSpeed = playbackSpeed,
                onToggleControls = onToggleControls,
                onSeekBy = onSeekBy,
                onBrightnessDelta = onBrightnessDelta,
                onVolumeDelta = onVolumeDelta,
                onLongPressBoost = onLongPressBoost
            )
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    this.resizeMode = resizeMode
                }
            },
            update = {
                it.player = player
                it.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackState == Player.STATE_BUFFERING && playbackError == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("缓冲中", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        gestureMessage?.let {
            GestureMessage(
                message = it,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (controlsLocked) {
            PlayerIconButton(
                icon = PlayerIcon.LockClosed,
                contentDescription = "解锁",
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .systemBarsPadding()
                    .padding(start = 12.dp)
            )
        }

        if (showControls) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.48f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
            )

            PlayerTopControls(
                title = title,
                subtitle = subtitle,
                controlsLocked = controlsLocked,
                isLandscape = isLandscape,
                onBack = onBack,
                onRotate = onRotate,
                onToggleLock = onToggleLock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.systemBarsPadding() else Modifier)
                    .padding(if (isLandscape) 12.dp else 8.dp)
            )

            if (!isPlaying || playbackNotice != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isPlaying) {
                        CenterPlaybackControls(
                            isLandscape = isLandscape,
                            onTogglePlay = onTogglePlay
                        )
                    }
                    playbackNotice?.let { notice ->
                        PlaybackNoticePill(message = notice)
                    }
                }
            }

            PlayerBottomControls(
                currentPosition = safePosition,
                duration = safeDuration,
                bufferedPercent = bufferedPercent,
                playbackSpeed = playbackSpeed,
                isPlaying = isPlaying,
                speedMenuExpanded = speedMenuExpanded,
                resizeLabel = resizeLabel,
                onSeekTo = onSeekTo,
                onTogglePlay = onTogglePlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onShowEpisodes = onShowEpisodes,
                onCycleResize = onCycleResize,
                onSpeedMenuExpandedChange = onSpeedMenuExpandedChange,
                onSelectSpeed = onSelectSpeed,
                isLandscape = isLandscape,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.systemBarsPadding() else Modifier)
                    .padding(horizontal = if (isLandscape) 10.dp else 8.dp, vertical = if (isLandscape) 6.dp else 2.dp)
            )
        }

        playbackError?.let { error ->
            PlaybackErrorPanel(
                error = error,
                onRetry = onRetry,
                onNextLine = onNextLine,
                onNextEpisode = onNext,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun PlayerTopControls(
    title: String,
    subtitle: String,
    controlsLocked: Boolean,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (isLandscape) 44 else 36
    val iconSize = if (isLandscape) 24 else 20
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            icon = PlayerIcon.Back,
            contentDescription = "返回",
            onClick = onBack,
            sizeDp = buttonSize,
            iconSizeDp = iconSize
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "正在播放" },
                color = Color.White,
                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PlayerIconButton(
            icon = if (controlsLocked) PlayerIcon.LockClosed else PlayerIcon.LockOpen,
            contentDescription = if (controlsLocked) "解锁面板" else "锁定面板",
            onClick = onToggleLock,
            sizeDp = buttonSize,
            iconSizeDp = iconSize
        )
        PlayerIconButton(
            icon = if (isLandscape) PlayerIcon.FullscreenExit else PlayerIcon.Fullscreen,
            contentDescription = if (isLandscape) "竖屏" else "横屏",
            onClick = onRotate,
            sizeDp = buttonSize,
            iconSizeDp = iconSize
        )
    }
}

@Composable
private fun CenterPlaybackControls(
    isLandscape: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerIconButton(
        icon = PlayerIcon.Play,
        contentDescription = "播放",
        onClick = onTogglePlay,
        sizeDp = if (isLandscape) 72 else 56,
        iconSizeDp = if (isLandscape) 38 else 30,
        modifier = modifier
    )
}

@Composable
private fun PlayerBottomControls(
    currentPosition: Long,
    duration: Long,
    bufferedPercent: Int,
    playbackSpeed: Float,
    isPlaying: Boolean,
    speedMenuExpanded: Boolean,
    resizeLabel: String,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowEpisodes: () -> Unit,
    onCycleResize: () -> Unit,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (isLandscape) 44 else 38
    val iconSize = if (isLandscape) 24 else 21
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatPlaybackTime(currentPosition)} / ${formatPlaybackTime(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$bufferedPercent%",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        CompactPlayerProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercent = bufferedPercent,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                icon = if (isPlaying) PlayerIcon.Pause else PlayerIcon.Play,
                contentDescription = "播放或暂停",
                onClick = onTogglePlay,
                sizeDp = buttonSize,
                iconSizeDp = iconSize
            )
            PlayerIconButton(
                icon = PlayerIcon.SkipPrevious,
                contentDescription = "上一集",
                onClick = onPrevious,
                sizeDp = buttonSize,
                iconSizeDp = iconSize
            )
            PlayerIconButton(
                icon = PlayerIcon.SkipNext,
                contentDescription = "下一集",
                onClick = onNext,
                sizeDp = buttonSize,
                iconSizeDp = iconSize
            )
            Spacer(modifier = Modifier.weight(1f))
            PlayerIconButton(
                icon = PlayerIcon.Episodes,
                contentDescription = "选集面板",
                onClick = onShowEpisodes,
                sizeDp = buttonSize,
                iconSizeDp = iconSize
            )
            PlayerIconButton(
                icon = PlayerIcon.AspectRatio,
                contentDescription = "画面比例 $resizeLabel",
                onClick = onCycleResize,
                sizeDp = buttonSize,
                iconSizeDp = iconSize
            )
            Box {
                SpeedButton(
                    text = formatSpeed(playbackSpeed),
                    onClick = { onSpeedMenuExpandedChange(true) }
                )
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { onSpeedMenuExpandedChange(false) }
                ) {
                    speedOptions.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(formatSpeed(speed)) },
                            onClick = { onSelectSpeed(speed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPercent: Int,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = duration > 0L
    val progressFraction = if (enabled) {
        currentPosition.toFloat() / duration.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPercent / 100f).coerceIn(progressFraction, 1f)

    Canvas(
        modifier = modifier
            .height(20.dp)
            .pointerInput(duration, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    fun seekToX(x: Float) {
                        val fraction = (x / size.width).coerceIn(0f, 1f)
                        onSeekTo((duration * fraction).toLong())
                    }

                    seekToX(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        if (!change.pressed) break
                        seekToX(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val y = size.height / 2f
        val trackWidth = 3.dp.toPx()
        val thumbRadius = 4.dp.toPx()
        val progressX = size.width * progressFraction
        val bufferedX = size.width * bufferedFraction

        drawLine(
            color = Color.White.copy(alpha = 0.26f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(0f, y),
            end = Offset(bufferedX, y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(0f, y),
            end = Offset(progressX, y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        if (enabled) {
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(progressX, y)
            )
        }
    }
}

@Composable
private fun PlaybackErrorPanel(
    error: String,
    onRetry: () -> Unit,
    onNextLine: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .padding(18.dp)
            .widthIn(max = 420.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "播放失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("重试") }
                Button(onClick = onNextLine) { Text("下一源/线路") }
                Button(onClick = onNextEpisode) { Text("下一集") }
            }
        }
    }
}

@Composable
private fun PlaybackNoticePill(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.widthIn(max = 360.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PlayerQueuePanel(
    details: List<VodDetail>,
    selectedDetailIndex: Int,
    selectedPlaySourceIndex: Int,
    selectedEpisodeIndex: Int,
    onOpenDrawer: () -> Unit,
    onSelectDetail: (Int) -> Unit,
    onSelectPlaySource: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = details.getOrNull(selectedDetailIndex) ?: return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail.displayPlaySourceLabel(selectedPlaySourceIndex),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onOpenDrawer) {
                Text("选集")
            }
        }

        if (details.size > 1) {
            InstantTabRow(
                items = details.mapIndexed { index, sourceDetail ->
                    InstantTabItem(
                        id = "player-source-$index-${sourceDetail.item.key}",
                        label = sourceDetail.displaySourceLabel()
                    )
                },
                selectedIndex = selectedDetailIndex,
                onSelected = onSelectDetail,
                contentPadding = PaddingValues(horizontal = 16.dp)
            )
        }

        InstantTabRow(
            items = detail.playSources.mapIndexed { index, source ->
                InstantTabItem("player-line-$index-${source.name}", detail.displayPlaySourceLabel(index))
            },
            selectedIndex = selectedPlaySourceIndex,
            onSelected = onSelectPlaySource,
            contentPadding = PaddingValues(horizontal = 16.dp)
        )

        val currentSource = detail.playSources.getOrNull(selectedPlaySourceIndex)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(currentSource?.episodes.orEmpty()) { index, episode ->
                EpisodeListRow(
                    episode = episode,
                    index = index,
                    selected = index == selectedEpisodeIndex,
                    onClick = { onSelectEpisode(index) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeDrawer(
    details: List<VodDetail>,
    selectedDetailIndex: Int,
    selectedPlaySourceIndex: Int,
    selectedEpisodeIndex: Int,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onSelectDetail: (Int) -> Unit,
    onSelectPlaySource: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit
) {
    val detail = details.getOrNull(selectedDetailIndex) ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (isLandscape) 0.42f else 0.88f)
                .widthIn(max = 380.dp)
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "线路与集数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = detail.item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

                if (details.size > 1) {
                    InstantTabRow(
                        items = details.mapIndexed { index, sourceDetail ->
                            InstantTabItem(
                                id = "drawer-source-$index-${sourceDetail.item.key}",
                                label = sourceDetail.displaySourceLabel()
                            )
                        },
                        selectedIndex = selectedDetailIndex,
                        onSelected = onSelectDetail,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                InstantTabRow(
                    items = detail.playSources.mapIndexed { index, source ->
                        InstantTabItem("drawer-line-$index-${source.name}", detail.displayPlaySourceLabel(index))
                    },
                    selectedIndex = selectedPlaySourceIndex,
                    onSelected = onSelectPlaySource,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val currentSource = detail.playSources.getOrNull(selectedPlaySourceIndex)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(currentSource?.episodes.orEmpty()) { index, episode ->
                        EpisodeListRow(
                            episode = episode,
                            index = index,
                            selected = index == selectedEpisodeIndex,
                            onClick = { onSelectEpisode(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: PlayerIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    iconSizeDp: Int = 24
) {
    Surface(
        color = Color.Transparent,
        contentColor = Color.White,
        shape = RoundedCornerShape((sizeDp / 2).dp),
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 2).dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlayerIconCanvas(
                icon = icon,
                modifier = Modifier.size(iconSizeDp.dp)
            )
        }
    }
}

@Composable
private fun SpeedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "倍速 $text" }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlayerIconCanvas(
    icon: PlayerIcon,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val color = Color.White
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)

        fun playTriangle(left: Float, top: Float, right: Float, bottom: Float) {
            val path = Path().apply {
                moveTo(left, top)
                lineTo(right, (top + bottom) / 2f)
                lineTo(left, bottom)
                close()
            }
            drawPath(path, color)
        }

        when (icon) {
            PlayerIcon.Back -> {
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.28f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.62f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            PlayerIcon.Play -> playTriangle(w * 0.33f, h * 0.22f, w * 0.78f, h * 0.78f)
            PlayerIcon.Pause -> {
                drawRoundRect(color, Offset(w * 0.28f, h * 0.2f), Size(w * 0.14f, h * 0.6f))
                drawRoundRect(color, Offset(w * 0.58f, h * 0.2f), Size(w * 0.14f, h * 0.6f))
            }
            PlayerIcon.SkipPrevious -> {
                drawLine(color, Offset(w * 0.24f, h * 0.22f), Offset(w * 0.24f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(w * 0.76f, h * 0.22f)
                    lineTo(w * 0.34f, h * 0.5f)
                    lineTo(w * 0.76f, h * 0.78f)
                    close()
                }
                drawPath(path, color)
            }
            PlayerIcon.SkipNext -> {
                drawLine(color, Offset(w * 0.76f, h * 0.22f), Offset(w * 0.76f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                playTriangle(w * 0.24f, h * 0.22f, w * 0.66f, h * 0.78f)
            }
            PlayerIcon.Episodes -> {
                repeat(3) { index ->
                    val y = h * (0.28f + index * 0.22f)
                    drawLine(color, Offset(w * 0.22f, y), Offset(w * 0.3f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.42f, y), Offset(w * 0.8f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            PlayerIcon.AspectRatio -> {
                val l = w * 0.2f
                val r = w * 0.8f
                val t = h * 0.26f
                val b = h * 0.74f
                drawLine(color, Offset(l, t), Offset(w * 0.42f, t), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(l, t), Offset(l, h * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(r, b), Offset(w * 0.58f, b), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(r, b), Offset(r, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            PlayerIcon.Fullscreen -> {
                drawLine(color, Offset(w * 0.22f, h * 0.42f), Offset(w * 0.22f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.42f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.22f), Offset(w * 0.78f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.78f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.58f), Offset(w * 0.78f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.78f), Offset(w * 0.58f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.78f), Offset(w * 0.22f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.22f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            PlayerIcon.FullscreenExit -> {
                drawLine(color, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.42f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.22f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.78f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.22f), Offset(w * 0.58f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.58f), Offset(w * 0.58f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.58f), Offset(w * 0.78f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.58f), Offset(w * 0.22f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.58f), Offset(w * 0.42f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            PlayerIcon.LockClosed,
            PlayerIcon.LockOpen -> {
                drawRoundRect(color, Offset(w * 0.25f, h * 0.46f), Size(w * 0.5f, h * 0.36f))
                val shackleLeft = if (icon == PlayerIcon.LockOpen) w * 0.34f else w * 0.32f
                val shackleRight = if (icon == PlayerIcon.LockOpen) w * 0.66f else w * 0.68f
                val shackleTop = h * 0.18f
                val shackleBottom = h * 0.48f
                drawLine(color, Offset(shackleLeft, shackleBottom), Offset(shackleLeft, shackleTop), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(shackleRight, shackleTop), Offset(shackleRight, shackleBottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(shackleLeft, shackleTop), Offset(shackleRight, shackleTop), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun GestureMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

private fun Modifier.playerGestures(
    controlsLocked: Boolean,
    playbackSpeed: Float,
    onToggleControls: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onLongPressBoost: (Boolean, Float) -> Unit
): Modifier = pointerInput(controlsLocked, playbackSpeed) {
    coroutineScope {
        var pendingSingleTapJob: Job? = null
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero
        val touchSlop = viewConfiguration.touchSlop
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val doubleTapDistance = 96.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val containerSize = size
            val start = down.position
            var totalDrag = Offset.Zero
            var adjustMode: AdjustMode? = null
            var longPressTriggered = false
            val originalSpeed = playbackSpeed
            val longPressJob = if (!controlsLocked) {
                launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (adjustMode == null && totalDrag.getDistance() < touchSlop) {
                        longPressTriggered = true
                        onLongPressBoost(true, originalSpeed)
                    }
                }
            } else {
                null
            }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDrag += delta

                if (
                    adjustMode == null &&
                    !longPressTriggered &&
                    !controlsLocked &&
                    abs(totalDrag.y) > touchSlop &&
                    abs(totalDrag.y) > abs(totalDrag.x) * 1.2f
                ) {
                    adjustMode = if (start.x < containerSize.width / 2f) {
                        AdjustMode.Brightness
                    } else {
                        AdjustMode.Volume
                    }
                    longPressJob?.cancel()
                }

                when (adjustMode) {
                    AdjustMode.Brightness -> {
                        onBrightnessDelta((-delta.y / containerSize.height).coerceIn(-0.08f, 0.08f))
                        change.consume()
                    }
                    AdjustMode.Volume -> {
                        onVolumeDelta((-delta.y / containerSize.height).coerceIn(-0.08f, 0.08f))
                        change.consume()
                    }
                    null -> Unit
                }
            }

            longPressJob?.cancel()

            if (longPressTriggered) {
                onLongPressBoost(false, originalSpeed)
                return@awaitEachGesture
            }

            if (adjustMode != null || totalDrag.getDistance() > touchSlop) {
                return@awaitEachGesture
            }

            val now = System.currentTimeMillis()
            val isDoubleTap = now - lastTapTime <= doubleTapTimeout &&
                (start - lastTapPosition).getDistance() <= doubleTapDistance

            if (isDoubleTap) {
                pendingSingleTapJob?.cancel()
                lastTapTime = 0L
                if (controlsLocked) {
                    onToggleControls()
                } else {
                    val seekDelta = if (start.x < containerSize.width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS
                    onSeekBy(seekDelta)
                }
            } else {
                lastTapTime = now
                lastTapPosition = start
                pendingSingleTapJob?.cancel()
                pendingSingleTapJob = launch {
                    delay(doubleTapTimeout)
                    onToggleControls()
                }
            }
        }
    }
}

private enum class AdjustMode {
    Brightness,
    Volume
}

private enum class PlayerIcon {
    Back,
    Play,
    Pause,
    SkipPrevious,
    SkipNext,
    Episodes,
    AspectRatio,
    Fullscreen,
    FullscreenExit,
    LockClosed,
    LockOpen
}

private data class ResizeOption(
    val mode: Int,
    val label: String
)

@androidx.annotation.OptIn(UnstableApi::class)
private val resizeOptions = listOf(
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_FIT, "适应"),
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_FILL, "填充"),
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "裁剪")
)

private val speedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun List<ResizeOption>.nextAfter(currentMode: Int): ResizeOption {
    val index = indexOfFirst { it.mode == currentMode }.takeIf { it >= 0 } ?: 0
    return this[(index + 1) % size]
}

private fun Long.isFiniteDuration(): Boolean = this > 0L && this != C.TIME_UNSET

private fun VodDetail.displayPlaySourceLabel(index: Int): String {
    val sourceName = item.sourceName.ifBlank { item.sourceId }
    val lineName = playSources.getOrNull(index)?.name.orEmpty()
    return when {
        lineName.isBlank() -> sourceName
        lineName.isTechnicalLineName() -> sourceName
        playSources.size <= 1 -> sourceName
        else -> lineName
    }
}

private fun VodDetail.displaySourceLabel(): String {
    val episodeCount = playSources.sumOf { it.episodes.size }
    return if (episodeCount > 0) {
        "${item.sourceName} $episodeCount 集"
    } else {
        item.sourceName
    }
}

private fun String.isTechnicalLineName(): Boolean {
    val normalized = trim().lowercase()
    return normalized.contains("m3u8") ||
        normalized == "play" ||
        normalized == "default" ||
        normalized == "在线播放" ||
        normalized.matches(Regex("line\\d*"))
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) {
        "${speed.roundToInt()}x"
    } else {
        "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"
    }

private fun formatPlaybackTime(timeMs: Long): String {
    if (timeMs <= 0L || timeMs == C.TIME_UNSET) return "00:00"
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun PlaybackException.toUserMessage(): String =
    when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接失败，请检查网络后重试"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "播放源返回错误，建议切换线路或数据源"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "播放地址格式异常，建议切换线路"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "设备解码失败，可尝试切换线路或画面比例"
        else -> message ?: errorCodeName
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.enterImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun Activity.exitImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

private fun Activity.currentScreenBrightness(): Float {
    val current = window.attributes.screenBrightness
    return current.takeIf { it >= 0f } ?: 0.5f
}

private fun Activity.setScreenBrightness(value: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = value.coerceIn(0.05f, 1f)
    window.attributes = attributes
}

private fun Context.currentMusicVolumeFraction(): Float {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return currentVolume / maxVolume.toFloat()
}

private fun Context.setMusicVolumeFraction(value: Float) {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val targetVolume = (value.coerceIn(0f, 1f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
}
