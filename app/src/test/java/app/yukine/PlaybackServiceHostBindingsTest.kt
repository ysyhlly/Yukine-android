package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceHostBindingsTest {
    @Test
    fun clearAndUiActionsDelegateToBindings() {
        val calls = mutableListOf<String>()
        val host = PlaybackServiceHostBindings(
            playbackSpeedProvider = PlaybackFloatProvider { 1.25f },
            appVolumeProvider = PlaybackFloatProvider { 0.75f },
            concurrentPlaybackProvider = PlaybackBooleanProvider { true },
            statusBarLyricsProvider = PlaybackBooleanProvider { false },
            playbackRestoreProvider = PlaybackBooleanProvider { true },
            playbackServiceSetter = PlaybackServiceSetter { calls += "service:${it == null}" },
            resetPlaybackStoreAction = Runnable { calls += "reset" },
            playPendingTracksAction = Runnable { calls += "pending" },
            renderSelectedTabAction = Runnable { calls += "tab" },
            renderNowBarAction = Runnable { calls += "nowbar" }
        )

        assertEquals(1.25f, host.playbackSpeed(), 0.0f)
        assertEquals(0.75f, host.appVolume(), 0.0f)
        assertEquals(true, host.concurrentPlaybackEnabled())
        assertEquals(false, host.statusBarLyricsEnabled())
        assertEquals(true, host.playbackRestoreEnabled())

        host.clearPlaybackService()
        host.resetPlaybackStore()
        host.playPendingTracksIfNeeded()
        host.renderSelectedTab()
        host.renderNowBar()

        assertEquals(
            listOf("service:true", "reset", "pending", "tab", "nowbar"),
            calls
        )
    }
}
