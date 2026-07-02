package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList

class NetworkMenuEventControllerTest {
    @Test
    fun forwardsSimpleMenuActionsToInjectedHandlers() {
        val fakes = Fakes()
        val controller = fakes.controller()

        controller.navigateNetworkPage(MainRoutes.NETWORK_STREAMING)
        controller.showAddStream()
        controller.showImportM3u()
        controller.openM3uFilePicker()
        controller.showAddWebDav()

        assertEquals(
            listOf(
                "navigate:${MainRoutes.NETWORK_STREAMING}",
                "dialog:addStream",
                "dialog:importM3u",
                "picker:m3u",
                "dialog:addWebDav"
            ),
            fakes.events
        )
    }

    @Test
    fun playAllStreamsPublishesEmptyStatusOrPlaysFirstTrack() {
        val fakes = Fakes()
        val controller = fakes.controller()

        controller.playAllStreams()
        fakes.streamTracks.add(track(1L))
        controller.playAllStreams()

        assertEquals(
            listOf(
                "status:label:no.streams.to.play",
                "play:1@0"
            ),
            fakes.events
        )
    }

    @Test
    fun webDavMenuActionsUseLibraryStateBeforeDelegating() {
        val fakes = Fakes()
        val controller = fakes.controller()

        controller.playAllWebDavTracks()
        controller.confirmDeleteAllStreams()
        controller.syncAllWebDavSources()
        fakes.webDavTracks.add(track(2L))
        fakes.streamTracks.add(track(3L))
        fakes.remoteSources.add(remoteSource(10L, RemoteSource.TYPE_WEBDAV))
        fakes.remoteSources.add(remoteSource(11L, "smb"))
        fakes.remoteSources.add(remoteSource(12L, RemoteSource.TYPE_WEBDAV))
        controller.playAllWebDavTracks()
        controller.confirmDeleteAllStreams()
        controller.syncAllWebDavSources()

        assertEquals(
            listOf(
                "status:label:no.webdav.tracks.to.play",
                "status:label:no.streams.to.delete",
                "status:label:no.webdav.sources",
                "play:2@0",
                "confirm:deleteAllStreams",
                "syncAll:10,12"
            ),
            fakes.events
        )
    }

    @Test
    fun publishNetworkMenuUpdatesViewModelDirectly() {
        val fakes = Fakes()
        val controller = fakes.controller()
        val metric = SettingsMetric("title", "value")
        val action = SettingsAction("Open", Runnable { fakes.events.add("action:open") })

        controller.publishNetworkMenu("Network", listOf(metric), listOf(action))

        assertEquals("Network", fakes.networkMenuViewModel.uiState.value.title)
        assertEquals(listOf(metric), fakes.networkMenuViewModel.uiState.value.metrics)
        assertEquals(listOf(action), fakes.networkMenuViewModel.uiState.value.actions)
    }

    private class Fakes {
        val events = ArrayList<String>()
        val streamTracks = ArrayList<Track>()
        val webDavTracks = ArrayList<Track>()
        val remoteSources = ArrayList<RemoteSource>()
        val networkMenuViewModel = NetworkMenuViewModel()

        fun controller(): NetworkMenuEventController =
            NetworkMenuEventController(
                { page -> events.add("navigate:$page") },
                Runnable { events.add("dialog:addStream") },
                Runnable { events.add("dialog:importM3u") },
                Runnable { events.add("dialog:addWebDav") },
                { events.add("picker:m3u") },
                { streamTracks },
                { streamTracks.size },
                { webDavTracks },
                { remoteSources },
                { sourceIds -> events.add("syncAll:${sourceIds.joinToString(",")}") },
                { events.add("confirm:deleteAllStreams") },
                { tracks, index -> events.add("play:${tracks.first().id}@$index") },
                { key -> "label:$key" },
                { status -> events.add("status:$status") },
                networkMenuViewModel
            )
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun remoteSource(id: Long, type: String): RemoteSource =
        RemoteSource(id, type, "Source $id", "https://example.test", "", "", "/", "", 0L)
}
