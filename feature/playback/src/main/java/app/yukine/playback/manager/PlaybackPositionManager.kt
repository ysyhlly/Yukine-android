package app.yukine.playback.manager

import app.yukine.model.Track
import java.util.function.LongSupplier
import java.util.function.Supplier

internal class PlaybackPositionManager @JvmOverloads constructor(
    private val queueStore: PlaybackQueueStore,
    private val stateProvider: StateProvider,
    @Suppress("UNUSED_PARAMETER") nowMs: LongSupplier = LongSupplier { System.currentTimeMillis() },
    @Suppress("UNUSED_PARAMETER") saveIntervalMs: Long = DEFAULT_SAVE_INTERVAL_MS
) {
    interface StateProvider {
        fun currentTrack(): Track?
        fun positionMs(): Long
    }

    private var restoredPositionTrackId = -1L
    private var restoredPositionMs = 0L
    private var restoredPositionExplicit = false

    fun setRestoredPosition(trackId: Long, positionMs: Long, explicit: Boolean) {
        restoredPositionTrackId = trackId
        restoredPositionMs = positionMs
        restoredPositionExplicit = explicit
    }

    fun setExplicitRestoredPosition(track: Track?, positionMs: Long): Long {
        if (track == null) {
            return 0L
        }
        val clampedPosition = clampPlaybackPosition(track, positionMs)
        setRestoredPosition(track.id, clampedPosition, explicit = true)
        return clampedPosition
    }

    fun restoredPositionFor(track: Track?): Long {
        if (track == null || track.id != restoredPositionTrackId) {
            return 0L
        }
        if (track.dataPath != null && track.dataPath.startsWith("streaming:") && !restoredPositionExplicit) {
            return 0L
        }
        return clampPlaybackPosition(track, restoredPositionMs)
    }

    fun clearRestoredPosition() {
        restoredPositionTrackId = -1L
        restoredPositionMs = 0L
        restoredPositionExplicit = false
    }

    fun clearPlaybackPosition() {
        clearRestoredPosition()
        queueStore.savePlaybackPosition(-1L, 0L)
    }

    fun positionMs(): Long {
        return stateProvider.positionMs()
    }

    @Suppress("UNUSED_PARAMETER")
    fun persistCurrentPosition(force: Boolean) {
        // Periodic progress, seek while playing, and shutdown callbacks must not create a
        // resume checkpoint. A user pause is the only action that persists a position.
    }

    fun persistCurrentPositionForPause() {
        val track = stateProvider.currentTrack() ?: return
        queueStore.savePlaybackPosition(track.id, clampPlaybackPosition(track, stateProvider.positionMs()))
    }

    fun resetCurrentPlaybackPosition() {
        clearPlaybackPosition()
    }

    @Suppress("UNUSED_PARAMETER")
    fun saveTrackPosition(track: Track?, positionMs: Long) {
        // Completion and queue-transition callbacks must not change a pause checkpoint.
        // Explicit user pauses use persistCurrentPositionForPause; in-memory source recovery
        // uses setExplicitRestoredPosition instead.
    }

    fun clampPlaybackPosition(track: Track?, positionMs: Long): Long {
        val position = maxOf(0L, positionMs)
        val duration = track?.durationMs ?: 0L
        if (duration <= 0L) {
            return position
        }
        val latestResumePosition = maxOf(0L, duration - 2000L)
        return minOf(position, latestResumePosition)
    }

    companion object {
        @JvmStatic
        fun stateProviderFromPlaybackState(
            currentTrackSupplier: Supplier<Track?>?,
            playbackPositionSupplier: LongSupplier?
        ): StateProvider = object : StateProvider {
            override fun currentTrack(): Track? = currentTrackSupplier?.get()

            override fun positionMs(): Long = playbackPositionSupplier?.asLong ?: 0L
        }

        private const val DEFAULT_SAVE_INTERVAL_MS = 5000L
    }
}
