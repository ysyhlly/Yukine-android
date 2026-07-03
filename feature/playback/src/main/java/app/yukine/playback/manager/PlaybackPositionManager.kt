package app.yukine.playback.manager

import app.yukine.model.Track
import java.util.function.LongSupplier
import java.util.function.Supplier
import kotlin.math.abs

internal class PlaybackPositionManager @JvmOverloads constructor(
    private val queueStore: PlaybackQueueStore,
    private val stateProvider: StateProvider,
    private val nowMs: LongSupplier = LongSupplier { System.currentTimeMillis() },
    private val saveIntervalMs: Long = DEFAULT_SAVE_INTERVAL_MS
) {
    interface StateProvider {
        fun currentTrack(): Track?
        fun positionMs(): Long
    }

    private var restoredPositionTrackId = -1L
    private var restoredPositionMs = 0L
    private var restoredPositionExplicit = false
    private var lastSavedPositionTrackId = -1L
    private var lastSavedPositionMs = 0L
    private var lastPositionSaveAtMs = 0L

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

    fun consumeRestoredPositionAfterPrepare(startPositionMs: Long) {
        if (startPositionMs > 0L) {
            clearRestoredPosition()
        }
    }

    fun clearPlaybackPosition() {
        clearRestoredPosition()
        queueStore.savePlaybackPosition(-1L, 0L)
        lastSavedPositionTrackId = -1L
        lastSavedPositionMs = 0L
        lastPositionSaveAtMs = 0L
    }

    fun positionMs(): Long {
        return stateProvider.positionMs()
    }

    fun persistCurrentPosition(force: Boolean) {
        val track = stateProvider.currentTrack() ?: return
        val position = stateProvider.positionMs()
        val now = nowMs.asLong
        if (!force &&
            track.id == lastSavedPositionTrackId &&
            abs(position - lastSavedPositionMs) < saveIntervalMs &&
            now - lastPositionSaveAtMs < saveIntervalMs
        ) {
            return
        }
        queueStore.savePlaybackPosition(track.id, clampPlaybackPosition(track, position))
        lastSavedPositionTrackId = track.id
        lastSavedPositionMs = position
        lastPositionSaveAtMs = now
    }

    fun resetCurrentPlaybackPosition() {
        val track = stateProvider.currentTrack() ?: return
        queueStore.savePlaybackPosition(track.id, 0L)
        lastSavedPositionTrackId = track.id
        lastSavedPositionMs = 0L
        lastPositionSaveAtMs = nowMs.asLong
    }

    fun saveTrackPosition(track: Track?, positionMs: Long) {
        if (track == null) {
            return
        }
        queueStore.savePlaybackPosition(track.id, clampPlaybackPosition(track, positionMs))
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
            queueManagerSupplier: Supplier<PlaybackQueueManager?>?,
            playbackPositionSupplier: LongSupplier?
        ): StateProvider = object : StateProvider {
            override fun currentTrack(): Track? = queueManagerSupplier?.get()?.queueStateSnapshot()?.currentTrack

            override fun positionMs(): Long = playbackPositionSupplier?.asLong ?: 0L
        }

        private const val DEFAULT_SAVE_INTERVAL_MS = 5000L
    }
}
