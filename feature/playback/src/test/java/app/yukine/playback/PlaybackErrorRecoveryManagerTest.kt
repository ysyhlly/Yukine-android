package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackErrorRecoveryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.function.Predicate

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorRecoveryManagerTest {
    @Test
    fun serviceUrlRefreshPreemptsRetryingTheExpiredUrl() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/expired.mp3"),
            canSkipFailedTrack = true,
            refreshStreamingTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

        manager.onPlayerError(Exception("expired url"))
        scheduler.runPending()

        assertEquals(listOf("refresh"), actions.calls)
        assertEquals(null, scheduler.pending)
        assertTrue(actions.logs.any { it.startsWith("warn:Refreshing expired streaming URL") })
    }

    @Test
    fun retriesStreamingTrackOnceBeforeSkipping() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "https://example.com/song.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())
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
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())
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
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

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
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

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
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

        manager.release()
        manager.onPlayerError(Exception("boom"))
        manager.onPlaybackReady()
        scheduler.runPending()

        assertEquals(null, scheduler.pending)
        assertEquals(emptyList<String>(), actions.calls)
        assertEquals(emptyList<String>(), actions.logs)
    }

    @Test
    fun invalidLocalTrackSkipsToNextWhenQueueCanContinue() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(2L, "content://local/missing.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

        manager.onPlayerError(Exception("missing file"))
        scheduler.runPending()

        assertEquals(
            listOf(
                "warn:Playback failed for 2",
                "warn:Skipping unplayable track: 2"
            ),
            actions.logs
        )
        assertEquals(listOf("error:", "skip"), actions.calls)
        assertEquals(null, scheduler.pending)
    }

    @Test
    fun repeatedStreamingErrorBeforeRetryCancelsStaleRetryBeforeSkipping() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(3L, "https://example.com/expired.mp3"),
            canSkipFailedTrack = true
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())
        val error = Exception("expired url")

        manager.onPlayerError(error)
        manager.onPlayerError(error)
        scheduler.runPending()

        assertEquals(1, scheduler.removedCallbacks)
        assertEquals(null, scheduler.pending)
        assertEquals(0, actions.calls.count { it == "prepare" })
        assertEquals(listOf("error:", "skip"), actions.calls)
    }

    @Test
    fun singleTrackErrorDoesNotSkipToNext() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(
            track = track(1L, "content://local/song.mp3"),
            canSkipFailedTrack = false
        )
        val manager = PlaybackErrorRecoveryManager(scheduler, actions, httpTrackPredicate())

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
        private val canSkipFailedTrack: Boolean,
        private val refreshStreamingTrack: Boolean = false
    ) : PlaybackErrorRecoveryManager.Actions {
        val calls = mutableListOf<String>()
        val logs = mutableListOf<String>()

        override fun currentTrack(): Track? = track
        override fun canSkipFailedTrack(failed: Track?): Boolean = canSkipFailedTrack && failed == track
        override fun refreshStreamingTrack(failed: Track?): Boolean {
            if (refreshStreamingTrack) {
                calls += "refresh"
            }
            return refreshStreamingTrack
        }
        override fun debugTrack(track: Track?): String = track?.id?.toString() ?: "null"
        override fun prepareCurrent(playWhenReady: Boolean) { calls.add("prepare") }
        override fun skipToNext() { calls.add("skip") }
        override fun setErrorMessage(message: String) { calls.add("error:$message") }
        override fun publishState() { calls.add("publish") }
        override fun logWarning(message: String, error: Exception) { logs.add("warn:$message") }
    }

    private companion object {
        fun httpTrackPredicate(): Predicate<Track?> {
            return Predicate { track ->
                val scheme = track?.contentUri?.scheme
                scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
            }
        }

        fun track(id: Long, uri: String): Track {
            return Track(id, "t$id", "artist", "album", 1000L, Uri.parse(uri), uri)
        }
    }

}
