package app.yukine.playback

import android.net.Uri
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackErrorRecoveryManager
import app.yukine.playback.manager.PlaybackMediaSourceProvider
import app.yukine.streaming.StreamingPlaybackHeaderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorRecoveryManagerTest {
    @Test
    fun retriesStreamingTrackOnceBeforeSkipping() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())
        val error = Exception("boom")

        manager.onPlayerError(error)
        scheduler.runPending()
        manager.onPlayerError(error)

        assertEquals(listOf("warn:Playback failed for 1", "warn:Retrying streaming track after error: 1"), actions.logs.take(2))
        assertTrue(actions.calls.contains("prepare"))
        assertTrue(actions.calls.contains("skip"))
    }

    @Test
    fun clearsRetryStateWhenPlaybackBecomesReady() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())
        val error = Exception("boom")

        manager.onPlayerError(error)
        manager.onPlaybackReady()
        manager.onPlayerError(error)

        assertEquals(1, scheduler.removedCallbacks)
        assertEquals(0, actions.calls.count { it == "prepare" })
        assertEquals(2, actions.logs.count { it.startsWith("warn:Retrying streaming track after error") })
    }

    @Test
    fun releaseCancelsPendingRetryAndPreventsLatePrepare() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())

        manager.onPlayerError(Exception("boom"))
        manager.release()
        scheduler.runPending()

        assertEquals(1, scheduler.removedCallbacks)
        assertEquals(0, actions.calls.count { it == "prepare" })
    }

    @Test
    fun releaseIsIdempotentAfterPendingRetryIsCancelled() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())

        manager.onPlayerError(Exception("boom"))
        manager.release()
        val removedAfterFirstRelease = scheduler.removedCallbacks
        manager.release()
        scheduler.runPending()

        assertEquals(1, removedAfterFirstRelease)
        assertEquals(removedAfterFirstRelease, scheduler.removedCallbacks)
        assertEquals(null, scheduler.pending)
        assertEquals(0, actions.calls.count { it == "prepare" })
    }

    @Test
    fun releasePreventsFutureErrorRecoveryActions() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())

        manager.release()
        manager.onPlayerError(Exception("boom"))
        manager.onPlaybackReady()
        scheduler.runPending()

        assertEquals(null, scheduler.pending)
        assertEquals(emptyList<String>(), actions.calls)
        assertEquals(emptyList<String>(), actions.logs)
    }

    @Test
    fun singleTrackErrorDoesNotSkipToNext() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "content://local/song.mp3"),
            canSkipFailedTrack = false
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, provider())

        manager.onPlayerError(Exception("boom"))

        assertTrue(actions.calls.contains("error:Unable to play this track."))
        assertTrue(actions.calls.contains("publish"))
        assertEquals(0, actions.calls.count { it == "skip" })
    }

    private class FakeScheduler : PlaybackErrorRecoveryManager.RetryScheduler {
        var pending: Runnable? = null
        var removedCallbacks = 0

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pending = runnable
        }

        override fun removeCallbacks(runnable: Runnable) {
            if (pending == runnable) {
                pending = null
            }
            removedCallbacks++
        }

        fun runPending() {
            val runnable = pending
            pending = null
            runnable?.run()
        }
    }

    private class FakeActions(
        private val track: Track?,
        private val canSkipFailedTrack: Boolean
    ) : PlaybackErrorRecoveryManager.Actions {
        val calls = mutableListOf<String>()
        val logs = mutableListOf<String>()

        override fun currentTrack(): Track? = track
        override fun canSkipFailedTrack(failed: Track?): Boolean = canSkipFailedTrack && failed == track
        override fun debugTrack(track: Track?): String = track?.id?.toString() ?: "null"
        override fun prepareCurrent(playWhenReady: Boolean) { calls.add("prepare") }
        override fun skipToNext() { calls.add("skip") }
        override fun setErrorMessage(message: String) { calls.add("error:$message") }
        override fun publishState() { calls.add("publish") }
        override fun logWarning(message: String, error: Exception) { logs.add("warn:$message") }
    }

    private companion object {
        fun provider(): PlaybackMediaSourceProvider {
            val context = RuntimeEnvironment.getApplication()
            return PlaybackMediaSourceProvider(
                context,
                MusicLibraryRepository(context, FakeStreamingDataPathParser),
                FakeStreamingPlaybackHeaderStore()
            )
        }

        fun track(id: Long, uri: String): Track {
            return Track(id, "t$id", "artist", "album", 1000L, Uri.parse(uri), uri)
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
