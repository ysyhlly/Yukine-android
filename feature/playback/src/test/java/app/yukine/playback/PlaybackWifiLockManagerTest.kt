package app.yukine.playback

import android.net.Uri
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackWifiLockManager
import app.yukine.playback.manager.PlaybackMediaSourceProvider
import app.yukine.streaming.StreamingPlaybackHeaderStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackWifiLockManagerTest {
    @Test
    fun acquireIfStreamingAcquiresForHttpCurrentTrack() {
        val lock = FakeLock()
        val provider = FakeTrackProvider(track("https://example.com/song.mp3"))
        val manager = PlaybackWifiLockManager(lock, provider, mediaSourceProvider())

        manager.acquireIfStreaming()

        assertEquals(1, lock.acquireCalls)
        assertEquals(true, lock.held)
    }

    @Test
    fun acquireIfStreamingSkipsNonStreamingOrUnavailableLock() {
        val fileLock = FakeLock()
        PlaybackWifiLockManager(fileLock, FakeTrackProvider(track("file:///music/song.mp3")), mediaSourceProvider())
            .acquireIfStreaming()

        val noTrackLock = FakeLock()
        PlaybackWifiLockManager(noTrackLock, FakeTrackProvider(null), mediaSourceProvider())
            .acquireIfStreaming()

        PlaybackWifiLockManager(null, FakeTrackProvider(track("https://example.com/song.mp3")), mediaSourceProvider())
            .acquireIfStreaming()

        assertEquals(0, fileLock.acquireCalls)
        assertEquals(0, noTrackLock.acquireCalls)
    }

    @Test
    fun acquireIfStreamingDoesNotAcquireTwiceWhenHeld() {
        val lock = FakeLock(held = true)
        val manager = PlaybackWifiLockManager(lock, FakeTrackProvider(track("https://example.com/song.mp3")), mediaSourceProvider())

        manager.acquireIfStreaming()

        assertEquals(0, lock.acquireCalls)
    }

    @Test
    fun releaseOnlyReleasesHeldLock() {
        val heldLock = FakeLock(held = true)
        PlaybackWifiLockManager(heldLock, FakeTrackProvider(null), mediaSourceProvider()).release()

        val releasedLock = FakeLock(held = false)
        PlaybackWifiLockManager(releasedLock, FakeTrackProvider(null), mediaSourceProvider()).release()

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
        fun mediaSourceProvider(): PlaybackMediaSourceProvider {
            val context = RuntimeEnvironment.getApplication()
            return PlaybackMediaSourceProvider(
                context,
                MusicLibraryRepository(context, FakeStreamingDataPathParser),
                FakeStreamingPlaybackHeaderStore()
            )
        }

        fun track(uri: String): Track {
            return Track(1L, "Track", "Artist", "Album", 180000L, Uri.parse(uri), uri)
        }
    }

    private object FakeStreamingDataPathParser : StreamingDataPathParser {
        override fun isStreamingTrack(dataPath: String): Boolean = dataPath.startsWith("streaming:")
        override fun providerName(dataPath: String): String? = dataPath.substringAfter("streaming:", "").substringBefore(":")
        override fun providerTrackId(dataPath: String): String = dataPath.substringAfterLast(":")
    }

    private class FakeStreamingPlaybackHeaderStore : StreamingPlaybackHeaderStore {
        override fun register(dataPath: String, headers: Map<String, String>) = Unit
        override fun forDataPath(dataPath: String?): Map<String, String> = emptyMap()
        override fun restoreForDataPath(dataPath: String?): Boolean = true
        override fun restoredTrackFor(track: Track?): Track? = null
    }
}
