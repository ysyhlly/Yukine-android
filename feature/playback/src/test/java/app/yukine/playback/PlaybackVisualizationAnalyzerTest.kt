package app.yukine.playback

import android.content.Context
import android.net.Uri
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackMediaSourceProvider
import app.yukine.streaming.StreamingPlaybackHeaderStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackVisualizationAnalyzerTest {
    @Test
    fun releaseStopsFutureSpectrumTaskScheduling() {
        val scheduler = FakeVisualizationTaskScheduler()
        val analyzer = analyzer(scheduler, FakeStateProvider())

        analyzer.release()
        analyzer.spectrumSnapshot(localTrack(1L), 180_000L, deferGeneration = false)

        assertEquals(0, scheduler.tasks.size)
    }

    @Test
    fun emptyUriTrackDoesNotScheduleSpectrumTask() {
        val scheduler = FakeVisualizationTaskScheduler()
        val analyzer = analyzer(scheduler, FakeStateProvider())

        analyzer.spectrumSnapshot(localTrack(3L, Uri.EMPTY), 180_000L, deferGeneration = false)

        assertEquals(0, scheduler.tasks.size)
    }

    @Test
    fun localTrackDoesNotScheduleStreamingWaveformTask() {
        val scheduler = FakeVisualizationTaskScheduler()
        val analyzer = analyzer(scheduler, FakeStateProvider())

        analyzer.waveformSnapshot(localTrack(4L), 180_000L, deferGeneration = false)

        assertEquals(0, scheduler.tasks.size)
    }

    @Test
    fun releaseBeforeScheduledTaskPreventsPublishState() {
        val scheduler = FakeVisualizationTaskScheduler()
        val stateProvider = FakeStateProvider()
        val analyzer = analyzer(scheduler, stateProvider)

        analyzer.spectrumSnapshot(localTrack(2L), 180_000L, deferGeneration = false)
        analyzer.release()
        scheduler.tasks.single().run()

        assertEquals(0, stateProvider.publishCalls)
    }

    private fun analyzer(
        scheduler: FakeVisualizationTaskScheduler,
        stateProvider: FakeStateProvider
    ): PlaybackVisualizationAnalyzer {
        val context = RuntimeEnvironment.getApplication()
        return PlaybackVisualizationAnalyzer(
            context,
            scheduler,
            stateProvider,
            mediaSourceProvider(context)
        )
    }

    private fun mediaSourceProvider(context: Context): PlaybackMediaSourceProvider {
        return PlaybackMediaSourceProvider(
            context,
            MusicLibraryRepository(context, FakeStreamingDataPathParser()),
            FakeStreamingPlaybackHeaderStore()
        )
    }

    private fun localTrack(id: Long): Track {
        return localTrack(id, Uri.parse("content://media/external/audio/media/$id"))
    }

    private fun localTrack(id: Long, uri: Uri): Track {
        return Track(
            id,
            "Track $id",
            "Artist",
            "Album",
            180_000L,
            uri,
            "local-$id"
        )
    }

    private class FakeVisualizationTaskScheduler : PlaybackVisualizationAnalyzer.VisualizationTaskScheduler {
        val tasks = mutableListOf<Runnable>()

        override fun schedule(priority: PlaybackTaskScheduler.Priority, task: Runnable) {
            tasks.add(task)
        }
    }

    private class FakeStateProvider : PlaybackVisualizationAnalyzer.StateProvider {
        var publishCalls = 0

        override fun isAppVisible(): Boolean = true

        override fun bufferedProgress(durationMs: Long): Float = 1.0f

        override fun publishState() {
            publishCalls++
        }
    }

    private class FakeStreamingDataPathParser : StreamingDataPathParser {
        override fun isStreamingTrack(dataPath: String): Boolean {
            return dataPath.startsWith("streaming:")
        }

        override fun providerName(dataPath: String): String = "test"

        override fun providerTrackId(dataPath: String): String {
            return dataPath.substringAfterLast(':')
        }
    }

    private class FakeStreamingPlaybackHeaderStore : StreamingPlaybackHeaderStore {
        override fun register(dataPath: String, headers: Map<String, String>) {
        }

        override fun forDataPath(dataPath: String?): MutableMap<String, String> {
            return mutableMapOf()
        }

        override fun restoreForDataPath(dataPath: String?): Boolean = false

        override fun restoredTrackFor(track: Track?): Track? = null
    }
}
