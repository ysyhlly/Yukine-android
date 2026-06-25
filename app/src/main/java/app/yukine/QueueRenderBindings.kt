package app.yukine

import app.yukine.model.Track
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackActions

internal fun interface TrackListPlaybackAction {
    fun play(tracks: List<Track>, index: Int)
}

internal fun interface QueueTrackAction {
    fun run(track: Track)
}

internal data class QueueChromeState(
    val actions: List<QueueTrackActions>,
    val onClearQueue: Runnable,
    val labels: QueueScreenLabels,
    val onBack: Runnable
)

internal fun interface QueueChromeSink {
    fun publish(state: QueueChromeState)
}

internal class QueueRenderBindings(
    private val playTrackListAction: TrackListPlaybackAction,
    private val libraryEventSink: LibraryEventSink,
    private val addToPlaylistAction: QueueTrackAction,
    private val removeQueueTrackAction: QueueTrackAction,
    private val confirmClearQueueAction: Runnable,
    private val requestBackAction: Runnable,
    private val chromeSink: QueueChromeSink
) : QueueRenderController.Listener {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.play(tracks, index)
    }

    override fun toggleFavorite(track: Track) {
        libraryEventSink.send(LibraryEvent.ToggleFavorite(track))
    }

    override fun showAddToPlaylist(track: Track) {
        addToPlaylistAction.run(track)
    }

    override fun removeQueueTrack(track: Track) {
        removeQueueTrackAction.run(track)
    }

    override fun confirmClearQueue() {
        confirmClearQueueAction.run()
    }

    override fun requestBack() {
        requestBackAction.run()
    }

    override fun publishQueueChrome(
        actions: List<QueueTrackActions>,
        onClearQueue: Runnable,
        labels: QueueScreenLabels,
        onBack: Runnable
    ) {
        chromeSink.publish(
            QueueChromeState(
                actions = ArrayList(actions),
                onClearQueue = onClearQueue,
                labels = labels,
                onBack = onBack
            )
        )
    }
}
