package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.navigation.NetworkTab
import app.yukine.navigation.SettingsTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList

class NetworkSourcesEventControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun navigationUpdatesReactiveRouteState() {
        val fakes = Fakes()
        val controller = fakes.controller()

        controller.openRemoteSourceTracks(42L)
        controller.backToNetwork()

        assertEquals(emptyList<String>(), fakes.events)
        assertEquals(MainRoutes.NETWORK_SOURCES, fakes.navigationViewModel.state.value.networkPage)
        assertEquals(-1L, fakes.navigationViewModel.state.value.selectedRemoteSourceId)
    }

    @Test
    fun backFromSourcesRestoresTheSettingsPageThatOpenedIt() {
        val fakes = Fakes()
        fakes.openSourcesFromSettings()
        val controller = fakes.controller()

        controller.backToNetwork()

        assertEquals(SettingsTab, fakes.navigationViewModel.state.value.selectedTab)
        assertEquals(SettingsPage.SourcesGroup, fakes.navigationViewModel.state.value.settingsPage)
    }

    @Test
    fun sourceRequestsAndDialogsAreForwarded() {
        val fakes = Fakes()
        val controller = fakes.controller()
        val source = remoteSource(7L)

        controller.testRemoteSource(7L)
        controller.syncRemoteSource(7L)
        controller.showEditWebDav(source)
        controller.confirmDeleteRemoteSource(source)

        assertEquals(
            listOf(
                "status:label:test...",
                "test:7",
                "status:label:syncingSource 7",
                "sync:7|Source 7",
                "dialog:edit:7",
                "confirm:delete:7"
            ),
            fakes.events
        )
    }

    @Test
    fun playRemoteSourceTracksPublishesEmptyStatusOrPlaysTracks() {
        val fakes = Fakes()
        val controller = fakes.controller()
        val source = remoteSource(9L)

        controller.playRemoteSourceTracks(source)
        fakes.sourceTracks.add(track(5L))
        controller.playRemoteSourceTracks(source)

        assertEquals(
            listOf(
                "status:label:no.source.tracks.to.play",
                "play:5@0"
            ),
            fakes.events
        )
    }

    private class Fakes {
        val events = ArrayList<String>()
        val sourceTracks = ArrayList<Track>()
        val navigationViewModel = NavigationViewModel(SavedStateHandle())
        private val routeController = MainRouteController(navigationViewModel)
        private val requestController = NetworkRequestController(
            FakeSink(events),
            object : NetworkRequestController.Labels {
                override fun text(key: String): String = "label:$key"
            },
            FakeListener(events)
        )

        init {
            routeController.persist(
                NavigationRouteState(
                    selectedTab = NetworkTab,
                    networkPage = MainRoutes.NETWORK_SOURCES
                )
            )
        }

        fun controller(): NetworkSourcesEventController =
            NetworkSourcesEventController(
                routeController,
                requestController,
                { sourceId -> "Source $sourceId" },
                { sourceId -> sourceTracks },
                { source -> events.add("dialog:edit:${source.id}") },
                { source -> events.add("confirm:delete:${source.id}") },
                { tracks, index -> events.add("play:${tracks.first().id}@$index") },
                { key -> "label:$key" },
                { status -> events.add("status:$status") }
            )

        fun openSourcesFromSettings() {
            routeController.persist(
                NavigationRouteState(
                    selectedTab = SettingsTab,
                    settingsPage = SettingsPage.SourcesGroup
                )
            )
            routeController.navigateToNetworkPageFromCurrent(MainRoutes.NETWORK_SOURCES)
            routeController.persist()
        }
    }

    private class FakeListener(private val events: ArrayList<String>) : NetworkRequestController.Listener {
        override fun setStatus(status: String) {
            events.add("status:$status")
        }
    }

    private class FakeSink(private val events: ArrayList<String>) : NetworkOperationSink {
        override fun addStreamUrl(title: String, url: String) = Unit

        override fun updateStreamUrl(oldTrack: Track?, title: String, url: String) = Unit

        override fun importM3uPlaylist(url: String) = Unit

        override fun deleteAllStreams() = Unit

        override fun deleteTrack(trackId: Long, status: String) = Unit

        override fun deleteTracks(trackIds: List<Long>, status: String) = Unit

        override fun deleteRemoteSource(sourceId: Long) = Unit

        override fun saveWebDavSource(
            sourceId: Long,
            name: String,
            baseUrl: String,
            username: String,
            password: String,
            rootPath: String
        ) = Unit

        override fun testRemoteSource(sourceId: Long) {
            events.add("test:$sourceId")
        }

        override fun syncRemoteSource(sourceId: Long, sourceName: String) {
            events.add("sync:$sourceId|$sourceName")
        }

        override fun syncAllWebDavSources(sourceIds: List<Long>) = Unit
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun remoteSource(id: Long): RemoteSource =
        RemoteSource(id, RemoteSource.TYPE_WEBDAV, "Source $id", "https://example.test", "", "", "/", "", 0L)
}
