package app.yukine.playback.manager

import app.yukine.model.Track
import java.util.function.Predicate

internal class PlaybackWifiLockManager(
    private val lock: Lock?,
    private val streamingTrackProvider: StreamingTrackProvider,
    private val streamingTrackPredicate: Predicate<Track?>
) {
    interface Lock {
        fun isHeld(): Boolean
        fun acquire()
        fun release()
    }

    interface StreamingTrackProvider {
        fun currentTrack(): Track?
    }

    fun acquireIfStreaming() {
        val track = streamingTrackProvider.currentTrack()
        if (streamingTrackPredicate.test(track) && lock != null && !lock.isHeld()) {
            lock.acquire()
        }
    }

    fun release() {
        if (lock != null && lock.isHeld()) {
            lock.release()
        }
    }
}
