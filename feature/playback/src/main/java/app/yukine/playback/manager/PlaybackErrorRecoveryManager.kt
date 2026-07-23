package app.yukine.playback.manager

import androidx.media3.common.PlaybackException
import app.yukine.model.Track
import app.yukine.playback.AudioFallbackReason
import java.util.function.Predicate

internal class PlaybackErrorRecoveryManager(
    private val scheduler: RetryScheduler,
    private val actions: Actions,
    private val streamingTrackPredicate: Predicate<Track?>,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
) {
    interface RetryScheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    interface Actions {
        fun currentTrack(): Track?
        fun canSkipFailedTrack(failed: Track?): Boolean
        fun refreshStreamingTrack(failed: Track?): Boolean = false
        fun debugTrack(track: Track?): String
        fun prepareCurrent(playWhenReady: Boolean)
        fun skipToNext()
        fun setErrorMessage(message: String)
        fun publishState()
        fun logWarning(message: String, error: Exception)
    }

    private var lastErrorTrackId = -1L
    private var retryGeneration = 0
    private var pendingRetry: Runnable? = null
    private var released = false

    fun onPlaybackReady() {
        if (released) {
            return
        }
        cancelPendingRetry()
        lastErrorTrackId = -1L
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        cancelPendingRetry()
    }

    fun onPlayerError(error: Exception) {
        if (released) {
            return
        }
        val fallbackReason = fallbackReasonFor(error)
        val errorMessage = if (fallbackReason == AudioFallbackReason.FORMAT_UNSUPPORTED) {
            "This audio format is not supported."
        } else {
            "Unable to play this track."
        }
        val failed = actions.currentTrack()
        val failedId = failed?.id ?: -1L
        val isStreaming = streamingTrackPredicate.test(failed)
        actions.logWarning("Playback failed for ${actions.debugTrack(failed)}", error)
        if (isStreaming && failedId != -1L && failedId != lastErrorTrackId) {
            lastErrorTrackId = failedId
            if (actions.refreshStreamingTrack(failed)) {
                actions.logWarning("Refreshing expired streaming URL: ${actions.debugTrack(failed)}", error)
                return
            }
            actions.logWarning("Retrying streaming track after error: ${actions.debugTrack(failed)}", error)
            val generation = ++retryGeneration
            val retry = Runnable {
                if (released || generation != retryGeneration) {
                    return@Runnable
                }
                pendingRetry = null
                val current = actions.currentTrack()
                if (current != null && current.id == failedId) {
                    actions.prepareCurrent(true)
                }
            }
            pendingRetry = retry
            scheduler.postDelayed(retry, retryDelayMs)
            return
        }
        cancelPendingRetry()
        if (actions.canSkipFailedTrack(failed)) {
            lastErrorTrackId = -1L
            actions.logWarning("Skipping unplayable track: ${actions.debugTrack(failed)}", error)
            actions.setErrorMessage(
                if (fallbackReason == AudioFallbackReason.FORMAT_UNSUPPORTED) errorMessage else ""
            )
            actions.skipToNext()
            return
        }
        actions.setErrorMessage(errorMessage)
        actions.publishState()
    }

    private fun cancelPendingRetry() {
        retryGeneration++
        pendingRetry?.let { scheduler.removeCallbacks(it) }
        pendingRetry = null
    }

    companion object {
        const val DEFAULT_RETRY_DELAY_MS = 1500L

        @JvmStatic
        fun fallbackReasonFor(error: Exception): AudioFallbackReason {
            val playbackError = error as? PlaybackException ?: return AudioFallbackReason.NONE
            return when (playbackError.errorCode) {
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                    AudioFallbackReason.FORMAT_UNSUPPORTED
                else -> AudioFallbackReason.NONE
            }
        }
    }
}
