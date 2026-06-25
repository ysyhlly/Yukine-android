package app.yukine

import app.yukine.model.Track

internal class PlaybackStartController(
    private val listener: Listener
) {
    interface Listener {
        fun stopHeartbeatRecommendationMode()

        fun startPlaybackService()

        fun hasPlaybackService(): Boolean

        fun savePendingPlayback(tracks: List<Track>, index: Int)

        fun pendingPlaybackTracks(): List<Track>

        fun pendingPlaybackIndex(): Int

        fun clearPendingPlayback()

        fun resolvingStatus(): String

        fun setStatus(status: String)

        fun playbackController(): PlaybackController

        fun openQueue()
    }

    fun playTrackList(tracks: List<Track>?, index: Int) {
        listener.stopHeartbeatRecommendationMode()
        playTrackListInternal(tracks, index)
    }

    fun playRecommendation(presentation: StreamingRecommendationPresentation) {
        if (presentation.empty) {
            listener.setStatus(presentation.emptyStatus)
            return
        }
        listener.setStatus(presentation.readyStatus)
        playTrackListInternal(presentation.tracks, 0)
        listener.openQueue()
    }

    fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
        if (presentation.empty) {
            listener.setStatus(presentation.emptyStatus)
            return
        }
        listener.setStatus(presentation.readyStatus)
        playTrackListInternal(presentation.tracks, 0)
    }

    fun playPendingTracksIfNeeded() {
        if (!listener.hasPlaybackService()) {
            return
        }
        val pendingTracks = listener.pendingPlaybackTracks()
        if (pendingTracks.isEmpty()) {
            return
        }
        val tracks = ArrayList(pendingTracks)
        val index = listener.pendingPlaybackIndex()
        listener.clearPendingPlayback()
        playTrackListInternal(tracks, index)
    }

    private fun playTrackListInternal(tracks: List<Track>?, index: Int) {
        listener.startPlaybackService()
        if (!listener.hasPlaybackService()) {
            listener.savePendingPlayback(tracks ?: emptyList(), index)
            listener.setStatus(listener.resolvingStatus())
            return
        }
        listener.playbackController().playTrackList(tracks, index)
    }
}
