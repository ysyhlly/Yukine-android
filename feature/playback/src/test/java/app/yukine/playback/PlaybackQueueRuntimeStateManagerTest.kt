package app.yukine.playback

import app.yukine.playback.manager.PlaybackQueueRuntimeStateManager
import org.junit.Assert.assertEquals
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

    @Test
    fun currentIndexStateCanBeManagedAndClamped() {
        val manager = PlaybackQueueRuntimeStateManager()

        assertEquals(-1, manager.currentIndex())

        manager.setCurrentIndex(3)
        assertEquals(3, manager.currentIndex())
        assertEquals(2, manager.clampCurrentIndex(queueSize = 3))

        manager.setClampedCurrentIndex(index = 7, queueSize = 4)
        assertEquals(3, manager.currentIndex())

        manager.setClampedCurrentIndex(index = 2, queueSize = 0)
        assertEquals(-1, manager.currentIndex())
        assertEquals(0, manager.clampCurrentIndex(queueSize = 0))
    }
}
