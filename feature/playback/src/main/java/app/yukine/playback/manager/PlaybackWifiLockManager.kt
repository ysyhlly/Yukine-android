package app.yukine.playback.manager

import app.yukine.model.Track

internal class PlaybackWifiLockManager(
    private val lock: Lock?,
    private val streamingTrackProvider: StreamingTrackProvider,
    private val mediaSourceProvider: PlaybackMediaSourceProvider
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
        if (mediaSourceProvider.isHttpTrack(track) && lock != null && !lock.isHeld()) {
            lock.acquire()
        }
    }

    fun release() {
        if (lock != null && lock.isHeld()) {
            lock.release()
        }
    }
}
