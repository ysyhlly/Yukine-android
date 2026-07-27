package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track

/**
 * Collections mutation surface owned by [LibraryViewModel] / [PlaylistMutationOwner].
 * UI adapters and Binding only forward here — unit tests can exercise these paths without
 * assembling the full [CollectionsActionAdapter] dependency graph.
 */
internal open class CollectionsIntentOwner(
    private val viewModel: LibraryViewModel,
    private val playlistMutationOwner: PlaylistMutationOwner,
    private val playTrackList: (List<Track>, Int) -> Unit = { tracks, index ->
        viewModel.onEvent(LibraryEvent.PlayTrackList(tracks, index))
    },
    private val showAddToPlaylist: (Track) -> Unit = { track ->
        viewModel.onEvent(LibraryEvent.AddToPlaylist(track))
    },
    private val showCreatePlaylist: () -> Unit = {},
    private val showRenamePlaylist: (Playlist) -> Unit = {},
    private val confirmDeletePlaylist: (Playlist) -> Unit = {},
    private val selectPlaylist: (Long) -> Unit = {},
    private val downloadTrack: (Track) -> Unit = {},
    private val downloadTracks: (List<Track>) -> Unit = {}
) {
    open fun toggleFavorite(track: Track) {
        viewModel.onEvent(LibraryEvent.ToggleFavorite(track))
    }

    open fun play(tracks: List<Track>, index: Int) {
        playTrackList(tracks, index)
    }

    open fun addToPlaylist(track: Track) {
        showAddToPlaylist(track)
    }

    open fun createPlaylist() {
        showCreatePlaylist()
    }

    open fun renamePlaylist(playlist: Playlist) {
        showRenamePlaylist(playlist)
    }

    open fun deletePlaylist(playlist: Playlist) {
        confirmDeletePlaylist(playlist)
    }

    open fun select(playlistId: Long) {
        selectPlaylist(playlistId)
    }

    open fun download(track: Track) {
        downloadTrack(track)
    }

    open fun downloadAll(tracks: List<Track>) {
        downloadTracks(tracks)
    }

    open fun moveSelectedPlaylistTrack(
        playlistId: Long,
        track: Track,
        trackIndex: Int,
        direction: Int
    ) {
        viewModel.playlists.moveSelectedPlaylistTrack(
            playlistId,
            track,
            trackIndex,
            direction
        ) { moved ->
            playlistMutationOwner.onSelectedPlaylistTrackMoved(
                playlistId,
                track,
                trackIndex,
                direction,
                moved
            )
        }
    }

    open fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) {
        viewModel.playlists.removeSelectedPlaylistTrack(playlistId, track) { removed ->
            playlistMutationOwner.onSelectedPlaylistTrackRemoved(playlistId, removed)
        }
    }

    /** Completes a create dialog by writing through the playlist owner + incremental patch. */
    open fun commitCreatePlaylist(name: String) {
        playlistMutationOwner.createPlaylist(name)
    }

    open fun commitRenamePlaylist(playlistId: Long, name: String) {
        playlistMutationOwner.renamePlaylist(playlistId, name)
    }

    open fun commitDeletePlaylist(playlistId: Long, name: String) {
        playlistMutationOwner.deletePlaylist(playlistId, name)
    }
}
