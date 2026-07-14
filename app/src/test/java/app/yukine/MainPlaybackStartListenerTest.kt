package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPlaybackStartListenerTest {
    @Test
    fun ownsPendingPlaybackAndDelegatesPlaybackStartCallbacks() {
        val calls = mutableListOf<String>()
        val tracks = mutableListOf(playbackStartListenerTrack(1L))
        val listener = MainPlaybackStartListener(
            heartbeatStopper = PlaybackStartHeartbeatStopper { calls += "stopHeartbeat" },
            serviceStarter = PlaybackStartServiceStarter { calls += "startService" },
            serviceAvailability = PlaybackStartServiceAvailability { true },
            resolvingStatusProvider = PlaybackStartResolvingStatusProvider { "Resolving" },
            statusSink = PlaybackStartStatusSink { calls += "status:$it" },
            queueOpener = PlaybackStartQueueOpener { calls += "openQueue" }
        )

        listener.savePendingPlayback(tracks, 2)
        tracks += playbackStartListenerTrack(2L)
        listener.stopHeartbeatRecommendationMode()
        listener.startPlaybackService()
        listener.setStatus(listener.resolvingStatus())
        listener.openQueue()

        assertEquals(true, listener.hasPlaybackService())
        assertEquals(listOf(1L), listener.pendingPlaybackTracks().map { it.id })
        assertEquals(2, listener.pendingPlaybackIndex())
        assertEquals(listOf("stopHeartbeat", "startService", "status:Resolving", "openQueue"), calls)

        listener.clearPendingPlayback()

        assertEquals(emptyList<Track>(), listener.pendingPlaybackTracks())
        assertEquals(-1, listener.pendingPlaybackIndex())
    }

    @Test
    fun directConstructionCreatesPlaybackStartControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainPlaybackStartListener(
            PlaybackStartHeartbeatStopper { calls += "stopHeartbeat" },
            PlaybackStartServiceStarter { calls += "startService" },
            PlaybackStartServiceAvailability { false },
            PlaybackStartResolvingStatusProvider { "Resolving" },
            PlaybackStartStatusSink { calls += "status:$it" },
            PlaybackStartQueueOpener { calls += "openQueue" }
        )

        listener.savePendingPlayback(listOf(playbackStartListenerTrack(3L)), 0)
        listener.stopHeartbeatRecommendationMode()
        listener.startPlaybackService()
        listener.setStatus(listener.resolvingStatus())
        listener.openQueue()

        assertEquals(false, listener.hasPlaybackService())
        assertEquals(listOf(3L), listener.pendingPlaybackTracks().map { it.id })
        assertEquals(0, listener.pendingPlaybackIndex())
        assertEquals(listOf("stopHeartbeat", "startService", "status:Resolving", "openQueue"), calls)
    }
}

private fun playbackStartListenerTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
