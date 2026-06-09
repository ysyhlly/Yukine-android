package app.echo.next

import android.net.Uri
import android.view.View
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
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

    private class Fakes {
        val events = ArrayList<String>()
        val streamTracks = ArrayList<Track>()
        val webDavTracks = ArrayList<Track>()
        val remoteSources = ArrayList<RemoteSource>()

        fun controller(): NetworkMenuEventController =
            NetworkMenuEventController(
                { page -> events.add("navigate:$page") },
                object : NetworkMenuEventController.Dialogs {
                    override fun showAddStream() {
                        events.add("dialog:addStream")
                    }

                    override fun showImportM3u() {
                        events.add("dialog:importM3u")
                    }

                    override fun showAddWebDav() {
                        events.add("dialog:addWebDav")
                    }
                },
                { events.add("picker:m3u") },
                object : NetworkMenuEventController.LibrarySource {
                    override fun streamTracks(): ArrayList<Track> = streamTracks

                    override fun streamTrackCount(): Int = streamTracks.size

                    override fun webDavTracks(): ArrayList<Track> = webDavTracks

                    override fun remoteSources(): List<RemoteSource> = remoteSources
                },
                { sourceIds -> events.add("syncAll:${sourceIds.joinToString(",")}") },
                { events.add("confirm:deleteAllStreams") },
                { tracks, index -> events.add("play:${tracks.first().id}@$index") },
                { key -> "label:$key" },
                { status -> events.add("status:$status") },
                object : NetworkMenuEventController.ContentSink {
                    override fun addVirtualContent(view: View) = Unit
                }
            )
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun remoteSource(id: Long, type: String): RemoteSource =
        RemoteSource(id, type, "Source $id", "https://example.test", "", "", "/", "", 0L)
}
