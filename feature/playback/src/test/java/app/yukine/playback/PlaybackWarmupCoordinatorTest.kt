package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWarmupCoordinatorTest {
    @Test
    fun warmupRunsPrecacheAndVisualization() {
        val calls = mutableListOf<String>()
        val coordinator = PlaybackWarmupCoordinator(
            precacheTrack = { calls.add("precache:${it.id}") },
            scheduleVisualizationCache = { calls.add("visual:${it.id}") }
        )

        coordinator.warmup(track(42L))

        assertEquals(listOf("precache:42", "visual:42"), calls)
    }

    @Test
    fun warmupIgnoresNullTrack() {
        val calls = mutableListOf<String>()
        val coordinator = PlaybackWarmupCoordinator(
            precacheTrack = { calls.add("precache") },
            scheduleVisualizationCache = { calls.add("visual") }
        )

        coordinator.warmup(null)

        assertEquals(emptyList<String>(), calls)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 180000L, Uri.EMPTY, "track-$id")
    }
}
