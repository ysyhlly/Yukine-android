package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track

internal fun interface PlaylistDeleteConfirmer {
    fun confirm(playlist: Playlist)
}

internal fun interface LibraryPlaylistTrackListRenderer {
    fun render(request: LibraryPlaylistTrackListRequest)
}

internal class LibraryPlaylistsRenderBindings(
    private val libraryEventSink: LibraryEventSink,
    private val playlistDeleteConfirmer: PlaylistDeleteConfirmer,
    private val chromeSink: LibraryGroupsChromeSink,
    private val trackListRenderer: LibraryPlaylistTrackListRenderer
) : LibraryPlaylistsRenderController.Listener {
    override fun openFavoritePlaylist(title: String) {
        libraryEventSink.send(LibraryEvent.OpenGroup("virtual:favorites", title))
    }

    override fun openPlayHistory(title: String) {
        libraryEventSink.send(LibraryEvent.OpenGroup("virtual:play-history", title))
    }

    override fun openPlaylist(playlistId: Long, title: String) {
        libraryEventSink.send(LibraryEvent.OpenPlaylist(playlistId, title))
    }

    override fun playPlaylist(playlistId: Long) {
        libraryEventSink.send(LibraryEvent.PlayPlaylist(playlistId))
    }

    override fun confirmDeletePlaylist(playlist: Playlist) {
        playlistDeleteConfirmer.confirm(playlist)
    }

    override fun backFromPlaylist() {
        libraryEventSink.send(LibraryEvent.BackFromGroup)
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        libraryEventSink.send(LibraryEvent.PlayTrackList(tracks, index))
    }

    override fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState) {
        chromeSink.publish(state)
    }

    override fun renderPlaylistTracks(request: LibraryPlaylistTrackListRequest) {
        trackListRenderer.render(request)
    }
}
