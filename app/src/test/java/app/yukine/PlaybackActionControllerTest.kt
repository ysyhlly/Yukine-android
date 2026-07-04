package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackActionControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun forwardsPlaybackButtonsThroughNowPlayingViewModel() {
        val playbackGateway = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(playbackGateway)
        val listener = FakeListener()
        val controller = PlaybackActionController(viewModel, listener)

        controller.skipToPrevious()
        controller.skipToNext()
        controller.togglePlayback()
        controller.toggleShuffle()
        controller.cycleBottomPlaybackMode()
        controller.cycleRepeat()

        assertEquals(
            listOf("previous", "next", "play", "shuffle:true", "repeat:1", "repeat"),
            playbackGateway.calls
        )
        assertEquals(listOf("resolve", "resolve", "resolve"), listener.resolveCalls)
        assertEquals(listOf(null, null, null, null), listener.appliedStatuses)
    }

    @Test
    fun togglePlaybackSkipsViewModelWhenStreamingResolveHandlesCurrentTrack() {
        val playbackGateway = FakePlaybackGateway()
        val viewModel = NowPlayingViewModel()
        viewModel.bindPlaybackGateway(playbackGateway)
        val listener = FakeListener(resolveCurrent = true)
        val controller = PlaybackActionController(viewModel, listener)

        controller.togglePlayback()

        assertEquals(listOf("resolve"), listener.resolveCalls)
        assertEquals(emptyList<String>(), playbackGateway.calls)
        assertEquals(emptyList<String?>(), listener.appliedStatuses)
    }

    private class FakeListener(
        private val resolveCurrent: Boolean = false
    ) : PlaybackActionController.Listener {
        val resolveCalls = mutableListOf<String>()
        val appliedStatuses = mutableListOf<String?>()

        override fun resolveCurrentStreamingQueueTrackIfNeeded(): Boolean {
            resolveCalls += "resolve"
            return resolveCurrent
        }

        override fun playbackSnapshot(): PlaybackStateSnapshot? {
            return PlaybackStateSnapshot(
                playbackActionTrack(1L),
                0,
                1,
                100L,
                1000L,
                false,
                false,
                "",
                false,
                EchoPlaybackService.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
        }

        override fun fallbackTracks(): List<Track> {
            return listOf(playbackActionTrack(1L), playbackActionTrack(2L))
        }

        override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
            appliedStatuses += result?.status
        }
    }

    private class FakePlaybackGateway : NowPlayingPlaybackGateway {
        val calls = mutableListOf<String>()
        private var snapshot = PlaybackStateSnapshot.empty()

        override fun serviceConnected(): Boolean = true
        override fun snapshot(): PlaybackStateSnapshot? = snapshot
        override fun hasQueue(): Boolean = false
        override fun skipToPrevious() {
            calls += "previous"
        }
        override fun skipToNext() {
            calls += "next"
        }
        override fun seekTo(positionMs: Long) {}
        override fun removeTracksById(trackIds: Set<Long>) {}
        override fun clearQueue() {}
        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {}
        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {}
        override fun retainTracksById(trackIds: Set<Long>) {}
        override fun warmPlaybackTrack(track: Track) {}
        override fun appendToQueue(tracks: List<Track>) {}
        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {}
        override fun startSleepTimerMinutes(minutes: Int) {}
        override fun cancelSleepTimer() {}
        override fun playQueue(tracks: List<Track>, index: Int) {
            calls += "playQueue:$index:${tracks.size}"
        }
        override fun pause() {
            calls += "pause"
        }
        override fun play() {
            calls += "play"
        }
        override fun setShuffleEnabled(enabled: Boolean) {
            calls += "shuffle:$enabled"
        }
        override fun cycleRepeatMode() {
            calls += "repeat"
        }
        override fun setRepeatMode(repeatMode: Int) {
            calls += "repeat:$repeatMode"
        }
    }
}

private fun playbackActionTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
