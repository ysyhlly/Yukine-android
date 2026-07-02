package app.yukine.playback

import app.yukine.playback.manager.PlaybackQueueRuntimeStateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueRuntimeStateManagerTest {
    @Test
    fun mirroredQueueStateCanBeManaged() {
        val manager = PlaybackQueueRuntimeStateManager()

        assertFalse(manager.playerMirrorsQueue())

        manager.setPlayerMirrorsQueue(true)
        assertTrue(manager.playerMirrorsQueue())

        manager.setPlayerMirrorsQueue(false)
        assertFalse(manager.playerMirrorsQueue())
    }

}
