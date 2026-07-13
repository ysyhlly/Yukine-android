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

@Composable
internal fun WaveformProgress(
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
