package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStartBindingsTest {
    @Test
    fun forwardsPlaybackStartEdges() {
        val calls = mutableListOf<String>()
        val track = playbackStartTrack(1L)
        val result = PlaybackActionResultUi(null, "played", false, false, false, false)
        val bindings = PlaybackStartBindings(
            heartbeatRecommendationStopper = QueueNoArgAction { calls += "stopHeartbeat" },
            playbackServiceStarter = PlaybackStartServiceStarter { calls += "startService" },
            playbackServiceAvailability = PlaybackServiceAvailability {
                calls += "service"
                true
            },
            pendingPlaybackSaver = PendingPlaybackSaver { tracks, index -> calls += "save:${tracks.size}:$index" },
            pendingPlaybackTracksProvider = PendingPlaybackTracksProvider {
                calls += "pendingTracks"
                listOf(track)
            },
            pendingPlaybackIndexProvider = PendingPlaybackIndexProvider {
                calls += "pendingIndex"
                2
            },
            pendingPlaybackClearer = QueueNoArgAction { calls += "clear" },
            resolvingStatusProvider = QueueStatusProvider {
                calls += "resolving"
                "Resolving"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" },
            playbackController = PlaybackController { tracks, index ->
                calls += "playback:${tracks?.size ?: 0}:$index"
                result
            },
            queueOpener = PlaybackQueueOpener {
                calls += "openQueue"
            }
        )

        bindings.stopHeartbeatRecommendationMode()
        bindings.startPlaybackService()
        bindings.hasPlaybackService()
        bindings.savePendingPlayback(listOf(track), 3)
        bindings.pendingPlaybackTracks()
        bindings.pendingPlaybackIndex()
        bindings.clearPendingPlayback()
        bindings.resolvingStatus()
        bindings.setStatus("Ready")
        bindings.playbackController().playTrackList(listOf(track), 4)
        bindings.openQueue()

        assertEquals(
            listOf(
                "stopHeartbeat",
                "startService",
                "service",
                "save:1:3",
                "pendingTracks",
                "pendingIndex",
                "clear",
                "resolving",
                "status:Ready",
                "playback:1:4",
                "openQueue"
            ),
            calls
        )
    }
}

private fun playbackStartTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
