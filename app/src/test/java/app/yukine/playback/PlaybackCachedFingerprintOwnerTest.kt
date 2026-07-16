package app.yukine.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.yukine.model.Track
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackCachedFingerprintOwnerTest {
    @Test
    fun schedulesOnlyCurrentWebDavTrackAfterCacheSettleDelay() {
        val selected = track(1L, "webdav:9:/song.flac", "https://dav.example/song.flac")
        var current: Track? = selected
        val scheduled = mutableListOf<Runnable>()
        val analyzed = AtomicInteger()
        val owner = PlaybackCachedFingerprintOwner(
            Handler(Looper.getMainLooper()),
            PlaybackCachedFingerprintOwner.CurrentTrackProvider { current },
            PlaybackCachedFingerprintOwner.TaskScheduler { scheduled += it },
            PlaybackCachedFingerprintOwner.CachedFingerprintAnalyzer {
                analyzed.incrementAndGet()
                true
            },
            true
        )

        owner.schedule(selected)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(11_999L))
        assertEquals(0, scheduled.size)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1L))
        assertEquals(1, scheduled.size)
        scheduled.single().run()
        assertEquals(1, analyzed.get())

        current = track(2L, "webdav:9:/other.flac", "https://dav.example/other.flac")
        owner.schedule(selected)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12L))
        assertEquals(1, scheduled.size)
        owner.release()
    }

    @Test
    fun ignoresLocalAndStreamingTracks() {
        val scheduled = AtomicInteger()
        val owner = PlaybackCachedFingerprintOwner(
            Handler(Looper.getMainLooper()),
            PlaybackCachedFingerprintOwner.CurrentTrackProvider { null },
            PlaybackCachedFingerprintOwner.TaskScheduler { scheduled.incrementAndGet() },
            PlaybackCachedFingerprintOwner.CachedFingerprintAnalyzer { true },
            true
        )

        owner.schedule(track(3L, "/music/local.flac", "file:///music/local.flac"))
        owner.schedule(track(4L, "streaming:netease:4", "https://audio.example/4.flac"))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20L))

        assertEquals(0, scheduled.get())
        owner.release()
    }

    private fun track(id: Long, dataPath: String, uri: String) = Track(
        id,
        "Song $id",
        "Artist",
        "Album",
        180_000L,
        Uri.parse(uri),
        dataPath
    )
}
