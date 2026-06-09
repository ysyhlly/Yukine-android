package app.echo.next.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R
import app.echo.next.model.Track
import app.echo.next.playback.EchoPlaybackService
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

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
    val repeatLabel: String,
    val repeatOffLabel: String,
    val repeatMode: Int = EchoPlaybackService.REPEAT_ALL,
    val albumArtUri: Uri? = null,
    val trackId: Long = -1L,
    val contentUri: Uri? = null,
    val dataPath: String = "",
    val waveformBars: FloatArray = FloatArray(0),
    val waveformGeneratedBars: Int = 0,
    val waveformCachedProgress: Float = 0f
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
    val waveformCachedProgress: Float
)

@Immutable
private data class NowBarTrackSlice(
    val artUriString: String?,
    val title: String,
    val subtitle: String,
    val canExpand: Boolean
)

@Immutable
private data class NowBarTransportSlice(val playing: Boolean)

@Immutable
private data class NowBarModeSlice(
    val favoriteEnabled: Boolean,
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleEnabled: Boolean,
    val shuffleLabel: String,
    val inOrderLabel: String,
    val repeatLabel: String,
    val repeatOffLabel: String,
    val repeatMode: Int
)

class NowBarController(
    context: Context,
    private val onPrevious: Runnable,
    private val onPlayPause: Runnable,
    private val onNext: Runnable,
    private val onFavorite: Runnable,
    private val onShuffle: Runnable,
    private val onRepeat: Runnable,
    private val onOpenNowPlaying: Runnable,
    private val onOpenQueue: Runnable,
    private val onSeek: SeekAction
) {
    private val state: MutableState<NowBarState> = mutableStateOf(emptyState())
    private val waveformExpanded: MutableState<Boolean> = mutableStateOf(false)
    private var waveformTrackKey: String = ""

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                NowBar(
                    state = state.value,
                    waveformExpanded = waveformExpanded.value,
                    onExpandWaveform = { waveformExpanded.value = true },
                    onCollapseWaveform = { waveformExpanded.value = false },
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onFavorite = onFavorite,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onOpenNowPlaying = onOpenNowPlaying,
                    onOpenQueue = onOpenQueue,
                    onSeek = onSeek
                )
            }
        }
    }

    fun updateState(nextState: NowBarState) {
        val nextTrackKey = "${nextState.trackId}|${nextState.contentUri}|${nextState.dataPath}"
        if (nextTrackKey != waveformTrackKey) {
            waveformTrackKey = nextTrackKey
            waveformExpanded.value = false
        }
        state.value = nextState
    }

    fun collapseWaveform() {
        waveformExpanded.value = false
    }

    fun currentHeightDp(): Int {
        return if (waveformExpanded.value) 132 else 104
    }

    companion object {
        @JvmStatic
        fun emptyState() = NowBarState(
            title = "No track selected",
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
            favoriteLabel = "Favorite",
            favoritedLabel = "Favorited",
            shuffleLabel = "Shuffle",
            inOrderLabel = "In order",
            repeatLabel = "Repeat all",
            repeatOffLabel = "Repeat off",
            repeatMode = EchoPlaybackService.REPEAT_ALL
        )
    }
}

fun interface SeekAction {
    fun seekTo(positionMs: Long)
}

