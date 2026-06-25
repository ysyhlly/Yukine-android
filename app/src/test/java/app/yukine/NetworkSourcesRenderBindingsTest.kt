package app.yukine

import app.yukine.model.RemoteSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSourcesRenderBindingsTest {
    @Test
    fun forwardsSourceActions() {
        val listener = RecordingListener()
        val source = RemoteSource(7L, "webdav", "Source", "https://example.test", "", "", "", "", 0L)
        val bindings = NetworkSourcesRenderBindings(events = listener)

        bindings.backToNetwork()
        bindings.testRemoteSource(1L)
        bindings.syncRemoteSource(2L)
        bindings.playRemoteSourceTracks(source)
        bindings.openRemoteSourceTracks(3L)
        bindings.showEditWebDav(source)
        bindings.confirmDeleteRemoteSource(source)
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
    }
}
