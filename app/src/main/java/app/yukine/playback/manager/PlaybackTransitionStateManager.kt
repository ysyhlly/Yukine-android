package app.yukine.playback.manager

import app.yukine.model.Track

internal class PlaybackTransitionStateManager {
    private var lastMarkedTrack: Track? = null
    private var fadeOutAdvancing = false

    fun lastMarkedTrack(): Track? {
        return lastMarkedTrack
    }

    fun setLastMarkedTrack(track: Track?) {
        lastMarkedTrack = track
    }

    fun fadeOutAdvancing(): Boolean {
        return fadeOutAdvancing
    }

    fun setFadeOutAdvancing(enabled: Boolean) {
        fadeOutAdvancing = enabled
    }

    fun clear() {
        lastMarkedTrack = null
        fadeOutAdvancing = false
    }
}
