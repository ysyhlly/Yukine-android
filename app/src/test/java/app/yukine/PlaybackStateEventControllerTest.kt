package app.yukine

import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackStateEventControllerTest {
    @Test
    fun progressTicksDoNotRepublishLargeQueueWhenQueueIdentityIsUnchanged() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val queueSource = CountingQueueSource(
            List(500) { index -> track(index.toLong()) }
        )
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            queueSource,
            RecordingListener()
        )

        controller.onPlaybackStateChanged(snapshot(track = track(7L), positionMs = 1_000L, queueSize = 500))
        idleMain()
        controller.onPlaybackStateChanged(snapshot(track = track(7L), positionMs = 2_000L, queueSize = 500))
        idleMain()
        controller.onPlaybackStateChanged(snapshot(track = track(7L), positionMs = 3_000L, queueSize = 500))
        idleMain()

        assertEquals(1, queueSource.calls)
        assertEquals(500, playbackViewModel.playback.value.queue.size)
    }

    @Test
    fun queueChangesRepublishQueueSnapshot() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val queueSource = CountingQueueSource(
            List(2) { index -> track(index.toLong()) }
        )
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            queueSource,
            RecordingListener()
        )

        controller.onPlaybackStateChanged(snapshot(track = track(1L), positionMs = 1_000L, queueSize = 2))
        idleMain()
        queueSource.queue = List(3) { index -> track(index.toLong()) }
        controller.onPlaybackStateChanged(snapshot(track = track(1L), positionMs = 2_000L, queueSize = 3))
        idleMain()
        queueSource.queue = List(3) { index -> track((index + 10).toLong()) }
        controller.onPlaybackStateChanged(snapshot(track = track(10L), positionMs = 0L, queueSize = 3))
        idleMain()

        assertEquals(3, queueSource.calls)
        assertEquals(3, playbackViewModel.playback.value.queue.size)
        assertEquals(10L, playbackViewModel.playback.value.queue.first().id)
    }

    @Test
    fun hiddenQueueTabDoesNotReadLargeQueueOnTrackChanges() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val queueSource = CountingQueueSource(
            List(500) { index -> track(index.toLong()) }
        )
        val listener = RecordingListener(selectedTab = MainRoutes.TAB_HOME)
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            queueSource,
            listener
        )

        controller.onPlaybackStateChanged(snapshot(track = track(7L), positionMs = 1_000L, queueSize = 500))
        idleMain()
        controller.onPlaybackStateChanged(snapshot(track = track(8L), positionMs = 0L, queueSize = 500))
        idleMain()

        assertEquals(0, queueSource.calls)
        assertEquals(emptyList<Track>(), playbackViewModel.playback.value.queue)
    }

    @Test
    fun queueTabPublishesAfterHiddenQueueChanges() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val queueSource = CountingQueueSource(
            List(500) { index -> track(index.toLong()) }
        )
        val listener = RecordingListener(selectedTab = MainRoutes.TAB_HOME)
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            queueSource,
            listener
        )
        val playing = snapshot(track = track(7L), positionMs = 1_000L, queueSize = 500)

        controller.onPlaybackStateChanged(playing)
        idleMain()
        listener.selectedTab = MainRoutes.TAB_QUEUE
        controller.onPlaybackStateChanged(playing)
        idleMain()

        assertEquals(1, queueSource.calls)
        assertEquals(500, playbackViewModel.playback.value.queue.size)
    }

    private class CountingQueueSource(
        var queue: List<Track>
    ) : PlaybackStateEventController.QueueSnapshotSource {
        var calls = 0

        override fun queueSnapshot(): List<Track> {
            calls += 1
            return queue
        }
    }

    private class RecordingListener(
        var selectedTab: String = MainRoutes.TAB_QUEUE
    ) : PlaybackStateEventController.Listener {
        override fun selectedTab(): String = selectedTab

        override fun currentLyricsTrackId(): Long = -1L

        override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) = Unit

        override fun loadLyrics(track: Track?) = Unit

        override fun loadCollections() = Unit

        override fun renderNowBar() = Unit

        override fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot) = Unit

        override fun renderSelectedTab() = Unit

        override fun updateNowPlayingContent() = Unit

        override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) = Unit

        override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) = Unit

        override fun resolveCurrentStreamingTrackIfNeeded(): Boolean = false

        override fun setStatus(status: String) = Unit
    }

    private fun snapshot(
        track: Track,
        positionMs: Long,
        queueSize: Int
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            queueSize,
            positionMs,
            track.durationMs,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file:$id")

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
