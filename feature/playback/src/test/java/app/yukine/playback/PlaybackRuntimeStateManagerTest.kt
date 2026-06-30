package app.yukine.playback

import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackRuntimeStateManager
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackRuntimeStateManagerTest {
    @Test
    fun playbackSpeedIsClampedToSupportedRange() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())
        assertEquals(0.5f, manager.normalizePlaybackSpeed(0.3f), 0.0f)
        assertEquals(1.23f, manager.normalizePlaybackSpeed(1.234f), 0.0f)
        assertEquals(2.0f, manager.normalizePlaybackSpeed(3.4f), 0.0f)
    }

    @Test
    fun appVolumeIsClampedToNormalizedRange() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())
        assertEquals(0.0f, manager.normalizeAppVolume(-0.1f), 0.0f)
        assertEquals(0.78f, manager.normalizeAppVolume(0.784f), 0.0f)
        assertEquals(1.0f, manager.normalizeAppVolume(1.5f), 0.0f)
    }

    @Test
    fun replayGainMultiplierReturnsNeutralWhenDisabledOrMissingTrack() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())
        manager.setReplayGainEnabled(false)
        assertEquals(1.0f, manager.replayGainMultiplierForTrack(null), 0.0f)
        assertEquals(1.0f, manager.replayGainMultiplierForTrack(track = null), 0.0f)
    }

    @Test
    fun playbackSpeedAndAppVolumeCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setPlaybackSpeed(1.236f)
        manager.setAppVolume(0.784f)

        assertEquals(1.24f, manager.playbackSpeed(), 0.0f)
        assertEquals(0.78f, manager.appVolume(), 0.0f)
    }

    @Test
    fun applyPlaybackParametersToPlayerAppliesSpeedAndVolumeTogether() {
        val player = RecordingExoPlayer()
        val manager = PlaybackRuntimeStateManager(FakeStateProvider(player = player.proxy))

        manager.setPlaybackSpeed(1.236f)
        manager.setAppVolume(0.784f)
        manager.applyPlaybackParametersToPlayer()

        assertEquals(listOf("setPlaybackParameters", "setVolume"), player.calls)
        assertEquals(1.24f, player.playbackSpeed, 0.0f)
        assertEquals(0.78f, player.volume, 0.0f)
    }

    @Test
    fun currentTrackVolumeAppliesReplayGainAndNormalizedAppVolume() {
        val manager = PlaybackRuntimeStateManager(
            FakeStateProvider(
                track = track(replayGainTrackDb = -6.0f)
            )
        )

        manager.setAppVolume(0.8f)

        assertEquals(0.4f, manager.currentTrackVolume(), 0.01f)
    }

    @Test
    fun applyCurrentTrackVolumeToPlayerUsesCurrentTrackReplayGain() {
        val player = RecordingExoPlayer()
        val manager = PlaybackRuntimeStateManager(
            FakeStateProvider(
                player = player.proxy,
                track = track(replayGainTrackDb = 6.0f)
            )
        )

        manager.setAppVolume(0.75f)
        manager.applyCurrentTrackVolumeToPlayer()

        assertEquals(listOf("setVolume"), player.calls)
        assertEquals(1.0f, player.volume, 0.0f)
    }

    @Test
    fun concurrentPlaybackStateCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setConcurrentPlaybackEnabled(true)
        assertTrue(manager.concurrentPlaybackEnabled())
    }

    @Test
    fun shuffleAndRepeatStateCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setShuffleEnabled(true)
        manager.setRepeatMode(PlaybackRepeatMode.REPEAT_ONE)

        assertTrue(manager.shuffleEnabled())
        assertEquals(PlaybackRepeatMode.REPEAT_ONE, manager.repeatMode())
    }

    @Test
    fun applyPlaybackModeAndParametersToPlayerAppliesSingleRuntimeSnapshot() {
        val player = RecordingExoPlayer()
        val manager = PlaybackRuntimeStateManager(
            FakeStateProvider(
                player = player.proxy,
                playerMirrorsQueue = true
            )
        )

        manager.setPlaybackSpeed(1.25f)
        manager.setAppVolume(0.5f)
        manager.setShuffleEnabled(true)
        manager.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)
        manager.applyPlaybackModeAndParametersToPlayer()

        assertEquals(
            listOf("setPlaybackParameters", "setVolume", "setShuffleModeEnabled", "setRepeatMode"),
            player.calls
        )
        assertEquals(1.25f, player.playbackSpeed, 0.0f)
        assertEquals(0.5f, player.volume, 0.0f)
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
    }

    @Test
    fun preparingAndErrorStateCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setPreparing(true)
        manager.setErrorMessage("Unable to play this track.")

        assertTrue(manager.preparing())
        assertEquals("Unable to play this track.", manager.errorMessage())

        manager.setPreparing(false)
        manager.setErrorMessage(null)

        assertEquals(false, manager.preparing())
        assertEquals("", manager.errorMessage())
    }

    @Test
    fun repeatModeFallbackMatchesQueueMirroringRules() {
        assertEquals(
            ExoPlayer.REPEAT_MODE_OFF,
            PlaybackRuntimeStateManager.media3RepeatModeForAppRepeatMode(
                appRepeatMode = PlaybackRepeatMode.REPEAT_ALL,
                playerMirrorsQueue = false
            )
        )
        assertEquals(
            ExoPlayer.REPEAT_MODE_ONE,
            PlaybackRuntimeStateManager.media3RepeatModeForAppRepeatMode(
                appRepeatMode = PlaybackRepeatMode.REPEAT_ONE,
                playerMirrorsQueue = false
            )
        )
        assertEquals(
            ExoPlayer.REPEAT_MODE_ALL,
            PlaybackRuntimeStateManager.media3RepeatModeForAppRepeatMode(
                appRepeatMode = PlaybackRepeatMode.REPEAT_ALL,
                playerMirrorsQueue = true
            )
        )
    }

    private class FakeStateProvider(
        private val player: ExoPlayer? = null,
        private val playerMirrorsQueue: Boolean = false,
        private val track: Track? = null
    ) : PlaybackRuntimeStateManager.StateProvider {
        override fun player(): ExoPlayer? = player
        override fun playerMirrorsQueue(): Boolean = playerMirrorsQueue
        override fun currentTrack(): Track? = track
    }

    private class RecordingExoPlayer {
        val calls = mutableListOf<String>()
        var playbackSpeed = 0.0f
        var volume = 0.0f
        var shuffleModeEnabled = false
        var repeatMode = Player.REPEAT_MODE_OFF

        val proxy: ExoPlayer = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setPlaybackParameters" -> {
                    calls += method.name
                    playbackSpeed = (args?.get(0) as PlaybackParameters).speed
                }
                "setVolume" -> {
                    calls += method.name
                    volume = args?.get(0) as Float
                }
                "setShuffleModeEnabled" -> {
                    calls += method.name
                    shuffleModeEnabled = args?.get(0) as Boolean
                }
                "setRepeatMode" -> {
                    calls += method.name
                    repeatMode = args?.get(0) as Int
                }
            }
            defaultReturnValue(method.returnType)
        } as ExoPlayer
    }

    companion object {
        private fun track(
            replayGainTrackDb: Float = 0.0f,
            replayGainAlbumDb: Float = 0.0f
        ): Track {
            return Track(
                1L,
                "Track",
                "Artist",
                "Album",
                1000L,
                null,
                "file:track.mp3",
                0L,
                null,
                "",
                0,
                0,
                0,
                0,
                replayGainTrackDb,
                replayGainAlbumDb
            )
        }

        private fun defaultReturnValue(returnType: Class<*>): Any? {
            return when (returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> 0.toChar()
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0.0f
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Void.TYPE -> null
                else -> null
            }
        }
    }
}
