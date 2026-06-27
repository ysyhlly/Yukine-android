package app.yukine.playback

import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackRuntimeStateManager
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun concurrentPlaybackStateCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setConcurrentPlaybackEnabled(true)
        assertTrue(manager.concurrentPlaybackEnabled())
    }

    @Test
    fun shuffleAndRepeatStateCanBeManaged() {
        val manager = PlaybackRuntimeStateManager(FakeStateProvider())

        manager.setShuffleEnabled(true)
        manager.setRepeatMode(EchoPlaybackService.REPEAT_ONE)

        assertTrue(manager.shuffleEnabled())
        assertEquals(EchoPlaybackService.REPEAT_ONE, manager.repeatMode())
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
                appRepeatMode = EchoPlaybackService.REPEAT_ALL,
                playerMirrorsQueue = false
            )
        )
        assertEquals(
            ExoPlayer.REPEAT_MODE_ONE,
            PlaybackRuntimeStateManager.media3RepeatModeForAppRepeatMode(
                appRepeatMode = EchoPlaybackService.REPEAT_ONE,
                playerMirrorsQueue = false
            )
        )
        assertEquals(
            ExoPlayer.REPEAT_MODE_ALL,
            PlaybackRuntimeStateManager.media3RepeatModeForAppRepeatMode(
                appRepeatMode = EchoPlaybackService.REPEAT_ALL,
                playerMirrorsQueue = true
            )
        )
    }

    private class FakeStateProvider(
    ) : PlaybackRuntimeStateManager.StateProvider {
        override fun player(): ExoPlayer? = null
        override fun playerMirrorsQueue(): Boolean = false
        override fun currentTrack(): Track? = null
    }
}
