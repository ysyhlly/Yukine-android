package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsActionControllerTest {
    @Test
    fun forwardsSettingsActionsToViewModelsAndActivityEdges() {
        val settingsViewModel = SettingsViewModel()
        val nowPlayingViewModel = NowPlayingViewModel()
        nowPlayingViewModel.bindPlaybackGateway(FakePlaybackGateway())
        val listener = FakeListener()
        val controller = SettingsActionController(settingsViewModel, nowPlayingViewModel, listener)

        controller.applyThemeMode("dark")
        controller.applyAccentMode("rose")
        controller.applyLanguageMode("zh")
        controller.applyPlaybackSpeed(1.25f)
        controller.applyAppVolume(0.7f)
        controller.applyStreamingAudioQuality("lossless")
        controller.setConcurrentPlaybackEnabled(true)
        controller.setOnlineLyricsEnabled(true)
        controller.applyLyricsOffset(250L)
        controller.applyStreamingGatewayEndpoint("http://gateway")
        controller.startSleepTimer(30)
        controller.cancelSleepTimer()
        controller.reloadCurrentLyrics()

        assertEquals(
            listOf(
                "gateway:http://gateway",
                "playback:Sleep timer set: 30 minutes",
                "playback:Sleep timer cancelled",
                "reload"
            ),
            listener.calls
        )
    }

    private class FakeListener : SettingsActionController.Listener {
        val calls = mutableListOf<String>()

        override fun applyStreamingGatewayEndpoint(endpoint: String) {
            calls += "gateway:$endpoint"
        }

        override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
            calls += "playback:${result?.status}"
        }

        override fun reloadCurrentLyrics() {
            calls += "reload"
        }
    }

    private class FakePlaybackGateway : NowPlayingPlaybackGateway {
        override fun serviceConnected(): Boolean = true
        override fun startPlaybackService(action: String?) {}
        override fun snapshot(): PlaybackStateSnapshot? = PlaybackStateSnapshot.empty()
        override fun queueSnapshot(): List<app.yukine.model.Track> = emptyList()
        override fun skipToPrevious() {}
        override fun skipToNext() {}
        override fun seekTo(positionMs: Long) {}
        override fun removeTracksById(trackIds: Set<Long>) {}
        override fun clearQueue() {}
        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {}
        override fun replaceQueuedTrack(updated: app.yukine.model.Track) {}
        override fun replaceQueuedTrackById(oldTrackId: Long, updated: app.yukine.model.Track) {}
        override fun retainTracksById(trackIds: Set<Long>) {}
        override fun precacheTrack(track: app.yukine.model.Track) {}
        override fun appendToQueue(tracks: List<app.yukine.model.Track>) {}
        override fun replaceCurrentTrackAndResume(track: app.yukine.model.Track, positionMs: Long) {}
        override fun startSleepTimerMinutes(minutes: Int) {}
        override fun cancelSleepTimer() {}
        override fun playQueue(tracks: List<app.yukine.model.Track>, index: Int) {}
        override fun pause() {}
        override fun play() {}
        override fun setShuffleEnabled(enabled: Boolean) {}
        override fun cycleRepeatMode() {}
        override fun setRepeatMode(repeatMode: Int) {}
    }
}
