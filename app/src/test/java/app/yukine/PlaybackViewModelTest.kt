package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun resetClearsPlaybackState() {
        val viewModel = PlaybackViewModel()
        val readModel = FakePlaybackReadModel()
        viewModel.bind(readModel)
        readModel.state.value = snapshot(track(1L))

        viewModel.resetPlayback()

        assertSame(null, viewModel.playback.value.snapshot.currentTrack)
        assertEquals(emptyList<Track>(), viewModel.playback.value.queue)
    }

    @Test
    fun bindConsumesReactivePlaybackStateAndMatchingQueueRevision() = runTest {
        val viewModel = PlaybackViewModel()
        val readModel = FakePlaybackReadModel()
        val first = track(1L)
        val second = track(2L)

        viewModel.bind(readModel)
        readModel.queue.value = PlaybackQueueSnapshot(7L, 0, listOf(first, second))
        readModel.state.value = snapshot(first, queueSize = 2, queueRevision = 7L)
        runCurrent()

        assertEquals(1L, viewModel.playback.value.snapshot.currentTrack.id)
        assertEquals(listOf(1L, 2L), viewModel.playback.value.queue.map { it.id })
        assertEquals(7L, viewModel.playback.value.publishedQueueRevision)
    }

    @Test
    fun bindDoesNotPairNewPlaybackRevisionWithStaleQueue() = runTest {
        val viewModel = PlaybackViewModel()
        val readModel = FakePlaybackReadModel()
        val first = track(1L)

        viewModel.bind(readModel)
        readModel.queue.value = PlaybackQueueSnapshot(7L, 0, listOf(first))
        readModel.state.value = snapshot(first, queueSize = 1, queueRevision = 8L)
        runCurrent()

        assertEquals(emptyList<Track>(), viewModel.playback.value.queue)
        assertEquals(null, viewModel.playback.value.publishedQueueRevision)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private fun snapshot(
        track: Track,
        queueSize: Int = 1,
        queueRevision: Long = 0L
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            queueSize,
            0L,
            1_000L,
            false,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L,
            queueRevision
        )

    private class FakePlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }
}
