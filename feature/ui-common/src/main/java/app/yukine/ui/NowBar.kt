package app.yukine.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.feature.uicommon.R
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val WAVEFORM_TWO_PI = 6.2831855f

val LocalEchoNowBarCompactProgress = staticCompositionLocalOf { 0f }
val LocalEchoNowBarScrollProgress = staticCompositionLocalOf { 0f }
val LocalEchoNowBarPageScrollEvent = staticCompositionLocalOf { 0 }
val LocalEchoNowBarBottomInset = staticCompositionLocalOf { 0.dp }
val LocalEchoNowBarTopCloudChanged = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalEchoNowBarTopCloudClearanceChanged = staticCompositionLocalOf<(androidx.compose.ui.unit.Dp) -> Unit> { {} }

private enum class NowBarDockPosition {
    Expanded,
    BottomLeft,
    BottomRight,
    TopCloud,
    TopCloudExpanded,
    TopCloudCollapsed
}

data class NowBarState @JvmOverloads constructor(
    val title: String,
    val subtitle: String,
    val elapsed: String,
    val duration: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val favorite: Boolean,
    val favoriteEnabled: Boolean,
    val canExpand: Boolean,
    val shuffleEnabled: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleLabel: String,
    val inOrderLabel: String,
    val repeatOneLabel: String,
    val repeatAllLabel: String,
    val repeatOffLabel: String,
    val nowPlayingLabel: String = "",
    val repeatMode: Int = PlaybackRepeatMode.REPEAT_ALL,
    val albumArtUri: Uri? = null,
    val trackId: Long = -1L,
    val contentUri: Uri? = null,
    val dataPath: String = "",
    val waveformBars: FloatArray = FloatArray(0),
    val waveformGeneratedBars: Int = 0,
    val waveformCachedProgress: Float = 0f,
    val lyricsTitle: String = "",
    val lyricsStatus: String = "",
    val closeLabel: String = "",
    val showLyricsLabel: String = "",
    val showArtworkLabel: String = "",
    val noLyricsFoundLabel: String = "",
    val previousLabel: String = "",
    val playLabel: String = "",
    val pauseLabel: String = "",
    val nextLabel: String = "",
    val queueLabel: String = "",
    val moreLabel: String = "",
    val addToPlaylistLabel: String = "",
    val playbackProgressLabel: String = "",
    val expandWaveformLabel: String = "",
    val playbackErrorTitle: String = "",
    val playbackErrorMessage: String = "",
    val retryLabel: String = "",
    val dockLeftLabel: String = "",
    val dockRightLabel: String = "",
    val expandNowBarLabel: String = "",
    val dockTopLabel: String = "",
    val restoreBottomLabel: String = "",
    val collapseTopCloudLabel: String = "",
    val showTopCloudLabel: String = "",
    val expandTopCloudLabel: String = "",
    val compactTopCloudLabel: String = "",
    val lyrics: List<LyricUiLine> = emptyList()
)

@Immutable
private data class NowBarProgressSlice(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val elapsed: String,
    val duration: String,
    val trackId: Long,
    val contentUriString: String?,
    val dataPath: String,
    val waveformBars: FloatArray,
    val waveformGeneratedBars: Int,
    val waveformCachedProgress: Float,
    val playbackProgressLabel: String,
    val expandWaveformLabel: String
)

@Immutable
private data class NowBarTrackSlice(
    val artUriString: String?,
    val title: String,
    val subtitle: String,
    val canExpand: Boolean
)

@Immutable
private data class NowBarTransportSlice(
    val playing: Boolean,
    val previousLabel: String,
    val playLabel: String,
    val pauseLabel: String,
    val nextLabel: String
)

@Immutable
private data class NowBarModeSlice(
    val favoriteEnabled: Boolean,
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleEnabled: Boolean,
    val shuffleLabel: String,
    val inOrderLabel: String,
    val repeatOneLabel: String,
    val repeatAllLabel: String,
    val repeatOffLabel: String,
    val queueLabel: String,
    val repeatMode: Int
)

fun nowBarEmptyState() = NowBarState(
    title = "\u672a\u9009\u4e2d\u6b4c\u66f2",
    subtitle = "",
    elapsed = Track.formatDuration(0),
    duration = Track.formatDuration(0),
    positionMs = 0,
    durationMs = 0,
    playing = false,
    favorite = false,
    favoriteEnabled = false,
    canExpand = false,
    shuffleEnabled = false,
    favoriteLabel = "\u6536\u85cf",
    favoritedLabel = "\u5df2\u6536\u85cf",
    shuffleLabel = "\u968f\u673a",
    inOrderLabel = "\u987a\u5e8f",
    repeatOneLabel = "\u5355\u66f2\u5faa\u73af",
    repeatAllLabel = "\u5217\u8868\u5faa\u73af",
    repeatOffLabel = "\u5173\u95ed\u5faa\u73af",
    repeatMode = PlaybackRepeatMode.REPEAT_ALL
)

fun interface SeekAction {
    fun seekTo(positionMs: Long)
}