@Composable
private fun NowBar(
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
        waveformCachedProgress = state.waveformCachedProgress
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
        repeatLabel = state.repeatLabel,
        repeatOffLabel = state.repeatOffLabel,
        repeatMode = state.repeatMode
    )
    EchoGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight),
        shape = EchoShapes.large
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            val nowBarBlankCollapseInteraction = remember { MutableInteractionSource() }
            if (waveformExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapseWaveform
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clickable(
                        enabled = waveformExpanded,
                        interactionSource = nowBarBlankCollapseInteraction,
                        indication = null,
                        onClick = onCollapseWaveform
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
                    .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NowBarTrackSection(
                        slice = trackSlice,
                        onOpenNowPlaying = onOpenNowPlaying,
                        onCollapseWaveform = onCollapseWaveform,
                        modifier = Modifier.weight(1f)
                    )
                    NowBarTransportControls(
                        slice = NowBarTransportSlice(state.playing),
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onCollapseWaveform = onCollapseWaveform
                    )
                }
                NowBarModeControls(modeSlice, onFavorite, onShuffle, onOpenQueue, onCollapseWaveform)
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
            onExpand = onCollapseWaveform,
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
            onExpand = onExpandWaveform,
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoMobileLayoutMetrics.nowBarProgressHeight)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = waveformExpanded, onClick = onCollapseWaveform)
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
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
    Canvas(
        modifier = modifier
            .clickable(onClick = onExpand)
            .semantics { contentDescription = "Expand playback waveform" }
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
    Row(
        modifier = modifier.clickable(enabled = slice.canExpand) {
            onCollapseWaveform()
            onOpenNowPlaying.run()
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(uri, slice.title, slice.subtitle)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                slice.title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slice.subtitle.isNotBlank()) {
                Text(
                    slice.subtitle,
                    style = EchoTypography.caption,
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
        IconButton(EchoIconKind.Previous, "Previous") {
            onCollapseWaveform()
            onPrevious.run()
        }
        IconButton(
            icon = if (slice.playing) EchoIconKind.Pause else EchoIconKind.Play,
            desc = if (slice.playing) "Pause" else "Play",
            accent = true
        ) {
            onCollapseWaveform()
            onPlayPause.run()
        }
        IconButton(EchoIconKind.Next, "Next") {
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
            icon = if (slice.shuffleEnabled) EchoIconKind.Shuffle else EchoIconKind.Repeat,
            label = when {
                slice.shuffleEnabled -> slice.shuffleLabel
                slice.repeatMode == EchoPlaybackService.REPEAT_ONE -> slice.repeatLabel
                else -> slice.repeatLabel
            },
            active = slice.shuffleEnabled || slice.repeatMode == EchoPlaybackService.REPEAT_ONE,
            modifier = Modifier.weight(1f)
        ) {
            onCollapseWaveform()
            onShuffle.run()
        }
        AuxChip(
            icon = EchoIconKind.Queue,
            label = "\u961f\u5217",
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
    val waveform = if (serviceHasVisibleWaveform) {
        serviceWaveformBars
    } else {
        localWaveform
    }
    val generatedBars = if (serviceHasVisibleWaveform) {
        serviceGeneratedBars
    } else {
        waveform?.size ?: 0
    }.coerceIn(0, barCount)
    val cachedProgress = waveformCachedProgressForDraw(serviceWaveformCachedProgress, serviceGeneratedBars)
    val visiblePeakRange = remember(waveform, generatedBars) {
        visibleWaveformPeakRange(waveform, generatedBars)
    }
    val scrub = rememberScrubbablePlaybackPosition(positionMs, durationMs, playing)
    Box(
        modifier = modifier
            .semantics { contentDescription = "Playback progress" }
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
                    if (waveformBars == null && playedWidth > 0f) {
                        drawRoundRect(
                            color = playedColor.copy(alpha = 0.54f),
                            topLeft = Offset(0f, 0f),
                            size = Size(playedWidth, trackHeight),
                            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                        )
                    }
                    if (waveformBars != null && generatedBars < barCount && playedWidth > 0f) {
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
                    val cachedWidth = width * cachedProgress.coerceAtLeast(progress)
                    for (index in 0 until barCount) {
                        val x = index * (barWidth + gap)
                        val played = x + barWidth / 2f <= playedWidth
                        val cached = x + barWidth / 2f <= cachedWidth
                        if (waveformBars == null) {
                            continue
                        }
                        if (index >= generatedBars || (!played && !cached)) {
                            continue
                        }
                        val normalizedPeak = ((waveformBars.getOrElse(index) { 0f } - visibleMinPeak) / visibleSpan)
                            .coerceIn(0f, 1f)
                        val shapedPeak = sqrt(normalizedPeak).coerceAtLeast(if (played) 0.16f else 0.10f)
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
            .pointerInput(scrub.seekEnabled, scrub.duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!scrub.seekEnabled) {
                        return@awaitEachGesture
                    }
                    var targetPosition = scrub.scrubTo(down.position.x, size.width.toFloat())
                    val completed = drag(down.id) { change ->
                        targetPosition = scrub.scrubTo(change.position.x, size.width.toFloat())
                        change.consume()
                    }
                    if (completed) {
                        onSeek.seekTo(targetPosition)
                    }
                    scrub.clearScrub()
                }
            }
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

internal fun waveformCachedProgressForDraw(serviceCachedProgress: Float, serviceGeneratedBars: Int): Float {
    val clampedServiceProgress = serviceCachedProgress.coerceIn(0f, 1f)
    return if (serviceGeneratedBars > 0 || clampedServiceProgress > 0f) {
        clampedServiceProgress
    } else {
        1f
    }
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
