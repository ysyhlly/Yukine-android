package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.queue.QueueIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIntentOwnerTest {
    @Test
    fun dispatchesEveryTypedQueueIntentToItsFocusedOwner() {
        val track = Track(7L, "Song", "Artist", "Album", 1_000L, Uri.EMPTY, "file:7")
        val calls = mutableListOf<String>()
        val owner = QueueIntentOwner(
            dispatchLibraryEvent = { event ->
                calls += when (event) {
                    is LibraryEvent.PlayTrackList -> "play:${event.tracks.single().id}:${event.index}"
                    is LibraryEvent.ToggleFavorite -> "favorite:${event.track.id}"
                    else -> error("unexpected library event: $event")
                }
            },
            showAddToPlaylist = { calls += "playlist:${it.id}" },
            removeTrack = { calls += "remove:${it.id}" },
            moveTrack = { from, to -> calls += "move:$from:$to" },
            confirmClear = { calls += "clear" },
            navigateBack = { calls += "back" }
        )

        owner.onIntent(QueueIntent.PlayAt(listOf(track), 0))
        owner.onIntent(QueueIntent.ToggleFavorite(track))
        owner.onIntent(QueueIntent.AddToPlaylist(track))
        owner.onIntent(QueueIntent.Remove(track))
        owner.onIntent(QueueIntent.Move(3, 1))
        owner.onIntent(QueueIntent.ClearQueue)
        owner.onIntent(QueueIntent.Back)

        assertEquals(
            listOf("play:7:0", "favorite:7", "playlist:7", "remove:7", "move:3:1", "clear", "back"),
            calls
        )
    }
}
