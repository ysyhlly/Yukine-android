package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainPlaybackActionListenerTest {
    @Test
    fun delegatesPlaybackActionCallbacksToInjectedOwners() {
        val snapshot = PlaybackStateSnapshot.empty()
        val fallbackTracks = listOf(track(1L), track(2L))
        val result = PlaybackActionResultUi("ready", false, false)
        var resolveCalls = 0
        val appliedResults = mutableListOf<PlaybackActionResultUi?>()
        val listener = MainPlaybackActionListener(
            streamingResolver = StreamingQueueResolveHandler {
                resolveCalls += 1
                true
            },
            snapshotSource = PlaybackSnapshotSource { snapshot },
            fallbackTracksSource = PlaybackFallbackTracksSource { fallbackTracks },
            resultSink = PlaybackActionResultSink { appliedResults += it }
        )

        assertEquals(true, listener.resolveCurrentStreamingQueueTrackIfNeeded())
        assertSame(snapshot, listener.playbackSnapshot())
        assertEquals(fallbackTracks, listener.fallbackTracks())
        listener.applyPlaybackActionResult(result)

        assertEquals(1, resolveCalls)
        assertEquals(listOf(result), appliedResults)
    }

    @Test
    fun factoryCreatesPlaybackActionControllerListener() {
        val factory = PlaybackUiModule.provideMainPlaybackActionListenerFactory()
        val snapshot = PlaybackStateSnapshot.empty()
        val appliedResults = mutableListOf<PlaybackActionResultUi?>()
        val listener = factory.create(
            StreamingQueueResolveHandler { false },
            PlaybackSnapshotSource { snapshot },
            PlaybackFallbackTracksSource { listOf(track(3L)) },
            PlaybackActionResultSink { appliedResults += it }
        )
        val result = PlaybackActionResultUi(null, false, false)

        assertEquals(false, listener.resolveCurrentStreamingQueueTrackIfNeeded())
        assertSame(snapshot, listener.playbackSnapshot())
        assertEquals(listOf(3L), listener.fallbackTracks().map { it.id })
        listener.applyPlaybackActionResult(result)

        assertEquals(listOf(result), appliedResults)
    }
}

private fun track(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
