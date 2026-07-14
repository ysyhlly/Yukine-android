package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSourcesStateReducerTest {
    @Test
    fun renderPublishesRowsThroughNetworkSourcesViewModel() {
        val viewModel = NetworkSourcesViewModel()
        val listener = RecordingListener()
        val controller = NetworkSourcesStateReducer(viewModel, listener)
        val source = RemoteSource(4L, RemoteSource.TYPE_WEBDAV, "Archive", "https://example.test", "", "", "/", "", 0L)
        val tracks = listOf(track(10L, source.id))

        controller.reduce(AppLanguage.MODE_ENGLISH, listOf(source), tracks)

        assertEquals("Remote music sources", viewModel.uiState.value.title)
        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals("Archive", viewModel.uiState.value.rows.single().title)
        assertEquals(1, viewModel.uiState.value.sources.size)
        assertEquals(source.id, viewModel.uiState.value.sources.single().id)
        assertEquals(1, viewModel.uiState.value.actions.size)
        assertEquals(1, viewModel.uiState.value.headerActions.size)
        assertTrue(viewModel.uiState.value.emptyText.isNotBlank())
    }

    private class RecordingListener : NetworkSourcesStateReducer.Listener {
        override fun backToNetwork() = Unit

        override fun testRemoteSource(sourceId: Long) = Unit

        override fun syncRemoteSource(sourceId: Long) = Unit

        override fun playRemoteSourceTracks(source: RemoteSource) = Unit

        override fun openRemoteSourceTracks(sourceId: Long) = Unit

        override fun showEditWebDav(source: RemoteSource) = Unit

        override fun confirmDeleteRemoteSource(source: RemoteSource) = Unit
    }

    private fun track(id: Long, sourceId: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "webdav:$sourceId:/track-$id.mp3")
}
