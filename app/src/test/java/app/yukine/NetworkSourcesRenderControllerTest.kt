package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.TrackListHeaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSourcesRenderControllerTest {
    @Test
    fun renderPublishesRowsThroughNetworkSourcesViewModel() {
        val viewModel = NetworkSourcesViewModel()
        val listener = RecordingListener()
        val controller = NetworkSourcesRenderController(viewModel, listener)
        val source = RemoteSource(4L, RemoteSource.TYPE_WEBDAV, "Archive", "https://example.test", "", "", "/", "", 0L)
        val tracks = listOf(track(10L, source.id))

        controller.render(AppLanguage.MODE_ENGLISH, listOf(source), tracks)

        assertEquals("Remote music sources", viewModel.screen.value.title)
        assertEquals(1, viewModel.screen.value.rows.size)
        assertEquals("Archive", viewModel.screen.value.rows.single().title)
        assertEquals(1, viewModel.uiState.value.sources.size)
        assertEquals(source.id, viewModel.uiState.value.sources.single().id)
        assertEquals(1, listener.chromeActions.size)
        assertEquals(1, listener.headerActions.size)
        assertTrue(listener.emptyText.isNotBlank())
    }

    private class RecordingListener : NetworkSourcesRenderController.Listener {
        var chromeActions: List<NetworkSourceActions> = emptyList()
        var headerActions: List<TrackListHeaderAction> = emptyList()
        var emptyText: String = ""

        override fun backToNetwork() = Unit

        override fun testRemoteSource(sourceId: Long) = Unit

        override fun syncRemoteSource(sourceId: Long) = Unit

        override fun playRemoteSourceTracks(source: RemoteSource) = Unit

        override fun openRemoteSourceTracks(sourceId: Long) = Unit

        override fun showEditWebDav(source: RemoteSource) = Unit

        override fun confirmDeleteRemoteSource(source: RemoteSource) = Unit

        override fun publishNetworkSourcesChrome(
            actions: List<NetworkSourceActions>,
            headerActions: List<TrackListHeaderAction>,
            emptyText: String,
            labels: NetworkSourceLabels
        ) {
            chromeActions = actions
            this.headerActions = headerActions
            this.emptyText = emptyText
        }
    }

    private fun track(id: Long, sourceId: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "webdav:$sourceId:/track-$id.mp3")
}
