package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackWifiLockManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWifiLockManagerTest {
    @Test
    fun acquireIfStreamingAcquiresForHttpCurrentTrack() {
        val lock = FakeLock()
        val provider = FakeTrackProvider(track(), httpUri = true)
        val manager = PlaybackWifiLockManager(lock, provider)

        manager.acquireIfStreaming()

        assertEquals(1, lock.acquireCalls)
        assertEquals(true, lock.held)
    }

    @Test
    fun acquireIfStreamingSkipsNonStreamingOrUnavailableLock() {
        val fileLock = FakeLock()
        PlaybackWifiLockManager(fileLock, FakeTrackProvider(track(), httpUri = false))
            .acquireIfStreaming()

        val noTrackLock = FakeLock()
        PlaybackWifiLockManager(noTrackLock, FakeTrackProvider(null))
            .acquireIfStreaming()

        PlaybackWifiLockManager(null, FakeTrackProvider(track(), httpUri = true))
            .acquireIfStreaming()

        assertEquals(0, fileLock.acquireCalls)
        assertEquals(0, noTrackLock.acquireCalls)
    }

    @Test
    fun acquireIfStreamingDoesNotAcquireTwiceWhenHeld() {
        val lock = FakeLock(held = true)
        val manager = PlaybackWifiLockManager(lock, FakeTrackProvider(track(), httpUri = true))

        manager.acquireIfStreaming()

        assertEquals(0, lock.acquireCalls)
    }

    @Test
    fun releaseOnlyReleasesHeldLock() {
        val heldLock = FakeLock(held = true)
        PlaybackWifiLockManager(heldLock, FakeTrackProvider(null)).release()

        val releasedLock = FakeLock(held = false)
        PlaybackWifiLockManager(releasedLock, FakeTrackProvider(null)).release()

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
        private val track: Track?,
        private val httpUri: Boolean = false
    ) : PlaybackWifiLockManager.StreamingTrackProvider {
        override fun currentTrack(): Track? = track

        override fun isHttpUri(uri: Uri?): Boolean = httpUri
    }

    private companion object {
        fun track(): Track {
            return Track(1L, "Track", "Artist", "Album", 180000L, Uri.EMPTY, "track")
        }
    }
}
