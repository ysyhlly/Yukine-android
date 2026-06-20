package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal class PlaybackActionController(
    private val viewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun resolveCurrentStreamingQueueTrackIfNeeded(): Boolean

        fun playbackSnapshot(): PlaybackStateSnapshot?

        fun fallbackTracks(): List<Track>

        fun applyPlaybackActionResult(result: PlaybackActionResultUi?)
    }

    fun skipToPrevious() {
        viewModel.skipToPrevious()
        listener.resolveCurrentStreamingQueueTrackIfNeeded()
    }

    fun skipToNext() {
        viewModel.skipToNext()
        listener.resolveCurrentStreamingQueueTrackIfNeeded()
    }

    fun togglePlayback() {
        if (listener.resolveCurrentStreamingQueueTrackIfNeeded()) {
            return
        }
        listener.applyPlaybackActionResult(
            viewModel.togglePlayback(listener.playbackSnapshot(), listener.fallbackTracks())
        )
    }

    fun toggleShuffle() {
        listener.applyPlaybackActionResult(viewModel.toggleShuffle(listener.playbackSnapshot()))
    }

    fun cycleBottomPlaybackMode() {
        listener.applyPlaybackActionResult(viewModel.cycleBottomPlaybackMode(listener.playbackSnapshot()))
    }

    fun cycleRepeat() {
        listener.applyPlaybackActionResult(viewModel.cycleRepeat())
    }
}
