package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackTransitionStateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransitionStateManagerTest {
    @Test
    fun transitionStateCanBeManaged() {
        val manager = PlaybackTransitionStateManager()
        val track = Track(
            1L,
            "Track",
            "Artist",
            "Album",
            1_000L,
            Uri.EMPTY,
            "data",
            0L,
            Uri.EMPTY,
            "",
            0,
            0,
            0,
            0,
            0f,
            0f
        )

        assertNull(manager.lastMarkedTrack())
        assertFalse(manager.fadeOutAdvancing())

        manager.setLastMarkedTrack(track)
        manager.setFadeOutAdvancing(true)

        assertSame(track, manager.lastMarkedTrack())
        assertTrue(manager.fadeOutAdvancing())

        manager.clear()

        assertNull(manager.lastMarkedTrack())
        assertFalse(manager.fadeOutAdvancing())
    }
}
