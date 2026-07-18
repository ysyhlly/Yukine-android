package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStreamingPlaylistListenerTest {
    @Test
    fun delegatesStreamingPlaylistCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        var selectedPlaylistId = 42L
        val selectedTracks = listOf(playlistTrack(1L), playlistTrack(2L))
        val favoriteTracks = listOf(playlistTrack(3L))
        val accountPlaylists = listOf(StreamingPlaylist(StreamingProviderName.NETEASE, "100", "Daily"))
        val listener = listener(calls, selectedPlaylistId, selectedTracks, favoriteTracks) {
            selectedPlaylistId = it
        }

        assertEquals(42L, listener.selectedPlaylistId())
        listener.setSelectedPlaylistId(99L)
        listener.loadCollections()
        listener.refreshLibraryAfterStreamingImport()
        assertEquals("Local", listener.selectedPlaylistName())
        assertEquals(selectedTracks, listener.selectedPlaylistTracks())
        assertEquals(favoriteTracks, listener.favoriteTracks())
        assertEquals(StreamingProviderName.KUGOU, listener.selectedStreamingProvider())
        listener.showStreamingProviderPicker("Export", selectedTracks)
        listener.navigateToStreaming()
        listener.showStreamingPlaylistLoadedDialog("Loaded")
        listener.showAccountPlaylistImportPicker(StreamingProviderName.NETEASE, accountPlaylists)
        listener.setStatus("Ready")

        assertEquals(99L, selectedPlaylistId)
        assertEquals(
            listOf(
                "setPlaylist:99",
                "loadCollections",
                "refreshLibrary",
                "providerPicker:Export:2",
                "navigate",
                "loaded:Loaded",
                "accountPicker:netease:100",
                "status:Ready"
            ),
            calls
        )
    }

    @Test
    fun directConstructionCreatesStreamingPlaylistControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainStreamingPlaylistListener(
            StreamingPlaylistIdSource { 7L },
            StreamingPlaylistIdSink { calls += "set:$it" },
            StreamingCollectionsLoader { calls += "collections" },
            StreamingLibraryRefreshSink { calls += "refresh" },
            StreamingSelectedPlaylistNameSource { "Favorites" },
            StreamingSelectedPlaylistTracksSource { listOf(playlistTrack(10L)) },
            StreamingFavoriteTracksSource { listOf(playlistTrack(11L), playlistTrack(12L)) },
            StreamingSelectedProviderSource { StreamingProviderName.NETEASE },
            StreamingProviderPickerPresenter { playlistName, tracks ->
                calls += "provider:$playlistName:${tracks.map { it.id }}"
            },
            StreamingNavigationSink { calls += "navigate" },
            StreamingPlaylistLoadedDialogPresenter { calls += "loaded:$it" },
            StreamingPlaylistImportPreviewPresenter { _, _, _, _ -> },
            StreamingAccountPlaylistImportPickerPresenter { provider, playlists ->
                calls += "account:${provider.wireName}:${playlists.size}"
            },
            StreamingPlaylistStatusSink { calls += "status:$it" }
        )

        assertEquals(7L, listener.selectedPlaylistId())
        listener.setSelectedPlaylistId(8L)
        listener.loadCollections()
        listener.refreshLibraryAfterStreamingImport()
        assertEquals("Favorites", listener.selectedPlaylistName())
        assertEquals(listOf(10L), listener.selectedPlaylistTracks().map { it.id })
        assertEquals(listOf(11L, 12L), listener.favoriteTracks().map { it.id })
        assertEquals(StreamingProviderName.NETEASE, listener.selectedStreamingProvider())
        listener.showStreamingProviderPicker("Export", listOf(playlistTrack(13L)))
        listener.navigateToStreaming()
        listener.showStreamingPlaylistLoadedDialog("Done")
        listener.showAccountPlaylistImportPicker(
            StreamingProviderName.KUGOU,
            listOf(StreamingPlaylist(StreamingProviderName.KUGOU, "200", "Cloud"))
        )
        listener.setStatus("Synced")

        assertEquals(
            listOf(
                "set:8",
                "collections",
                "refresh",
                "provider:Export:[13]",
                "navigate",
                "loaded:Done",
                "account:kugou:1",
                "status:Synced"
            ),
            calls
        )
    }

    private fun listener(
        calls: MutableList<String>,
        selectedPlaylistId: Long,
        selectedTracks: List<Track>,
        favoriteTracks: List<Track>,
        playlistIdSetter: (Long) -> Unit
    ): StreamingPlaylistController.Listener =
        MainStreamingPlaylistListener(
            playlistIdSource = StreamingPlaylistIdSource { selectedPlaylistId },
            playlistIdSink = StreamingPlaylistIdSink {
                playlistIdSetter(it)
                calls += "setPlaylist:$it"
            },
            collectionsLoader = StreamingCollectionsLoader { calls += "loadCollections" },
            libraryRefreshSink = StreamingLibraryRefreshSink { calls += "refreshLibrary" },
            playlistNameSource = StreamingSelectedPlaylistNameSource { "Local" },
            playlistTracksSource = StreamingSelectedPlaylistTracksSource { selectedTracks },
            favoriteTracksSource = StreamingFavoriteTracksSource { favoriteTracks },
            selectedProviderSource = StreamingSelectedProviderSource { StreamingProviderName.KUGOU },
            providerPickerPresenter = StreamingProviderPickerPresenter { playlistName, tracks ->
                calls += "providerPicker:$playlistName:${tracks.size}"
            },
            navigationSink = StreamingNavigationSink { calls += "navigate" },
            loadedDialogPresenter = StreamingPlaylistLoadedDialogPresenter { calls += "loaded:$it" },
            importPreviewPresenter = StreamingPlaylistImportPreviewPresenter { provider, _, playlistName, tracks ->
                calls += "preview:${provider.wireName}:$playlistName:${tracks.size}"
            },
            accountPlaylistPickerPresenter = StreamingAccountPlaylistImportPickerPresenter { provider, playlists ->
                calls += "accountPicker:${provider.wireName}:${playlists.single().providerPlaylistId}"
            },
            statusSink = StreamingPlaylistStatusSink { calls += "status:$it" }
        )
}

private fun playlistTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
