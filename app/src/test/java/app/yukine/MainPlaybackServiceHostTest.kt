package app.yukine

import app.yukine.model.Track
import app.yukine.playback.AudioEffectSettings
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPlaybackServiceHostTest {
    @Test
    fun delegatesDetachAndPlaybackChromeToInjectedOwners() {
        val calls = mutableListOf<String>()
        val host = MainPlaybackServiceHost(
            playbackSettingsSource = MainPlaybackServiceHost.PlaybackSettingsSource {
                PlaybackServiceConnectionSettings(
                    playbackSpeed = 1.35f,
                    appVolume = 0.65f,
                    concurrentPlaybackEnabled = true,
                    statusBarLyricsEnabled = false,
                    playbackRestoreEnabled = true,
                    replayGainEnabled = false
                )
            },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            playbackServiceDetacher = MainPlaybackServiceHost.PlaybackServiceDetacher { calls += "detach" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            playbackChromeRenderer = MainPlaybackServiceHost.PlaybackChromeRenderer { calls += "chrome" }
        )

        host.detachPlaybackService()
        host.renderPlaybackChrome()

        assertEquals(listOf("detach", "chrome"), calls)
    }

    @Test
    fun attachConfiguresServiceStartsPendingTracksAndMarksPlaybackVisible() {
        val calls = mutableListOf<String>()
        val service = FakePlaybackServiceHostPort(calls)
        val host = MainPlaybackServiceHost(
            playbackSettingsSource = MainPlaybackServiceHost.PlaybackSettingsSource {
                calls += "settings"
                PlaybackServiceConnectionSettings(
                    playbackSpeed = 1.4f,
                    appVolume = 0.7f,
                    concurrentPlaybackEnabled = true,
                    statusBarLyricsEnabled = false,
                    playbackRestoreEnabled = true,
                    replayGainEnabled = true
                )
            },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher {
                calls += "attach"
            },
            playbackServiceDetacher = MainPlaybackServiceHost.PlaybackServiceDetacher { calls += "detach" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            playbackChromeRenderer = MainPlaybackServiceHost.PlaybackChromeRenderer { calls += "chrome" }
        )

        host.attachPlaybackService(service)

        assertEquals(
            listOf(
                "attach",
                "visible:true",
                "settings",
                "speed:1.4",
                "volume:0.7",
                "concurrent:true",
                "status-bar-lyrics:false",
                "restore:true",
                "replay-gain:true",
                "pending"
            ),
            calls
        )
    }

    @Test
    fun controllerAppliesConnectionSettingsSnapshotToService() {
        val calls = mutableListOf<String>()
        val service = FakePlaybackServiceHostPort(calls)
        val host = MainPlaybackServiceHost(
            playbackSettingsSource = MainPlaybackServiceHost.PlaybackSettingsSource {
                calls += "settings"
                PlaybackServiceConnectionSettings(
                    playbackSpeed = 1.25f,
                    appVolume = 0.55f,
                    concurrentPlaybackEnabled = true,
                    statusBarLyricsEnabled = false,
                    playbackRestoreEnabled = true,
                    replayGainEnabled = true
                )
            },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            playbackServiceDetacher = MainPlaybackServiceHost.PlaybackServiceDetacher { calls += "detach" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            playbackChromeRenderer = MainPlaybackServiceHost.PlaybackChromeRenderer { calls += "chrome" }
        )

        PlaybackServiceHostController(host).onPlaybackServiceConnected(service)

        assertEquals(
            listOf(
                "attach",
                "visible:true",
                "settings",
                "speed:1.25",
                "volume:0.55",
                "concurrent:true",
                "status-bar-lyrics:false",
                "restore:true",
                "replay-gain:true",
                "pending",
                "chrome"
            ),
            calls
        )
    }

    @Test
    fun controllerDetachesServiceAndRefreshesChromeOnDisconnect() {
        val calls = mutableListOf<String>()
        val host = MainPlaybackServiceHost(
            playbackSettingsSource = MainPlaybackServiceHost.PlaybackSettingsSource {
                PlaybackServiceConnectionSettings(
                    playbackSpeed = 1.0f,
                    appVolume = 1.0f,
                    concurrentPlaybackEnabled = false,
                    statusBarLyricsEnabled = true,
                    playbackRestoreEnabled = true,
                    replayGainEnabled = false
                )
            },
            playbackServiceAttacher = MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            playbackServiceDetacher = MainPlaybackServiceHost.PlaybackServiceDetacher { calls += "detach" },
            pendingTracksPlayer = MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            playbackChromeRenderer = MainPlaybackServiceHost.PlaybackChromeRenderer { calls += "chrome" }
        )

        PlaybackServiceHostController(host).onPlaybackServiceDisconnected()

        assertEquals(listOf("detach", "chrome"), calls)
    }

    @Test
    fun factoryCreatesPlaybackServiceHostControllerHost() {
        val calls = mutableListOf<String>()
        val host = PlaybackUiModule.provideMainPlaybackServiceHostFactory().create(
            MainPlaybackServiceHost.PlaybackSettingsSource {
                PlaybackServiceConnectionSettings(
                    playbackSpeed = 1.0f,
                    appVolume = 0.9f,
                    concurrentPlaybackEnabled = false,
                    statusBarLyricsEnabled = true,
                    playbackRestoreEnabled = false,
                    replayGainEnabled = true
                )
            },
            MainPlaybackServiceHost.PlaybackServiceAttacher { calls += "attach" },
            MainPlaybackServiceHost.PlaybackServiceDetacher { calls += "detach" },
            MainPlaybackServiceHost.PendingTracksPlayer { calls += "pending" },
            MainPlaybackServiceHost.PlaybackChromeRenderer { calls += "chrome" }
        )

        host.attachPlaybackService(FakePlaybackServiceHostPort(calls))

        assertEquals(
            listOf(
                "attach",
                "visible:true",
                "speed:1.0",
                "volume:0.9",
                "concurrent:false",
                "status-bar-lyrics:true",
                "restore:false",
                "replay-gain:true",
                "pending"
            ),
            calls
        )
    }

    private class FakePlaybackServiceHostPort(
        private val recorder: MutableList<String>? = null
    ) : PlaybackServiceHostPort {
        val calls = mutableListOf<String>()

        override fun registerListener(listener: PlaybackStateListener?) = Unit

        override fun unregisterListener(listener: PlaybackStateListener?) = Unit

        override fun setAppVisible(visible: Boolean) {
            record("visible:$visible")
        }

        override fun realtimeBeat(): Float = 0f

        override fun realtimeBands(): FloatArray = FloatArray(0)

        override fun snapshot(): PlaybackStateSnapshot? = null

        override fun queueSnapshot(): List<Track> = emptyList()

        override fun skipToPrevious() = Unit

        override fun skipToNext() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun removeTracksById(trackIds: Set<Long>) = Unit

        override fun clearQueue() = Unit

        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) = Unit

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

        override fun setPlaybackSpeed(speed: Float) {
            record("speed:$speed")
        }

        override fun setAppVolume(volume: Float) {
            record("volume:$volume")
        }

        override fun setConcurrentPlaybackEnabled(enabled: Boolean) {
            record("concurrent:$enabled")
        }

        override fun applyAudioEffectSettings(settings: AudioEffectSettings) = Unit

        override fun setStatusBarLyricsEnabled(enabled: Boolean) {
            record("status-bar-lyrics:$enabled")
        }

        override fun setPlaybackRestoreEnabled(enabled: Boolean) {
            record("restore:$enabled")
        }

        override fun setReplayGainEnabled(enabled: Boolean) {
            record("replay-gain:$enabled")
        }

        private fun record(call: String) {
            calls += call
            recorder?.add(call)
        }
    }
}
