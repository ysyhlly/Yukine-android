package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPlaybackServiceHostTest {
    @Test
    fun delegatesSettingsAndPlaybackChromeToInjectedOwners() {
        val calls = mutableListOf<String>()
        val host = MainPlaybackServiceHost(
            playbackSpeedSource = MainPlaybackServiceHost.PlaybackSpeedSource { 1.35f },
            appVolumeSource = MainPlaybackServiceHost.AppVolumeSource { 0.65f },
            concurrentPlaybackSource = MainPlaybackServiceHost.ConcurrentPlaybackSource { true },
            statusBarLyricsSource = MainPlaybackServiceHost.StatusBarLyricsSource { false },
            playbackRestoreSource = MainPlaybackServiceHost.PlaybackRestoreSource { true },
            replayGainSource = MainPlaybackServiceHost.ReplayGainSource { false },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            playbackServiceClearer = MainPlaybackServiceHost.PlaybackServiceClearer { calls += "clear" },
            playbackStoreResetter = MainPlaybackServiceHost.PlaybackStoreResetter { calls += "reset" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            selectedTabRenderer = MainPlaybackServiceHost.SelectedTabRenderer { calls += "selected-tab" },
            nowBarRenderer = MainPlaybackServiceHost.NowBarRenderer { calls += "now-bar" }
        )

        assertEquals(1.35f, host.playbackSpeed())
        assertEquals(0.65f, host.appVolume())
        assertTrue(host.concurrentPlaybackEnabled())
        assertFalse(host.statusBarLyricsEnabled())
        assertTrue(host.playbackRestoreEnabled())
        assertFalse(host.replayGainEnabled())
        host.clearPlaybackService()
        host.resetPlaybackStore()
        host.playPendingTracksIfNeeded()
        host.renderSelectedTab()
        host.renderNowBar()

        assertEquals(listOf("clear", "reset", "pending", "selected-tab", "now-bar"), calls)
    }

    @Test
    fun factoryCreatesPlaybackServiceHostControllerHost() {
        val calls = mutableListOf<String>()
        val host = PlaybackUiModule.provideMainPlaybackServiceHostFactory().create(
            MainPlaybackServiceHost.PlaybackSpeedSource { 1.0f },
            MainPlaybackServiceHost.AppVolumeSource { 0.9f },
            MainPlaybackServiceHost.ConcurrentPlaybackSource { false },
            MainPlaybackServiceHost.StatusBarLyricsSource { true },
            MainPlaybackServiceHost.PlaybackRestoreSource { false },
            MainPlaybackServiceHost.ReplayGainSource { true },
            MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            MainPlaybackServiceHost.PlaybackServiceClearer { calls += "clear" },
            MainPlaybackServiceHost.PlaybackStoreResetter { calls += "reset" },
            MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            MainPlaybackServiceHost.SelectedTabRenderer { calls += "selected-tab" },
            MainPlaybackServiceHost.NowBarRenderer { calls += "now-bar" }
        )

        assertEquals(1.0f, host.playbackSpeed())
        assertEquals(0.9f, host.appVolume())
        assertFalse(host.concurrentPlaybackEnabled())
        assertTrue(host.statusBarLyricsEnabled())
        assertFalse(host.playbackRestoreEnabled())
        assertTrue(host.replayGainEnabled())
        host.playPendingTracksIfNeeded()

        assertEquals(listOf("pending"), calls)
    }
}
