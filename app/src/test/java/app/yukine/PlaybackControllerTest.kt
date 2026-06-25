package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackControllerTest {
    @Test
    fun streamingResolverCanTakeOverPlaybackStart() {
        val calls = mutableListOf<String>()
        val controller = PlaybackStartControllerAdapter(
            streamingTrackListResolver = StreamingTrackListResolver { tracks, index ->
                calls += "resolve:${tracks?.size ?: 0}:$index"
                true
            },
            playbackTrackListPlayer = PlaybackTrackListPlayer { tracks, index ->
                calls += "play:${tracks?.size ?: 0}:$index"
                PlaybackActionResultUi(null, "played", false, false, false, false)
            },
            playbackActionResultApplier = QueuePlaybackActionResultApplier { result ->
                calls += "result:${result?.status}"
            }
        )

        val result = controller.playTrackList(listOf(playbackControllerTrack(1L)), 0)

        assertNull(result)
        assertEquals(listOf("resolve:1:0"), calls)
    }

    @Test
    fun localTrackListPlaybackAppliesResult() {
        val calls = mutableListOf<String>()
        val expected = PlaybackActionResultUi(null, "played", false, false, true, false)
        val controller = PlaybackStartControllerAdapter(
            streamingTrackListResolver = StreamingTrackListResolver { tracks, index ->
                calls += "resolve:${tracks?.size ?: 0}:$index"
                false
            },
            playbackTrackListPlayer = PlaybackTrackListPlayer { tracks, index ->
                calls += "play:${tracks?.size ?: 0}:$index"
                expected
            },
            playbackActionResultApplier = QueuePlaybackActionResultApplier { result ->
                calls += "result:${result?.status}:${result?.renderNowBar}"
            }
        )

        val result = controller.playTrackList(listOf(playbackControllerTrack(2L)), 0)

        assertEquals(expected, result)
        assertEquals(listOf("resolve:1:0", "play:1:0", "result:played:true"), calls)
    }
}

private fun playbackControllerTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
