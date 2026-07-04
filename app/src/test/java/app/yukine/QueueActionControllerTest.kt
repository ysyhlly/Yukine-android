package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueActionControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun removeAndClearQueueApplyViewModelResults() {
        val player = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(player)
        val listener = FakeQueueActionListener()
        val controller = QueueActionController(viewModel, listener)
        val track = track(8L)

        controller.removeQueueTrack(track)
        controller.clearQueue()

        assertEquals(listOf("remove:8", "clear"), player.calls)
        assertEquals(listOf("Status: Track 8", "Status"), listener.appliedStatuses)
    }

    @Test
    fun confirmClearQueueUsesStatusWhenQueueIsEmpty() {
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(FakePlaybackGateway(queue = emptyList()))
        val listener = FakeQueueActionListener()
        val controller = QueueActionController(viewModel, listener)

        controller.confirmClearQueue()

        assertEquals(listOf("Queue empty"), listener.statuses)
        assertEquals(0, listener.confirmClearCount)
    }

    @Test
    fun confirmClearQueueDelegatesWhenQueueHasTracks() {
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(FakePlaybackGateway(queue = listOf(track(1L))))
        val listener = FakeQueueActionListener()
        val controller = QueueActionController(viewModel, listener)

        controller.confirmClearQueue()

        assertEquals(1, listener.confirmClearCount)
        assertEquals(emptyList<String>(), listener.statuses)
    }

    @Test
    fun moveQueueTrackRefreshesOnlyWhenPlaybackServiceExists() {
        val viewModel = NowPlayingViewModel()
        val listener = FakeQueueActionListener(hasPlaybackService = false)
        val controller = QueueActionController(viewModel, listener)

        controller.moveQueueTrack(0, 1)
        assertEquals(emptyList<String>(), listener.calls)

        listener.hasPlaybackService = true
        controller.moveQueueTrack(0, 1)

        assertEquals(listOf("move:0:1", "nowBar", "tab"), listener.calls)
    }

    private class FakeQueueActionListener(
        var hasPlaybackService: Boolean = true
    ) : QueueActionController.Listener {
        val calls = mutableListOf<String>()
        val appliedStatuses = mutableListOf<String?>()
        val statuses = mutableListOf<String>()
        var confirmClearCount = 0

        override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
            appliedStatuses += result?.status
        }

        override fun hasPlaybackService(): Boolean = hasPlaybackService

        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
            calls += "move:$fromIndex:$toIndex"
        }

        override fun renderNowBar() {
            calls += "nowBar"
        }

        override fun renderSelectedTab() {
            calls += "tab"
        }

        override fun confirmClearQueue() {
            confirmClearCount += 1
        }

        override fun queueEmptyStatus(): String = "Queue empty"

        override fun setStatus(status: String) {
            statuses += status
        }
    }

    private class FakePlaybackGateway(
        private val connected: Boolean = true,
        var queue: List<Track> = listOf(track(1L))
    ) : NowPlayingPlaybackGateway {
        val calls = mutableListOf<String>()

        override fun snapshot(): PlaybackStateSnapshot? =
            if (!connected) {
                null
            } else {
            PlaybackStateSnapshot(
                queue.firstOrNull(),
                if (queue.isEmpty()) -1 else 0,
                queue.size,
                0L,
                queue.firstOrNull()?.durationMs ?: 0L,
                false,
                false,
                "",
                false,
                PlaybackRepeatMode.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
            }
        override fun skipToPrevious() {}
        override fun skipToNext() {}
        override fun seekTo(positionMs: Long) {}
        override fun removeTracksById(trackIds: Set<Long>) {
            calls += "remove:${trackIds.joinToString(",")}"
        }
        override fun clearQueue() {
            calls += "clear"
        }
        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {}
        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {}
        override fun retainTracksById(trackIds: Set<Long>) {}
        override fun warmPlaybackTrack(track: Track) {}
        override fun appendToQueue(tracks: List<Track>) {}
        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {}
        override fun startSleepTimerMinutes(minutes: Int) {}
        override fun cancelSleepTimer() {}
        override fun playQueue(tracks: List<Track>, index: Int) {}
        override fun pause() {}
        override fun play() {}
        override fun setShuffleEnabled(enabled: Boolean) {}
        override fun cycleRepeatMode() {}
        override fun setRepeatMode(repeatMode: Int) {}
    }
}

private fun track(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
