package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryPlaylistsIntentOwnerTest {
    @Test
    fun routesTypedPlaylistIntentsAndPublishesDestinationState() {
        val viewModel = LibraryViewModel()
        val gateway = RecordingLibraryGateway()
        val deleted = mutableListOf<Long>()
        val published = mutableListOf<LibraryPlaylistTrackListRequest>()
        viewModel.bindGateway(gateway)
        val owner = LibraryPlaylistsIntentOwner(
            libraryViewModel = viewModel,
            confirmDelete = { deleted += it.id },
            publishPlaylist = published::add
        )
        val playlist = Playlist(7L, "Daily", 1, 0L, 0L)
        val track = track(9L)
        val request = LibraryPlaylistTrackListRequest(
            title = "Daily",
            tracks = arrayListOf(track),
            headerMetrics = arrayListOf(TrackListHeaderMetric("Songs", "1")),
            headerActions = arrayListOf(TrackListHeaderAction("Back", Runnable {})),
            emptyText = "Empty",
            modeActions = arrayListOf()
        )

        owner.openFavoritePlaylist("Favorites")
        owner.openPlayHistory("History")
        owner.openPlaylist(playlist.id, playlist.name)
        owner.backFromPlaylist()
        owner.playTrackList(listOf(track), 0)
        owner.confirmDeletePlaylist(playlist)
        owner.renderPlaylistTracks(request)
        owner.publishLibraryGroupsChrome(
            LibraryGroupsChromeState(
                actions = emptyList(),
                emptyText = "No playlists",
                modeActions = listOf(TrackListModeAction("Playlists", "playlists", true, Runnable {}))
            )
        )

        assertEquals(
            listOf(
                "group:virtual:favorites:Favorites",
                "group:virtual:play-history:History",
                "playlist:7:Daily",
                "back",
                "play:9:0"
            ),
            gateway.calls
        )
        assertEquals(listOf(7L), deleted)
        assertSame(request, published.single())
        assertEquals("No playlists", viewModel.libraryGroups.value.emptyText)
        assertEquals(listOf("playlists"), viewModel.libraryGroups.value.modeActions.map { it.mode })
    }

    private class RecordingLibraryGateway : LibraryGateway {
        val calls = mutableListOf<String>()

        override fun playTrackList(tracks: List<Track>, index: Int) {
            calls += "play:${tracks.joinToString { it.id.toString() }}:$index"
        }

        override fun showStatusKey(key: String) = Unit
        override fun applyFavorite(trackId: Long, favorite: Boolean) = Unit
        override fun addToPlaylist(track: Track) = Unit
        override fun changeGroupMode(mode: String) = Unit

        override fun openGroup(key: String, title: String) {
            calls += "group:$key:$title"
        }

        override fun openPlaylist(playlistId: Long, title: String) {
            calls += "playlist:$playlistId:$title"
        }

        override fun backFromGroup() {
            calls += "back"
        }

        override fun search(query: String) = Unit
        override fun importFiles() = Unit
        override fun scanLibrary() = Unit
    }

    private fun track(id: Long) =
        Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
}
