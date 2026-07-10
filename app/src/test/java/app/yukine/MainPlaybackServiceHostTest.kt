package app.yukine

import app.yukine.model.Track
import app.yukine.playback.AudioEffectSettings
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
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
            systemMediaLyricsTitleSource = MainPlaybackServiceHost.SystemMediaLyricsTitleSource { true },
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
        assertTrue(host.systemMediaLyricsTitleEnabled())
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
    fun attachUsesHostPortAndMarksPlaybackVisible() {
        val calls = mutableListOf<String>()
        val service = FakePlaybackServiceHostPort()
        val host = MainPlaybackServiceHost(
            playbackSpeedSource = MainPlaybackServiceHost.PlaybackSpeedSource { 1.0f },
            appVolumeSource = MainPlaybackServiceHost.AppVolumeSource { 1.0f },
            concurrentPlaybackSource = MainPlaybackServiceHost.ConcurrentPlaybackSource { false },
            statusBarLyricsSource = MainPlaybackServiceHost.StatusBarLyricsSource { true },
            systemMediaLyricsTitleSource = MainPlaybackServiceHost.SystemMediaLyricsTitleSource { false },
            playbackRestoreSource = MainPlaybackServiceHost.PlaybackRestoreSource { true },
            replayGainSource = MainPlaybackServiceHost.ReplayGainSource { false },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher {
                calls += "attach"
            },
            playbackServiceClearer = MainPlaybackServiceHost.PlaybackServiceClearer { calls += "clear" },
            playbackStoreResetter = MainPlaybackServiceHost.PlaybackStoreResetter { calls += "reset" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            selectedTabRenderer = MainPlaybackServiceHost.SelectedTabRenderer { calls += "selected-tab" },
            nowBarRenderer = MainPlaybackServiceHost.NowBarRenderer { calls += "now-bar" }
        )

        host.attachPlaybackService(service)

        assertEquals(listOf("attach", "visible:true"), calls + service.calls)
    }

    @Test
    fun factoryCreatesPlaybackServiceHostControllerHost() {
        val calls = mutableListOf<String>()
        val host = PlaybackUiModule.provideMainPlaybackServiceHostFactory().create(
            MainPlaybackServiceHost.PlaybackSpeedSource { 1.0f },
            MainPlaybackServiceHost.AppVolumeSource { 0.9f },
            MainPlaybackServiceHost.ConcurrentPlaybackSource { false },
            MainPlaybackServiceHost.StatusBarLyricsSource { true },
            MainPlaybackServiceHost.SystemMediaLyricsTitleSource { true },
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
        assertTrue(host.systemMediaLyricsTitleEnabled())
        assertFalse(host.playbackRestoreEnabled())
        assertTrue(host.replayGainEnabled())
        host.playPendingTracksIfNeeded()

        assertEquals(listOf("pending"), calls)
    }

    private class FakePlaybackServiceHostPort : PlaybackServiceHostPort {
        val calls = mutableListOf<String>()

        override fun registerListener(listener: PlaybackStateListener?) = Unit

        override fun unregisterListener(listener: PlaybackStateListener?) = Unit

        override fun setAppVisible(visible: Boolean) {
            calls += "visible:$visible"
        }

        override fun realtimeBeat(): Float = 0f

        override fun realtimeBands(): FloatArray = FloatArray(0)

        override fun snapshot(): PlaybackStateSnapshot? = null

        override fun queueSnapshot(): List<Track> = emptyList()

        override fun queueSize(): Int = 0

        override fun queueTrackAt(index: Int): Track? = null

        override fun skipToPrevious() = Unit

        override fun skipToNext() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun removeTracksById(trackIds: Set<Long>) = Unit

        override fun clearQueue() = Unit

        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) = Unit

        override fun replaceQueuedTrack(updated: Track) = Unit

        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) = Unit

        override fun retainTracksById(trackIds: Set<Long>) = Unit

        override fun warmPlaybackTrack(track: Track) = Unit

        override fun appendToQueue(tracks: List<Track>) = Unit

        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) = Unit

        override fun startSleepTimerMinutes(minutes: Int) = Unit

        override fun cancelSleepTimer() = Unit

        override fun playQueue(tracks: List<Track>, index: Int) = Unit

        override fun pause() = Unit

        override fun play() = Unit

        override fun setShuffleEnabled(enabled: Boolean) = Unit

        override fun cycleRepeatMode() = Unit

        override fun setRepeatMode(repeatMode: Int) = Unit

        override fun setPlaybackSpeed(speed: Float) = Unit

        override fun setAppVolume(volume: Float) = Unit

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) = Unit

        override fun applyAudioEffectSettings(settings: AudioEffectSettings) = Unit

        override fun setStatusBarLyricsEnabled(enabled: Boolean) = Unit

        override fun setSystemMediaLyricsTitleEnabled(enabled: Boolean) = Unit

        override fun setPlaybackRestoreEnabled(enabled: Boolean) = Unit

        override fun setReplayGainEnabled(enabled: Boolean) = Unit
    }
}
