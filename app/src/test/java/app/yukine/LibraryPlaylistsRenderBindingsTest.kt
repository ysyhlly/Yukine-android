package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryPlaylistsRenderBindingsTest {
    @Test
    fun forwardsPlaylistActionsToBoundOperations() {
        val events = mutableListOf<LibraryEvent>()
        val calls = mutableListOf<String>()
        val playlist = Playlist(9L, "Daily", 2, 0L, 0L)
        val chrome = LibraryGroupsChromeState(emptyList(), "Empty", emptyList())
        val request = LibraryPlaylistTrackListRequest(
            title = "Daily",
            tracks = arrayListOf(track(1L)),
            headerMetrics = arrayListOf(),
            headerActions = arrayListOf(),
            emptyText = "",
            modeActions = arrayListOf()
        )
        var publishedChrome: LibraryGroupsChromeState? = null
        var renderedRequest: LibraryPlaylistTrackListRequest? = null
        val bindings = LibraryPlaylistsRenderBindings(
            libraryEventSink = LibraryEventSink { events += it },
            playlistDeleteConfirmer = PlaylistDeleteConfirmer { calls += "delete:${it.id}" },
            chromeSink = LibraryGroupsChromeSink { publishedChrome = it },
            trackListRenderer = LibraryPlaylistTrackListRenderer { renderedRequest = it }
        )

        bindings.openPlaylist(playlist.id, playlist.name)
        bindings.playPlaylist(playlist.id)
        bindings.backFromPlaylist()
        bindings.playTrackList(request.tracks, 0)
        bindings.confirmDeletePlaylist(playlist)
        bindings.publishLibraryGroupsChrome(chrome)
        bindings.renderPlaylistTracks(request)

        assertEquals(
            listOf(
                LibraryEvent.OpenPlaylist(playlist.id, playlist.name),
                LibraryEvent.PlayPlaylist(playlist.id),
                LibraryEvent.BackFromGroup,
                LibraryEvent.PlayTrackList(request.tracks, 0)
            ),
            events
        )
        assertEquals(listOf("delete:9"), calls)
        assertSame(chrome, publishedChrome)
        assertSame(request, renderedRequest)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
