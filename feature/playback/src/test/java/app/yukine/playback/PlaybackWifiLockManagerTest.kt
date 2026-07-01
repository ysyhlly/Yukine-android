package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
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
        val provider = FakeTrackProvider(track("https://example.com/song.mp3"))
        val manager = PlaybackWifiLockManager(lock, provider, httpTrackPredicate())

        manager.acquireIfStreaming()

        assertEquals(1, lock.acquireCalls)
        assertEquals(true, lock.held)
    }

    @Test
    fun acquireIfStreamingSkipsNonStreamingOrUnavailableLock() {
        val fileLock = FakeLock()
        PlaybackWifiLockManager(fileLock, FakeTrackProvider(track("file:///music/song.mp3")), httpTrackPredicate())
            .acquireIfStreaming()

        val noTrackLock = FakeLock()
        PlaybackWifiLockManager(noTrackLock, FakeTrackProvider(null), httpTrackPredicate())
            .acquireIfStreaming()

        PlaybackWifiLockManager(null, FakeTrackProvider(track("https://example.com/song.mp3")), httpTrackPredicate())
            .acquireIfStreaming()

        assertEquals(0, fileLock.acquireCalls)
        assertEquals(0, noTrackLock.acquireCalls)
    }

    @Test
    fun acquireIfStreamingDoesNotAcquireTwiceWhenHeld() {
        val lock = FakeLock(held = true)
        val manager = PlaybackWifiLockManager(lock, FakeTrackProvider(track("https://example.com/song.mp3")), httpTrackPredicate())

        manager.acquireIfStreaming()

        assertEquals(0, lock.acquireCalls)
    }

    @Test
    fun releaseOnlyReleasesHeldLock() {
        val heldLock = FakeLock(held = true)
        PlaybackWifiLockManager(heldLock, FakeTrackProvider(null), httpTrackPredicate()).release()

        val releasedLock = FakeLock(held = false)
        PlaybackWifiLockManager(releasedLock, FakeTrackProvider(null), httpTrackPredicate()).release()

        assertEquals(1, heldLock.releaseCalls)
        assertEquals(false, heldLock.held)
        assertEquals(0, releasedLock.releaseCalls)
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

    private class FakeTrackProvider(
        private val track: Track?
    ) : PlaybackWifiLockManager.StreamingTrackProvider {
        override fun currentTrack(): Track? = track
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
    }

}
