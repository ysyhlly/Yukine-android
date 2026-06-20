package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.queue.QueueIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIntentBindingsTest {
    @Test
    fun forwardsQueueIntentActionsToAppBridges() {
        val events = mutableListOf<LibraryEvent>()
        val calls = mutableListOf<String>()
        val bindings = QueueIntentBindings(
            libraryEventSink = LibraryEventSink { events += it },
            addToPlaylistAction = TrackAction { calls += "add:${it.id}" },
            removeQueueTrackAction = TrackAction { calls += "remove:${it.id}" },
            moveQueueTrackAction = QueueTrackMoveAction { fromIndex, toIndex -> calls += "move:$fromIndex:$toIndex" },
            clearQueueConfirmer = QueueNoArgAction { calls += "clear" },
            backHandler = QueueNoArgAction { calls += "back" }
        )
        val track = track(3L)

        bindings.playTrackList(listOf(track), 0)
        bindings.toggleFavorite(track)
        bindings.showAddToPlaylist(track)
        bindings.removeQueueTrack(track)
        bindings.moveQueueTrack(2, 4)
        bindings.confirmClearQueue()
        bindings.back()

        assertEquals(
            listOf(
                LibraryEvent.PlayTrackList(listOf(track), 0),
                LibraryEvent.ToggleFavorite(track)
            ),
            events
        )
        assertEquals(listOf("add:3", "remove:3", "move:2:4", "clear", "back"), calls)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
