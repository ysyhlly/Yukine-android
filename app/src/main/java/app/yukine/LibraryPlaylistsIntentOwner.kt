package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import java.util.ArrayList
import java.util.Collections

/** Owns playlist presentation intents and their typed destination state adaptation. */
internal class LibraryPlaylistsIntentOwner(
    private val libraryViewModel: LibraryViewModel,
    private val confirmDelete: (Playlist) -> Unit,
    private val publishPlaylist: (LibraryPlaylistTrackListRequest) -> Unit
) : LibraryPlaylistsRenderController.Listener {
    override fun openFavoritePlaylist(title: String) {
        libraryViewModel.onEvent(LibraryEvent.OpenGroup("virtual:favorites", title))
    }

    override fun openPlayHistory(title: String) {
        libraryViewModel.onEvent(LibraryEvent.OpenGroup("virtual:play-history", title))
    }

    override fun openPlaylist(playlistId: Long, title: String) {
        libraryViewModel.onEvent(LibraryEvent.OpenPlaylist(playlistId, title))
    }

    override fun playPlaylist(playlistId: Long) {
        libraryViewModel.onEvent(LibraryEvent.PlayPlaylist(playlistId))
    }

    override fun confirmDeletePlaylist(playlist: Playlist) {
        confirmDelete(playlist)
    }

    override fun backFromPlaylist() {
        libraryViewModel.onEvent(LibraryEvent.BackFromGroup)
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        libraryViewModel.onEvent(LibraryEvent.PlayTrackList(tracks, index))
    }

    override fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState) {
        libraryViewModel.presentation.updateLibraryGroupsChrome(
            LibraryGroupsDestinationState(
                "",
                Collections.emptyList(),
                ArrayList(state.actions),
                state.emptyText,
                ArrayList(state.modeActions)
            )
        )
    }

    override fun renderPlaylistTracks(request: LibraryPlaylistTrackListRequest) {
        publishPlaylist(request)
    }
}
