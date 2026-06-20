package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.TrackListHeaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkSourcesRenderBindingsTest {
    @Test
    fun forwardsSourceActionsAndPublishesChromeState() {
        val listener = RecordingListener()
        val source = RemoteSource(7L, "webdav", "Source", "https://example.test", "", "", "", "", 0L)
        val actions = listOf(
            NetworkSourceActions(
                onTest = Runnable { },
                onSync = Runnable { },
                onPlay = Runnable { },
                onTracks = Runnable { },
                onEdit = Runnable { },
                onDelete = Runnable { }
            )
        )
        val headerActions = listOf(TrackListHeaderAction("Back", Runnable { }))
        val labels = NetworkSourceLabels("Test", "Sync", "Play", "Tracks", "Edit", "Delete")
        var chromeState: NetworkSourcesChromeState? = null
        val bindings = NetworkSourcesRenderBindings(
            events = listener,
            chromeSink = NetworkSourcesChromeSink { chromeState = it }
        )

        bindings.backToNetwork()
        bindings.testRemoteSource(1L)
        bindings.syncRemoteSource(2L)
        bindings.playRemoteSourceTracks(source)
        bindings.openRemoteSourceTracks(3L)
        bindings.showEditWebDav(source)
        bindings.confirmDeleteRemoteSource(source)
        bindings.publishNetworkSourcesChrome(actions, headerActions, "Empty", labels)

        assertEquals(
            listOf(
                "back",
                "test:1",
                "sync:2",
                "play:7",
                "open:3",
                "edit:7",
                "delete:7"
            ),
            listener.calls
        )
        assertSame(actions, chromeState?.actions)
        assertSame(headerActions, chromeState?.headerActions)
        assertEquals("Empty", chromeState?.emptyText)
        assertSame(labels, chromeState?.labels)
    }

    private class RecordingListener : NetworkSourcesRenderController.Listener {
        val calls = mutableListOf<String>()

        override fun backToNetwork() {
            calls += "back"
        }

        override fun testRemoteSource(sourceId: Long) {
            calls += "test:$sourceId"
        }

        override fun syncRemoteSource(sourceId: Long) {
            calls += "sync:$sourceId"
        }

        override fun playRemoteSourceTracks(source: RemoteSource) {
            calls += "play:${source.id}"
        }

        override fun openRemoteSourceTracks(sourceId: Long) {
            calls += "open:$sourceId"
        }

        override fun showEditWebDav(source: RemoteSource) {
            calls += "edit:${source.id}"
        }

        override fun confirmDeleteRemoteSource(source: RemoteSource) {
            calls += "delete:${source.id}"
        }

        override fun publishNetworkSourcesChrome(
            actions: List<NetworkSourceActions>,
            headerActions: List<TrackListHeaderAction>,
            emptyText: String,
            labels: NetworkSourceLabels
        ) = Unit
    }
}
