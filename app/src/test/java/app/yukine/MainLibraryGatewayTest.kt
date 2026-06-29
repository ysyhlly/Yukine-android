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

    private fun gateway(events: MutableList<String>): MainLibraryGateway {
        return MainLibraryGateway(
            trackListPlayer = { tracks, index -> events += "play:${tracks[index].id}:$index" },
            languageModeProvider = { "zh" },
            statusSink = { status -> events += "status:$status" },
            favoriteApplier = { trackId, favorite -> events += "favorite:$trackId:$favorite" },
            nowBarRenderer = { events += "nowBar" },
            selectedTabRenderer = { events += "render" },
            collectionsLoader = { events += "loadCollections" },
            playlistAdder = { track -> events += "playlistAdd:${track.id}" },
            routeActions = FakeRouteActions(events),
            searchApplier = { events += "applySearch" },
            audioImporter = { events += "importFiles" },
            libraryScanner = { allowCachedFirst -> events += "scan:$allowCachedFirst" }
        )
    }

    private class FakeRouteActions(
        private val events: MutableList<String>
    ) : LibraryRouteActions {
        override fun setLibraryMode(mode: String) {
            events += "mode:$mode"
        }

        override fun selectLibraryGroup(key: String, title: String) {
            events += "group:$key:$title"
        }

        override fun setSelectedPlaylistId(playlistId: Long) {
            events += "playlist:$playlistId"
        }

        override fun clearLibraryGroup() {
            events += "clearGroup"
        }

        override fun setSearchQuery(query: String) {
            events += "search:$query"
        }
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
