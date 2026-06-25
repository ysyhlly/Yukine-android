package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class QueueRenderBindingsTest {
    @Test
    fun forwardsQueueActionsAndPublishesChromeCopies() {
        val calls = mutableListOf<String>()
        val events = mutableListOf<LibraryEvent>()
        val tracks = listOf(track(1L), track(2L))
        val actions = mutableListOf(QueueTrackActions(Runnable { }, Runnable { }, Runnable { }, Runnable { }))
        val labels = QueueScreenLabels(title = "Queue")
        val clear = Runnable { calls += "clearRunnable" }
        val back = Runnable { calls += "backRunnable" }
        var chromeState: QueueChromeState? = null
        val bindings = QueueRenderBindings(
            playTrackListAction = TrackListPlaybackAction { nextTracks, index ->
                calls += "play:${nextTracks.size}:$index"
            },
            libraryEventSink = LibraryEventSink { events += it },
            addToPlaylistAction = QueueTrackAction { calls += "playlist:${it.id}" },
            removeQueueTrackAction = QueueTrackAction { calls += "remove:${it.id}" },
            confirmClearQueueAction = Runnable { calls += "confirmClear" },
            requestBackAction = Runnable { calls += "back" },
            chromeSink = QueueChromeSink { chromeState = it }
        )

        bindings.playTrackList(tracks, 1)
        bindings.toggleFavorite(tracks[0])
        bindings.showAddToPlaylist(tracks[1])
        bindings.removeQueueTrack(tracks[0])
        bindings.confirmClearQueue()
        bindings.requestBack()
        bindings.publishQueueChrome(actions, clear, labels, back)

        assertEquals(
            listOf("play:2:1", "playlist:2", "remove:1", "confirmClear", "back"),
            calls
        )
        assertEquals(listOf(LibraryEvent.ToggleFavorite(tracks[0])), events)
        assertEquals(actions, chromeState?.actions)
        assertNotSame(actions, chromeState?.actions)
        assertEquals(clear, chromeState?.onClearQueue)
        assertEquals(labels, chromeState?.labels)
        assertEquals(back, chromeState?.onBack)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

}
