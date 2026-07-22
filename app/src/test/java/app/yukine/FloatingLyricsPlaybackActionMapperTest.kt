package app.yukine

import app.yukine.playback.service.PlaybackServiceActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingLyricsPlaybackActionMapperTest {
    @Test
    fun playPauseUsesTheExistingPlaybackServiceActions() {
        assertEquals(
            PlaybackServiceActions.RESTORE_AND_PLAY,
            FloatingLyricsPlaybackActionMapper.serviceAction(
                FloatingLyricsOverlayAction.PlayPause,
                playing = false
            )
        )
        assertEquals(
            PlaybackServiceActions.PAUSE,
            FloatingLyricsPlaybackActionMapper.serviceAction(
                FloatingLyricsOverlayAction.PlayPause,
                playing = true
            )
        )
    }

    @Test
    fun previousAndNextUseTheExistingPlaybackServiceActions() {
        assertEquals(
            PlaybackServiceActions.PREVIOUS,
            FloatingLyricsPlaybackActionMapper.serviceAction(
                FloatingLyricsOverlayAction.Previous,
                playing = true
            )
        )
        assertEquals(
            PlaybackServiceActions.NEXT,
            FloatingLyricsPlaybackActionMapper.serviceAction(
                FloatingLyricsOverlayAction.Next,
                playing = false
            )
        )
    }

    @Test
    fun presentationActionsDoNotMapToPlaybackCommands() {
        assertNull(
            FloatingLyricsPlaybackActionMapper.serviceAction(
                FloatingLyricsOverlayAction.HideSession,
                playing = false
            )
        )
    }
}
