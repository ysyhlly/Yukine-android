package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeDashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fetchHomeDashboardPreservesStreamingConnectedFlag() = runTest {
        val viewModel = HomeDashboardViewModel(null)

        viewModel.fetchHomeDashboard(
            localTracks = emptyList(),
            localRecords = emptyList(),
            localPlayback = PlaybackStateSnapshot.empty(),
            streamingConnected = true
        ).join()

        assertTrue(viewModel.uiState.value.content.streamingConnected)
    }
}
