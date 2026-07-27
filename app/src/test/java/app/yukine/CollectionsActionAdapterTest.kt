package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Playlist
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsActionAdapterTest {
    @Test
    fun delegatesCollectionsRenderActionsThroughIntentOwnerAndPlatformSinks() {
        val calls = mutableListOf<String>()
        val track = track(1L)
        val playlist = playlist(9L)
        val listener = listener(calls, selectedPlaylistId = 9L, selectedPlaylistTracks = listOf(track))

        listener.showCreatePlaylist()
        listener.openPlaylistM3uFilePicker()
        listener.confirmClearPlayHistory()
        listener.requestBack()
        listener.playTrackList(listOf(track), 0)
        listener.toggleFavorite(track)
        listener.showAddToPlaylist(track)
        listener.downloadTrack(track)
        listener.downloadTracks(listOf(track))
        listener.selectPlaylist(playlist.id)
        listener.showRenamePlaylist(playlist)
        listener.confirmDeletePlaylist(playlist)
        listener.openSelectedPlaylistExportDocument()
        listener.importSelectedPlaylistToStreaming()
        listener.importFavoritesToStreaming()
        listener.importStreamingFavorites()
        listener.syncSelectedPlaylistFromStreaming()
        listener.moveSelectedPlaylistTrack(playlist.id, track, 2, -1)
        listener.removeSelectedPlaylistTrack(playlist.id, track)

        assertEquals(
            listOf(
                "create-playlist",
                "open-m3u",
                "confirm-clear-history",
                "back",
                "play:1:0",
                "favorite:1",
                "add-to-playlist:1",
                "download:1",
                "download-list:1",
                "select-playlist:9",
                "rename-playlist:9",
                "delete-playlist:9",
                "export-playlist:9:Road Mix",
                "import-selected-streaming",
                "import-favorites-streaming",
                "import-streaming-favorites",
                "sync-selected-streaming",
                "move-track:9:1:2:-1",
                "remove-track:9:1"
            ),
            calls
        )
    }

    @Test
    fun selectedPlaylistExportPublishesStatusWhenNoPlaylistIsSelected() {
        val calls = mutableListOf<String>()
        val listener = listener(calls, selectedPlaylistId = -1L, selectedPlaylistTracks = listOf(track(1L)))

        listener.openSelectedPlaylistExportDocument()

        assertEquals(listOf("status:no.tracks.in.playlist"), calls)
    }

    @Test
    fun selectedPlaylistExportPublishesStatusWhenSelectedPlaylistIsEmpty() {
        val calls = mutableListOf<String>()
        val listener = listener(calls, selectedPlaylistId = 9L, selectedPlaylistTracks = emptyList())

        listener.openSelectedPlaylistExportDocument()

        assertEquals(listOf("status:no.tracks.in.playlist"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        selectedPlaylistId: Long,
        selectedPlaylistTracks: List<Track>
    ): CollectionsActionAdapter =
        CollectionsActionAdapter(
            intents = RecordingCollectionsIntentOwner(calls),
            playlistM3uPicker = CollectionsActionAdapter.PlaylistM3uPicker { calls += "open-m3u" },
            playHistoryClearConfirmer = CollectionsActionAdapter.PlayHistoryClearConfirmer {
                calls += "confirm-clear-history"
            },
            backRequester = CollectionsActionAdapter.BackRequester { calls += "back" },
            statusKeySink = CollectionsActionAdapter.StatusKeySink { calls += "status:$it" },
            playlistExportDocumentOpener = CollectionsActionAdapter.PlaylistExportDocumentOpener { playlistId, name ->
                calls += "export-playlist:$playlistId:$name"
            },
            selectedPlaylistIdSource = CollectionsActionAdapter.SelectedPlaylistIdSource { selectedPlaylistId },
            selectedPlaylistTracksSource = CollectionsActionAdapter.SelectedPlaylistTracksSource {
                selectedPlaylistTracks
            },
            selectedPlaylistNameSource = CollectionsActionAdapter.SelectedPlaylistNameSource { "Road Mix" },
            selectedPlaylistStreamingImporter = CollectionsActionAdapter.SelectedPlaylistStreamingImporter {
                calls += "import-selected-streaming"
            },
            favoritesStreamingImporter = CollectionsActionAdapter.FavoritesStreamingImporter {
                calls += "import-favorites-streaming"
            },
            streamingFavoritesImporter = CollectionsActionAdapter.StreamingFavoritesImporter {
                calls += "import-streaming-favorites"
            },
            selectedPlaylistStreamingSyncer = CollectionsActionAdapter.SelectedPlaylistStreamingSyncer {
                calls += "sync-selected-streaming"
            }
        )

    private class RecordingCollectionsIntentOwner(
        private val calls: MutableList<String>
    ) : CollectionsIntentOwner(
        viewModel = LibraryViewModel(),
        playlistMutationOwner = PlaylistMutationOwner(
            LibraryViewModel(),
            MainRouteController(NavigationViewModel(SavedStateHandle())),
            PlaylistMutationOwner.LanguageModeSource { AppLanguage.MODE_ENGLISH },
            PlaylistMutationOwner.StatusSink { },
            PlaylistMutationOwner.CollectionsLoader { }
        ),
        playTrackList = { tracks, index -> calls += "play:${tracks.size}:$index" },
        showAddToPlaylist = { calls += "add-to-playlist:${it.id}" },
        showCreatePlaylist = { calls += "create-playlist" },
        showRenamePlaylist = { calls += "rename-playlist:${it.id}" },
        confirmDeletePlaylist = { calls += "delete-playlist:${it.id}" },
        selectPlaylist = { calls += "select-playlist:$it" },
        downloadTrack = { calls += "download:${it.id}" },
        downloadTracks = { calls += "download-list:${it.size}" }
    ) {
        override fun toggleFavorite(track: Track) {
            calls += "favorite:${track.id}"
        }

        override fun moveSelectedPlaylistTrack(
            playlistId: Long,
            track: Track,
            trackIndex: Int,
            direction: Int
        ) {
            calls += "move-track:$playlistId:${track.id}:$trackIndex:$direction"
        }

        override fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) {
            calls += "remove-track:$playlistId:${track.id}"
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun playlist(id: Long): Playlist =
        Playlist(id, "Playlist $id", 1, 0L, 0L)
}
