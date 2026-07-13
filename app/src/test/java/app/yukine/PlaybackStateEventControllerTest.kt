package app.yukine

import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackStateEventControllerTest {
    @Test
    fun busyMainHandlerKeepsOnlyTheLatestPlaybackSnapshot() {
        val listener = RecordingListener()
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            listener
        )
        val current = track(7L)

        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 1_000L, queueSize = 1))
        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 2_000L, queueSize = 1))
        controller.onPlaybackStateChanged(snapshot(track = current, positionMs = 3_000L, queueSize = 1))
        idleMain()

        assertEquals(listOf(3_000L), listener.preResolvedPositions)
    }

    @Test
    fun coalescedTrackTransitionStillUsesTheLatestTrackForDomainEffects() {
        val listener = RecordingListener()
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            listener
        )

        controller.onPlaybackStateChanged(snapshot(track = track(1L), positionMs = 1_000L, queueSize = 2))
        controller.onPlaybackStateChanged(snapshot(track = track(2L), positionMs = 0L, queueSize = 2))
        idleMain()

        assertEquals(listOf(2L), listener.loadedLyricsTrackIds)
        assertEquals(listOf(2L), listener.preResolvedTrackIds)
    }

    @Test
    fun resolvedStreamingPlaybackErrorRefreshesUrlAndSuppressesStaleError() {
        val listener = RecordingListener(resolveStreamingResult = true)
        val controller = PlaybackStateEventController(
            Handler(Looper.getMainLooper()),
            listener
        )
        val streaming = Track(
            77L,
            "Streaming",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://expired.example.test/song.mp3"),
            "streaming:netease:song-77"
        )

        controller.onPlaybackStateChanged(
            snapshot(streaming, 10_000L, 1, errorMessage = "Unable to play this track.")
        )
        idleMain()

        assertEquals(1, listener.streamingResolveCalls)
        assertEquals(emptyList<String>(), listener.statuses)
    }

    private class RecordingListener(
        private val resolveStreamingResult: Boolean = false
    ) : PlaybackStateEventController.Listener {
        val preResolvedPositions = mutableListOf<Long>()
        val loadedLyricsTrackIds = mutableListOf<Long>()
        val preResolvedTrackIds = mutableListOf<Long>()
        var streamingResolveCalls = 0
        val statuses = mutableListOf<String>()

        override fun currentLyricsTrackId(): Long = -1L

        override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) = Unit

        override fun loadLyrics(track: Track?) {
            track?.let { loadedLyricsTrackIds += it.id }
        }

        override fun loadCollections() = Unit

        override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) {
            preResolvedPositions += snapshot.positionMs
            snapshot.currentTrack?.let { preResolvedTrackIds += it.id }
        }

        override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) = Unit

        override fun resolveCurrentStreamingTrackIfNeeded(): Boolean {
            streamingResolveCalls += 1
            return resolveStreamingResult
        }

        override fun setStatus(status: String) {
            statuses += status
        }
    }

    private fun snapshot(
        track: Track,
        positionMs: Long,
        queueSize: Int,
        queueRevision: Long = 0L,
        errorMessage: String = ""
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            0,
            queueSize,
            positionMs,
            track.durationMs,
            true,
            false,
            errorMessage,
            false,
            0,
            1.0f,
            1.0f,
            0L,
            queueRevision
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file:$id")

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
