package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.queue.QueueIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIntentControllerTest {
    @Test
    fun dispatchesEachQueueIntentToTheMatchingHostAction() {
        val calls = mutableListOf<String>()
        val controller = QueueIntentController(
            object : QueueIntentController.Listener {
                override fun playTrackList(tracks: List<Track>, index: Int) {
                    calls += "play:${tracks.size}:$index"
                }

                override fun toggleFavorite(track: Track) {
                    calls += "favorite:${track.id}"
                }

                override fun showAddToPlaylist(track: Track) {
                    calls += "add:${track.id}"
                }

                override fun removeQueueTrack(track: Track) {
                    calls += "remove:${track.id}"
                }

                override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
                    calls += "move:$fromIndex:$toIndex"
                }

                override fun confirmClearQueue() {
                    calls += "clear"
                }

                override fun back() {
                    calls += "back"
                }
            }
        )
        val track = track(7L)
        val tracks = listOf(track(1L), track(2L))

        controller.handle(QueueIntent.PlayAt(tracks, 1))
        controller.handle(QueueIntent.ToggleFavorite(track))
        controller.handle(QueueIntent.AddToPlaylist(track))
        controller.handle(QueueIntent.Remove(track))
        controller.handle(QueueIntent.Move(0, 1))
        controller.handle(QueueIntent.ClearQueue)
        controller.handle(QueueIntent.Back)

        assertEquals(
            listOf("play:2:1", "favorite:7", "add:7", "remove:7", "move:0:1", "clear", "back"),
            calls
        )
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
