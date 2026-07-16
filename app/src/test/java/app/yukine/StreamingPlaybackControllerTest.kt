package app.yukine

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingPlaybackControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun canonicalPhysicalSourceBypassesPlatformUrlResolution() = runTest {
        val placeholder = streamingPlaceholderTrack(91L)
        val local = track(92L)
        val listener = CountingListener(emptyList())
        val planner = RecordingPlanner()
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(StandardTestDispatcher(testScheduler))
        viewModel.playbackResolution.bindPlaybackCoordinator(planner, NoopStreamingPlaybackTaskQueue)
        val controller = StreamingPlaybackController(
            viewModel,
            NowPlayingViewModel(),
            listener,
            CanonicalPlaybackSourceResolver { _, callback ->
                callback.onResolved(local)
                true
            }
        )

        assertTrue(controller.resolveAndPlayStreamingTrack(listOf(placeholder), 0))

        assertEquals(1, listener.playbackResultsApplied)
        assertEquals(0, planner.prepareCalls)
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun preResolveUsesBoundedQueueReadsWithoutFullSnapshot() = runTest {
        val currentIndex = 250
        val queue = (0 until 500).map { index ->
            if (index in 252..254) {
                streamingPlaceholderTrack(index.toLong())
            } else {
                track(index.toLong())
            }
        }
        val listener = CountingListener(queue)
        val planner = RecordingPlanner()
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(StandardTestDispatcher(testScheduler))
        viewModel.playbackResolution.bindPlaybackCoordinator(planner, NoopStreamingPlaybackTaskQueue)
        val controller = StreamingPlaybackController(
            viewModel,
            NowPlayingViewModel(),
            listener
        )

        controller.preResolveNextStreamingTrack(
            PlaybackStateSnapshot(
                queue[currentIndex],
                currentIndex,
                queue.size,
                90_000L,
                120_000L,
                true,
                false,
                "",
                false,
                0,
                1.0f,
                1.0f,
                0L
            )
        )

        assertEquals(0, listener.queueSnapshotReads)
        assertEquals(listOf(251, 252, 253, 254), listener.trackAtReads)
        val plannedQueue = planner.queue.orEmpty()
        assertEquals(5, plannedQueue.size)
        assertSame(queue[250], plannedQueue[0])
        assertSame(queue[251], plannedQueue[1])
        assertSame(queue[252], plannedQueue[2])
        assertSame(queue[253], plannedQueue[3])
        assertSame(queue[254], plannedQueue[4])
        assertEquals(0, planner.snapshot?.currentIndex)
        assertEquals(5, planner.snapshot?.queueSize)
        assertSame(queue[currentIndex], planner.snapshot?.currentTrack)
        advanceUntilIdle()
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun preResolveSkipsResolvedLookaheadTracksForStreamingCandidatesWithoutFullSnapshot() = runTest {
        val currentIndex = 10
        val queue = (0 until 100).map { index -> track(index.toLong()) }.toMutableList()
        queue[15] = streamingPlaceholderTrack(15L)
        queue[18] = streamingPlaceholderTrack(18L)
        queue[20] = streamingPlaceholderTrack(20L)
        val listener = CountingListener(queue)
        val planner = RecordingPlanner()
        val viewModel = StreamingViewModel()
        viewModel.bindIoDispatcherForTest(StandardTestDispatcher(testScheduler))
        viewModel.playbackResolution.bindPlaybackCoordinator(planner, NoopStreamingPlaybackTaskQueue)
        val controller = StreamingPlaybackController(
            viewModel,
            NowPlayingViewModel(),
            listener
        )

        controller.preResolveNextStreamingTrack(
            PlaybackStateSnapshot(
                queue[currentIndex],
                currentIndex,
                queue.size,
                90_000L,
                120_000L,
                true,
                false,
                "",
                false,
                0,
                1.0f,
                1.0f,
                0L
            )
        )

        assertEquals(0, listener.queueSnapshotReads)
        assertEquals((11..20).toList(), listener.trackAtReads)
        val plannedQueue = planner.queue.orEmpty()
        assertEquals(5, plannedQueue.size)
        assertSame(queue[10], plannedQueue[0])
        assertSame(queue[11], plannedQueue[1])
        assertSame(queue[15], plannedQueue[2])
        assertSame(queue[18], plannedQueue[3])
        assertSame(queue[20], plannedQueue[4])
        assertEquals(0, planner.snapshot?.currentIndex)
        assertEquals(5, planner.snapshot?.queueSize)
        assertSame(queue[currentIndex], planner.snapshot?.currentTrack)
        advanceUntilIdle()
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private class CountingListener(
        private val queue: List<Track>
    ) : StreamingPlaybackController.Listener {
        var queueSnapshotReads: Int = 0
            private set
        val trackAtReads = mutableListOf<Int>()
        var playbackResultsApplied: Int = 0
            private set

        override fun languageMode(): String = "en"

        override fun adaptiveStreamingQuality(): StreamingAudioQuality = StreamingAudioQuality.HIGH

        override fun selectedStreamingQuality(): StreamingAudioQuality = StreamingAudioQuality.HIGH

        override fun refuseAutomaticQualityDowngrade(): Boolean = false

        override fun queueSnapshot(): List<Track> {
            queueSnapshotReads += 1
            return queue
        }

        override fun queueSize(): Int = queue.size

        override fun queueTrackAt(index: Int): Track? {
            trackAtReads += index
            return queue.getOrNull(index)
        }

        override fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot) {}

        override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
            playbackResultsApplied += 1
        }

        override fun setStatus(status: String) {}
    }

    private class RecordingPlanner : StreamingPlaybackResolvePlanner {
        var prepareCalls: Int = 0
            private set
        var snapshot: PlaybackStateSnapshot? = null
            private set
        var queue: List<Track>? = null
            private set

        override fun prepareNextPreResolve(
            snapshot: PlaybackStateSnapshot?,
            queue: List<Track>?
        ): StreamingPreResolveRequest? {
            this.snapshot = snapshot
            this.queue = queue
            return null
        }

        override fun clearPreResolve(key: String?) {}

        override fun prepare(tracks: List<Track>?, index: Int): ResolveStreamingPlaybackRequest? {
            prepareCalls += 1
            return null
        }

        override fun replaceResolvedTrack(
            request: ResolveStreamingPlaybackRequest,
            resolved: Track
        ): ArrayList<Track> = ArrayList(request.tracks)

        override fun prepareDownload(track: Track?): StreamingDownloadResolveRequest? = null

        override fun prepareRecovery(
            snapshot: PlaybackStateSnapshot?,
            selectedQuality: StreamingAudioQuality,
            adaptiveQuality: StreamingAudioQuality,
            refuseAutomaticQualityDowngrade: Boolean
        ): StreamingRecoveryRequest? = null

        override fun clearRecovery(key: String?) {}
    }

    private object NoopStreamingPlaybackTaskQueue : StreamingPlaybackTaskQueue {
        override fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask) {}

        override fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask) {}

        override fun scheduleNextUrlResolve(task: StreamingPlaybackTask) {}
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private fun streamingPlaceholderTrack(id: Long): Track =
        StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = id.toString(),
                title = "Streaming $id",
                artist = "Artist",
                album = "Album",
                durationMs = 1_000L
            )
        )
}
