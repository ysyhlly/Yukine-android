package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaylistBindingsTest {
    @Test
    fun forwardsStreamingPlaylistEdges() {
        val calls = mutableListOf<String>()
        val bindings = StreamingPlaylistBindings(
            selectedPlaylistIdProvider = StreamingPlaylistIdProvider {
                calls += "selected"
                42L
            },
            selectedPlaylistIdSetter = StreamingPlaylistIdSetter { playlistId ->
                calls += "set:$playlistId"
            },
            collectionsLoader = QueueNoArgAction { calls += "load" },
            selectedPlaylistNameProvider = StreamingPlaylistNameProvider {
                calls += "name"
                "Local"
            },
            selectedPlaylistTrackProvider = StreamingPlaylistTrackProvider {
                calls += "tracks"
                listOf(track("A"))
            },
            favoriteTrackProvider = StreamingPlaylistTrackProvider {
                calls += "favorites"
                listOf(track("F"))
            },
            selectedProviderProvider = StreamingSelectedProviderProvider {
                calls += "provider"
                StreamingProviderName.NETEASE
            },
            providerPicker = StreamingPlaylistProviderPicker { playlistName, tracks ->
                calls += "picker:$playlistName:${tracks.size}"
            },
            streamingNavigator = QueueNoArgAction { calls += "navigate" },
            loadedDialog = StreamingPlaylistLoadedDialog { message ->
                calls += "dialog:$message"
            },
            accountPlaylistImportPicker = StreamingAccountPlaylistImportPicker { provider, playlists ->
                calls += "accountPicker:${provider.wireName}:${playlists.size}"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" },
            selectedTabRenderer = QueueNoArgAction { calls += "render" }
        )

        val selected = bindings.selectedPlaylistId()
        val playlistName = bindings.selectedPlaylistName()
        val tracks = bindings.selectedPlaylistTracks()
        val favorites = bindings.favoriteTracks()
        val provider = bindings.selectedStreamingProvider()
        bindings.setSelectedPlaylistId(7L)
        bindings.loadCollections()
        bindings.showStreamingProviderPicker("Export", tracks)
        bindings.navigateToStreaming()
        bindings.showStreamingPlaylistLoadedDialog("Loaded")
        bindings.showAccountPlaylistImportPicker(
            StreamingProviderName.NETEASE,
            listOf(StreamingPlaylist(StreamingProviderName.NETEASE, "1", "P"))
        )
        bindings.setStatus("Done")
        bindings.renderSelectedTab()

        assertEquals(42L, selected)
        assertEquals("Local", playlistName)
        assertEquals("A", tracks.single().title)
        assertEquals("F", favorites.single().title)
        assertEquals(StreamingProviderName.NETEASE, provider)
        assertEquals(
            listOf(
                "selected",
                "name",
                "tracks",
                "favorites",
                "provider",
                "set:7",
                "load",
                "picker:Export:1",
                "navigate",
                "dialog:Loaded",
                "accountPicker:netease:1",
                "status:Done",
                "render"
            ),
            calls
        )
    }

    private fun track(title: String) = Track(
        title.hashCode().toLong(),
        title,
        "Artist",
        "Album",
        0L,
        android.net.Uri.EMPTY,
        title
    )
}
