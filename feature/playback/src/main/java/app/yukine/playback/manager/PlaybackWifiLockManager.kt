package app.yukine.playback.manager

import android.net.Uri
import app.yukine.model.Track

internal class PlaybackWifiLockManager(
    private val lock: Lock?,
    private val streamingTrackProvider: StreamingTrackProvider
) {
    interface Lock {
        fun isHeld(): Boolean
        fun acquire()
        fun release()
    }

    interface StreamingTrackProvider {
        fun currentTrack(): Track?
        fun isHttpUri(uri: Uri?): Boolean
    }

    fun acquireIfStreaming() {
        val track = streamingTrackProvider.currentTrack()
        if (track != null && streamingTrackProvider.isHttpUri(track.contentUri) && lock != null && !lock.isHeld()) {
            lock.acquire()
        }
    }

    fun release() {
        if (lock != null && lock.isHeld()) {
            lock.release()
        }
    }
}
