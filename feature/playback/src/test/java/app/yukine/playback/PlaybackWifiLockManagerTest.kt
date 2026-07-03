package app.yukine.playback

import android.net.Uri
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackQueueManager
import app.yukine.playback.manager.PlaybackQueueStore
import app.yukine.playback.manager.PlaybackWifiLockManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.function.Predicate

@RunWith(RobolectricTestRunner::class)
class PlaybackWifiLockManagerTest {
    @Test
    fun acquireIfStreamingAcquiresForHttpCurrentTrack() {
        val lock = FakeLock()
        val manager = PlaybackWifiLockManager(
            lock,
            queueManager(track("https://example.com/song.mp3")),
            httpTrackPredicate()
        )

        manager.acquireIfStreaming()

        assertEquals(1, lock.acquireCalls)
        assertEquals(true, lock.held)
    }

    @Test
    fun acquireIfStreamingSkipsNonStreamingOrUnavailableLock() {
        val fileLock = FakeLock()
        PlaybackWifiLockManager(fileLock, queueManager(track("file:///music/song.mp3")), httpTrackPredicate())
            .acquireIfStreaming()

        val noTrackLock = FakeLock()
        PlaybackWifiLockManager(noTrackLock, queueManager(null), httpTrackPredicate())
            .acquireIfStreaming()

        PlaybackWifiLockManager(null, queueManager(track("https://example.com/song.mp3")), httpTrackPredicate())
            .acquireIfStreaming()

        assertEquals(0, fileLock.acquireCalls)
        assertEquals(0, noTrackLock.acquireCalls)
    }

    @Test
    fun acquireIfStreamingDoesNotAcquireTwiceWhenHeld() {
        val lock = FakeLock(held = true)
        val manager = PlaybackWifiLockManager(
            lock,
            queueManager(track("https://example.com/song.mp3")),
            httpTrackPredicate()
        )

        manager.acquireIfStreaming()

        assertEquals(0, lock.acquireCalls)
    }

    @Test
    fun acquireIfStreamingSkipsMissingQueueStateProvider() {
        val lock = FakeLock()
        PlaybackWifiLockManager(lock, null, httpTrackPredicate()).acquireIfStreaming()

        assertEquals(0, lock.acquireCalls)
    }

    @Test
    fun releaseOnlyReleasesHeldLock() {
        val heldLock = FakeLock(held = true)
        PlaybackWifiLockManager(heldLock, queueManager(null), httpTrackPredicate()).release()

        val releasedLock = FakeLock(held = false)
        PlaybackWifiLockManager(releasedLock, queueManager(null), httpTrackPredicate()).release()

        assertEquals(1, heldLock.releaseCalls)
        assertEquals(false, heldLock.held)
        assertEquals(0, releasedLock.releaseCalls)
    }

    @Test
    fun acquireIfStreamingActionUsesLatestManagerAndIgnoresMissingManager() {
        val lock = FakeLock()
        var manager: PlaybackWifiLockManager? = null
        val action = PlaybackWifiLockManager.acquireIfStreamingAction { manager }

        action.run()
        manager = PlaybackWifiLockManager(
            lock,
            queueManager(track("https://example.com/song.mp3")),
            httpTrackPredicate()
        )
        action.run()

        assertEquals(1, lock.acquireCalls)
        assertEquals(true, lock.held)
    }

    @Test
    fun releaseActionUsesLatestManagerAndIgnoresMissingManager() {
        val lock = FakeLock(held = true)
        var manager: PlaybackWifiLockManager? = null
        val action = PlaybackWifiLockManager.releaseAction { manager }

        action.run()
        manager = PlaybackWifiLockManager(lock, queueManager(null), httpTrackPredicate())
        action.run()

        assertEquals(1, lock.releaseCalls)
        assertEquals(false, lock.held)
    }

    private class FakeLock(var held: Boolean = false) : PlaybackWifiLockManager.Lock {
        var acquireCalls = 0
        var releaseCalls = 0

        override fun isHeld(): Boolean = held

        override fun acquire() {
            acquireCalls += 1
            held = true
        }

        override fun release() {
            releaseCalls += 1
            held = false
        }
    }

    private companion object {
        fun httpTrackPredicate(): Predicate<Track?> {
            return Predicate { track ->
                val scheme = track?.contentUri?.scheme
                scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
            }
        }

        fun track(uri: String): Track {
            return Track(1L, "Track", "Artist", "Album", 180000L, Uri.parse(uri), uri)
        }

        fun queueManager(track: Track?): PlaybackQueueManager {
            val manager = PlaybackQueueManager(
                FakeQueueStore(),
                NoopQueuePlaybackActions,
                null,
                NoopStreamingRestoreProvider,
                NoopMirroredQueuePlayer,
                null,
                null
            )
            if (track != null) {
                manager.playQueue(listOf(track), 0, 0L)
            }
            return manager
        }
    }

    private class FakeQueueStore : PlaybackQueueStore {
        override fun load(): PlaybackQueueState = PlaybackQueueState(emptyList(), -1)
        override fun save(tracks: List<Track>, currentIndex: Int) {}
        override fun loadResumeRequested(): Boolean = false
        override fun saveResumeRequested(requested: Boolean) {}
        override fun loadPlaybackRestoreEnabled(): Boolean = true
        override fun savePlaybackRestoreEnabled(enabled: Boolean) {}
        override fun loadPlaybackPositionTrackId(): Long = -1L
        override fun loadPlaybackPositionMs(): Long = 0L
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {}
    }

    private object NoopQueuePlaybackActions : PlaybackQueueManager.QueuePlaybackActions {
        override fun prepareCurrent(playWhenReady: Boolean) {}
        override fun publishState() {}
    }

    private object NoopStreamingRestoreProvider : PlaybackQueueManager.StreamingRestoreProvider {
        override fun restoreTrackForPlayback(track: Track): Track = track
    }

    private object NoopMirroredQueuePlayer : PlaybackQueueManager.MirroredQueuePlayer {
        override fun matchesCurrentQueue(): Boolean = false
        override fun seekTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean = false
    }

}
