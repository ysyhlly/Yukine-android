package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MainLibraryGatewayTest {
    @Test
    fun favoriteAppliesHostStateAndRefreshesViews() {
        val events = mutableListOf<String>()
        val gateway = gateway(events)

        gateway.applyFavorite(7L, true)

        assertEquals(
            listOf(
                "favorite:7:true",
                "nowBar",
                "render",
                "loadCollections"
            ),
            events
        )
    }

    @Test
    fun routingAndSearchActionsDelegateInOrder() {
        val events = mutableListOf<String>()
        val gateway = gateway(events)

        gateway.changeGroupMode(LibraryGrouping.ALBUMS)
        gateway.openGroup("artist:A", "Artist A")
        gateway.openPlaylist(42L, "Mix")
        gateway.backFromGroup()
        gateway.search("qq")

        assertEquals(
            listOf(
                "mode:albums",
                "render",
                "group:artist:A:Artist A",
                "render",
                "group:playlist:42:Mix",
                "playlist:42",
                "loadCollections",
                "clearGroup",
                "playlist:-1",
                "render",
                "search:qq",
                "applySearch"
            ),
            events
        )
    }

    @Test
    fun routingReadsRouteActionsFromProviderAtActionTime() {
        val events = mutableListOf<String>()
        var routeActions: LibraryRouteActions = FakeRouteActions(events, "first")
        val gateway = gateway(events) { routeActions }

        routeActions = FakeRouteActions(events, "second")
        gateway.changeGroupMode(LibraryGrouping.ARTISTS)

        assertEquals(
            listOf(
                "second:mode:artists",
                "render"
            ),
            events
        )
    }

    @Test
    fun statusImportScanAndTrackActionsDelegate() {
        val events = mutableListOf<String>()
        val track = track(3L)
        val gateway = gateway(events)

        gateway.showStatusKey("scan.library")
        gateway.importFiles()
        gateway.scanLibrary()
        gateway.playTrackList(listOf(track), 0)
        gateway.addToPlaylist(track)

        assertEquals(
            listOf(
                "status:${AppLanguage.text("zh", "scan.library")}",
                "importFiles",
                "scan:false",
                "play:3:0",
                "playlistAdd:3"
            ),
            events
        )
    }

    private fun gateway(
        events: MutableList<String>,
        routeActionsProvider: () -> LibraryRouteActions = { FakeRouteActions(events) }
    ): MainLibraryGateway {
        return MainLibraryGateway(
            trackListPlayer = { tracks, index -> events += "play:${tracks[index].id}:$index" },
            languageModeProvider = { "zh" },
            statusSink = { status -> events += "status:$status" },
            favoriteApplier = { trackId, favorite -> events += "favorite:$trackId:$favorite" },
            nowBarRenderer = { events += "nowBar" },
            selectedTabRenderer = { events += "render" },
            collectionsLoader = { events += "loadCollections" },
            playlistAdder = { track -> events += "playlistAdd:${track.id}" },
            routeActionsProvider = routeActionsProvider,
            searchApplier = { events += "applySearch" },
            audioImporter = { events += "importFiles" },
            libraryScanner = { allowCachedFirst -> events += "scan:$allowCachedFirst" }
        )
    }

    private class FakeRouteActions(
        private val events: MutableList<String>,
        private val prefix: String = ""
    ) : LibraryRouteActions {
        override fun setLibraryMode(mode: String) {
            events += event("mode:$mode")
        }

        override fun selectLibraryGroup(key: String, title: String) {
            events += event("group:$key:$title")
        }

        override fun setSelectedPlaylistId(playlistId: Long) {
            events += event("playlist:$playlistId")
        }

        override fun clearLibraryGroup() {
            events += event("clearGroup")
        }

        override fun setSearchQuery(query: String) {
            events += event("search:$query")
        }

        private fun event(value: String): String =
            if (prefix.isEmpty()) value else "$prefix:$value"
    }

    private fun track(id: Long): Track {
        return Track(
            id,
            "Track $id",
            "Artist",
            "Album",
            1000L,
            Uri.EMPTY,
            "/music/$id.mp3"
        )
    }
}
