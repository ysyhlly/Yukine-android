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

@Composable
internal fun DockedNowBarCapsule(
    state: NowBarState,
    dockPosition: NowBarDockPosition,
    expandedTopCloud: Boolean,
    onExpand: () -> Unit,
    onDockLeft: () -> Unit,
    onDockRight: () -> Unit,
    onDockTop: () -> Unit,
    onRestoreBottom: () -> Unit,
    onCompactTopCloud: () -> Unit,
    onPlayPause: Runnable,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val progress = if (state.progress.durationMs > 0L) {
        (state.progress.positionMs.toFloat() / state.progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val capsuleLyrics = state.lyrics.lines.firstOrNull { it.active }?.text
        ?: state.lyrics.lines.firstOrNull()?.text
        ?: state.lyrics.status.takeIf { it.isNotBlank() }
        ?: state.track.title
    val interactionLabel = when (dockPosition) {
        NowBarDockPosition.TopCloud ->
            state.labels.expandTopCloud.ifBlank { "展开流体云内容" }
        NowBarDockPosition.TopCloudExpanded ->
            state.labels.compactTopCloud.ifBlank { "收起流体云内容" }
        else -> state.labels.expandNowBar.ifBlank { "展开 Now Bar" }
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
                onCompactTopCloud = onCompactTopCloud,
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(expandInteraction),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (expandedTopCloud) {
                    val artworkSize = EchoMobileLayoutMetrics.nowBarTopCloudExpandedArtworkSize
                    AsyncArtwork(
                        uri = state.artwork.albumArtUri,
                        title = state.track.title,
                        subtitle = state.track.subtitle,
                        modifier = Modifier
                            .size(artworkSize)
                            .testTag("top-cloud-artwork"),
                        cornerRadius = artworkSize / 2f,
                        fallbackTextSize = 12.sp,
                        targetSize = artworkSize,
                        backgroundColor = p.surfaceVariant,
                        fallbackResId = R.drawable.ic_stat_echo
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (expandedTopCloud) 0.dp else 4.dp),
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
                            text = listOf(state.track.title, state.track.subtitle)
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
            val playbackIcon = if (state.progress.playing) EchoIconKind.Pause else EchoIconKind.Play
            if (interactive) {
                if (dockPosition == NowBarDockPosition.TopCloud ||
                    dockPosition == NowBarDockPosition.TopCloudExpanded
                ) {
                    TopCloudPlaybackButton(
                        icon = playbackIcon,
                        desc = if (state.progress.playing) state.labels.pause else state.labels.play,
                        expanded = expandedTopCloud,
                        onClick = onPlayPause::run
                    )
                } else {
                    IconButton(
                        icon = playbackIcon,
                        desc = if (state.progress.playing) state.labels.pause else state.labels.play,
                        accent = true
                    ) {
                        onPlayPause.run()
                    }
                }
            } else {
                DockedPlaybackIndicator(playbackIcon)
            }
        }
    }
}

@Composable
private fun TopCloudPlaybackButton(
    icon: EchoIconKind,
    desc: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val controlSize = if (expanded) {
        EchoMobileLayoutMetrics.nowBarTopCloudExpandedControlSize
    } else {
        EchoMobileLayoutMetrics.nowBarTopCloudControlSize
    }
    val iconSize = if (expanded) {
        EchoMobileLayoutMetrics.nowBarTopCloudExpandedControlIconSize
    } else {
        EchoMobileLayoutMetrics.nowBarTopCloudControlIconSize
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(controlSize)
            .echoPressScale(interaction)
            .semantics { contentDescription = desc },
        shape = CircleShape,
        color = p.accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = icon,
                animationSpec = tween(EchoMotion.FAST_CROSSFADE_MS),
                label = "topCloudPlaybackIcon"
            ) { current ->
                EchoIcon(current, Modifier.size(iconSize), p.onAccent)
            }
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
internal fun MiniLyricsStrip(state: NowBarState, compactProgress: Float) {
    val activeLine = state.lyrics.lines.firstOrNull { it.active }?.text
        ?: state.lyrics.lines.firstOrNull()?.text
        ?: state.lyrics.status
    if (activeLine.isBlank() || !state.track.canExpand) {
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
            .semantics { contentDescription = state.lyrics.title.ifBlank { "歌词" } },
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
internal fun NowBarProgressSection(
    slice: NowBarProgressSlice,
    scrub: ScrubbablePlaybackPosition,
    waveformExpanded: Boolean,
    onExpandWaveform: () -> Unit,
    onCollapseWaveform: () -> Unit,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    if (waveformExpanded) {
        ExpandedWaveformProgress(
            slice = slice,
            scrub = scrub,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
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
internal fun ExpandedWaveformProgress(
    slice: NowBarProgressSlice,
    scrub: ScrubbablePlaybackPosition,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .height(EchoMobileLayoutMetrics.nowBarProgressHeight)
            .clipToBounds()
            .testTag("waveform-progress")
    )
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
internal fun NowBarTrackSection(
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
internal fun NowBarTransportControls(
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
internal fun NowBarModeControls(
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
