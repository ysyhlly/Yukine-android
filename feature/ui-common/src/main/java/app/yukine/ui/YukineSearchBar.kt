package app.yukine.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yukine.TrackDownloadItem
import app.yukine.TrackDownloadStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun YukineSearchBar(
    label: String = "搜索本地和网络音乐",
    actionLabel: String = "搜索",
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(10.dp))
            Text(actionLabel, style = EchoTypography.label, color = p.accent)
            Spacer(Modifier.width(10.dp))
            YukineDownloadOrb(
                item = activeDownload,
                playbackQuality = playbackQuality,
                audioMotion = audioMotion,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
fun YukineDownloadOrb(
    item: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val smoothPosition = rememberSmoothPosition(
        positionMs = audioMotion.positionMs,
        durationMs = audioMotion.durationMs,
        playing = audioMotion.playing
    )
    val waveformPhase = if (audioMotion.visualMotionEnabled && (audioMotion.playing || item?.status != null)) {
        ((smoothPosition.value % YukineOrbPhasePeriodMs).toFloat() / YukineOrbPhasePeriodMs.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val fallbackPulse = if (audioMotion.visualMotionEnabled) {
        (0.18f + ((sin((waveformPhase * 2f * PI).toFloat()) + 1f) * 0.5f) * 0.20f)
            .coerceIn(0.12f, 0.44f)
    } else {
        0.18f
    }
    val progress = when (item?.status) {
        null, TrackDownloadStatus.Finished -> 1f
        else -> item.progressPercent.coerceIn(0, 100) / 100f
    }
    val outerQuality = item?.quality?.takeIf { it.isNotBlank() } ?: playbackQuality
    val outerColors = yukineQualityGradient(outerQuality, base = p.accent)
    val centerColor = yukineQualityColor(playbackQuality, base = p.accent, fallback = p.accent)
    val motion = audioMotion.copy(positionMs = smoothPosition.value)
    val spectrumState = motion.toOrbSpectrum(waveformPhase)
    val waveformState = motion.toWaveformOrbSpectrum(waveformPhase)
    val fallbackBreath = fallbackPulse * if (audioMotion.playing || item?.status != null) 1f else 0.34f
    val targetSpectrum = if (spectrumState.hasSignal) {
        spectrumState
    } else if (waveformState.hasSignal) {
        waveformState
    } else {
        YukineOrbSpectrumState(fallbackSpectrumBars(fallbackBreath), fallbackBreath, false, audioMotion.visualMotionEnabled)
    }
    val realtimeBeat = audioMotion.realtimeBeat.coerceIn(0f, 1f)
    val realtimeSpectrum = audioMotion.toRealtimeOrbSpectrum()
    val visualSpectrum = targetSpectrum.blendWithRealtime(realtimeSpectrum)
    val renderedSpectrum = rememberYukineOrbRenderedSpectrum(
        visualSpectrum.copy(bass = max(visualSpectrum.bass, realtimeBeat * 0.55f))
    )
    val beat = max(renderedSpectrum.beat, realtimeBeat * 0.72f)
    val bars = renderedSpectrum.bars
    val peaks = renderedSpectrum.peaks
    val idleBreath = if (audioMotion.playing || item?.status != null) {
        0.012f + fallbackPulse * 0.010f
    } else {
        0.006f + fallbackPulse * 0.006f
    }
    val ringScale = 0.952f + idleBreath + beat.coerceIn(0f, 1f) * 0.180f
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = ringScale
                scaleY = ringScale
            }
            .semantics {
                contentDescription = if (item == null) {
                    "播放音质状态"
                } else {
                    "下载进度 ${item.progressPercent.coerceIn(0, 100)}%"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize().padding(4.dp)) {
            val strokeWidth = 2.5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            drawArc(
                color = p.accent.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(outerColors),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Canvas(modifier = Modifier.matchParentSize().padding(7.dp)) {
            val centerRadius = size.minDimension * 0.26f
            val baseRadius = size.minDimension * 0.35f
            val maxBar = size.minDimension * 0.16f
            val strokeWidth = 1.65.dp.toPx()
            bars.forEachIndexed { index, value ->
                val angle = ((index.toFloat() / bars.size) * 2f * PI - PI / 2f).toFloat()
                val animatedValue = visibleOrbSpectrumValue(value)
                val peakValue = visibleOrbSpectrumValue(peaks.getOrElse(index) { value })
                val visualValue = max(animatedValue, peakValue * 0.72f).coerceIn(0f, 0.88f)
                val startRadius = baseRadius
                val endRadius = baseRadius + maxBar * visualValue
                val start = Offset(
                    x = center.x + cos(angle) * startRadius,
                    y = center.y + sin(angle) * startRadius
                )
                val end = Offset(
                    x = center.x + cos(angle) * endRadius,
                    y = center.y + sin(angle) * endRadius
                )
                drawLine(
                    color = centerColor.copy(alpha = 0.30f + visualValue * 0.62f),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                if (peakValue > animatedValue + 0.08f) {
                    val peakRadius = baseRadius + maxBar * peakValue.coerceIn(0f, 0.88f)
                    val peakStart = Offset(
                        x = center.x + cos(angle) * (peakRadius - 1.7.dp.toPx()),
                        y = center.y + sin(angle) * (peakRadius - 1.7.dp.toPx())
                    )
                    val peakEnd = Offset(
                        x = center.x + cos(angle) * peakRadius,
                        y = center.y + sin(angle) * peakRadius
                    )
                    drawLine(
                        color = centerColor.copy(alpha = 0.54f),
                        start = peakStart,
                        end = peakEnd,
                        strokeWidth = (strokeWidth * 0.82f).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                }
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(centerColor.copy(alpha = 0.98f), centerColor.copy(alpha = 0.68f)),
                    center = center,
                    radius = centerRadius
                ),
                radius = centerRadius
            )
        }
    }
}

private data class YukineOrbSpectrumState(
    val bars: List<Float>,
    val bass: Float,
    val hasSignal: Boolean,
    val visualMotionEnabled: Boolean = true
)

private data class YukineOrbRenderedSpectrumState(
    val bars: List<Float>,
    val peaks: List<Float>,
    val baselines: List<Float>,
    val beat: Float
)

private const val YukineOrbSpectrumBarCount = 24
private const val YukineOrbKickBandStart = 1
private const val YukineOrbKickBandEndExclusive = 3
private const val YukineOrbPhasePeriodMs = 4000L

@Composable
private fun rememberYukineOrbRenderedSpectrum(
    target: YukineOrbSpectrumState
): YukineOrbRenderedSpectrumState {
    return YukineOrbRenderedSpectrumState(target.bars, target.bars, target.bars, target.bass)
}

data class YukineOrbAudioMotion(
    val spectrumBands: FloatArray = FloatArray(0),
    val generatedFrames: Int = 0,
    val bandCount: Int = 0,
    val waveformBars: FloatArray = FloatArray(0),
    val waveformGeneratedBars: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val realtimeBeat: Float = 0f,
    val realtimeBands: FloatArray = FloatArray(0),
    val visualMotionEnabled: Boolean = true
) {
    companion object {
        val Empty = YukineOrbAudioMotion()
    }
}

private fun YukineOrbAudioMotion.toRealtimeOrbSpectrum(): YukineOrbSpectrumState {
    if (realtimeBands.isEmpty()) {
        return YukineOrbSpectrumState(List(YukineOrbSpectrumBarCount) { 0f }, realtimeBeat.coerceIn(0f, 1f), false, visualMotionEnabled)
    }
    val values = List(YukineOrbSpectrumBarCount) { index ->
        val source = ((index * realtimeBands.size) / YukineOrbSpectrumBarCount).coerceIn(0, realtimeBands.size - 1)
        enhancedSpectrumValue(realtimeBands[source] * 0.92f)
    }
    return YukineOrbSpectrumState(values, realtimeBeat.coerceIn(0f, 1f), values.any { it > 0.02f }, visualMotionEnabled)
}

private fun YukineOrbSpectrumState.blendWithRealtime(
    realtime: YukineOrbSpectrumState
): YukineOrbSpectrumState {
    if (!realtime.hasSignal) {
        return this
    }
    val values = List(YukineOrbSpectrumBarCount) { index ->
        val base = bars.getOrElse(index) { 0f }
        val live = realtime.bars.getOrElse(index) { 0f }
        blendYukineOrbSpectrumBand(base, live)
    }
    return YukineOrbSpectrumState(
        bars = values,
        bass = blendYukineOrbSpectrumBand(bass, realtime.bass),
        hasSignal = true,
        visualMotionEnabled = visualMotionEnabled && realtime.visualMotionEnabled
    )
}

/**
 * Keep generated spectrum art as a light baseline, but let the audio callback determine the
 * visible height while a track is playing. Taking a maximum here caused static bands to mask
 * quieter live bands, which made the ring look frozen even though fresh samples arrived.
 */
internal fun blendYukineOrbSpectrumBand(base: Float, realtime: Float): Float {
    val baseline = base.coerceIn(0f, 1f) * 0.18f
    val live = realtime.coerceIn(0f, 1f) * 0.96f
    return (baseline + live).coerceIn(0f, 1f)
}

private fun enhancedSpectrumValue(value: Float): Float {
    val bounded = value.coerceIn(0f, 1f)
    if (bounded <= 0f) {
        return 0f
    }
    return bounded.pow(0.82f).coerceIn(0f, 1f)
}

private fun visibleOrbSpectrumValue(value: Float): Float {
    val bounded = value.coerceIn(0f, 1f)
    if (bounded <= 0f) {
        return 0f
    }
    return (0.10f + bounded.pow(0.72f) * 0.76f).coerceIn(0f, 0.86f)
}

private fun YukineOrbAudioMotion.toOrbSpectrum(phase: Float): YukineOrbSpectrumState {
    val generated = generatedFrames.coerceAtLeast(0)
    val bands = bandCount.coerceAtLeast(0)
    val totalFrames = if (bands <= 0) 0 else spectrumBands.size / bands
    if (generated <= 0 || totalFrames <= 0 || bands <= 0 || durationMs <= 0L) {
        return waveformFallbackSpectrum(phase)
    }
    val visualLeadMs = if (playing) 55L else 0L
    val progress = ((positionMs + visualLeadMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val generatedLimit = generated.coerceAtMost(totalFrames)
    val exactFrame = ((totalFrames - 1) * progress).coerceIn(0f, (totalFrames - 1).toFloat())
    if (exactFrame > (generatedLimit - 1).toFloat()) {
        return waveformFallbackSpectrum(phase)
    }
    val frame = exactFrame.toInt().coerceIn(0, generatedLimit - 1)
    val nextFrame = (frame + 1).coerceAtMost(generatedLimit - 1)
    val frameMix = (exactFrame - frame).coerceIn(0f, 1f)
    fun bandAt(frameIndex: Int, band: Int): Float =
        spectrumBands[frameIndex * bands + band].coerceIn(0f, 1f)

    val values = List(YukineOrbSpectrumBarCount) { index ->
        val band = ((index * bands) / YukineOrbSpectrumBarCount).coerceIn(0, bands - 1)
        val raw = lerpFloat(bandAt(frame, band), bandAt(nextFrame, band), frameMix)
        val shimmer = if (playing) {
            0.9f + 0.1f * sin((phase * 2f * PI + index * 0.36f).toFloat())
        } else {
            1f
        }
        (raw * shimmer).coerceIn(0f, 1f)
    }
    val kickStart = YukineOrbKickBandStart.coerceIn(0, bands - 1)
    val kickEnd = YukineOrbKickBandEndExclusive.coerceIn(kickStart + 1, bands)
    var kickOnset = 0f
    for (band in kickStart until kickEnd) {
        val current = lerpFloat(bandAt(frame, band), bandAt(nextFrame, band), frameMix)
        var recentAverage = 0f
        var recentCount = 0
        val lookBackFrames = max(2, (generatedLimit / 850).coerceAtMost(8))
        val lookAheadFrames = max(3, (generatedLimit / 520).coerceAtMost(12))
        for (lookBack in 1..lookBackFrames) {
            recentAverage += bandAt((frame - lookBack).coerceAtLeast(0), band)
            recentCount += 1
        }
        recentAverage /= recentCount.coerceAtLeast(1)
        var forwardPeak = current
        for (lookAhead in 1..lookAheadFrames) {
            forwardPeak = max(forwardPeak, bandAt((frame + lookAhead).coerceAtMost(generatedLimit - 1), band))
        }
        val currentRise = max(0f, current - recentAverage * 1.04f - 0.026f)
        val forwardRise = max(0f, forwardPeak - recentAverage * 1.08f - 0.032f)
        val onset = max(currentRise, forwardRise)
        kickOnset = max(kickOnset, onset)
    }
    val bass = transientBeatCurve((kickOnset * 9.2f).coerceIn(0f, 1f))
    return YukineOrbSpectrumState(values, bass, values.any { it > 0.015f }, visualMotionEnabled)
}

private fun YukineOrbAudioMotion.waveformFallbackSpectrum(phase: Float): YukineOrbSpectrumState {
    return toWaveformOrbSpectrum(phase)
}

private fun YukineOrbAudioMotion.toWaveformOrbSpectrum(phase: Float): YukineOrbSpectrumState {
    val visible = waveformGeneratedBars.coerceIn(0, waveformBars.size)
    if (visible <= 0 || durationMs <= 0L) {
        return YukineOrbSpectrumState(List(YukineOrbSpectrumBarCount) { 0f }, 0f, false, visualMotionEnabled)
    }
    val visualLeadMs = if (playing) 55L else 0L
    val progress = ((positionMs + visualLeadMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val center = ((visible - 1) * progress).coerceIn(0f, (visible - 1).toFloat())
    val range = visibleWaveformPeakRange(waveformBars, visible)
    val visibleMinPeak = range.first
    val visibleSpan = range.second
    fun waveformAt(exactIndex: Float): Float {
        val bounded = exactIndex.coerceIn(0f, (visible - 1).toFloat())
        val left = bounded.toInt().coerceIn(0, visible - 1)
        val right = (left + 1).coerceAtMost(visible - 1)
        val raw = lerpFloat(
            waveformBars[left].coerceIn(0f, 1f),
            waveformBars[right].coerceIn(0f, 1f),
            bounded - left
        )
        return ((raw - visibleMinPeak) / visibleSpan).coerceIn(0f, 1f)
    }

    val values = List(YukineOrbSpectrumBarCount) { index ->
        val offset = (index - YukineOrbSpectrumBarCount / 2) * 0.72f
        val basePeak = kotlin.math.sqrt(waveformAt(center + offset)).coerceAtLeast(0.08f)
        liveWaveformPeak(basePeak, index, phase, playing)
    }
    val current = waveformAt(center)
    val previous = (waveformAt(center - 1f) + waveformAt(center - 2f)) * 0.5f
    var forwardPeak = current
    for (lookAhead in 1..5) {
        forwardPeak = max(forwardPeak, waveformAt(center + lookAhead * 0.36f))
    }
    val currentRise = max(0f, current - previous * 1.04f - 0.026f)
    val forwardRise = max(0f, forwardPeak - previous * 1.08f - 0.032f)
    val bass = transientBeatCurve((max(currentRise, forwardRise) * 8.6f).coerceIn(0f, 1f))
    return YukineOrbSpectrumState(values, bass, values.any { it > 0.015f }, visualMotionEnabled)
}

private fun fallbackSpectrumBars(pulse: Float): List<Float> =
    List(YukineOrbSpectrumBarCount) { index ->
        val phase = index.toFloat() / YukineOrbSpectrumBarCount
        val wave = (sin((phase * 2f * PI + pulse * 2f * PI).toFloat()) + 1f) * 0.5f
        (0.1f + wave * 0.24f + pulse * 0.18f).coerceIn(0f, 0.62f)
    }

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

private fun transientBeatCurve(value: Float): Float {
    val clean = value.coerceIn(0f, 1f)
    return 1f - (1f - clean) * (1f - clean) * (1f - clean)
}

private fun yukineQualityColor(quality: String, base: Color, fallback: Color): Color = when (quality.normalizedQuality()) {
    "standard" -> lightenThemeColor(base, 0.28f)
    "high" -> lightenThemeColor(base, 0.14f)
    "lossless" -> base
    "hires" -> deepenThemeColor(base, 0.22f)
    else -> fallback
}

private fun yukineQualityGradient(quality: String, base: Color): List<Color> = when (quality.normalizedQuality()) {
    "standard" -> listOf(
        lightenThemeColor(base, 0.36f),
        lightenThemeColor(base, 0.28f),
        lightenThemeColor(base, 0.2f),
        lightenThemeColor(base, 0.36f)
    )
    "high" -> listOf(
        lightenThemeColor(base, 0.22f),
        lightenThemeColor(base, 0.14f),
        lightenThemeColor(base, 0.06f),
        lightenThemeColor(base, 0.22f)
    )
    "lossless" -> listOf(
        lightenThemeColor(base, 0.08f),
        base,
        deepenThemeColor(base, 0.08f),
        lightenThemeColor(base, 0.08f)
    )
    "hires" -> listOf(
        base,
        deepenThemeColor(base, 0.18f),
        deepenThemeColor(base, 0.3f),
        base
    )
    else -> listOf(
        lightenThemeColor(base, 0.1f),
        base,
        deepenThemeColor(base, 0.08f),
        lightenThemeColor(base, 0.1f)
    )
}

private fun String.normalizedQuality(): String =
    trim()
        .lowercase()
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .let {
            when {
                it == "hires" || it == "hireslossless" || it.contains("hires") -> "hires"
                it == "lossless" || it.contains("无损") || it.contains("flac") -> "lossless"
                it == "high" || it.contains("高") || it.contains("320") -> "high"
                it == "standard" || it.contains("标准") || it.contains("128") -> "standard"
                else -> it
            }
        }

private fun lightenThemeColor(color: Color, amount: Float): Color =
    lerp(color, Color.White, amount.coerceIn(0f, 1f))

private fun deepenThemeColor(color: Color, amount: Float): Color =
    lerp(color, Color.Black, amount.coerceIn(0f, 1f))
