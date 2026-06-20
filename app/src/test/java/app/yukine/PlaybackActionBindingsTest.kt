package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackActionBindingsTest {
    @Test
    fun forwardsPlaybackActionEdges() {
        val calls = mutableListOf<String>()
        val fallback = listOf(playbackActionTrack(1L))
        val result = PlaybackActionResultUi(null, "done", false, false, false, false)
        val bindings = PlaybackActionBindings(
            streamingQueueResolveAction = StreamingQueueResolveAction {
                calls += "resolve"
                false
            },
            playbackSnapshotProvider = PlaybackSnapshotProvider {
                calls += "snapshot"
                PlaybackStateSnapshot.empty()
            },
            fallbackTracksProvider = PlaybackFallbackTracksProvider {
                calls += "fallback:${fallback.size}"
                fallback
            },
            playbackActionResultApplier = QueuePlaybackActionResultApplier { nextResult ->
                calls += "result:${nextResult?.status}"
            }
        )

        bindings.resolveCurrentStreamingQueueTrackIfNeeded()
        bindings.playbackSnapshot()
        bindings.fallbackTracks()
        bindings.applyPlaybackActionResult(result)

        assertEquals(
            listOf("resolve", "snapshot", "fallback:1", "result:done"),
            calls
        )
    }
}

private fun playbackActionTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, android.net.Uri.EMPTY, "file:$id")
