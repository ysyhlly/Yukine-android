package app.yukine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackHeaderStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackDownloadManagerTest {
    @Test
    fun directoryChangesEmitAControllerChangeNotification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("track_downloads", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = TrackDownloadManager(context, FakeStreamingPlaybackHeaderStore())
        val notification = async { manager.changes.first() }
        yield()

        manager.setDownloadDirectory(DOWNLOAD_DIRECTORY_DOWNLOADS)

        withTimeout(1_000L) { notification.await() }
        manager.shutdownNow()
    }

    @Test
    fun shutdownStopsExecutorsAndRejectsNewAppManagedDownloads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("track_downloads", Context.MODE_PRIVATE).edit().clear().commit()
        val customExecutor = Executors.newSingleThreadExecutor()
        val segmentExecutor = Executors.newSingleThreadExecutor()
        val manager = TrackDownloadManager(
            context,
            FakeStreamingPlaybackHeaderStore(),
            customExecutor,
            segmentExecutor
        )

        manager.shutdownNow()
        val result = manager.enqueue(
            track(),
            StreamingAudioQuality.HIGH
        )

        assertFalse(result.started)
        assertTrue(customExecutor.isShutdown)
        assertTrue(segmentExecutor.isShutdown)
        assertTrue(manager.snapshot().isEmpty())
    }

    private class FakeStreamingPlaybackHeaderStore : StreamingPlaybackHeaderStore {
        override fun register(dataPath: String, headers: Map<String, String>) {
        }

        override fun forDataPath(dataPath: String?): Map<String, String> = emptyMap()

        override fun restoreForDataPath(dataPath: String?): Boolean = false

        override fun restoredTrackFor(track: Track?): Track? = null
    }

    private fun track(): Track =
        Track(
            1L,
            "Cloud Track",
            "Artist",
            "Album",
            1000L,
            Uri.parse("https://example.test/cloud.mp3"),
            "streaming:netease:1"
        )
}
