package app.yukine.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackShutdownCoordinatorTest {
    @Test
    fun releasePlaybackResourcesKeepsServiceResourcesAlive() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.releasePlaybackResources()

        assertEquals(listOf("lyrics", "wifi", "player"), calls)
    }

    @Test
    fun releaseServiceResourcesRunsFullServiceTeardown() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.releaseServiceResources()

        assertEquals(
            listOf("lyrics", "noisy", "schedulers", "artwork", "precache", "wifi", "player"),
            calls
        )
    }

    private fun coordinator(calls: MutableList<String>): PlaybackShutdownCoordinator {
        return PlaybackShutdownCoordinator(
            playbackLyricsRelease = Runnable { calls.add("lyrics") },
            playbackNotificationArtworkRelease = Runnable { calls.add("artwork") },
            playbackPrecacheRelease = Runnable { calls.add("precache") },
            unregisterNoisyReceiver = Runnable { calls.add("noisy") },
            shutdownTaskSchedulers = Runnable { calls.add("schedulers") },
            releaseWifiLock = Runnable { calls.add("wifi") },
            releasePlayer = Runnable { calls.add("player") }
        )
    }
}
