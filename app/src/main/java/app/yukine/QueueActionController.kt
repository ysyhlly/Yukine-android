package app.yukine

import app.yukine.model.Track

internal class QueueActionController(
    private val viewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun applyPlaybackActionResult(result: PlaybackActionResultUi?)

        fun hasPlaybackService(): Boolean

        fun moveQueueTrack(fromIndex: Int, toIndex: Int)

        fun renderNowBar()

        fun renderSelectedTab()

        fun confirmClearQueue()

        fun queueEmptyStatus(): String

        fun setStatus(status: String)
    }

    fun removeQueueTrack(track: Track?) {
        listener.applyPlaybackActionResult(viewModel.removeQueueTrack(track))
    }

    fun confirmClearQueue() {
        if (!viewModel.hasQueue()) {
            listener.setStatus(listener.queueEmptyStatus())
            return
        }
        listener.confirmClearQueue()
    }

    fun clearQueue() {
        listener.applyPlaybackActionResult(viewModel.clearQueue())
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        if (!listener.hasPlaybackService()) {
            return
        }
        listener.moveQueueTrack(fromIndex, toIndex)
        listener.renderNowBar()
        listener.renderSelectedTab()
    }
}
