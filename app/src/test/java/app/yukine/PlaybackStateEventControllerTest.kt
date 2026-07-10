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
    fun queueContentRevisionRepublishesSameSizedQueueWithoutProgressChurn() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val queueSource = CountingQueueSource(listOf(track(1L), track(2L)))
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            queueSource,
            RecordingListener()
        )

        controller.onPlaybackStateChanged(
            snapshot(track = track(1L), positionMs = 1_000L, queueSize = 2, queueRevision = 4L)
        )
        idleMain()
        queueSource.queue = listOf(track(1L), track(22L))
        controller.onPlaybackStateChanged(
            snapshot(track = track(1L), positionMs = 2_000L, queueSize = 2, queueRevision = 5L)
        )
        idleMain()

        assertEquals(2, queueSource.calls)
        assertEquals(22L, playbackViewModel.playback.value.queue.last().id)
    }

    @Test
    fun publishedQueueIsReusedOnlyForTheMatchingQueueRevision() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val current = track(1L)
        val first = snapshot(current, positionMs = 1_000L, queueSize = 2, queueRevision = 9L)
        playbackStore.replaceSnapshot(first)
        playbackStore.publish(listOf(current, track(2L)))

        assertEquals(listOf(1L, 2L), playbackStore.publishedQueueFor(first)?.map { it.id })
        assertEquals(
            null,
            playbackStore.publishedQueueFor(
                snapshot(current, positionMs = 2_000L, queueSize = 2, queueRevision = 10L)
            )
        )
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

    @Test
    fun busyMainHandlerKeepsOnlyTheLatestPlaybackSnapshot() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val listener = RecordingListener()
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            CountingQueueSource(emptyList()),
            listener
        )
        val current = track(7L)

        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 1_000L, queueSize = 1))
        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 2_000L, queueSize = 1))
        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 3_000L, queueSize = 1))
        idleMain()

        assertEquals(3_000L, playbackStore.snapshot()?.positionMs)
        assertEquals(1, listener.nowBarUpdates)
        assertEquals(listOf(3_000L), listener.homePlaybackPositions)
        assertEquals(listOf(3_000L), listener.preResolvedPositions)
    }

    @Test
    fun coalescedTrackTransitionStillUsesTheLatestTrackForUiEffects() {
        val playbackViewModel = PlaybackViewModel()
        val playbackStore = MainPlaybackStore(playbackViewModel)
        val listener = RecordingListener()
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            playbackStore,
            CountingQueueSource(listOf(track(1L), track(2L))),
            listener
        )

        controller.onPlaybackStateChanged(snapshot(track = track(1L), positionMs = 1_000L, queueSize = 2))
        controller.onPlaybackStateChanged(snapshot(track = track(2L), positionMs = 0L, queueSize = 2))
        idleMain()

        assertEquals(2L, playbackStore.snapshot()?.currentTrack?.id)
        assertEquals(listOf(2L), listener.loadedLyricsTrackIds)
        assertEquals(listOf(2L), listener.homeTrackIds)
        assertEquals(listOf(2L), listener.preResolvedTrackIds)
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
        var nowBarUpdates = 0
        val homePlaybackPositions = mutableListOf<Long>()
        val preResolvedPositions = mutableListOf<Long>()
        val loadedLyricsTrackIds = mutableListOf<Long>()
        val homeTrackIds = mutableListOf<Long>()
        val preResolvedTrackIds = mutableListOf<Long>()

        override fun selectedTab(): String = selectedTab

        override fun currentLyricsTrackId(): Long = -1L

        override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) = Unit

        override fun loadLyrics(track: Track?) {
            track?.let { loadedLyricsTrackIds += it.id }
        }

        override fun loadCollections() = Unit

        override fun renderNowBar() {
            nowBarUpdates += 1
        }

        override fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot) {
            homePlaybackPositions += snapshot.positionMs
            snapshot.currentTrack?.let { homeTrackIds += it.id }
        }

        override fun renderSelectedTab() = Unit

        override fun updateNowPlayingContent() = Unit

        override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) {
            preResolvedPositions += snapshot.positionMs
            snapshot.currentTrack?.let { preResolvedTrackIds += it.id }
        }

        override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) = Unit

        override fun resolveCurrentStreamingTrackIfNeeded(): Boolean = false

        override fun setStatus(status: String) = Unit
    }

    private fun snapshot(
        track: Track,
        positionMs: Long,
        queueSize: Int,
        queueRevision: Long = 0L
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
            0L,
            queueRevision
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file:$id")

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