@Composable
fun NowBar(
    state: NowBarState,
    waveformExpanded: Boolean,
    onExpandWaveform: () -> Unit,
    onCollapseWaveform: () -> Unit,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable,
    onFavorite: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onOpenNowPlaying: Runnable,
    onOpenQueue: Runnable,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    val density = LocalDensity.current
    var dockName by rememberSaveable { mutableStateOf(NowBarDockPosition.Expanded.name) }
    var previousBottomDockName by rememberSaveable {
        mutableStateOf(NowBarDockPosition.BottomRight.name)
    }
    var previousTopCloudName by rememberSaveable {
        mutableStateOf(NowBarDockPosition.TopCloud.name)
    }
    val dockPosition = NowBarDockPosition.entries.firstOrNull { it.name == dockName }
        ?: NowBarDockPosition.Expanded
    val docked = dockPosition != NowBarDockPosition.Expanded
    val topCloud = dockPosition == NowBarDockPosition.TopCloud
    val topCloudExpanded = dockPosition == NowBarDockPosition.TopCloudExpanded
    val topCloudCollapsed = dockPosition == NowBarDockPosition.TopCloudCollapsed
    val topCloudVisible = topCloud || topCloudExpanded
    val topCloudPosition = topCloudVisible || topCloudCollapsed
    var cloudFoldPreview by remember { mutableStateOf<Float?>(null) }
    val settledCloudFoldProgress by animateFloatAsState(
        targetValue = if (topCloudCollapsed) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "topCloudSettledFold"
    )
    val cloudFoldProgress = cloudFoldPreview ?: settledCloudFoldProgress
    val compactProgress = if (docked) 0f else LocalEchoNowBarCompactProgress.current.coerceIn(0f, 1f)
    val scrollProgress = if (topCloudPosition) 0f else LocalEchoNowBarScrollProgress.current.coerceIn(-1f, 1f)
    val scrollCompactProgress = scrollProgress.coerceIn(0f, 1f)
    val scrollStretchProgress = (-scrollProgress).coerceIn(0f, 1f)
    val bottomInset = LocalEchoNowBarBottomInset.current
    val pageScrollEvent = LocalEchoNowBarPageScrollEvent.current
    var acknowledgedPageScrollEvent by remember { mutableIntStateOf(pageScrollEvent) }
    val onTopCloudChanged = LocalEchoNowBarTopCloudChanged.current
    val onTopCloudClearanceChanged = LocalEchoNowBarTopCloudClearanceChanged.current
    SideEffect {
        onTopCloudChanged(topCloudVisible)
        onTopCloudClearanceChanged(
            when {
                topCloudExpanded -> EchoMobileLayoutMetrics.nowBarTopCloudExpandedContentClearance
                topCloud -> EchoMobileLayoutMetrics.nowBarTopCloudContentClearance
                topCloudCollapsed -> EchoMobileLayoutMetrics.nowBarTopCloudCollapsedContentClearance
                else -> 0.dp
            }
        )
    }
    val dockBottomLeft = {
        previousBottomDockName = NowBarDockPosition.BottomLeft.name
        dockName = NowBarDockPosition.BottomLeft.name
    }
    val dockBottomRight = {
        previousBottomDockName = NowBarDockPosition.BottomRight.name
        dockName = NowBarDockPosition.BottomRight.name
    }
    val dockTop = {
        if (dockPosition == NowBarDockPosition.BottomLeft ||
            dockPosition == NowBarDockPosition.BottomRight
        ) {
            previousBottomDockName = dockPosition.name
        }
        dockName = NowBarDockPosition.TopCloud.name
    }
    val collapseTopCloud = {
        if (topCloudVisible) {
            previousTopCloudName = dockPosition.name
        }
        dockName = NowBarDockPosition.TopCloudCollapsed.name
    }
    val showTopCloud = {
        dockName = NowBarDockPosition.entries.firstOrNull {
            it.name == previousTopCloudName &&
                (it == NowBarDockPosition.TopCloud || it == NowBarDockPosition.TopCloudExpanded)
        }?.name ?: NowBarDockPosition.TopCloud.name
    }
    val toggleTopCloudExpansion = {
        dockName = if (topCloudExpanded) {
            NowBarDockPosition.TopCloud.name
        } else {
            NowBarDockPosition.TopCloudExpanded.name
        }
    }
    val previewTopCloudFold: (Float?) -> Unit = { progress ->
        cloudFoldPreview = progress?.coerceIn(0f, 1f)
    }
    val restoreBottom = {
        val restored = NowBarDockPosition.entries.firstOrNull {
            it.name == previousBottomDockName &&
                (it == NowBarDockPosition.BottomLeft || it == NowBarDockPosition.BottomRight)
        } ?: NowBarDockPosition.BottomRight
        dockName = restored.name
    }
    LaunchedEffect(pageScrollEvent) {
        val pageScrolled = pageScrollEvent != acknowledgedPageScrollEvent
        acknowledgedPageScrollEvent = pageScrollEvent
        if (pageScrolled) {
            when {
                pageScrollEvent < 0 && topCloudVisible -> collapseTopCloud()
                pageScrollEvent > 0 && topCloudCollapsed -> showTopCloud()
            }
        }
    }
    val dockMorphProgress by animateFloatAsState(
        targetValue = if (docked) 1f else 0f,
        animationSpec = EchoMotion.floatSpring(),
        label = "nowBarDockMorph"
    )
    val barHeight = if (waveformExpanded) EchoMobileLayoutMetrics.nowBarExpandedHeight else EchoMobileLayoutMetrics.nowBarHeight
    val progressSlice = NowBarProgressSlice(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        playing = state.playing,
        elapsed = state.elapsed,
        duration = state.duration,
        trackId = state.trackId,
        contentUriString = state.contentUri?.toString(),
        dataPath = state.dataPath,
        waveformBars = state.waveformBars,
        waveformGeneratedBars = state.waveformGeneratedBars,
        waveformCachedProgress = state.waveformCachedProgress,
        playbackProgressLabel = state.playbackProgressLabel,
        expandWaveformLabel = state.expandWaveformLabel
    )
    val playbackScrub = rememberScrubbablePlaybackPosition(
        positionMs = progressSlice.positionMs,
        durationMs = progressSlice.durationMs,
        playing = progressSlice.playing
    )
    val trackSlice = NowBarTrackSlice(
        artUriString = state.albumArtUri?.toString(),
        title = state.title,
        subtitle = state.subtitle,
        canExpand = state.canExpand
    )
    val modeSlice = NowBarModeSlice(
        favoriteEnabled = state.favoriteEnabled,
        favorite = state.favorite,
        favoriteLabel = state.favoriteLabel,
        favoritedLabel = state.favoritedLabel,
        shuffleEnabled = state.shuffleEnabled,
        shuffleLabel = state.shuffleLabel,
        inOrderLabel = state.inOrderLabel,
        repeatOneLabel = state.repeatOneLabel,
        repeatAllLabel = state.repeatAllLabel,
        repeatOffLabel = state.repeatOffLabel,
        queueLabel = state.queueLabel,
        repeatMode = state.repeatMode
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val availableWidth = (maxWidth - EchoMobileLayoutMetrics.floatingChromeHorizontalPadding * 2)
            .coerceAtLeast(0.dp)
        val topCloudBaseWidth = if (topCloudExpanded) {
            EchoMobileLayoutMetrics.nowBarTopCloudExpandedWidth
        } else {
            EchoMobileLayoutMetrics.nowBarTopCloudWidth
        }
        val topCloudBaseHeight = if (topCloudExpanded) {
            EchoMobileLayoutMetrics.nowBarTopCloudExpandedHeight
        } else {
            EchoMobileLayoutMetrics.nowBarTopCloudHeight
        }
        val targetSurfaceWidth = when (dockPosition) {
            NowBarDockPosition.Expanded -> availableWidth
            NowBarDockPosition.BottomLeft,
            NowBarDockPosition.BottomRight -> EchoMobileLayoutMetrics.nowBarDockedWidth
            NowBarDockPosition.TopCloud,
            NowBarDockPosition.TopCloudExpanded,
            NowBarDockPosition.TopCloudCollapsed -> topCloudBaseWidth +
                (EchoMobileLayoutMetrics.nowBarTopCloudCollapsedWidth -
                    topCloudBaseWidth) * cloudFoldProgress
        }
        val targetSurfaceHeight = when (dockPosition) {
            NowBarDockPosition.Expanded -> barHeight
            NowBarDockPosition.BottomLeft,
            NowBarDockPosition.BottomRight -> EchoMobileLayoutMetrics.nowBarDockedHeight
            NowBarDockPosition.TopCloud,
            NowBarDockPosition.TopCloudExpanded,
            NowBarDockPosition.TopCloudCollapsed -> topCloudBaseHeight +
                (EchoMobileLayoutMetrics.nowBarTopCloudCollapsedHeight -
                    topCloudBaseHeight) * cloudFoldProgress
        }
        val surfaceWidth by animateDpAsState(
            targetValue = targetSurfaceWidth,
            animationSpec = if (cloudFoldPreview != null) snap() else tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockSizeDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockWidth"
        )
        val surfaceHeight by animateDpAsState(
            targetValue = targetSurfaceHeight,
            animationSpec = if (cloudFoldPreview != null) snap() else tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockSizeDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockHeight"
        )
        val dockTravel = ((availableWidth - EchoMobileLayoutMetrics.nowBarDockedWidth)
            .coerceAtLeast(0.dp)) / 2
        val surfaceHorizontalOffset by animateDpAsState(
            targetValue = when (dockPosition) {
                NowBarDockPosition.BottomLeft -> -dockTravel
                NowBarDockPosition.BottomRight -> dockTravel
                NowBarDockPosition.Expanded,
                NowBarDockPosition.TopCloud,
                NowBarDockPosition.TopCloudExpanded,
                NowBarDockPosition.TopCloudCollapsed -> 0.dp
            },
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockMoveDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockHorizontalOffset"
        )
        val topCloudY = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            if (topCloudCollapsed) {
                EchoMobileLayoutMetrics.nowBarTopCloudCollapsedOffset
            } else {
                EchoMobileLayoutMetrics.nowBarTopCloudOffset
            }
        val bottomY = (maxHeight - bottomInset - targetSurfaceHeight -
            if (docked) EchoMobileLayoutMetrics.nowBarDockedBottomPadding else 0.dp)
            .coerceAtLeast(0.dp)
        val surfaceVerticalOffset by animateDpAsState(
            targetValue = if (topCloudPosition) topCloudY else bottomY,
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockMoveDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockVerticalOffset"
        )
        EchoGlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = surfaceHorizontalOffset, y = surfaceVerticalOffset)
                .width(surfaceWidth)
                .height(surfaceHeight)
                .testTag("echo-now-bar-surface")
                .blockPointerInputBehind()
                .graphicsLayer {
                    translationY = with(density) {
                        EchoMobileLayoutMetrics.nowBarScrollTranslation.toPx() * scrollCompactProgress +
                            EchoMobileLayoutMetrics.nowBarScrollStretchTranslation.toPx() * scrollStretchProgress
                    }
                    scaleY = 1f -
                        (1f - EchoMobileLayoutMetrics.nowBarScrollScale) * scrollCompactProgress +
                        (EchoMobileLayoutMetrics.nowBarScrollStretchScale - 1f) * scrollStretchProgress
                }
                .semantics {
                    customActions = if (topCloudCollapsed) {
                        listOf(
                            CustomAccessibilityAction(
                                state.showTopCloudLabel.ifBlank { "显示流体云" }
                            ) {
                                showTopCloud()
                                true
                            },
                            CustomAccessibilityAction(
                                state.restoreBottomLabel.ifBlank { "恢复到底部" }
                            ) {
                                restoreBottom()
                                true
                            }
                        )
                    } else if (topCloudVisible) {
                        listOf(
                            CustomAccessibilityAction(
                                if (topCloudExpanded) {
                                    state.compactTopCloudLabel.ifBlank { "收起流体云内容" }
                                } else {
                                    state.expandTopCloudLabel.ifBlank { "展开流体云内容" }
                                }
                            ) {
                                toggleTopCloudExpansion()
                                true
                            },
                            CustomAccessibilityAction(
                                state.expandNowBarLabel.ifBlank { "展开 Now Bar" }
                            ) {
                                dockName = NowBarDockPosition.Expanded.name
                                true
                            },
                            CustomAccessibilityAction(
                                state.restoreBottomLabel.ifBlank { "恢复到底部" }
                            ) {
                                restoreBottom()
                                true
                            },
                            CustomAccessibilityAction(
                                state.collapseTopCloudLabel.ifBlank { "折叠流体云" }
                            ) {
                                collapseTopCloud()
                                true
                            },
                            CustomAccessibilityAction(
                                state.dockLeftLabel.ifBlank { "停靠左侧" }
                            ) {
                                dockBottomLeft()
                                true
                            },
                            CustomAccessibilityAction(
                                state.dockRightLabel.ifBlank { "停靠右侧" }
                            ) {
                                dockBottomRight()
                                true
                            }
                        )
                    } else if (docked) {
                        listOf(
                            CustomAccessibilityAction(
                                state.expandNowBarLabel.ifBlank { "展开 Now Bar" }
                            ) {
                                dockName = NowBarDockPosition.Expanded.name
                                true
                            },
                            CustomAccessibilityAction(
                                state.dockTopLabel.ifBlank { "停靠顶部" }
                            ) {
                                dockTop()
                                true
                            },
                            CustomAccessibilityAction(
                                if (dockPosition == NowBarDockPosition.BottomRight) {
                                    state.dockLeftLabel.ifBlank { "停靠左侧" }
                                } else {
                                    state.dockRightLabel.ifBlank { "停靠右侧" }
                                }
                            ) {
                                if (dockPosition == NowBarDockPosition.BottomRight) {
                                    dockBottomLeft()
                                } else {
                                    dockBottomRight()
                                }
                                true
                            }
                        )
                    } else {
                        listOf(
                            CustomAccessibilityAction(
                                state.dockLeftLabel.ifBlank { "停靠左侧" }
                            ) {
                                onCollapseWaveform()
                                dockBottomLeft()
                                true
                            },
                            CustomAccessibilityAction(
                                state.dockRightLabel.ifBlank { "停靠右侧" }
                            ) {
                                onCollapseWaveform()
                                dockBottomRight()
                                true
                            }
                        )
                    }
                },
            shape = if (docked) EchoShapes.pill else EchoShapes.large,
            elevation = EchoMobileLayoutMetrics.floatingChromeElevation *
                (if (topCloudPosition) 0.72f else 1f) *
                (1f - compactProgress * (1f - EchoMobileLayoutMetrics.nowBarCompactShadowFactor))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (!docked || dockMorphProgress < 0.99f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .graphicsLayer {
                                alpha = 1f - dockMorphProgress
                                scaleX = 1f - dockMorphProgress * 0.08f
                                scaleY = 1f - dockMorphProgress * 0.08f
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MiniLyricsStrip(state, compactProgress)
                        NowBarProgressSection(
                            slice = progressSlice,
                            scrub = playbackScrub,
                            waveformExpanded = waveformExpanded,
                            onExpandWaveform = onExpandWaveform,
                            onCollapseWaveform = onCollapseWaveform,
                            onSeek = onSeek
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EchoMobileLayoutMetrics.nowBarArtworkSize),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NowBarTrackSection(
                                slice = trackSlice,
                                onOpenNowPlaying = onOpenNowPlaying,
                                onCollapseWaveform = onCollapseWaveform,
                                onDockLeft = {
                                    onCollapseWaveform()
                                    dockBottomLeft()
                                },
                                onDockRight = {
                                    onCollapseWaveform()
                                    dockBottomRight()
                                },
                                onDockTop = {
                                    onCollapseWaveform()
                                    dockTop()
                                },
                                dockGesturesEnabled = !docked,
                                modifier = Modifier.weight(1f)
                            )
                            NowBarTransportControls(
                                slice = NowBarTransportSlice(
                                    playing = state.playing,
                                    previousLabel = state.previousLabel,
                                    playLabel = state.playLabel,
                                    pauseLabel = state.pauseLabel,
                                    nextLabel = state.nextLabel
                                ),
                                onPrevious = onPrevious,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                onCollapseWaveform = onCollapseWaveform
                            )
                        }
                        if (!waveformExpanded) {
                            Spacer(Modifier.height(2.dp))
                            NowBarModeControls(modeSlice, onFavorite, onShuffle, onRepeat, onOpenQueue, onCollapseWaveform)
                        }
                    }
                    if (waveformExpanded) {
                        BottomWaveformProgress(
                            slice = progressSlice,
                            scrub = playbackScrub,
                            onSeek = onSeek,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
                if (dockMorphProgress > 0.01f) {
                    if (topCloudPosition) {
                        DockedNowBarCapsule(
                            state = state,
                            dockPosition = dockPosition,
                            expandedTopCloud = topCloudExpanded,
                            onExpand = toggleTopCloudExpansion,
                            onDockLeft = dockBottomLeft,
                            onDockRight = dockBottomRight,
                            onDockTop = dockTop,
                            onRestoreBottom = restoreBottom,
                            onCollapseTopCloud = collapseTopCloud,
                            onPreviewTopCloudFold = previewTopCloudFold,
                            onPlayPause = onPlayPause,
                            interactive = !topCloudCollapsed,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = dockMorphProgress * (1f - cloudFoldProgress)
                                }
                        )
                        CollapsedTopCloudHandle(
                            state = state,
                            onShowTopCloud = showTopCloud,
                            onRestoreBottom = restoreBottom,
                            onPreviewTopCloudFold = previewTopCloudFold,
                            interactive = topCloudCollapsed,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = dockMorphProgress * cloudFoldProgress
                                }
                        )
                    } else {
                        DockedNowBarCapsule(
                            state = state,
                            dockPosition = dockPosition,
                            expandedTopCloud = false,
                            onExpand = { dockName = NowBarDockPosition.Expanded.name },
                            onDockLeft = dockBottomLeft,
                            onDockRight = dockBottomRight,
                            onDockTop = dockTop,
                            onRestoreBottom = restoreBottom,
                            onCollapseTopCloud = collapseTopCloud,
                            onPreviewTopCloudFold = previewTopCloudFold,
                            onPlayPause = onPlayPause,
                            interactive = docked,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = dockMorphProgress }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockedNowBarCapsule(
    state: NowBarState,
    dockPosition: NowBarDockPosition,
    expandedTopCloud: Boolean,
    onExpand: () -> Unit,
    onDockLeft: () -> Unit,
    onDockRight: () -> Unit,
    onDockTop: () -> Unit,
    onRestoreBottom: () -> Unit,
    onCollapseTopCloud: () -> Unit,
    onPreviewTopCloudFold: (Float?) -> Unit,
    onPlayPause: Runnable,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val capsuleLyrics = state.lyrics.firstOrNull { it.active }?.text
        ?: state.lyrics.firstOrNull()?.text
        ?: state.lyricsStatus.takeIf { it.isNotBlank() }
        ?: state.title
    val interactionLabel = when (dockPosition) {
        NowBarDockPosition.TopCloud ->
            state.expandTopCloudLabel.ifBlank { "展开流体云内容" }
        NowBarDockPosition.TopCloudExpanded ->
            state.compactTopCloudLabel.ifBlank { "收起流体云内容" }
        else -> state.expandNowBarLabel.ifBlank { "展开 Now Bar" }
    }
    val expandInteraction = if (interactive) {
        Modifier
            .nowBarDockGesture(
                enabled = true,
                dockPosition = dockPosition,
                onDockLeft = onDockLeft,
                onDockRight = onDockRight,
                onDockTop = onDockTop,
                onRestoreBottom = onRestoreBottom,
                onCollapseTopCloud = onCollapseTopCloud,
                onPreviewTopCloudFold = onPreviewTopCloudFold,
                onTap = onExpand
            )
            .semantics {
                contentDescription = interactionLabel
                onClick(interactionLabel) {
                    onExpand()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(
                        interactionLabel
                    ) {
                        onExpand()
                        true
                    }
                )
            }
    } else {
        Modifier
    }
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = p.accentSoft.copy(alpha = 0.52f),
                size = Size(size.width * progress, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(expandInteraction)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = capsuleLyrics.replace('\n', ' '),
                        style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (expandedTopCloud) {
                        Text(
                            text = listOf(state.title, state.subtitle)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = EchoTypography.small,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            val playbackIcon = if (state.playing) EchoIconKind.Pause else EchoIconKind.Play
            if (interactive) {
                IconButton(
                    icon = playbackIcon,
                    desc = if (state.playing) state.pauseLabel else state.playLabel,
                    accent = true
                ) {
                    onPlayPause.run()
                }
            } else {
                DockedPlaybackIndicator(playbackIcon)
            }
        }
    }
}

@Composable
private fun CollapsedTopCloudHandle(
    state: NowBarState,
    onShowTopCloud: () -> Unit,
    onRestoreBottom: () -> Unit,
    onPreviewTopCloudFold: (Float?) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val interaction = if (interactive) {
        Modifier
            .nowBarDockGesture(
                enabled = true,
                dockPosition = NowBarDockPosition.TopCloudCollapsed,
                onDockLeft = {},
                onDockRight = {},
                onRestoreBottom = onRestoreBottom,
                onShowTopCloud = onShowTopCloud,
                onPreviewTopCloudFold = onPreviewTopCloudFold,
                onTap = onShowTopCloud
            )
            .semantics {
                contentDescription = state.showTopCloudLabel.ifBlank { "显示流体云" }
                onClick(state.showTopCloudLabel.ifBlank { "显示流体云" }) {
                    onShowTopCloud()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(
                        state.restoreBottomLabel.ifBlank { "恢复到底部" }
                    ) {
                        onRestoreBottom()
                        true
                    }
                )
            }
    } else {
        Modifier
    }
    Box(
        modifier = modifier.then(interaction)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = p.accent.copy(alpha = 0.58f),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }
    }
}

@Composable
private fun DockedPlaybackIndicator(icon: EchoIconKind) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = p.accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(18.dp), p.onAccent)
        }
    }
}

