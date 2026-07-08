package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStartControllerTest {
    @Test
    fun storesPendingPlaybackWhenServiceIsUnavailable() {
        val listener = FakeListener(hasService = false)
        val controller = listener.controller()

        controller.playTrackList(listOf(playbackStartTrack(1L)), 1)

        assertEquals(
            listOf("stopHeartbeat", "startService", "save:1:1", "status:Resolving"),
            listener.calls
        )
    }

    @Test
    fun playsImmediatelyWhenServiceIsAvailableAndStreamingDoesNotIntercept() {
        val listener = FakeListener(hasService = true, streamResolved = false)
        val controller = listener.controller()

        controller.playTrackList(listOf(playbackStartTrack(1L), playbackStartTrack(2L)), 1)

        assertEquals(
            listOf("stopHeartbeat", "startService", "resolve:2:1", "play:2:1", "result:played"),
            listener.calls
        )
    }

    @Test
    fun skipsLocalPlayWhenStreamingResolverSchedulesPlayback() {
        val listener = FakeListener(hasService = true, streamResolved = true)
        val controller = listener.controller()

        controller.playTrackList(listOf(playbackStartTrack(1L)), 0)

        assertEquals(
            listOf("stopHeartbeat", "startService", "resolve:1:0"),
            listener.calls
        )
    }

    @Test
    fun replaysPendingTracksAfterServiceConnectsWithoutStoppingHeartbeatModeAgain() {
        val listener = FakeListener(hasService = true, streamResolved = false)
        listener.pendingTracks = listOf(playbackStartTrack(3L))
        listener.pendingIndex = 0
        val controller = listener.controller()

        controller.playPendingTracksIfNeeded()

        assertEquals(
            listOf("clear", "startService", "resolve:1:0", "play:1:0", "result:played"),
            listener.calls
        )
    }

    @Test
    fun playsRecommendationPresentationWithoutForcingQueueOpen() {
        val listener = FakeListener(hasService = true, streamResolved = false)
        val controller = listener.controller()

        controller.playRecommendation(
            StreamingRecommendationPresentation(
                tracks = listOf(playbackStartTrack(4L)),
                emptyStatus = "Empty",
                readyStatus = "Ready"
            )
        )

        assertEquals(
            listOf("status:Ready", "startService", "resolve:1:0", "play:1:0", "result:played"),
            listener.calls
        )
    }

    @Test
    fun playsHeartbeatRecommendationWithoutStoppingHeartbeatModeOrOpeningQueue() {
        val listener = FakeListener(hasService = true, streamResolved = false)
        val controller = listener.controller()

        controller.playHeartbeatRecommendation(
            StreamingRecommendationPresentation(
                tracks = listOf(playbackStartTrack(5L)),
                emptyStatus = "Empty",
                readyStatus = "Playing"
            )
        )

        assertEquals(
            listOf("status:Playing", "startService", "resolve:1:0", "play:1:0", "result:played"),
            listener.calls
        )
    }

    private class FakeListener(
        private val hasService: Boolean,
        private val streamResolved: Boolean = false
    ) : PlaybackStartController.Listener {
        val calls = mutableListOf<String>()
        var pendingTracks: List<Track> = emptyList()
        var pendingIndex: Int = -1
        fun controller(): PlaybackStartController {
            return PlaybackStartController(
                streamingTrackListResolver = StreamingTrackListResolver { tracks, index ->
                    calls += "resolve:${tracks?.size ?: 0}:$index"
                    streamResolved
                },
                playbackTrackListPlayer = PlaybackTrackListPlayer { tracks, index ->
                    calls += "play:${tracks?.size ?: 0}:$index"
                    val result = PlaybackActionResultUi(null, "played", false, false, false, false)
                    calls += "result:${result.status}"
                    result
                },
                playbackActionResultApplier = QueuePlaybackActionResultApplier {},
                listener = this
            )
        }

        override fun stopHeartbeatRecommendationMode() {
            calls += "stopHeartbeat"
        }

        override fun startPlaybackService() {
            calls += "startService"
        }

        override fun hasPlaybackService(): Boolean = hasService

        override fun savePendingPlayback(tracks: List<Track>, index: Int) {
            pendingTracks = tracks
            pendingIndex = index
            calls += "save:${tracks.size}:$index"
        }

        override fun pendingPlaybackTracks(): List<Track> = pendingTracks

        override fun pendingPlaybackIndex(): Int = pendingIndex

        override fun clearPendingPlayback() {
            pendingTracks = emptyList()
            pendingIndex = -1
            calls += "clear"
        }

        override fun resolvingStatus(): String = "Resolving"

        override fun setStatus(status: String) {
            calls += "status:$status"
        }

        override fun openQueue() {
            calls += "openQueue"
        }
    }
}

private fun playbackStartTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
