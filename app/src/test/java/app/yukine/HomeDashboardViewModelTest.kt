package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
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

    @Test
    fun updatePlaybackUsesRequestedLanguageForContinueCopy() {
        val viewModel = HomeDashboardViewModel(null)
        val track = Track(1L, "Snowlight", "Yukine", "Winter", 200_000L, Uri.EMPTY, "file:1")

        viewModel.updatePlayback(
            PlaybackStateSnapshot(
                track,
                0,
                1,
                50_000L,
                200_000L,
                true,
                false,
                "",
                false,
                0,
                1.0f,
                1.0f,
                0L
            ),
            AppLanguage.MODE_ENGLISH
        )

        val content = viewModel.uiState.value.content
        assertEquals(
            "Pick up Yukine's \"Snowlight\", or start from a cover recently added to the library.",
            content.heroSubtitle
        )
        assertEquals("Snowlight", content.continueTitle)
        assertEquals("Now playing", content.continueDetail)
        assertEquals(0.25f, content.continueProgress, 0.0001f)
    }
}