@Composable
private fun MiniLyricsStrip(state: NowBarState, compactProgress: Float) {
    val activeLine = state.lyrics.firstOrNull { it.active }?.text
        ?: state.lyrics.firstOrNull()?.text
        ?: state.lyricsStatus
    if (activeLine.isBlank() || !state.canExpand) {
        Spacer(Modifier.height(0.dp))
        return
    }
    val p = EchoTheme.colors()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .graphicsLayer {
                alpha = 1f - compactProgress * (1f - EchoMobileLayoutMetrics.nowBarCompactLyricsAlpha)
            }
            .clickable {
                clipboard.setText(AnnotatedString(activeLine))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            .semantics { contentDescription = state.lyricsTitle.ifBlank { "歌词" } },
        shape = EchoShapes.small,
        color = p.accentSoft.copy(alpha = 0.32f)
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Text(
                text = activeLine.replace('\n', ' '),
                style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 9.dp)
            )
        }
    }
}

@Composable
private fun NowBarProgressSection(
    slice: NowBarProgressSlice,
    scrub: ScrubbablePlaybackPosition,
    waveformExpanded: Boolean,
    onExpandWaveform: () -> Unit,
    onCollapseWaveform: () -> Unit,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    if (waveformExpanded) {
        CollapsedProgress(
            scrub = scrub,
            cachedProgress = slice.waveformCachedProgress,
            progressLabel = slice.playbackProgressLabel,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoMobileLayoutMetrics.nowBarProgressHeight)
        )
    } else {
        CollapsedProgress(
            scrub = scrub,
            cachedProgress = slice.waveformCachedProgress,
            progressLabel = slice.playbackProgressLabel,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoMobileLayoutMetrics.nowBarProgressHeight)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (waveformExpanded) onCollapseWaveform else onExpandWaveform)
            .semantics {
                contentDescription = slice.expandWaveformLabel.ifBlank { slice.playbackProgressLabel }
            }
            .padding(top = 0.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(Track.formatDuration(scrub.displayPosition.value), style = EchoTypography.caption, color = p.muted)
        Text(slice.duration, style = EchoTypography.caption, color = p.muted)
    }
}

