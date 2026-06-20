package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal class PlaybackActionResultController(
    private val listener: Listener
) {
    interface Listener {
        fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot)

        fun setStatus(status: String)

        fun publishPlaybackState()

        fun renderNowBar()

        fun renderSelectedTab()

        fun navigateNow()
    }

    fun apply(result: PlaybackActionResultUi?) {
        if (result == null) {
            return
        }
        result.snapshot?.let(listener::replacePlaybackSnapshot)
        val status = result.status
        if (status != null && status.trim().isNotEmpty()) {
            listener.setStatus(status)
        }
        if (result.publishPlaybackState) {
            listener.publishPlaybackState()
        }
        if (result.renderNowBar) {
            listener.renderNowBar()
        }
        if (result.renderSelectedTab) {
            listener.renderSelectedTab()
        }
        if (result.navigateNow) {
            listener.navigateNow()
        }
    }
}
