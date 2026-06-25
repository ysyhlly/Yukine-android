package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSourcesViewModelTest {
    @Test
    fun updateSourcesPublishesDomainState() {
        val viewModel = NetworkSourcesViewModel()
        val source = source(4L)
        val rows = listOf(NetworkSourceUiState(4L, "NAS", "2 tracks", "OK"))

        viewModel.updateSources("Remote sources", listOf(source), rows, selectedSourceId = 4L)

        val state = viewModel.uiState.value
        assertEquals("Remote sources", state.title)
        assertEquals(listOf(source), state.sources)
        assertEquals(rows, state.rows)
        assertEquals(4L, state.selectedSourceId)
    }

    @Test
    fun loadingAndErrorStateAreExplicit() {
        val viewModel = NetworkSourcesViewModel()

        viewModel.setLoading(true, "Syncing")
        assertTrue(viewModel.uiState.value.loading)
        assertEquals("Syncing", viewModel.uiState.value.statusMessage)
        assertNull(viewModel.uiState.value.error)

        viewModel.setError("Failed")
        assertFalse(viewModel.uiState.value.loading)
        assertEquals("Failed", viewModel.uiState.value.error)
    }

    private fun source(id: Long): RemoteSource {
        return RemoteSource(
            id,
            RemoteSource.TYPE_WEBDAV,
            "NAS",
            "https://example.test/dav",
            "user",
            "pass",
            "Music",
            "OK",
            0L
        )
    }
}
