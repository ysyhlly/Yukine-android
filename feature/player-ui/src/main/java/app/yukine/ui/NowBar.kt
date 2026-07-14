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
import app.yukine.core.designsystem.R
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
val LocalEchoNowBarTopCloudClearanceChanged = staticCompositionLocalOf<(androidx.compose.ui.unit.Dp) -> Unit> { {} }


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
    val onTopCloudClearanceChanged = LocalEchoNowBarTopCloudClearanceChanged.current
    SideEffect {
        onTopCloudClearanceChanged(
            when {
                topCloudExpanded -> EchoMobileLayoutMetrics.nowBarTopCloudExpandedContentClearance
                topCloud -> EchoMobileLayoutMetrics.nowBarTopCloudContentClearance
                topCloudCollapsed -> EchoMobileLayoutMetrics.nowBarTopCloudCollapsedContentClearance
                else -> 0.dp
            }
        )
    }
    var acknowledgedPageScrollEvent by remember { mutableIntStateOf(pageScrollEvent) }
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
    val restoreNowBar = {
        dockName = NowBarDockPosition.Expanded.name
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
        positionMs = state.progress.positionMs,
        durationMs = state.progress.durationMs,
        playing = state.progress.playing,
        elapsed = state.progress.elapsed,
        duration = state.progress.duration,
        trackId = state.track.trackId,
        contentUriString = state.track.contentUri?.toString(),
        dataPath = state.track.dataPath,
        waveformBars = state.progress.waveform.samples.valuesForRendering(),
        waveformGeneratedBars = state.progress.waveform.generatedBars,
        waveformCachedProgress = state.progress.waveform.cachedProgress,
        playbackProgressLabel = state.labels.playbackProgress,
        expandWaveformLabel = state.labels.expandWaveform
    )
    val playbackScrub = rememberScrubbablePlaybackPosition(
        positionMs = progressSlice.positionMs,
        durationMs = progressSlice.durationMs,
        playing = progressSlice.playing
    )
    val trackSlice = NowBarTrackSlice(
        artUriString = state.artwork.albumArtUri?.toString(),
        title = state.track.title,
        subtitle = state.track.subtitle,
        canExpand = state.track.canExpand
    )
    val modeSlice = NowBarModeSlice(
        favoriteEnabled = state.modes.favoriteEnabled,
        favorite = state.modes.favorite,
        favoriteLabel = state.labels.favorite,
        favoritedLabel = state.labels.favorited,
        shuffleEnabled = state.modes.shuffleEnabled,
        shuffleLabel = state.labels.shuffle,
        inOrderLabel = state.labels.inOrder,
        repeatOneLabel = state.labels.repeatOne,
        repeatAllLabel = state.labels.repeatAll,
        repeatOffLabel = state.labels.repeatOff,
        queueLabel = state.labels.queue,
        repeatMode = state.modes.repeatMode
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
                                state.labels.showTopCloud.ifBlank { "显示流体云" }
                            ) {
                                showTopCloud()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.restoreBottom.ifBlank { "恢复到底部" }
                            ) {
                                restoreBottom()
                                true
                            }
                        )
                    } else if (topCloudVisible) {
                        listOf(
                            CustomAccessibilityAction(
                                if (topCloudExpanded) {
                                    state.labels.compactTopCloud.ifBlank { "收起流体云内容" }
                                } else {
                                    state.labels.expandTopCloud.ifBlank { "展开流体云内容" }
                                }
                            ) {
                                toggleTopCloudExpansion()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.expandNowBar.ifBlank { "展开 Now Bar" }
                            ) {
                                dockName = NowBarDockPosition.Expanded.name
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.restoreBottom.ifBlank { "恢复到底部" }
                            ) {
                                restoreBottom()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.collapseTopCloud.ifBlank { "折叠流体云" }
                            ) {
                                collapseTopCloud()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.dockLeft.ifBlank { "停靠左侧" }
                            ) {
                                dockBottomLeft()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.dockRight.ifBlank { "停靠右侧" }
                            ) {
                                dockBottomRight()
                                true
                            }
                        )
                    } else if (docked) {
                        listOf(
                            CustomAccessibilityAction(
                                state.labels.expandNowBar.ifBlank { "展开 Now Bar" }
                            ) {
                                dockName = NowBarDockPosition.Expanded.name
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.dockTop.ifBlank { "停靠顶部" }
                            ) {
                                dockTop()
                                true
                            },
                            CustomAccessibilityAction(
                                if (dockPosition == NowBarDockPosition.BottomRight) {
                                    state.labels.dockLeft.ifBlank { "停靠左侧" }
                                } else {
                                    state.labels.dockRight.ifBlank { "停靠右侧" }
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
                                state.labels.dockLeft.ifBlank { "停靠左侧" }
                            ) {
                                onCollapseWaveform()
                                dockBottomLeft()
                                true
                            },
                            CustomAccessibilityAction(
                                state.labels.dockRight.ifBlank { "停靠右侧" }
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
                                    playing = state.progress.playing,
                                    previousLabel = state.labels.previous,
                                    playLabel = state.labels.play,
                                    pauseLabel = state.labels.pause,
                                    nextLabel = state.labels.next
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
                            onRestoreBottom = restoreNowBar,
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
