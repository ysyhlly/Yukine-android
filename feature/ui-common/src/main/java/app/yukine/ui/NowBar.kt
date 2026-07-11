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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.feature.uicommon.R
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val WAVEFORM_TWO_PI = 6.2831855f

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = EchoMobileLayoutMetrics.floatingChromeHorizontalPadding,
                top = EchoMobileLayoutMetrics.floatingChromeGap,
                end = EchoMobileLayoutMetrics.floatingChromeHorizontalPadding
            )
    ) {
        EchoGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            shape = EchoShapes.large,
            elevation = EchoMobileLayoutMetrics.floatingChromeElevation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    MiniLyricsStrip(state)
                    NowBarProgressSection(
                        slice = progressSlice,
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
                        onSeek = onSeek,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniLyricsStrip(state: NowBarState) {
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
    waveformExpanded: Boolean,
    onExpandWaveform: () -> Unit,
    onCollapseWaveform: () -> Unit,
    onSeek: SeekAction
) {
    val p = EchoTheme.colors()
    if (waveformExpanded) {
        CollapsedProgress(
            positionMs = slice.positionMs,
            durationMs = slice.durationMs,
            playing = slice.playing,
            cachedProgress = slice.waveformCachedProgress,
            progressLabel = slice.playbackProgressLabel,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoMobileLayoutMetrics.nowBarProgressHeight)
        )
    } else {
        CollapsedProgress(
            positionMs = slice.positionMs,
            durationMs = slice.durationMs,
            playing = slice.playing,
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
        Text(slice.elapsed, style = EchoTypography.caption, color = p.muted)
        Text(slice.duration, style = EchoTypography.caption, color = p.muted)
    }
}

@Composable
private fun BottomWaveformProgress(
    slice: NowBarProgressSlice,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .clipToBounds()
    ) {
        WaveformProgress(
            positionMs = slice.positionMs,
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
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    cachedProgress: Float,
    progressLabel: String,
    onSeek: SeekAction,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
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
        modifier = modifier.combinedClickable(
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
        onSeek.seekTo(targetPosition)
        drag(down.id) { change ->
            targetPosition = scrub.scrubTo(change.position.x, size.width.toFloat())
            onSeek.seekTo(targetPosition)
            change.consume()
        }
        scrub.clearScrub()
    }
}

@Composable
private fun WaveformProgress(
    positionMs: Long,
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
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
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
