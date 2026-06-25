package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaybackControllerTest {

    private lateinit var controller: FakePlaybackController

    @Before
    fun setup() {
        controller = FakePlaybackController()
    }

    @Test
    fun initialState_isEmpty() {
        val state = controller.state.value
        assertEquals(null, state.currentTrack)
        assertEquals(0L, state.positionMs)
        assertFalse(state.playing)
        assertTrue(controller.queue.value.isEmpty())
    }

    @Test
    fun playQueue_updatesStateAndQueue() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, "")
        )

        controller.playQueue(tracks, 0)

        assertEquals(2, controller.queue.value.size)
        assertEquals(tracks[0], controller.state.value.currentTrack)
        assertTrue(controller.state.value.playing)
        assertTrue(controller.hasCommand("playQueue"))
    }

    @Test
    fun togglePlayPause_togglesPlayingState() = runTest {
        controller.setPlaying(false)

        controller.togglePlayPause()
        assertTrue(controller.state.value.playing)

        controller.togglePlayPause()
        assertFalse(controller.state.value.playing)
    }

    @Test
    fun skipToNext_movesToNextTrack() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(3L, "Track 3", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.playQueue(tracks, 0)

        controller.skipToNext()

        assertEquals(tracks[1], controller.state.value.currentTrack)
    }

    @Test
    fun skipToPrevious_movesToPreviousTrack() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(3L, "Track 3", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.playQueue(tracks, 1)

        controller.skipToPrevious()

        assertEquals(tracks[0], controller.state.value.currentTrack)
    }

    @Test
    fun seekTo_updatesPosition() = runTest {
        controller.seekTo(5000L)

        assertEquals(5000L, controller.state.value.positionMs)
    }

    @Test
    fun setShuffleEnabled_updatesShuffleState() = runTest {
        controller.setShuffleEnabled(true)

        assertTrue(controller.state.value.shuffleEnabled)
    }

    @Test
    fun cycleRepeatMode_cyclesThroughModes() = runTest {

        controller.cycleRepeatMode()
        assertEquals(1, controller.state.value.repeatMode) // ALL

        controller.cycleRepeatMode()
        assertEquals(2, controller.state.value.repeatMode) // ONE

        controller.cycleRepeatMode()
        assertEquals(0, controller.state.value.repeatMode) // OFF
    }

    @Test
    fun removeQueueItems_removesTracksById() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(3L, "Track 3", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.setQueue(tracks)

        controller.removeQueueItems(setOf(2L))

        assertEquals(2, controller.queue.value.size)
        assertFalse(controller.queue.value.any { it.id == 2L })
    }

    @Test
    fun clearQueue_emptiesQueueAndStopsPlayback() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.playQueue(tracks, 0)

        controller.clearQueue()

        assertTrue(controller.queue.value.isEmpty())
        assertEquals(null, controller.state.value.currentTrack)
        assertFalse(controller.state.value.playing)
    }

    @Test
    fun appendToQueue_addsTracksToEnd() = runTest {
        val initialTracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        val newTracks = listOf(
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(3L, "Track 3", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.setQueue(initialTracks)

        controller.appendToQueue(newTracks)

        assertEquals(3, controller.queue.value.size)
        assertEquals(3L, controller.queue.value.last().id)
    }

    @Test
    fun moveQueueItem_reordersQueue() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(3L, "Track 3", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.setQueue(tracks)

        controller.moveQueueItem(0, 2)

        assertEquals(1L, controller.queue.value[2].id)
        assertEquals(2L, controller.queue.value[0].id)
    }

    @Test
    fun replaceQueuedTrack_updatesTrackInQueue() = runTest {
        val tracks = listOf(
            Track(1L, "Track 1", "Artist", "Album", 0L, Uri.EMPTY, ""),
            Track(2L, "Track 2", "Artist", "Album", 0L, Uri.EMPTY, "")
        )
        controller.setQueue(tracks)
        val updatedTrack = Track(2L, "Track 2 Updated", "Artist", "Album", 0L, Uri.EMPTY, "")

        controller.replaceQueuedTrack(updatedTrack)

        assertEquals("Track 2 Updated", controller.queue.value[1].title)
    }

    @Test
    fun commands_areRecorded() = runTest {
        controller.playQueue(emptyList(), 0)
        controller.pause()
        controller.seekTo(1000)

        assertEquals(3, controller.commands.size)
        assertTrue(controller.hasCommand("playQueue"))
        assertTrue(controller.hasCommand("pause"))
        assertTrue(controller.hasCommand("seekTo"))
    }

    @Test
    fun clearCommands_clearsCommandHistory() = runTest {
        controller.playQueue(emptyList(), 0)

        controller.clearCommands()

        assertTrue(controller.commands.isEmpty())
    }

    @Test
    fun isServiceConnected_returnsCorrectValue() {
        assertTrue(controller.isServiceConnected())

        controller.setServiceConnected(false)

        assertFalse(controller.isServiceConnected())
    }
}
