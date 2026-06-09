package app.echo.next.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * Produces a locally-advancing playback position so the progress UI moves smoothly between the
 * (roughly once-per-second) authoritative updates pushed by [app.echo.next.playback.EchoPlaybackService].
 *
 * The service remains the single source of truth: every time [positionMs], [durationMs] or [playing]
 * changes, the local clock is re-seeded from the authoritative value (this is the calibration that
 * keeps the local estimate from drifting, and that snaps to the real value on seek / track change /
 * pause / buffer). While [playing] is true the value is advanced once per display frame via
 * [withFrameNanos], which is frame-synced and self-cancels when this composable leaves the
 * composition or when the keys change.
 *
 * The returned [State] is intended to be read inside a draw lambda (so it only triggers a redraw,
 * not a recomposition) or behind a `derivedStateOf` for text labels.
 */
@Composable
fun rememberSmoothPosition(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean
): State<Long> {
    val duration = durationMs.coerceAtLeast(1L)
    val position = remember {
        mutableStateOf(if (durationMs > 0L) positionMs.coerceIn(0L, duration) else 0L)
    }
    LaunchedEffect(positionMs, durationMs, playing) {
        if (durationMs <= 0L) {
            position.value = 0L
            return@LaunchedEffect
        }
        val base = positionMs.coerceIn(0L, duration)
        val current = position.value.coerceIn(0L, duration)
        val startBase = if (
            !playing ||
            base >= current ||
            abs(base - current) > POSITION_SNAP_THRESHOLD_MS
        ) {
            base
        } else {
            current
        }
        position.value = startBase
        if (playing) {
            val startNanos = withFrameNanos { it }
            while (position.value < duration) {
                val frameNanos = withFrameNanos { it }
                val elapsedMs = (frameNanos - startNanos) / 1_000_000L
                val nextPosition = (startBase + elapsedMs).coerceIn(0L, duration)
                if (position.value != nextPosition) {
                    position.value = nextPosition
                }
                if (nextPosition >= duration) {
                    break
                }
            }
        }
    }
    return position
}

class ScrubbablePlaybackPosition internal constructor(
    val displayPosition: State<Long>,
    val duration: Long,
    val seekEnabled: Boolean,
    private val scrubPosition: MutableState<Long?>
) {
    fun scrubTo(x: Float, width: Float): Long {
        if (!seekEnabled) {
            scrubPosition.value = null
            return 0L
        }
        val progress = if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)
        val nextPosition = (duration * progress).toLong().coerceIn(0L, duration)
        scrubPosition.value = nextPosition
        return nextPosition
    }

    fun clearScrub() {
        scrubPosition.value = null
    }
}

@Composable
fun rememberScrubbablePlaybackPosition(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean
): ScrubbablePlaybackPosition {
    val duration = durationMs.coerceAtLeast(1L)
    val seekEnabled = durationMs > 0L
    val smoothPosition = rememberSmoothPosition(positionMs, durationMs, playing)
    val scrubPosition = remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekEnabled) {
        if (!seekEnabled) {
            scrubPosition.value = null
        }
    }
    val displayPosition = remember(duration, seekEnabled) {
        derivedStateOf {
            if (!seekEnabled) {
                0L
            } else {
                (scrubPosition.value ?: smoothPosition.value).coerceIn(0L, duration)
            }
        }
    }
    return remember(displayPosition, duration, seekEnabled, scrubPosition) {
        ScrubbablePlaybackPosition(displayPosition, duration, seekEnabled, scrubPosition)
    }
}

private const val POSITION_SNAP_THRESHOLD_MS = 250L
