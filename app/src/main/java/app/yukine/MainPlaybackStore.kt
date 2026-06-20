package app.yukine

import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot

internal class MainPlaybackStore(
    private val viewModel: PlaybackViewModel
) {
    fun snapshot(): PlaybackStateSnapshot {
        return viewModel.playback.value.snapshot
    }

    fun replaceSnapshot(snapshot: PlaybackStateSnapshot?): PlaybackStateSnapshot {
        return viewModel.replacePlaybackSnapshot(snapshot)
    }

    fun reset() {
        viewModel.resetPlayback()
    }

    fun lastHistoryRefreshTrackId(): Long {
        return viewModel.lastHistoryRefreshTrackId()
    }

    fun setLastHistoryRefreshTrackId(trackId: Long) {
        viewModel.setLastHistoryRefreshTrackId(trackId)
    }

    fun publish(playbackService: EchoPlaybackService?) {
        val queue: List<Track> = playbackService?.queueSnapshot().orEmpty()
        viewModel.updatePlayback(snapshot(), queue)
    }
}
