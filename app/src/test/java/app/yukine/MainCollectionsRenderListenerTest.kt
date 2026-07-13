package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MainCollectionsRenderListenerTest {
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
    fun directConstructionCreatesCollectionsRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainCollectionsRenderListener(
            MainCollectionsRenderListener.PlaylistCreator { calls += "create-playlist" },
            MainCollectionsRenderListener.PlaylistM3uPicker { calls += "open-m3u" },
            MainCollectionsRenderListener.PlayHistoryClearConfirmer { calls += "confirm-clear-history" },
            MainCollectionsRenderListener.BackRequester { calls += "back" },
            MainCollectionsRenderListener.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            MainCollectionsRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            MainCollectionsRenderListener.PlaylistAdder { calls += "add-to-playlist:${it.id}" },
            MainCollectionsRenderListener.TrackDownloader { calls += "download:${it.id}" },
            MainCollectionsRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            MainCollectionsRenderListener.PlaylistSelector { calls += "select-playlist:$it" },
            MainCollectionsRenderListener.PlaylistRenamer { calls += "rename-playlist:${it.id}" },
            MainCollectionsRenderListener.PlaylistDeleteConfirmer { calls += "delete-playlist:${it.id}" },
            MainCollectionsRenderListener.SelectedPlaylistIdSource { 3L },
            MainCollectionsRenderListener.SelectedPlaylistTracksSource { listOf(track(1L)) },
            MainCollectionsRenderListener.SelectedPlaylistNameSource { "Road Mix" },
            MainCollectionsRenderListener.StatusKeySink { calls += "status:$it" },
            MainCollectionsRenderListener.PlaylistExportDocumentOpener { playlistId, name ->
                calls += "export-playlist:$playlistId:$name"
            },
            MainCollectionsRenderListener.SelectedPlaylistStreamingImporter { calls += "import-selected-streaming" },
            MainCollectionsRenderListener.FavoritesStreamingImporter { calls += "import-favorites-streaming" },
            MainCollectionsRenderListener.StreamingFavoritesImporter { calls += "import-streaming-favorites" },
            MainCollectionsRenderListener.SelectedPlaylistStreamingSyncer { calls += "sync-selected-streaming" },
            MainCollectionsRenderListener.SelectedPlaylistTrackMover { playlistId, track, index, direction ->
                calls += "move-track:$playlistId:${track.id}:$index:$direction"
            },
            MainCollectionsRenderListener.SelectedPlaylistTrackRemover { playlistId, track ->
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
    ): MainCollectionsRenderListener =
        MainCollectionsRenderListener(
            playlistCreator = MainCollectionsRenderListener.PlaylistCreator { calls += "create-playlist" },
            playlistM3uPicker = MainCollectionsRenderListener.PlaylistM3uPicker { calls += "open-m3u" },
            playHistoryClearConfirmer = MainCollectionsRenderListener.PlayHistoryClearConfirmer {
                calls += "confirm-clear-history"
            },
            backRequester = MainCollectionsRenderListener.BackRequester { calls += "back" },
            trackListPlayer = MainCollectionsRenderListener.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = MainCollectionsRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = MainCollectionsRenderListener.PlaylistAdder { calls += "add-to-playlist:${it.id}" },
            trackDownloader = MainCollectionsRenderListener.TrackDownloader { calls += "download:${it.id}" },
            tracksDownloader = MainCollectionsRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            playlistSelector = MainCollectionsRenderListener.PlaylistSelector { calls += "select-playlist:$it" },
            playlistRenamer = MainCollectionsRenderListener.PlaylistRenamer { calls += "rename-playlist:${it.id}" },
            playlistDeleteConfirmer = MainCollectionsRenderListener.PlaylistDeleteConfirmer {
                calls += "delete-playlist:${it.id}"
            },
            selectedPlaylistIdSource = MainCollectionsRenderListener.SelectedPlaylistIdSource { selectedPlaylistId },
            selectedPlaylistTracksSource = MainCollectionsRenderListener.SelectedPlaylistTracksSource {
                selectedPlaylistTracks
            },
            selectedPlaylistNameSource = MainCollectionsRenderListener.SelectedPlaylistNameSource { "Road Mix" },
            statusKeySink = MainCollectionsRenderListener.StatusKeySink { calls += "status:$it" },
            playlistExportDocumentOpener = MainCollectionsRenderListener.PlaylistExportDocumentOpener { playlistId, name ->
                calls += "export-playlist:$playlistId:$name"
            },
            selectedPlaylistStreamingImporter = MainCollectionsRenderListener.SelectedPlaylistStreamingImporter {
                calls += "import-selected-streaming"
            },
            favoritesStreamingImporter = MainCollectionsRenderListener.FavoritesStreamingImporter {
                calls += "import-favorites-streaming"
            },
            streamingFavoritesImporter = MainCollectionsRenderListener.StreamingFavoritesImporter {
                calls += "import-streaming-favorites"
            },
            selectedPlaylistStreamingSyncer = MainCollectionsRenderListener.SelectedPlaylistStreamingSyncer {
                calls += "sync-selected-streaming"
            },
            selectedPlaylistTrackMover = MainCollectionsRenderListener.SelectedPlaylistTrackMover { playlistId, track, index, direction ->
                calls += "move-track:$playlistId:${track.id}:$index:$direction"
            },
            selectedPlaylistTrackRemover = MainCollectionsRenderListener.SelectedPlaylistTrackRemover { playlistId, track ->
                calls += "remove-track:$playlistId:${track.id}"
            }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun playlist(id: Long): Playlist =
        Playlist(id, "Playlist $id", 1, 0L, 0L)
}
