package app.yukine.playback.manager

import android.net.Uri
import app.yukine.model.Track

internal class PlaybackErrorRecoveryManager(
    private val scheduler: RetryScheduler,
    private val actions: Actions,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
) {
    interface RetryScheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)
    }

    interface Actions {
        fun currentTrack(): Track?
        fun isHttpUri(uri: Uri?): Boolean
        fun queueSize(): Int
        fun debugTrack(track: Track?): String
        fun prepareCurrent(playWhenReady: Boolean)
        fun skipToNext()
        fun setErrorMessage(message: String)
        fun publishState()
        fun logWarning(message: String, error: Exception)
    }

    private var lastErrorTrackId = -1L

    fun onPlaybackReady() {
        lastErrorTrackId = -1L
    }

    fun onPlayerError(error: Exception) {
        val failed = actions.currentTrack()
        val failedId = failed?.id ?: -1L
        val isStreaming = failed != null && actions.isHttpUri(failed.contentUri)
        actions.logWarning("Playback failed for ${actions.debugTrack(failed)}", error)
        if (isStreaming && failedId != -1L && failedId != lastErrorTrackId) {
            lastErrorTrackId = failedId
            actions.logWarning("Retrying streaming track after error: ${actions.debugTrack(failed)}", error)
            scheduler.postDelayed(
                Runnable {
                    val current = actions.currentTrack()
                    if (current != null && current.id == failedId) {
                        actions.prepareCurrent(true)
                    }
                },
                retryDelayMs
            )
            return
        }
        if (failedId != -1L && actions.queueSize() > 1) {
            lastErrorTrackId = -1L
            actions.logWarning("Skipping unplayable track: ${actions.debugTrack(failed)}", error)
            actions.setErrorMessage("")
            actions.skipToNext()
            return
        }
        actions.setErrorMessage("Unable to play this track.")
        actions.publishState()
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MS = 1500L
    }
}
