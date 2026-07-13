package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsActionAdapterTest {
    @Test
    fun delegatesCollectionsRenderActionsToInjectedOwners() {
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

    @Test
    fun directConstructionCreatesCollectionsStateBindingListener() {
        val calls = mutableListOf<String>()
        val listener = CollectionsActionAdapter(
            CollectionsActionAdapter.PlaylistCreator { calls += "create-playlist" },
            CollectionsActionAdapter.PlaylistM3uPicker { calls += "open-m3u" },
            CollectionsActionAdapter.PlayHistoryClearConfirmer { calls += "confirm-clear-history" },
            CollectionsActionAdapter.BackRequester { calls += "back" },
            CollectionsActionAdapter.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            CollectionsActionAdapter.FavoriteToggler { calls += "favorite:${it.id}" },
            CollectionsActionAdapter.PlaylistAdder { calls += "add-to-playlist:${it.id}" },
            CollectionsActionAdapter.TrackDownloader { calls += "download:${it.id}" },
            CollectionsActionAdapter.TracksDownloader { calls += "download-list:${it.size}" },
            CollectionsActionAdapter.PlaylistSelector { calls += "select-playlist:$it" },
            CollectionsActionAdapter.PlaylistRenamer { calls += "rename-playlist:${it.id}" },
            CollectionsActionAdapter.PlaylistDeleteConfirmer { calls += "delete-playlist:${it.id}" },
            CollectionsActionAdapter.SelectedPlaylistIdSource { 3L },
            CollectionsActionAdapter.SelectedPlaylistTracksSource { listOf(track(1L)) },
            CollectionsActionAdapter.SelectedPlaylistNameSource { "Road Mix" },
            CollectionsActionAdapter.StatusKeySink { calls += "status:$it" },
            CollectionsActionAdapter.PlaylistExportDocumentOpener { playlistId, name ->
                calls += "export-playlist:$playlistId:$name"
            },
            CollectionsActionAdapter.SelectedPlaylistStreamingImporter { calls += "import-selected-streaming" },
            CollectionsActionAdapter.FavoritesStreamingImporter { calls += "import-favorites-streaming" },
            CollectionsActionAdapter.StreamingFavoritesImporter { calls += "import-streaming-favorites" },
            CollectionsActionAdapter.SelectedPlaylistStreamingSyncer { calls += "sync-selected-streaming" },
            CollectionsActionAdapter.SelectedPlaylistTrackMover { playlistId, track, index, direction ->
                calls += "move-track:$playlistId:${track.id}:$index:$direction"
            },
            CollectionsActionAdapter.SelectedPlaylistTrackRemover { playlistId, track ->
                calls += "remove-track:$playlistId:${track.id}"
            }
        )

        listener.showCreatePlaylist()
        listener.openSelectedPlaylistExportDocument()

        assertEquals(listOf("create-playlist", "export-playlist:3:Road Mix"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        selectedPlaylistId: Long,
        selectedPlaylistTracks: List<Track>
    ): CollectionsActionAdapter =
        CollectionsActionAdapter(
            playlistCreator = CollectionsActionAdapter.PlaylistCreator { calls += "create-playlist" },
            playlistM3uPicker = CollectionsActionAdapter.PlaylistM3uPicker { calls += "open-m3u" },
            playHistoryClearConfirmer = CollectionsActionAdapter.PlayHistoryClearConfirmer {
                calls += "confirm-clear-history"
            },
            backRequester = CollectionsActionAdapter.BackRequester { calls += "back" },
            trackListPlayer = CollectionsActionAdapter.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = CollectionsActionAdapter.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = CollectionsActionAdapter.PlaylistAdder { calls += "add-to-playlist:${it.id}" },
            trackDownloader = CollectionsActionAdapter.TrackDownloader { calls += "download:${it.id}" },
            tracksDownloader = CollectionsActionAdapter.TracksDownloader { calls += "download-list:${it.size}" },
            playlistSelector = CollectionsActionAdapter.PlaylistSelector { calls += "select-playlist:$it" },
            playlistRenamer = CollectionsActionAdapter.PlaylistRenamer { calls += "rename-playlist:${it.id}" },
            playlistDeleteConfirmer = CollectionsActionAdapter.PlaylistDeleteConfirmer {
                calls += "delete-playlist:${it.id}"
            },
            selectedPlaylistIdSource = CollectionsActionAdapter.SelectedPlaylistIdSource { selectedPlaylistId },
            selectedPlaylistTracksSource = CollectionsActionAdapter.SelectedPlaylistTracksSource {
                selectedPlaylistTracks
            },
            selectedPlaylistNameSource = CollectionsActionAdapter.SelectedPlaylistNameSource { "Road Mix" },
            statusKeySink = CollectionsActionAdapter.StatusKeySink { calls += "status:$it" },
            playlistExportDocumentOpener = CollectionsActionAdapter.PlaylistExportDocumentOpener { playlistId, name ->
                calls += "export-playlist:$playlistId:$name"
            },
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
            },
            selectedPlaylistTrackMover = CollectionsActionAdapter.SelectedPlaylistTrackMover { playlistId, track, index, direction ->
                calls += "move-track:$playlistId:${track.id}:$index:$direction"
            },
            selectedPlaylistTrackRemover = CollectionsActionAdapter.SelectedPlaylistTrackRemover { playlistId, track ->
                calls += "remove-track:$playlistId:${track.id}"
            }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun playlist(id: Long): Playlist =
        Playlist(id, "Playlist $id", 1, 0L, 0L)
}