@Composable
private fun BottomWaveformProgress(
    slice: NowBarProgressSlice,
    scrub: ScrubbablePlaybackPosition,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .clipToBounds()
    ) {
        WaveformProgress(
            scrub = scrub,
            durationMs = slice.durationMs,
            playing = slice.playing,
            trackId = slice.trackId,
            contentUriString = slice.contentUriString,
            dataPath = slice.dataPath,
            serviceWaveformBars = slice.waveformBars,
            serviceWaveformGeneratedBars = slice.waveformGeneratedBars,
            serviceWaveformCachedProgress = slice.waveformCachedProgress,
            progressLabel = slice.playbackProgressLabel,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

@Composable
private fun CollapsedProgress(
    scrub: ScrubbablePlaybackPosition,
    cachedProgress: Float,
    progressLabel: String,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = progressLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (scrub.displayPosition.value.toFloat() / scrub.duration.toFloat())
                        .coerceIn(0f, 1f),
                    range = 0f..1f
                )
                if (scrub.seekEnabled) {
                    setProgress { targetProgress ->
                        onSeek.seekTo((scrub.duration * targetProgress.coerceIn(0f, 1f)).toLong())
                        true
                    }
                }
            }
            .playbackSeekGesture(scrub, onSeek)
    ) {
        val progress = (scrub.displayPosition.value.toFloat() / scrub.duration.toFloat())
            .coerceIn(0f, 1f)
        val width = size.width
        val trackHeight = min(size.height, 7.dp.toPx())
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        drawRoundRect(
            color = p.surfaceVariant.copy(alpha = 0.58f),
            topLeft = Offset(0f, top),
            size = Size(width, trackHeight),
            cornerRadius = radius
        )
        val cachedWidth = width * cachedProgress.coerceIn(0f, 1f).coerceAtLeast(progress)
        if (cachedWidth > 0f) {
            drawRoundRect(
                color = p.accentSoft.copy(alpha = 0.62f),
                topLeft = Offset(0f, top),
                size = Size(cachedWidth, trackHeight),
                cornerRadius = radius
            )
        }
        val playedWidth = width * progress
        if (playedWidth > 0f) {
            drawRoundRect(
                color = p.accent,
                topLeft = Offset(0f, top),
                size = Size(playedWidth, trackHeight),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun NowBarTrackSection(
    slice: NowBarTrackSlice,
    onOpenNowPlaying: Runnable,
    onCollapseWaveform: () -> Unit,
    onDockLeft: () -> Unit,
    onDockRight: () -> Unit,
    onDockTop: () -> Unit,
    dockGesturesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val uri = remember(slice.artUriString) { slice.artUriString?.let { Uri.parse(it) } }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copyText = remember(slice.title, slice.subtitle) {
        listOf(slice.title, slice.subtitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    Row(
        modifier = modifier
            .nowBarDockGesture(
                enabled = dockGesturesEnabled && slice.canExpand,
                onDockLeft = onDockLeft,
                onDockRight = onDockRight,
                onDockTop = onDockTop
            )
            .combinedClickable(
                enabled = slice.canExpand,
                onClick = {
                    onCollapseWaveform()
                    onOpenNowPlaying.run()
                },
                onLongClick = {
                    if (copyText.isNotBlank()) {
                        clipboard.setText(AnnotatedString(copyText))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(uri, slice.title, slice.subtitle)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                slice.title,
                style = EchoTypography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                ),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slice.subtitle.isNotBlank()) {
                Text(
                    slice.subtitle,
                    style = EchoTypography.caption.copy(lineHeight = 15.sp),
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NowBarTransportControls(
    slice: NowBarTransportSlice,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable,
    onCollapseWaveform: () -> Unit
) {
    Row(
        modifier = Modifier.width(102.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(EchoIconKind.Previous, slice.previousLabel) {
            onCollapseWaveform()
            onPrevious.run()
        }
        IconButton(
            icon = if (slice.playing) EchoIconKind.Pause else EchoIconKind.Play,
            desc = if (slice.playing) slice.pauseLabel else slice.playLabel,
            accent = true
        ) {
            onCollapseWaveform()
            onPlayPause.run()
        }
        IconButton(EchoIconKind.Next, slice.nextLabel) {
            onCollapseWaveform()
            onNext.run()
        }
    }
}

@Composable
private fun NowBarModeControls(
    slice: NowBarModeSlice,
    onFavorite: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onQueue: Runnable,
    onCollapseWaveform: () -> Unit
) {
    if (!slice.favoriteEnabled) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AuxChip(
            icon = EchoIconKind.Heart,
            label = if (slice.favorite) slice.favoritedLabel else slice.favoriteLabel,
            active = slice.favorite,
            modifier = Modifier.weight(1f)
        ) {
            onCollapseWaveform()
            onFavorite.run()
        }
        AuxChip(
            icon = EchoIconKind.Shuffle,
            label = if (slice.shuffleEnabled) slice.shuffleLabel else slice.inOrderLabel,
            active = slice.shuffleEnabled,
            modifier = Modifier.weight(1f)
        ) {
            onCollapseWaveform()
            onShuffle.run()
        }
        AuxChip(
            icon = EchoIconKind.Repeat,
            label = when (slice.repeatMode) {
                PlaybackRepeatMode.REPEAT_ONE -> slice.repeatOneLabel
                PlaybackRepeatMode.REPEAT_ALL -> slice.repeatAllLabel
                else -> slice.repeatOffLabel
            },
            active = slice.repeatMode != PlaybackRepeatMode.REPEAT_OFF,
            modifier = Modifier.weight(1f)
        ) {
            onCollapseWaveform()
            onRepeat.run()
        }
        AuxChip(
            icon = EchoIconKind.Queue,
            label = slice.queueLabel,
            active = false,
            modifier = Modifier.weight(1f)
        ) {
            onCollapseWaveform()
            onQueue.run()
        }
    }
}

@Composable
private fun AlbumArtThumb(uri: Uri?, title: String, subtitle: String) {
    val p = EchoTheme.colors()
    AsyncArtwork(
        uri = uri,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.size(EchoMobileLayoutMetrics.nowBarArtworkSize),
        cornerRadius = EchoMobileLayoutMetrics.nowBarArtworkCornerRadius,
        fallbackTextSize = 16.sp,
        targetSize = EchoMobileLayoutMetrics.nowBarArtworkSize,
        backgroundColor = p.surfaceVariant,
        fallbackResId = R.drawable.ic_stat_echo
    )
}

private fun Modifier.playbackSeekGesture(
    scrub: ScrubbablePlaybackPosition,
    onSeek: SeekAction
): Modifier = pointerInput(scrub.seekEnabled, scrub.duration) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!scrub.seekEnabled) {
            return@awaitEachGesture
        }
        var targetPosition = scrub.scrubTo(down.position.x, size.width.toFloat())
        try {
            val completed = drag(down.id) { change ->
                targetPosition = scrub.scrubTo(change.position.x, size.width.toFloat())
                change.consume()
            }
            if (completed) {
                onSeek.seekTo(targetPosition)
            }
        } finally {
            scrub.clearScrub()
        }
    }
}

private fun Modifier.nowBarDockGesture(
    enabled: Boolean,
    dockPosition: NowBarDockPosition = NowBarDockPosition.Expanded,
    onDockLeft: () -> Unit,
    onDockRight: () -> Unit,
    onDockTop: () -> Unit = {},
    onRestoreBottom: () -> Unit = {},
    onCollapseTopCloud: () -> Unit = {},
    onShowTopCloud: () -> Unit = {},
    onPreviewTopCloudFold: (Float?) -> Unit = {},
    onTap: () -> Unit = {}
): Modifier = composed {
    if (!enabled) return@composed this
    val density = LocalDensity.current
    val distanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarDockSwipeDistance.toPx()
    }
    val velocityThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarDockSwipeVelocityDpPerSecond.dp.toPx()
    }
    val topCloudEnterDistanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudEnterDistance.toPx()
    }
    val topCloudVelocityThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudSwipeVelocityDpPerSecond.dp.toPx()
    }
    val restoreDistanceThresholdPx = with(density) {
        EchoMobileLayoutMetrics.nowBarTopCloudRestoreDistance.toPx()
    }
    pointerInput(
        dockPosition,
        onDockLeft,
        onDockRight,
        onDockTop,
        onRestoreBottom,
        onCollapseTopCloud,
        onShowTopCloud,
        onPreviewTopCloudFold,
        onTap,
        distanceThresholdPx,
        topCloudEnterDistanceThresholdPx,
        restoreDistanceThresholdPx,
        velocityThresholdPx,
        topCloudVelocityThresholdPx
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var deltaX = 0f
            var deltaY = 0f
            var gestureAxis = 0

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                deltaX = change.position.x - down.position.x
                deltaY = change.position.y - down.position.y

                if (gestureAxis == 0) {
                    val topCloudDiagonalDown =
                        (dockPosition == NowBarDockPosition.TopCloud ||
                            dockPosition == NowBarDockPosition.TopCloudExpanded) &&
                            deltaY > viewConfiguration.touchSlop &&
                            abs(deltaY) > abs(deltaX) / EchoMobileLayoutMetrics.nowBarDockHorizontalRatio
                    gestureAxis = when {
                        abs(deltaX) > viewConfiguration.touchSlop &&
                            abs(deltaX) > EchoMobileLayoutMetrics.nowBarDockHorizontalRatio * abs(deltaY) -> 1
                        topCloudDiagonalDown -> 2
                        abs(deltaY) > viewConfiguration.touchSlop &&
                            abs(deltaY) > EchoMobileLayoutMetrics.nowBarDockVerticalRatio * abs(deltaX) -> 2
                        else -> 0
                    }
                }
                if (gestureAxis != 0) change.consume()
                if (gestureAxis == 2) {
                    when {
                        (dockPosition == NowBarDockPosition.TopCloud ||
                            dockPosition == NowBarDockPosition.TopCloudExpanded) && deltaY < 0f ->
                            onPreviewTopCloudFold(
                                (abs(deltaY) / topCloudEnterDistanceThresholdPx).coerceIn(0f, 1f)
                            )
                        dockPosition == NowBarDockPosition.TopCloudCollapsed && deltaY > 0f ->
                            onPreviewTopCloudFold(
                                (1f - abs(deltaY) / restoreDistanceThresholdPx).coerceIn(0f, 1f)
                            )
                    }
                }

                if (!change.pressed) {
                    val velocity = velocityTracker.calculateVelocity()
                    val horizontalDominant =
                        abs(deltaX) > EchoMobileLayoutMetrics.nowBarDockHorizontalRatio * abs(deltaY)
                    val verticalDominant =
                        abs(deltaY) > EchoMobileLayoutMetrics.nowBarDockVerticalRatio * abs(deltaX)
                    val horizontalThreshold =
                        abs(deltaX) >= distanceThresholdPx || abs(velocity.x) >= velocityThresholdPx
                    if (dockPosition != NowBarDockPosition.TopCloud &&
                        dockPosition != NowBarDockPosition.TopCloudExpanded &&
                        dockPosition != NowBarDockPosition.TopCloudCollapsed &&
                        horizontalDominant && horizontalThreshold
                    ) {
                        val direction = if (abs(deltaX) >= distanceThresholdPx) deltaX else velocity.x
                        if (direction > 0f) onDockRight() else onDockLeft()
                    } else if (gestureAxis == 2 || verticalDominant) {
                        val direction = if (abs(deltaY) > viewConfiguration.touchSlop) {
                            deltaY
                        } else {
                            velocity.y
                        }
                        val verticalDistance = if (
                            dockPosition == NowBarDockPosition.TopCloudCollapsed ||
                            ((dockPosition == NowBarDockPosition.TopCloud ||
                                dockPosition == NowBarDockPosition.TopCloudExpanded) && direction > 0f)
                        ) {
                            restoreDistanceThresholdPx
                        } else {
                            topCloudEnterDistanceThresholdPx
                        }
                        val verticalThreshold =
                            abs(deltaY) >= verticalDistance ||
                                abs(velocity.y) >= topCloudVelocityThresholdPx
                        if (verticalThreshold) {
                            val resolvedDirection = if (abs(deltaY) >= verticalDistance) deltaY else velocity.y
                            when {
                                dockPosition == NowBarDockPosition.Expanded && resolvedDirection < 0f ->
                                    onDockTop()
                                (dockPosition == NowBarDockPosition.TopCloud ||
                                    dockPosition == NowBarDockPosition.TopCloudExpanded) && resolvedDirection > 0f -> {
                                    val diagonalThreshold = abs(deltaY) *
                                        EchoMobileLayoutMetrics.nowBarTopCloudDiagonalDockRatio
                                    when {
                                        deltaX <= -diagonalThreshold -> onDockLeft()
                                        deltaX >= diagonalThreshold -> onDockRight()
                                        else -> onRestoreBottom()
                                    }
                                }
                                (dockPosition == NowBarDockPosition.TopCloud ||
                                    dockPosition == NowBarDockPosition.TopCloudExpanded) && resolvedDirection < 0f ->
                                    onCollapseTopCloud()
                                dockPosition == NowBarDockPosition.TopCloudCollapsed && resolvedDirection > 0f ->
                                    onShowTopCloud()
                                (dockPosition == NowBarDockPosition.BottomLeft ||
                                    dockPosition == NowBarDockPosition.BottomRight) && resolvedDirection < 0f ->
                                    onDockTop()
                            }
                        }
                    } else if (gestureAxis == 0 &&
                        abs(deltaX) <= viewConfiguration.touchSlop &&
                        abs(deltaY) <= viewConfiguration.touchSlop
                    ) {
                        onTap()
                    }
                    if (dockPosition == NowBarDockPosition.TopCloud ||
                        dockPosition == NowBarDockPosition.TopCloudExpanded ||
                        dockPosition == NowBarDockPosition.TopCloudCollapsed
                    ) {
                        onPreviewTopCloudFold(null)
                    }
                    break
                }
            }
        }
    }
}

@Composable
private fun WaveformProgress(
    scrub: ScrubbablePlaybackPosition,
    durationMs: Long,
    playing: Boolean,
    trackId: Long,
    contentUriString: String?,
    dataPath: String,
    serviceWaveformBars: FloatArray,
    serviceWaveformGeneratedBars: Int,
    serviceWaveformCachedProgress: Float,
    progressLabel: String,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val context = LocalContext.current
    val barCount = 96
    val localWaveform by rememberPlaybackWaveform(
        context = context,
        trackId = trackId,
        contentUriString = contentUriString,
        dataPath = dataPath,
        durationMs = durationMs,
        barCount = barCount
    )
    val serviceGeneratedBars = serviceWaveformGeneratedBars.coerceIn(0, serviceWaveformBars.size)
    val serviceHasVisibleWaveform = serviceGeneratedBars > 0 &&
        hasVisibleWaveformBars(serviceWaveformBars, serviceGeneratedBars)
    val placeholderWaveform = remember(trackId, contentUriString, dataPath, durationMs) {
        streamingWaveformPreviewBars(
            key = "$trackId|${contentUriString.orEmpty()}|$dataPath|$durationMs",
            barCount = barCount
        )
    }
    val localWaveformBars = localWaveform
    val waveform = if (serviceHasVisibleWaveform) {
        serviceWaveformBars
    } else {
        localWaveformBars ?: placeholderWaveform
    }
    val generatedBars = if (serviceHasVisibleWaveform) {
        serviceGeneratedBars
    } else if (localWaveformBars != null) {
        localWaveformBars.size
    } else {
        placeholderWaveform.size
    }.coerceIn(0, barCount)
    val cachedProgress = waveformCachedProgressForDraw(
        serviceWaveformCachedProgress,
        serviceHasVisibleWaveform
    )
    val visiblePeakRange = remember(waveform, generatedBars) {
        visibleWaveformPeakRange(waveform, generatedBars)
    }
    val waveformMotionPhase = rememberLiveWaveformPhase(playing)
    Box(
        modifier = modifier
            .semantics { contentDescription = progressLabel }
            .drawWithCache {
                val trackHeight = size.height
                val width = size.width
                val gap = 0.75.dp.toPx()
                val barWidth = max(1.0.dp.toPx(), (width - gap * (barCount - 1)) / barCount)
                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                val playedColor = p.accent
                val cachedColor = p.accentSoft.copy(alpha = 0.78f)
                val waveformBars = waveform
                val visibleMinPeak = visiblePeakRange.first
                val visibleSpan = visiblePeakRange.second
                onDrawBehind {
                    val progress = (scrub.displayPosition.value.toFloat() / scrub.duration.toFloat())
                        .coerceIn(0f, 1f)
                    val playedWidth = width * progress
                    drawRoundRect(
                        color = p.surfaceVariant.copy(alpha = 0.72f),
                        topLeft = Offset(0f, 0f),
                        size = Size(width, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                    if (generatedBars < barCount && playedWidth > 0f) {
                        val generatedWidth = width * (generatedBars.toFloat() / barCount.toFloat())
                        val pendingPlayedStart = generatedWidth.coerceIn(0f, playedWidth)
                        val pendingPlayedWidth = playedWidth - pendingPlayedStart
                        if (pendingPlayedWidth > 0f) {
                            val railHeight = max(2.dp.toPx(), trackHeight * 0.16f)
                            drawRoundRect(
                                color = playedColor.copy(alpha = 0.50f),
                                topLeft = Offset(pendingPlayedStart, (trackHeight - railHeight) / 2f),
                                size = Size(pendingPlayedWidth, railHeight),
                                cornerRadius = CornerRadius(railHeight / 2f, railHeight / 2f)
                            )
                        }
                    }
                    val visibleWidth = width * waveformVisibleProgressForDraw(
                        cachedProgress = cachedProgress,
                        playbackProgress = progress,
                        serviceHasVisibleWaveform = serviceHasVisibleWaveform,
                        generatedBars = generatedBars,
                        barCount = barCount
                    )
                    for (index in 0 until barCount) {
                        val x = index * (barWidth + gap)
                        val played = x + barWidth / 2f <= playedWidth
                        val visible = x + barWidth / 2f <= visibleWidth
                        if (index >= generatedBars || (!played && !visible)) {
                            continue
                        }
                        val normalizedPeak = ((waveformBars.getOrElse(index) { 0f } - visibleMinPeak) / visibleSpan)
                            .coerceIn(0f, 1f)
                        val basePeak = sqrt(normalizedPeak).coerceAtLeast(if (played) 0.16f else 0.10f)
                        val shapedPeak = liveWaveformPeak(basePeak, index, waveformMotionPhase.value, playing)
                        val h = trackHeight * shapedPeak
                        val y = (trackHeight - h) / 2f
                        drawRoundRect(
                            color = if (played) playedColor else cachedColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, h),
                            cornerRadius = radius
                        )
                    }
                    val thumbX = (width * progress).coerceIn(4.dp.toPx(), width - 4.dp.toPx())
                    drawRoundRect(
                        color = p.onAccent.copy(alpha = 0.92f),
                        topLeft = Offset(thumbX - 2.dp.toPx(), -1.dp.toPx()),
                        size = Size(4.dp.toPx(), trackHeight + 2.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
            .playbackSeekGesture(scrub, onSeek)
    )
}

internal fun visibleWaveformPeakRange(waveformBars: FloatArray?, generatedBars: Int): Pair<Float, Float> {
    if (waveformBars == null || generatedBars <= 0) {
        return 0f to 1f
    }
    val visibleCount = generatedBars.coerceAtMost(waveformBars.size)
    if (visibleCount <= 0) {
        return 0f to 1f
    }
    val peaks = FloatArray(visibleCount)
    for (index in 0 until visibleCount) {
        peaks[index] = waveformBars[index].coerceIn(0f, 1f)
    }
    peaks.sort()
    val lowIndex = ((visibleCount - 1) * 0.12f).toInt().coerceIn(0, visibleCount - 1)
    val highIndex = ((visibleCount - 1) * 0.94f).toInt().coerceIn(lowIndex, visibleCount - 1)
    val lowPeak = peaks[lowIndex]
    val highPeak = peaks[highIndex]
    val visibleMinPeak = (lowPeak * 0.70f).coerceAtMost(highPeak * 0.64f)
    val visibleSpan = (highPeak - visibleMinPeak).coerceAtLeast(0.05f)
    return visibleMinPeak to visibleSpan
}

internal fun hasVisibleWaveformBars(waveformBars: FloatArray, generatedBars: Int): Boolean {
    val visibleCount = generatedBars.coerceIn(0, waveformBars.size)
    for (index in 0 until visibleCount) {
        if (waveformBars[index] > 0.015f) {
            return true
        }
    }
    return false
}

internal fun waveformCachedProgressForDraw(serviceCachedProgress: Float, serviceHasVisibleWaveform: Boolean): Float {
    val clampedServiceProgress = serviceCachedProgress.coerceIn(0f, 1f)
    return if (serviceHasVisibleWaveform) {
        clampedServiceProgress
    } else {
        1f
    }
}

internal fun waveformVisibleProgressForDraw(
    cachedProgress: Float,
    playbackProgress: Float,
    serviceHasVisibleWaveform: Boolean,
    generatedBars: Int,
    barCount: Int
): Float {
    val baseProgress = cachedProgress.coerceAtLeast(playbackProgress).coerceIn(0f, 1f)
    if (!serviceHasVisibleWaveform || barCount <= 0) {
        return baseProgress
    }
    val generatedProgress = (generatedBars.toFloat() / barCount.toFloat()).coerceIn(0f, 1f)
    return baseProgress.coerceAtLeast(generatedProgress)
}

@Composable
private fun rememberLiveWaveformPhase(playing: Boolean): State<Float> {
    if (!playing) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "waveformPulse")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 940, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformPulsePhase"
    )
}

internal fun liveWaveformPeak(basePeak: Float, index: Int, phase: Float, playing: Boolean): Float {
    val base = basePeak.coerceIn(0f, 1f)
    if (!playing) {
        return base
    }
    val primary = sin(phase * WAVEFORM_TWO_PI + index * 0.73f)
    val secondary = sin(phase * WAVEFORM_TWO_PI * 2f + index * 1.91f + 1.7f)
    val flutter = primary * 0.055f + secondary * 0.025f
    return (base * (1f + flutter) + max(0f, flutter) * 0.018f).coerceIn(0.06f, 1f)
}

internal fun streamingWaveformPreviewBars(key: String, barCount: Int): FloatArray {
    if (barCount <= 0) {
        return FloatArray(0)
    }
    var seed = key.hashCode()
    if (seed == 0) {
        seed = 0x4d595df4
    }
    val bars = FloatArray(barCount)
    var previous = 0.46f
    for (index in 0 until barCount) {
        seed = seed * 1103515245 + 12345
        val noise = ((seed ushr 16) and 0x7fff) / 32767f
        val phrase = kotlin.math.sin((index + 1) * 0.42f).toFloat() * 0.16f
        val beat = if (index % 4 == 0) 0.13f else 0f
        val target = (0.30f + noise * 0.42f + phrase + beat).coerceIn(0.12f, 0.94f)
        previous = (previous * 0.58f + target * 0.42f).coerceIn(0.10f, 0.96f)
        bars[index] = previous
    }
    return bars
}

@Composable
private fun IconButton(
    icon: EchoIconKind,
    desc: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val bg = if (accent) p.accent else p.surfaceVariant.copy(alpha = 0.34f)
    val fg = if (accent) p.onAccent else p.text
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(if (accent) 34.dp else 28.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = desc },
        shape = CircleShape,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = icon,
                animationSpec = androidx.compose.animation.core.tween(EchoMotion.FAST_CROSSFADE_MS),
                label = "nowBarIcon"
            ) { current ->
                EchoIcon(current, Modifier.size(if (accent) 18.dp else 15.dp), fg)
            }
        }
    }
}

@Composable
private fun AuxChip(
    icon: EchoIconKind,
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (active) p.accentSoft.copy(alpha = 0.62f) else p.surfaceVariant.copy(alpha = 0.30f),
        animationSpec = EchoMotion.colorSpring(),
        label = "auxChipContainer"
    )
    val tint by animateColorAsState(
        targetValue = if (active) p.accent else p.muted,
        animationSpec = EchoMotion.colorSpring(),
        label = "auxChipTint"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .height(26.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = label },
        shape = EchoShapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            EchoIcon(icon, Modifier.size(13.dp), tint)
            Spacer(Modifier.width(3.dp))
            Text(
                label,
                style = EchoTypography.small.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (active) p.accent else p.muted,
                maxLines = 1
            )
        }
    }
}
