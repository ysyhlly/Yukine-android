package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.ui.CollectionsActions

internal fun interface PlaylistIdAction {
    fun run(playlistId: Long)
}

internal fun interface PlaylistAction {
    fun run(playlist: Playlist)
}

internal fun interface SelectedPlaylistExportOpener {
    fun open()
}

internal fun interface SelectedPlaylistTrackMover {
    fun move(playlistId: Long, track: Track, trackIndex: Int, direction: Int)
}

internal fun interface SelectedPlaylistTrackRemover {
    fun remove(playlistId: Long, track: Track)
}

internal fun interface CollectionsActionsSink {
    fun publish(actions: CollectionsActions)
}

internal class CollectionsRenderBindings(
    private val showCreatePlaylistAction: Runnable,
    private val openPlaylistM3uFilePickerAction: Runnable,
    private val confirmClearPlayHistoryAction: Runnable,
    private val requestBackAction: Runnable,
    private val playTrackListAction: TrackListPlaybackAction,
    private val libraryEventSink: LibraryEventSink,
    private val addToPlaylistAction: QueueTrackAction,
    private val selectPlaylistAction: PlaylistIdAction,
    private val showRenamePlaylistAction: PlaylistAction,
    private val confirmDeletePlaylistAction: PlaylistAction,
    private val selectedPlaylistExportOpener: SelectedPlaylistExportOpener,
    private val importSelectedPlaylistToStreamingAction: Runnable,
    private val importFavoritesToStreamingAction: Runnable,
    private val importStreamingFavoritesAction: Runnable,
    private val syncSelectedPlaylistFromStreamingAction: Runnable,
    private val selectedPlaylistTrackMover: SelectedPlaylistTrackMover,
    private val selectedPlaylistTrackRemover: SelectedPlaylistTrackRemover,
    private val collectionsActionsSink: CollectionsActionsSink
) : CollectionsRenderController.Listener {
    override fun showCreatePlaylist() {
        showCreatePlaylistAction.run()
    }

    override fun openPlaylistM3uFilePicker() {
        openPlaylistM3uFilePickerAction.run()
    }

    override fun confirmClearPlayHistory() {
        confirmClearPlayHistoryAction.run()
    }

    override fun requestBack() {
        requestBackAction.run()
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.play(tracks, index)
    }

    override fun toggleFavorite(track: Track) {
        libraryEventSink.send(LibraryEvent.ToggleFavorite(track))
    }

    override fun showAddToPlaylist(track: Track) {
        addToPlaylistAction.run(track)
    }

    override fun selectPlaylist(playlistId: Long) {
        selectPlaylistAction.run(playlistId)
    }

    override fun showRenamePlaylist(playlist: Playlist) {
        showRenamePlaylistAction.run(playlist)
    }

    override fun confirmDeletePlaylist(playlist: Playlist) {
        confirmDeletePlaylistAction.run(playlist)
    }

    override fun openSelectedPlaylistExportDocument() {
        selectedPlaylistExportOpener.open()
    }

    override fun importSelectedPlaylistToStreaming() {
        importSelectedPlaylistToStreamingAction.run()
    }

    override fun importFavoritesToStreaming() {
        importFavoritesToStreamingAction.run()
    }

    override fun importStreamingFavorites() {
        importStreamingFavoritesAction.run()
    }

    override fun syncSelectedPlaylistFromStreaming() {
        syncSelectedPlaylistFromStreamingAction.run()
    }

    override fun moveSelectedPlaylistTrack(
        playlistId: Long,
        track: Track,
        trackIndex: Int,
        direction: Int
    ) {
        selectedPlaylistTrackMover.move(playlistId, track, trackIndex, direction)
    }

    override fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) {
        selectedPlaylistTrackRemover.remove(playlistId, track)
    }

    override fun publishCollectionsActions(actions: CollectionsActions) {
        collectionsActionsSink.publish(actions)
    }
}
