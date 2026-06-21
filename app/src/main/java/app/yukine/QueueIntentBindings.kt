package app.yukine

import app.yukine.model.Track

internal fun interface QueueTrackMoveAction {
    fun move(fromIndex: Int, toIndex: Int)
}

internal fun interface QueueNoArgAction {
    fun run()
}

internal class QueueIntentBindings(
    private val libraryEventSink: LibraryEventSink,
    private val addToPlaylistAction: TrackAction,
    private val removeQueueTrackAction: TrackAction,
    private val moveQueueTrackAction: QueueTrackMoveAction,
    private val clearQueueConfirmer: QueueNoArgAction,
    private val backHandler: QueueNoArgAction
) : QueueIntentController.Listener {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        libraryEventSink.send(LibraryEvent.PlayTrackList(tracks, index))
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

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        moveQueueTrackAction.move(fromIndex, toIndex)
    }

    override fun confirmClearQueue() {
        clearQueueConfirmer.run()
    }

    override fun back() {
        backHandler.run()
    }
}
