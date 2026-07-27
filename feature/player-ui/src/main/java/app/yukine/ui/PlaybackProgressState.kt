package app.yukine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Produces a locally-advancing playback position so the progress UI moves smoothly between the
 * (roughly once-per-second) authoritative updates pushed by the playback service boundary.
 *
 * The service remains the single source of truth: every time [positionMs], [durationMs] or [playing]
 * changes, the local clock is re-seeded from the authoritative value (this is the calibration that
 * keeps the local estimate from drifting, and that snaps to the real value on seek / track change /
 * pause / buffer). While [playing] is true the value is advanced at a low UI tick rate, which
 * avoids turning playback progress into a continuous main-thread recomposition source and
 * self-cancels when this composable leaves the
 * composition or when the keys change.
 *
 * The returned [State] is intended to be read inside a draw lambda (so it only triggers a redraw,
 * not a recomposition) or behind a `derivedStateOf` for text labels.
 */
@Composable
fun rememberSmoothPosition(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    /**
     * Track visual identity. Changing this reseeds the local clock and drops any
     * carry-over from the previous track (same position/duration must not stick).
     */
    identityKey: Any? = Unit
): State<Long> {
    val duration = durationMs.coerceAtLeast(1L)
    val position = remember(identityKey) {
        mutableStateOf(if (durationMs > 0L) positionMs.coerceIn(0L, duration) else 0L)
    }
    // Tracks which identity last seeded the smooth clock (outside remember(identityKey) so we
    // can still detect the change inside LaunchedEffect even if position state was recreated).
    val seededIdentity = remember { mutableStateOf<Any?>(identityKey) }
    LaunchedEffect(positionMs, durationMs, playing, identityKey) {
        if (durationMs <= 0L) {
            position.value = 0L
            seededIdentity.value = identityKey
            return@LaunchedEffect
        }
        val base = positionMs.coerceIn(0L, duration)
        val current = position.value.coerceIn(0L, duration)
        val identityChanged = seededIdentity.value != identityKey
        seededIdentity.value = identityKey
        // Always snap on identity change / pause / seek-forward / large correction so a new
        // track cannot keep the previous song's smooth clock.
        val startBase = if (
            identityChanged ||
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
                delay(POSITION_UI_TICK_MS)
                val elapsedNanos = withFrameNanos { it } - startNanos
                val elapsedMs = elapsedNanos / 1_000_000L
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
        // Ignore degenerate hit targets (e.g. collapsing progress height/width) so a fold
        // animation cannot pin scrub to 0% or 100% and freeze elapsed after re-expand.
        if (width < 8f) {
            return scrubPosition.value ?: 0L
        }
        val progress = (x / width).coerceIn(0f, 1f)
        val nextPosition = (duration * progress).toLong().coerceIn(0L, duration)
        scrubPosition.value = nextPosition
        return nextPosition
    }

    fun clearScrub() {
        scrubPosition.value = null
    }

    val isScrubbing: Boolean
        get() = scrubPosition.value != null
}

@Composable
fun rememberScrubbablePlaybackPosition(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    trackId: Long = -1L,
    contentUriString: String? = null,
    dataPath: String = ""
): ScrubbablePlaybackPosition {
    val identityKey = remember(trackId, contentUriString, dataPath) {
        Triple(trackId, contentUriString, dataPath)
    }
    val duration = durationMs.coerceAtLeast(1L)
    val seekEnabled = durationMs > 0L
    val smoothPosition = rememberSmoothPosition(
        positionMs = positionMs,
        durationMs = durationMs,
        playing = playing,
        identityKey = identityKey
    )
    // New scrub overlay state per track so a drag on the previous track cannot stick.
    val scrubPosition = remember(identityKey) { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekEnabled, identityKey) {
        if (!seekEnabled) {
            scrubPosition.value = null
        }
    }
    val displayPosition = remember(duration, seekEnabled, identityKey) {
        derivedStateOf {
            if (!seekEnabled) {
                0L
            } else {
                (scrubPosition.value ?: smoothPosition.value).coerceIn(0L, duration)
            }
        }
    }
    return remember(displayPosition, duration, seekEnabled, scrubPosition, identityKey) {
        ScrubbablePlaybackPosition(displayPosition, duration, seekEnabled, scrubPosition)
    }
}

private const val POSITION_SNAP_THRESHOLD_MS = 250L
private const val POSITION_UI_TICK_MS = 250L
