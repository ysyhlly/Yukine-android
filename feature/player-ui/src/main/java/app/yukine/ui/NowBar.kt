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
    /** Sticky compact-height lock: list scroll or bar swipe-down; cleared only by tap/swipe-up expand. */
    var heightCompactLocked by rememberSaveable { mutableStateOf(false) }
    var scrollCompactSuppressedUntilReset by rememberSaveable { mutableStateOf(false) }
    val dockPosition = NowBarDockPosition.entries.firstOrNull { it.name == dockName }
        ?: NowBarDockPosition.Expanded
    val docked = dockPosition != NowBarDockPosition.Expanded
    val topCloud = dockPosition == NowBarDockPosition.TopCloud
    val topCloudExpanded = dockPosition == NowBarDockPosition.TopCloudExpanded
    val topCloudVisible = topCloud || topCloudExpanded
    val topCloudPosition = topCloudVisible
    val scrollDrivenCompact =
        if (docked) 0f else LocalEchoNowBarCompactProgress.current.coerceIn(0f, 1f)
    LaunchedEffect(scrollDrivenCompact, docked) {
        when {
            docked -> scrollCompactSuppressedUntilReset = false
            scrollDrivenCompact <= 0.01f -> scrollCompactSuppressedUntilReset = false
            !scrollCompactSuppressedUntilReset &&
                scrollDrivenCompact >= EchoMobileLayoutMetrics.nowBarHeightCompactLockThreshold -> {
                heightCompactLocked = true
            }
        }
    }
    LaunchedEffect(docked) {
        if (docked) {
            heightCompactLocked = false
        }
    }
    val heightCompact = !docked && heightCompactLocked
    val compactProgress = when {
        docked -> 0f
        heightCompactLocked -> 1f
        scrollCompactSuppressedUntilReset -> 0f
        else -> scrollDrivenCompact
    }
    val scrollProgress = if (topCloudPosition) 0f else LocalEchoNowBarScrollProgress.current.coerceIn(-1f, 1f)
    val scrollCompactProgress = scrollProgress.coerceIn(0f, 1f)
    val scrollStretchProgress = (-scrollProgress).coerceIn(0f, 1f)
    val bottomInset = LocalEchoNowBarBottomInset.current
    val pageScrollEvent = LocalEchoNowBarPageScrollEvent.current
    val onTopCloudClearanceChanged = LocalEchoNowBarTopCloudClearanceChanged.current
    val onOccupiedHeightChanged = LocalEchoNowBarOccupiedHeightChanged.current
    // Compact lock must not clear waveform immediately — collapsing waveform mid-morph
    // used to flip the full-height base across the old 0.5 threshold and jump layout.
    val lockHeightCompact = {
        if (!docked) {
            scrollCompactSuppressedUntilReset = false
            heightCompactLocked = true
        }
    }
    val unlockHeightCompact = {
        heightCompactLocked = false
        scrollCompactSuppressedUntilReset = scrollDrivenCompact > 0.01f
    }
    SideEffect {
        onTopCloudClearanceChanged(
            when {
                topCloudExpanded -> EchoMobileLayoutMetrics.nowBarTopCloudExpandedContentClearance
                topCloud -> EchoMobileLayoutMetrics.nowBarTopCloudContentClearance
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
        dockName = NowBarDockPosition.TopCloudExpanded.name
    }
    val compactTopCloud = {
        if (topCloudExpanded) {
            dockName = NowBarDockPosition.TopCloud.name
        }
    }
    val toggleTopCloudExpansion = {
        dockName = if (topCloudExpanded) {
            NowBarDockPosition.TopCloud.name
        } else {
            NowBarDockPosition.TopCloudExpanded.name
        }
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
                pageScrollEvent < 0 && topCloudExpanded -> compactTopCloud()
                pageScrollEvent > 0 && topCloud -> toggleTopCloudExpansion()
            }
        }
    }
    val dockMorphProgress by animateFloatAsState(
        targetValue = if (docked) 1f else 0f,
        animationSpec = EchoMotion.floatSpring(),
        label = "nowBarDockMorph"
    )
    val heightCompactMorph by animateFloatAsState(
        targetValue = compactProgress,
        animationSpec = EchoMotion.floatSpring(),
        label = "nowBarHeightCompactMorph"
    )
    // Collapse waveform only after compact morph settles; reverse expand cancels this.
    LaunchedEffect(heightCompactLocked, heightCompactMorph, waveformExpanded) {
        if (
            heightCompactLocked &&
            heightCompactMorph >= 0.99f &&
            waveformExpanded
        ) {
            onCollapseWaveform()
        }
    }
    // Continuous full-height base: standard ↔ waveform-expanded share one morph (no 0.5 step).
    val waveformOpenMorph by animateFloatAsState(
        targetValue = if (waveformExpanded) 1f else 0f,
        animationSpec = EchoMotion.floatSpring(),
        label = "nowBarWaveformOpenMorph"
    )
    val standardBarHeight = EchoMobileLayoutMetrics.nowBarHeight
    val waveformBarHeight = EchoMobileLayoutMetrics.nowBarExpandedHeight
    val compactBarHeight = EchoMobileLayoutMetrics.nowBarCompactHeight
    val fullOpenBarHeight =
        standardBarHeight + (waveformBarHeight - standardBarHeight) * waveformOpenMorph
    // Expanded surface height: continuous blend full-open → compact with the same morph.
    val expandedBarHeight =
        fullOpenBarHeight + (compactBarHeight - fullOpenBarHeight) * heightCompactMorph
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
        playing = progressSlice.playing,
        trackId = progressSlice.trackId,
        contentUriString = progressSlice.contentUriString,
        dataPath = progressSlice.dataPath
    )
    // Collapsing used to dispose the progress section (open≈0), which could leave a scrub
    // overlay or desync the elapsed Text from the bar after re-expand. Clear scrub while folded.
    LaunchedEffect(heightCompactMorph, heightCompactLocked) {
        if (heightCompactLocked || heightCompactMorph >= 0.5f) {
            playbackScrub.clearScrub()
        }
    }
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
            NowBarDockPosition.TopCloudExpanded -> topCloudBaseWidth
        }
        // Capsule height for docked/top-cloud modes (animated on mode change).
        val capsuleTargetHeight = when (dockPosition) {
            NowBarDockPosition.Expanded -> expandedBarHeight
            NowBarDockPosition.BottomLeft,
            NowBarDockPosition.BottomRight -> EchoMobileLayoutMetrics.nowBarDockedHeight
            NowBarDockPosition.TopCloud,
            NowBarDockPosition.TopCloudExpanded -> topCloudBaseHeight
        }
        val surfaceWidth by animateDpAsState(
            targetValue = targetSurfaceWidth,
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockSizeDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockWidth"
        )
        val capsuleHeight by animateDpAsState(
            targetValue = capsuleTargetHeight,
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockSizeDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockHeight"
        )
        // Blend Expanded morph height with capsule by dockMorph so TopCloud↔Expanded
        // never jumps; when fully Expanded (dockMorph≈0) height tracks compact morph 1:1.
        val surfaceHeight =
            expandedBarHeight * (1f - dockMorphProgress) + capsuleHeight * dockMorphProgress
        SideEffect {
            val occupied = when (dockPosition) {
                NowBarDockPosition.Expanded -> surfaceHeight
                NowBarDockPosition.BottomLeft,
                NowBarDockPosition.BottomRight ->
                    EchoMobileLayoutMetrics.nowBarDockedHeight +
                        EchoMobileLayoutMetrics.nowBarDockedBottomPadding
                NowBarDockPosition.TopCloud,
                NowBarDockPosition.TopCloudExpanded -> 0.dp
            }
            onOccupiedHeightChanged(occupied)
        }
        val dockTravel = ((availableWidth - EchoMobileLayoutMetrics.nowBarDockedWidth)
            .coerceAtLeast(0.dp)) / 2
        val surfaceHorizontalOffset by animateDpAsState(
            targetValue = when (dockPosition) {
                NowBarDockPosition.BottomLeft -> -dockTravel
                NowBarDockPosition.BottomRight -> dockTravel
                NowBarDockPosition.Expanded,
                NowBarDockPosition.TopCloud,
                NowBarDockPosition.TopCloudExpanded -> 0.dp
            },
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockMoveDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarDockHorizontalOffset"
        )
        val topCloudY = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            EchoMobileLayoutMetrics.nowBarTopCloudOffset
        // Animated bottom-edge anchor: Expanded/bottom dock → screen bottom; TopCloud →
        // topCloudY + targetHeight. Top Y is always anchor - height so TopCloud↔Expanded
        // moves continuously and Expanded height changes keep the bottom edge pinned.
        val screenBottomAnchor = (maxHeight - bottomInset -
            if (docked && !topCloudPosition) {
                EchoMobileLayoutMetrics.nowBarDockedBottomPadding
            } else {
                0.dp
            }).coerceAtLeast(0.dp)
        val targetBottomAnchor = if (topCloudPosition) {
            topCloudY + capsuleTargetHeight
        } else {
            screenBottomAnchor
        }
        val animatedBottomAnchor by animateDpAsState(
            targetValue = targetBottomAnchor,
            animationSpec = tween(
                durationMillis = EchoMobileLayoutMetrics.nowBarDockMoveDurationMs,
                easing = FastOutSlowInEasing
            ),
            label = "nowBarBottomAnchor"
        )
        // Never bypass: top Y is always bottom-anchor − height. Expanded height morph keeps
        // the bottom edge fixed (anchor target constant); TopCloud↔Expanded animates the anchor.
        val surfaceVerticalOffset =
            (animatedBottomAnchor - surfaceHeight).coerceAtLeast(0.dp)
        EchoGlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = surfaceHorizontalOffset, y = surfaceVerticalOffset)
                .width(surfaceWidth)
                .height(surfaceHeight)
                .clipToBounds()
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
                    customActions = if (topCloudVisible) {
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
                        buildList {
                            if (heightCompact) {
                                add(
                                    CustomAccessibilityAction(
                                        state.labels.expandNowBar.ifBlank { "展开 Now Bar" }
                                    ) {
                                        unlockHeightCompact()
                                        true
                                    }
                                )
                            } else {
                                add(
                                    CustomAccessibilityAction(
                                        "收起 Now Bar"
                                    ) {
                                        lockHeightCompact()
                                        true
                                    }
                                )
                            }
                            add(
                                CustomAccessibilityAction(
                                    state.labels.dockLeft.ifBlank { "停靠左侧" }
                                ) {
                                    onCollapseWaveform()
                                    dockBottomLeft()
                                    true
                                }
                            )
                            add(
                                CustomAccessibilityAction(
                                    state.labels.dockRight.ifBlank { "停靠右侧" }
                                ) {
                                    onCollapseWaveform()
                                    dockBottomRight()
                                    true
                                }
                            )
                        }
                    }
                },
            shape = if (docked) EchoShapes.pill else EchoShapes.large,
            elevation = EchoMobileLayoutMetrics.floatingChromeElevation *
                (if (topCloudPosition) 0.72f else 1f) *
                (1f - heightCompactMorph * (1f - EchoMobileLayoutMetrics.nowBarCompactShadowFactor))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (!docked || dockMorphProgress < 0.99f) {
                    // One tree only: section heights, alphas, and surface height share the
                    // continuous morph (no dual-layer lag, no discrete 0.5 height flip).
                    val fold = heightCompactMorph
                    val open = (1f - fold).coerceIn(0f, 1f)
                    val verticalPad = 6.dp + 2.dp * fold
                    val hasLyrics = state.lyrics.lines.isNotEmpty() ||
                        state.lyrics.status.isNotBlank()
                    // Keep waveform chrome while compacting until parent state clears after settle.
                    val showWaveformProgress = waveformExpanded && open > 0.001f
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 1f - dockMorphProgress
                                scaleX = 1f - dockMorphProgress * 0.08f
                                scaleY = 1f - dockMorphProgress * 0.08f
                            }
                            .padding(horizontal = 12.dp, vertical = verticalPad),
                        verticalArrangement = Arrangement.spacedBy(2.dp * open)
                    ) {
                        NowBarCollapsingSection(
                            open = open,
                            fullHeight = if (hasLyrics) 20.dp else 0.dp
                        ) {
                            MiniLyricsStrip(state, fold)
                        }
                        NowBarCollapsingSection(
                            open = open,
                            fullHeight = EchoMobileLayoutMetrics.nowBarProgressBlockHeight
                        ) {
                            NowBarProgressSection(
                                slice = progressSlice,
                                scrub = playbackScrub,
                                waveformExpanded = showWaveformProgress,
                                onExpandWaveform = onExpandWaveform,
                                onCollapseWaveform = onCollapseWaveform,
                                onSeek = onSeek
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EchoMobileLayoutMetrics.nowBarArtworkSize),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NowBarTrackSection(
                                slice = trackSlice,
                                heightCompact = heightCompact,
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
                                onCompactHeight = lockHeightCompact,
                                onExpandHeight = unlockHeightCompact,
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
                        NowBarCollapsingSection(
                            open = open,
                            fullHeight = if (modeSlice.favoriteEnabled) {
                                EchoMobileLayoutMetrics.nowBarModeControlsHeight
                            } else {
                                0.dp
                            }
                        ) {
                            NowBarModeControls(
                                modeSlice,
                                onFavorite,
                                onShuffle,
                                onRepeat,
                                onOpenQueue,
                                onCollapseWaveform
                            )
                        }
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
                            onCompactTopCloud = compactTopCloud,
                            onPlayPause = onPlayPause,
                            interactive = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = dockMorphProgress }
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
                            onCompactTopCloud = {},
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
