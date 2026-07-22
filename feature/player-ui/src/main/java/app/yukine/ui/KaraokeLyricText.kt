package app.yukine.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun KaraokeLyricText(
    line: LyricUiLine,
    positionMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val words = remember(line.words) { line.words.map(LyricUiWord::asKaraokeWordTiming) }
    val frame = remember(line.text, words, positionMs) {
        karaokeHighlightFrame(line.text, words, positionMs)
    }
    if (!line.active || frame.ranges.isEmpty()) {
        Text(
            text = line.text,
            modifier = modifier,
            style = style,
            color = activeColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    var layoutResult by remember(line.text, style, maxLines) { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier = modifier) {
        Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            color = inactiveColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layoutResult = it }
        )
        Text(
            text = line.text,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val clip = layoutResult?.let { activeClipPath(frame, it) }
                    onDrawWithContent {
                        if (clip != null && !clip.isEmpty) {
                            clipPath(clip) { this@onDrawWithContent.drawContent() }
                        }
                    }
                },
            style = style,
            color = activeColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
internal fun rememberSmoothLyricPosition(
    trackId: Long,
    playbackPositionMs: Long,
    playing: Boolean,
    offsetMs: Long
): Long {
    var smoothPositionMs by remember(trackId) { mutableStateOf(playbackPositionMs) }
    LaunchedEffect(trackId, playbackPositionMs, playing) {
        smoothPositionMs = playbackPositionMs
        if (playing) {
            val anchorRealtime = SystemClock.elapsedRealtime()
            while (true) {
                delay(33L)
                smoothPositionMs = playbackPositionMs +
                    (SystemClock.elapsedRealtime() - anchorRealtime)
            }
        }
    }
    return adjustedLyricPositionMs(smoothPositionMs, offsetMs)
}

private fun activeClipPath(frame: KaraokeHighlightFrame, layout: TextLayoutResult): Path {
    val result = Path()
    frame.ranges.forEach { range ->
        val safeStart = range.start.coerceIn(0, layout.layoutInput.text.length)
        val safeEnd = range.end.coerceIn(safeStart, layout.layoutInput.text.length)
        if (safeEnd <= safeStart) return@forEach
        when (range.phase) {
            KaraokeHighlightPhase.COMPLETED -> result.addPath(layout.getPathForRange(safeStart, safeEnd))
            KaraokeHighlightPhase.CURRENT -> addPartialRange(
                destination = result,
                layout = layout,
                start = safeStart,
                end = safeEnd,
                progress = range.progress
            )
            KaraokeHighlightPhase.UPCOMING -> Unit
        }
    }
    return result
}

private fun addPartialRange(
    destination: Path,
    layout: TextLayoutResult,
    start: Int,
    end: Int,
    progress: Float
) {
    if (progress <= 0f) return
    if (progress >= 1f) {
        destination.addPath(layout.getPathForRange(start, end))
        return
    }
    val segments = buildList {
        val firstLine = layout.getLineForOffset(start)
        val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
        for (lineIndex in firstLine..lastLine) {
            val segmentStart = max(start, layout.getLineStart(lineIndex))
            val segmentEnd = min(end, layout.getLineEnd(lineIndex, visibleEnd = true))
            if (segmentEnd <= segmentStart) continue
            val startX = layout.getHorizontalPosition(segmentStart, usePrimaryDirection = true)
            val endX = layout.getHorizontalPosition(segmentEnd, usePrimaryDirection = true)
            val left = min(startX, endX)
            val right = max(startX, endX)
            val direction = layout.getBidiRunDirection(segmentStart)
            add(
                VisualSegment(
                    left = left,
                    right = right,
                    top = layout.getLineTop(lineIndex),
                    bottom = layout.getLineBottom(lineIndex),
                    rightToLeft = direction == ResolvedTextDirection.Rtl
                )
            )
        }
    }
    val totalWidth = segments.sumOf { abs(it.right - it.left).toDouble() }.toFloat()
    var remaining = totalWidth * progress.coerceIn(0f, 1f)
    segments.forEach { segment ->
        if (remaining <= 0f) return@forEach
        val width = abs(segment.right - segment.left)
        val filled = min(width, remaining)
        val rect = if (segment.rightToLeft) {
            Rect(segment.right - filled, segment.top, segment.right, segment.bottom)
        } else {
            Rect(segment.left, segment.top, segment.left + filled, segment.bottom)
        }
        destination.addRect(rect)
        remaining -= filled
    }
}

private data class VisualSegment(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val rightToLeft: Boolean
)
