package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class TrackListRenderBindingsTest {
    @Test
    fun forwardsTrackActionsAndPublishesChromeStateCopies() {
        val calls = mutableListOf<String>()
        val events = mutableListOf<LibraryEvent>()
        val track = track(7L)
        val actions = mutableListOf<TrackRowActions>()
        val headerMetrics = mutableListOf(TrackListHeaderMetric("Songs", "1"))
        val headerActions = mutableListOf(TrackListHeaderAction("Scan", Runnable { }))
        val modeActions = mutableListOf(TrackListModeAction("songs", "Songs", true, Runnable { }))
        val labels = TrackListLabels()
        var chromeState: TrackListChromeState? = null
        val bindings = TrackListRenderBindings(
            libraryEventSink = LibraryEventSink { events += it },
            editStreamAction = TrackAction { calls += "edit:${it.id}" },
            confirmDeleteTrackAction = TrackAction { calls += "delete:${it.id}" },
            downloadTrackAction = TrackAction { calls += "download:${it.id}" },
            downloadTracksAction = TrackListAction { calls += "downloadList:${it.size}" },
            chromeSink = TrackListChromeSink { chromeState = it }
        )

        bindings.playTrackList(listOf(track), 0)
        bindings.toggleFavorite(track)
        bindings.showAddToPlaylist(track)
        bindings.downloadTrack(track)
        bindings.downloadTracks(listOf(track))
        bindings.showEditStream(track)
        bindings.confirmDeleteTrack(track)
        bindings.publishTrackListChrome(actions, headerMetrics, headerActions, "Empty", modeActions, labels)

        assertEquals(listOf("download:7", "downloadList:1", "edit:7", "delete:7"), calls)
        assertEquals(
            listOf(
                LibraryEvent.PlayTrackList(listOf(track), 0),
                LibraryEvent.ToggleFavorite(track),
                LibraryEvent.AddToPlaylist(track)
            ),
            events
        )
        assertEquals("Empty", chromeState?.emptyText)
        assertEquals(labels, chromeState?.labels)
        assertEquals(actions, chromeState?.actions)
        assertEquals(headerMetrics, chromeState?.headerMetrics)
        assertEquals(headerActions, chromeState?.headerActions)
        assertEquals(modeActions, chromeState?.modeActions)
        assertNotSame(actions, chromeState?.actions)
        assertNotSame(headerMetrics, chromeState?.headerMetrics)
        assertNotSame(headerActions, chromeState?.headerActions)
        assertNotSame(modeActions, chromeState?.modeActions)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

}
