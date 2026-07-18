package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import android.net.Uri
import app.yukine.model.Track
import app.yukine.emptyHomeDashboardActions
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        val readModel = FakePlaybackReadModel()
        viewModel.bindPlayback(
            readModel,
            MutableStateFlow(AppLanguage.MODE_ENGLISH)
        )

        readModel.state.value = PlaybackStateSnapshot(
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

    @Test
    fun updateHomeDashboardActionsKeepsActionsWithDashboardState() {
        val viewModel = HomeDashboardViewModel(null)
        var opened = false
        val actions = emptyHomeDashboardActions().copy(
            onOpenNowPlaying = Runnable { opened = true }
        )

        viewModel.updateHomeDashboardActions(actions)
        viewModel.uiState.value.actions.onOpenNowPlaying.run()

        assertTrue(opened)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stateSourcesBuildDashboardAndActionsWithoutTabRenderCallback() = runTest {
        val viewModel = HomeDashboardViewModel(null)
        val readModel = FakePlaybackReadModel()
        val track = Track(7L, "Flow", "Yukine", "Reactive", 180_000L, Uri.EMPTY, "file:7")
        val handler = RecordingHomeIntentHandler()

        viewModel.bindStateSources(
            readModel,
            MutableStateFlow(
                LibraryStoreState(
                    allTracks = listOf(track),
                    visibleTracks = listOf(track)
                )
            ),
            MutableStateFlow(StreamingSearchState()),
            MutableStateFlow(AppLanguage.MODE_SYSTEM),
            handler
        )
        advanceUntilIdle()

        assertEquals("Flow", viewModel.uiState.value.content.continueTitle)
        viewModel.uiState.value.actions.onShuffleAll.run()
        assertEquals(listOf(7L), handler.shuffledTrackIds)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playingDashboardPublishesNextQueueTrackAndNextAction() = runTest {
        val viewModel = HomeDashboardViewModel(null)
        val readModel = FakePlaybackReadModel()
        val current = Track(10L, "Current", "Yukine", "Queue", 180_000L, Uri.EMPTY, "file:10")
        val next = Track(11L, "Next", "Echo", "Queue", 190_000L, Uri.EMPTY, "file:11")
        val handler = RecordingHomeIntentHandler()
        readModel.queue.value = PlaybackQueueSnapshot(
            currentIndex = 0,
            tracks = listOf(current, next)
        )
        readModel.state.value = PlaybackStateSnapshot(
            current,
            0,
            2,
            30_000L,
            180_000L,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )

        viewModel.bindStateSources(
            readModel,
            MutableStateFlow(LibraryStoreState(allTracks = listOf(current, next))),
            MutableStateFlow(StreamingSearchState()),
            MutableStateFlow(AppLanguage.MODE_ENGLISH),
            handler
        )
        advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertEquals("Current", content.continueTitle)
        assertEquals("Next", content.nextTitle)
        assertEquals("Echo - Queue", content.nextSubtitle)
        viewModel.uiState.value.actions.onNext.run()
        assertEquals(1, handler.nextCalls)
    }

    private class FakePlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }

    private class RecordingHomeIntentHandler : HomeDashboardIntentHandler {
        var shuffledTrackIds: List<Long> = emptyList()
        var nextCalls: Int = 0

        override fun openLibraryMode(mode: String) = Unit
        override fun continuePlayback(track: Track?) = Unit
        override fun openNowPlaying() = Unit
        override fun playTrack(track: Track) = Unit
        override fun refreshLibrary() = Unit
        override fun openQueue() = Unit
        override fun nextTrack() {
            nextCalls++
        }
        override fun shuffleAll(tracks: List<Track>) {
            shuffledTrackIds = tracks.map { it.id }
        }
        override fun openStreaming() = Unit
        override fun openSearch() = Unit
        override fun playDailyRecommendations() = Unit
        override fun playHeartbeatRecommendations() = Unit
    }
}
