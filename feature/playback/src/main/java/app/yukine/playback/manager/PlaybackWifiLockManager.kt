package app.yukine.playback.manager

import app.yukine.model.Track
import java.util.function.Predicate
import java.util.function.Supplier

internal class PlaybackWifiLockManager(
    private val lock: Lock?,
    private val playbackQueueManager: PlaybackQueueManager,
    private val streamingTrackPredicate: Predicate<Track?>
) {
    interface Lock {
        fun isHeld(): Boolean
        fun acquire()
        fun release()
    }

    fun acquireIfStreaming() {
        val track = currentTrack()
        if (streamingTrackPredicate.test(track) && lock != null && !lock.isHeld()) {
            lock.acquire()
        }
    }

    fun release() {
        if (lock != null && lock.isHeld()) {
            lock.release()
        }
    }

    private fun currentTrack(): Track? {
        return queueStateSnapshot().currentTrack
    }

    private fun queueStateSnapshot(): PlaybackQueueManager.QueueStateSnapshot {
        return playbackQueueManager.queueStateSnapshot()
    }

    companion object {
        @JvmStatic
        fun acquireIfStreamingAction(
            managerProvider: Supplier<PlaybackWifiLockManager?>?
        ): Runnable = Runnable {
            managerProvider?.get()?.acquireIfStreaming()
        }

        @JvmStatic
        fun releaseAction(
            managerProvider: Supplier<PlaybackWifiLockManager?>?
        ): Runnable = Runnable {
            managerProvider?.get()?.release()
        }
    }
}
