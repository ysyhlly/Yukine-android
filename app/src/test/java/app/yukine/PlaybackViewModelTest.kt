package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PlaybackViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun replaceSnapshotReturnsPreviousAndPublishesNext() {
        val viewModel = PlaybackViewModel()
        val first = snapshot(track(1L))
        val second = snapshot(track(2L))

        val initialPrevious = viewModel.replacePlaybackSnapshot(first)
        val secondPrevious = viewModel.replacePlaybackSnapshot(second)

        assertSame(PlaybackStateSnapshot.empty().currentTrack, initialPrevious.currentTrack)
        assertEquals(1L, secondPrevious.currentTrack.id)
        assertEquals(2L, viewModel.playback.value.snapshot.currentTrack.id)
    }

    @Test
    fun publishPlaybackStoresSnapshotAndDefensiveQueueCopy() {
        val viewModel = PlaybackViewModel()
        val queue = mutableListOf(track(1L))

        viewModel.updatePlayback(snapshot(queue[0]), queue)
        queue += track(2L)

        assertEquals(1L, viewModel.playback.value.snapshot.currentTrack.id)
        assertEquals(listOf(1L), viewModel.playback.value.queue.map { it.id })
    }

    @Test
    fun resetClearsStateAndHistoryRefreshMarker() {
        val viewModel = PlaybackViewModel()
        viewModel.updatePlayback(snapshot(track(1L)), listOf(track(1L)))
        viewModel.setLastHistoryRefreshTrackId(1L)

        viewModel.resetPlayback()

        assertSame(null, viewModel.playback.value.snapshot.currentTrack)
        assertEquals(emptyList<Track>(), viewModel.playback.value.queue)
        assertEquals(-1L, viewModel.lastHistoryRefreshTrackId())
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private fun snapshot(track: Track): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            1,
            0L,
            1_000L,
            false,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
}
