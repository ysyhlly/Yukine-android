package app.yukine.playback.manager

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock

/**
 * Native Android audio focus controller that fully manages audio focus lifecycle
 * via [AudioManager] APIs, independent of Media3's internal focus handling.
 *
 * Media3's handleAudioFocus is permanently disabled (false); this controller is the
 * single source of truth for all focus requests and releases.
 *
 * Modes:
 * - [Mode.EXCLUSIVE]: On focus loss, aggressively re-requests focus (up to [MAX_RETRIES]
 *   times with exponential backoff) and sends legacy pause broadcast to stop competitors.
 * - [Mode.COOPERATIVE]: Never requests audio focus — true simultaneous output
 *   with other apps. Focus change callbacks are inactive in this mode.
 *
 * This class does NOT implement Player.Listener — it is driven by explicit method calls
 * from [app.yukine.playback.PlaybackServiceRuntime], decoupling focus logic from player state.
 */
class NativeAudioFocusController(
    private val context: Context,
    private val handler: Handler
) {

    interface Callback {
        /** Focus successfully gained or restored. */
        fun onFocusGained()

        /** Focus permanently lost — cooperative mode yield, or exclusive mode retries exhausted. */
        fun onFocusLostPermanently()

        /** Transient focus loss (e.g. phone call) — should pause but prepare to resume. */
        fun onFocusLostTransiently()

        /** Exclusive mode counteraction succeeded — external should resume playback. */
        fun onPlayRequested()
    }

    enum class Mode { EXCLUSIVE, COOPERATIVE }

    enum class FocusState { IDLE, HELD, LOST, RECLAIMING }

    companion object {
        /** Debounce window to prevent focus request storms when two aggressive apps compete. */
        private const val DEBOUNCE_MS = 80L

        /** Time window during which volume changes are considered app-initiated (not system ducking). */
        private const val VOLUME_PROTECT_WINDOW_MS = 800L

        /** Maximum number of focus reclamation retries against aggressive competitors. */
        private const val MAX_RETRIES = 5

        /** Base delay for exponential backoff between retries (120ms, 240ms, 480ms, 960ms, 1920ms). */
        private const val RETRY_BASE_DELAY_MS = 120L

        /** Minimum interval between legacy pause broadcasts to prevent broadcast storms. */
        private const val BROADCAST_MIN_INTERVAL_MS = 2000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Cached AudioFocusRequest (API 26+) to avoid repeated allocation on each acquire(). */
    private var cachedFocusRequest: AudioFocusRequest? = null

    private var mode: Mode = Mode.EXCLUSIVE
    private var lastCounterActionTime = 0L
    private var lastBroadcastTime = 0L
    private var volumeProtectDeadline = 0L
    private var deferredCounteraction: Runnable? = null

    var callback: Callback? = null

    var focusState: FocusState = FocusState.IDLE
        private set

    var onFocusStateChanged: ((FocusState) -> Unit)? = null

    fun setMode(newMode: Mode) {
        val oldMode = mode
        mode = newMode
        if (newMode == Mode.COOPERATIVE && oldMode == Mode.EXCLUSIVE) {
            // Abandon any system focus held during exclusive mode.
            abandonSystemFocus()
            updateFocusState(FocusState.IDLE)
        }
    }

    fun isExclusiveMode(): Boolean = mode == Mode.EXCLUSIVE

    /**
     * Requests audio focus. Call when playback starts or resumes.
     * In [Mode.COOPERATIVE], this is a no-op that always returns true (true simultaneous output).
     * @return true if focus was granted immediately (or not needed).
     */
    fun acquire(): Boolean {
        // COOPERATIVE mode: true simultaneous output — never request system audio focus.
        if (mode == Mode.COOPERATIVE) {
            updateFocusState(FocusState.HELD)
            return true
        }
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = cachedFocusRequest ?: buildFocusRequest().also { cachedFocusRequest = it }
            val result = am.requestAudioFocus(request)
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    updateFocusState(FocusState.HELD)
                    true
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    updateFocusState(FocusState.RECLAIMING)
                    false // Wait for system to callback AUDIOFOCUS_GAIN
                }
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * Abandons audio focus. Call when playback is paused or stopped by user.
     * In [Mode.COOPERATIVE], this only resets internal state (no system focus was held).
     */
    fun release() {
        // COOPERATIVE mode never acquired system focus — skip handler cleanup and abandon.
        if (mode == Mode.COOPERATIVE) {
            deferredCounteraction = null
            updateFocusState(FocusState.IDLE)
            return
        }
        deferredCounteraction?.let { handler.removeCallbacks(it) }
        deferredCounteraction = null
        abandonSystemFocus()
        updateFocusState(FocusState.IDLE)
    }

    /**
     * Opens a time window during which subsequent volume changes are treated as
     * app-initiated (not system ducking). Covers crossfade multi-step volume ramps.
     */
    fun markUserVolumeChange() {
        volumeProtectDeadline = SystemClock.elapsedRealtime() + VOLUME_PROTECT_WINDOW_MS
    }

    /**
     * Returns true if currently within the volume protection window.
     * External callers can use this to decide whether to resist volume ducking.
     */
    fun isInVolumeProtectWindow(): Boolean {
        return SystemClock.elapsedRealtime() < volumeProtectDeadline
    }

    /**
     * Releases all resources. Call when the playback service is destroyed.
     */
    fun destroy() {
        release()
        callback = null
        onFocusStateChanged = null
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                updateFocusState(FocusState.HELD)
                callback?.onFocusGained()
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mode == Mode.EXCLUSIVE) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastCounterActionTime >= DEBOUNCE_MS) {
                        lastCounterActionTime = now
                        updateFocusState(FocusState.LOST)
                        counteract(0)
                    } else {
                        // Within debounce window: schedule deferred counteraction instead of dropping.
                        scheduleDeferredCounteraction()
                    }
                } else {
                    callback?.onFocusLostPermanently()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mode == Mode.EXCLUSIVE) {
                    // Exclusive mode rejects ducking — do nothing, external may restore volume.
                } else {
                    callback?.onFocusLostTransiently()
                }
            }
        }
    }

    /**
     * Aggressively reclaims audio focus with exponential backoff.
     * Sends legacy pause broadcast to stop competing players, then re-requests focus.
     */
    private fun counteract(attempt: Int) {
        // Guard: skip if user voluntarily paused (state reset to IDLE by release()).
        if (focusState == FocusState.IDLE) return
        updateFocusState(FocusState.RECLAIMING)
        sendLegacyPauseBroadcast()
        handler.postAtFrontOfQueue {
            val granted = acquire()
            if (granted) {
                updateFocusState(FocusState.HELD)
                callback?.onPlayRequested()
            } else if (attempt < MAX_RETRIES) {
                // Exponential backoff: 120ms, 240ms, 480ms, 960ms, 1920ms
                val delay = RETRY_BASE_DELAY_MS * (1L shl attempt)
                handler.postDelayed({
                    if (mode == Mode.EXCLUSIVE) {
                        counteract(attempt + 1)
                    }
                }, delay)
            } else {
                // All retries exhausted — degrade to pause.
                updateFocusState(FocusState.IDLE)
                callback?.onFocusLostPermanently()
            }
        }
    }

    /**
     * Schedules a deferred counteraction after the debounce window expires.
     * Prevents focus loss events from being silently dropped during rapid competition.
     */
    private fun scheduleDeferredCounteraction() {
        deferredCounteraction?.let { handler.removeCallbacks(it) }
        val action = Runnable {
            deferredCounteraction = null
            if (mode == Mode.EXCLUSIVE && focusState != FocusState.IDLE && focusState != FocusState.HELD) {
                lastCounterActionTime = SystemClock.elapsedRealtime()
                counteract(0)
            }
        }
        deferredCounteraction = action
        handler.postDelayed(action, DEBOUNCE_MS)
    }

    private fun abandonSystemFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cachedFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun buildFocusRequest(): AudioFocusRequest {
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusChangeListener, handler)
            .build()
    }

    private fun sendLegacyPauseBroadcast() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBroadcastTime < BROADCAST_MIN_INTERVAL_MS) return
        lastBroadcastTime = now
        val intent = Intent("com.android.music.musicservicecommand").apply {
            putExtra("command", "pause")
            addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        }
        context.sendBroadcast(intent)
    }

    private fun updateFocusState(newState: FocusState) {
        if (focusState != newState) {
            focusState = newState
            onFocusStateChanged?.invoke(newState)
        }
    }
}
