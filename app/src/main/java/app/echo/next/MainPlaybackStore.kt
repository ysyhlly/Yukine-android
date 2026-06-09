package app.echo.next

import app.echo.next.model.Track
import app.echo.next.playback.EchoPlaybackService
import app.echo.next.playback.PlaybackStateSnapshot

internal class MainPlaybackStore(
    private val viewModel: MainActivityViewModel
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
