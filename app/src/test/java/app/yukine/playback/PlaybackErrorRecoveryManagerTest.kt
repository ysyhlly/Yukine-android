package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackErrorRecoveryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorRecoveryManagerTest {
    @Test
    fun retriesStreamingTrackOnceBeforeSkipping() {
        val scheduler = FakeScheduler()
        val actions = FakeActions(track = track(1L, "https://example.com/song.mp3"), queueSize = 2)
        val manager = PlaybackErrorRecoveryManager(scheduler, actions)
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
        val actions = FakeActions(track = track(1L, "https://example.com/song.mp3"), queueSize = 2)
        val manager = PlaybackErrorRecoveryManager(scheduler, actions)
        val error = Exception("boom")

        manager.onPlayerError(error)
        manager.onPlaybackReady()
        manager.onPlayerError(error)

        assertEquals(0, actions.calls.count { it == "prepare" })
        assertEquals(2, actions.logs.count { it.startsWith("warn:Retrying streaming track after error") })
    }

    private class FakeScheduler : PlaybackErrorRecoveryManager.RetryScheduler {
        var pending: Runnable? = null

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pending = runnable
        }

        fun runPending() {
            val runnable = pending
            pending = null
            runnable?.run()
        }
    }

    private class FakeActions(
        private val track: Track?,
        private val queueSize: Int
    ) : PlaybackErrorRecoveryManager.Actions {
        val calls = mutableListOf<String>()
        val logs = mutableListOf<String>()

        override fun currentTrack(): Track? = track
        override fun isHttpUri(uri: Uri?): Boolean = true
        override fun queueSize(): Int = queueSize
        override fun debugTrack(track: Track?): String = track?.id?.toString() ?: "null"
        override fun prepareCurrent(playWhenReady: Boolean) { calls.add("prepare") }
        override fun skipToNext() { calls.add("skip") }
        override fun setErrorMessage(message: String) { calls.add("error:$message") }
        override fun publishState() { calls.add("publish") }
        override fun logWarning(message: String, error: Exception) { logs.add("warn:$message") }
    }

    private companion object {
        fun track(id: Long, uri: String): Track {
            return Track(id, "t$id", "artist", "album", 1000L, Uri.parse(uri), uri)
        }
    }
}
