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

        fun resolveAndPlayStreamingTrack(tracks: List<Track>?, index: Int): Boolean

        fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi?

        fun applyPlaybackActionResult(result: PlaybackActionResultUi?)
    }

    fun playTrackList(tracks: List<Track>?, index: Int) {
        listener.stopHeartbeatRecommendationMode()
        playTrackListInternal(tracks, index)
    }

    fun playHeartbeatRecommendationTrackList(tracks: List<Track>?, index: Int) {
        playTrackListInternal(tracks, index)
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
        if (listener.resolveAndPlayStreamingTrack(tracks, index)) {
            return
        }
        listener.applyPlaybackActionResult(listener.playTrackList(tracks, index))
    }
}
