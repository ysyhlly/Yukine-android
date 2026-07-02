package app.yukine.playback

import android.net.Uri
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackPositionManager
import app.yukine.playback.manager.PlaybackQueueManager
import app.yukine.playback.manager.PlaybackQueueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.function.LongSupplier
import java.util.function.Supplier

class PlaybackPositionManagerTest {
    @Test
    fun restoredPositionIsClampedBeforeTrackEnd() {
        val manager = manager()
        val track = track(id = 7L, durationMs = 10000L)

        manager.setRestoredPosition(track.id, 9500L, explicit = true)

        assertEquals(8000L, manager.restoredPositionFor(track))
    }

    @Test
    fun implicitStreamingPositionIsIgnoredButExplicitPositionIsAllowed() {
        val manager = manager()
        val track = track(id = 8L, dataPath = "streaming:qq:8", durationMs = 10000L)

        manager.setRestoredPosition(track.id, 5000L, explicit = false)
        assertEquals(0L, manager.restoredPositionFor(track))

        manager.setRestoredPosition(track.id, 5000L, explicit = true)
        assertEquals(5000L, manager.restoredPositionFor(track))
    }

    @Test
    fun throttledPersistSkipsSmallRepeatWrites() {
        val store = FakeQueueStore()
        val state = FakeStateProvider(track = track(9L), positionMs = 1000L)
        val clock = MutableClock(10000L)
        val manager = PlaybackPositionManager(store, state, clock, saveIntervalMs = 5000L)

        manager.persistCurrentPosition(force = false)
        state.positionMs = 1200L
        clock.now = 11000L
        manager.persistCurrentPosition(force = false)

        assertEquals(listOf(9L to 1000L), store.savedPositions)
    }

    @Test
    fun forcedPersistWritesCurrentPosition() {
        val store = FakeQueueStore()
        val state = FakeStateProvider(track = track(10L), positionMs = 1000L)
        val clock = MutableClock(10000L)
        val manager = PlaybackPositionManager(store, state, clock, saveIntervalMs = 5000L)

        manager.persistCurrentPosition(force = false)
        state.positionMs = 1200L
        clock.now = 11000L
        manager.persistCurrentPosition(force = true)

        assertEquals(listOf(10L to 1000L, 10L to 1200L), store.savedPositions)
    }

    @Test
    fun positionMsReadsCurrentPlaybackPositionFromStateProvider() {
        val state = FakeStateProvider(track = track(11L), positionMs = 2400L)
        val manager = PlaybackPositionManager(FakeQueueStore(), state, MutableClock())

        assertEquals(2400L, manager.positionMs())
    }

    @Test
    fun stateProviderFromPlaybackStateDelegatesToPlaybackBoundarySuppliers() {
        val events = mutableListOf<String>()
        val track = track(23L)
        val provider = PlaybackPositionManager.stateProviderFromPlaybackState(
            Supplier {
                events += "queueState"
                PlaybackQueueManager.QueueStateSnapshot(track, 0, 1)
            },
            LongSupplier {
                events += "position"
                321L
            }
        )

        assertSame(track, provider.currentTrack())
        assertEquals(321L, provider.positionMs())
        assertEquals(listOf("queueState", "position"), events)
    }

    @Test
    fun stateProviderFromPlaybackStateHandlesMissingQueueState() {
        val missingProvider = PlaybackPositionManager.stateProviderFromPlaybackState(null, null)
        val nullSnapshotProvider = PlaybackPositionManager.stateProviderFromPlaybackState(
            Supplier { null },
            null
        )

        assertEquals(null, missingProvider.currentTrack())
        assertEquals(0L, missingProvider.positionMs())
        assertEquals(null, nullSnapshotProvider.currentTrack())
    }

    private fun manager(): PlaybackPositionManager {
        return PlaybackPositionManager(FakeQueueStore(), FakeStateProvider(), MutableClock())
    }

    private fun track(
        id: Long,
        durationMs: Long = 10000L,
        dataPath: String = "/music/$id"
    ): Track {
        return Track(id, "Track $id", "Artist", "Album", durationMs, Uri.EMPTY, dataPath)
    }

    private class MutableClock(var now: Long = 0L) : LongSupplier {
        override fun getAsLong(): Long = now
    }

    private class FakeStateProvider(
        var track: Track? = null,
        var positionMs: Long = 0L
    ) : PlaybackPositionManager.StateProvider {
        override fun currentTrack(): Track? = track
        override fun positionMs(): Long = positionMs
    }

    private class FakeQueueStore : PlaybackQueueStore {
        val savedPositions = mutableListOf<Pair<Long, Long>>()

        override fun load(): PlaybackQueueState = PlaybackQueueState(emptyList(), -1)
        override fun save(tracks: List<Track>, currentIndex: Int) {}
        override fun loadResumeRequested(): Boolean = false
        override fun saveResumeRequested(requested: Boolean) {}
        override fun loadPlaybackRestoreEnabled(): Boolean = true
        override fun savePlaybackRestoreEnabled(enabled: Boolean) {}
        override fun loadPlaybackPositionTrackId(): Long = -1L
        override fun loadPlaybackPositionMs(): Long = 0L
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
            savedPositions.add(trackId to positionMs)
        }
    }
}
