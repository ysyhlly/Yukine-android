package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.util.ArrayList

class LibraryPlaylistsStateReducerTest {
    @Test
    fun rendersPlaylistRowsAndActionsThroughListener() {
        val listener = RecordingListener()
        val viewModel = LibraryViewModel()
        val controller = LibraryPlaylistsStateReducer(viewModel, listener)
        val playlists = listOf(
            Playlist(7L, "Favorites", 3, 0L, 0L),
            Playlist(8L, "Empty", 0, 0L, 0L)
        )
        val modeActions = listOf(TrackListModeAction("Playlists", "playlists", true, Runnable { }))

        controller.reduce(
            languageMode = AppLanguage.MODE_ENGLISH,
            playlists = playlists,
            selectedPlaylistId = -1L,
            selectedLibraryGroupKey = "",
            selectedPlaylistName = "",
            selectedPlaylistTracks = emptyList(),
            favoriteTracks = listOf(track(90L)),
            recentRecords = emptyList(),
            modeActions = modeActions,
            playlistSources = mapOf(7L to StreamingProviderName.NETEASE)
        )

        assertEquals("Playlists", viewModel.libraryGroups.value.title)
        assertEquals(
            listOf("virtual:favorites", "virtual:play-history"),
            viewModel.libraryGroups.value.rows.map(LibraryGroupUiState::id)
        )
        assertEquals(
            listOf("local", "netease"),
            viewModel.libraryGroups.value.playlistFolders.map { it.key }
        )
        assertEquals(
            listOf("Empty"),
            viewModel.libraryGroups.value.playlistFolders[0].entries.map { it.group.title }
        )
        assertEquals(
            listOf("Favorites"),
            viewModel.libraryGroups.value.playlistFolders[1].entries.map { it.group.title }
        )
        assertEquals(listOf(3, 2), viewModel.libraryGroups.value.playlistFolders.map { it.entries.single().actionIndex })
        assertEquals("No playlists", listener.chromeState?.emptyText)
        assertEquals(modeActions, listener.chromeState?.modeActions)
        assertNotSame(modeActions, listener.chromeState?.modeActions)

        listener.chromeState?.actions?.get(0)?.onOpen?.run()
        listener.chromeState?.actions?.get(0)?.onPlay?.run()
        listener.chromeState?.actions?.get(2)?.onOpen?.run()
        listener.chromeState?.actions?.get(2)?.onPlay?.run()
        listener.chromeState?.actions?.get(3)?.onPlay?.run()
        listener.chromeState?.actions?.get(2)?.onLongPress?.run()

        assertEquals(
            listOf("favorite:Favorite playlist", "playTracks:1:0", "open:7:Favorites", "playPlaylist:7", "playPlaylist:8", "delete:7"),
            listener.calls
        )
        assertEquals(false, listener.chromeState?.actions?.get(3)?.playEnabled)
    }

    @Test
    fun rendersSelectedPlaylistTracksWithHeaderActions() {
        val listener = RecordingListener()
        val controller = LibraryPlaylistsStateReducer(LibraryViewModel(), listener)
        val tracks = listOf(track(1L), track(2L))
        val modeActions = listOf(TrackListModeAction("Playlists", "playlists", true, Runnable { }))

        controller.reduce(
            languageMode = AppLanguage.MODE_ENGLISH,
            playlists = emptyList(),
            selectedPlaylistId = 9L,
            selectedLibraryGroupKey = "playlist:9",
            selectedPlaylistName = "Daily",
            selectedPlaylistTracks = tracks,
            favoriteTracks = emptyList(),
            recentRecords = emptyList(),
            modeActions = modeActions
        )

        val request = listener.playlistTrackRequest
        assertEquals("Daily", request?.title)
        assertEquals(tracks, request?.tracks)
        assertEquals(listOf("Songs"), request?.headerMetrics?.map { it.label })
        assertEquals(listOf("Back to playlists", "Play playlist"), request?.headerActions?.map { it.label })
        assertEquals("No tracks in this playlist", request?.emptyText)
        assertEquals(modeActions, request?.modeActions)
        assertNotSame(modeActions, request?.modeActions)

        request?.headerActions?.get(0)?.onClick?.run()
        request?.headerActions?.get(1)?.onClick?.run()

        assertEquals(listOf("back", "playTracks:2:0"), listener.calls)
    }

    private class RecordingListener : LibraryPlaylistsStateReducer.Listener {
        val calls = mutableListOf<String>()
        var chromeState: LibraryGroupsChromeState? = null
        var playlistTrackRequest: LibraryPlaylistTrackListRequest? = null

        override fun openFavoritePlaylist(title: String) {
            calls += "favorite:$title"
        }

        override fun openPlayHistory(title: String) {
            calls += "history:$title"
        }

        override fun openPlaylist(playlistId: Long, title: String) {
            calls += "open:$playlistId:$title"
        }

        override fun playPlaylist(playlistId: Long) {
            calls += "playPlaylist:$playlistId"
        }

        override fun confirmDeletePlaylist(playlist: Playlist) {
            calls += "delete:${playlist.id}"
        }

        override fun backFromPlaylist() {
            calls += "back"
        }

        override fun playTrackList(tracks: List<Track>, index: Int) {
            calls += "playTracks:${tracks.size}:$index"
        }

        override fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState) {
            chromeState = state
        }

        override fun publishPlaylistTracks(request: LibraryPlaylistTrackListRequest) {
            playlistTrackRequest = request
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
