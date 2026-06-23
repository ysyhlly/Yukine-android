package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.ArrayList

class LibraryGroupsRenderBindingsTest {
    @Test
    fun forwardsGroupEventsAndPublishesChromeAndTrackListRequests() {
        val calls = mutableListOf<String>()
        val events = mutableListOf<LibraryEvent>()
        val tracks = listOf(track(1L), track(2L))
        val actions = mutableListOf(LibraryGroupActions(Runnable { }, Runnable { }, true, Runnable { }))
        val modeActions = mutableListOf(TrackListModeAction("artists", "Artists", true, Runnable { }))
        val headerMetrics = arrayListOf(TrackListHeaderMetric("Songs", "2"))
        val headerActions = arrayListOf(TrackListHeaderAction("Back", Runnable { }))
        var chromeState: LibraryGroupsChromeState? = null
        var trackListRequest: LibraryGroupTrackListRequest? = null
        val bindings = LibraryGroupsRenderBindings(
            libraryEventSink = LibraryEventSink { events += it },
            libraryGroupSelectionClearer = LibraryGroupSelectionClearer { calls += "clear" },
            openFavoritesCollectionAction = Runnable { calls += "favorites" },
            confirmDeleteGroupAction = TrackGroupDeleteConfirmer { title, groupTracks ->
                calls += "delete:$title:${groupTracks.size}"
            },
            chromeSink = LibraryGroupsChromeSink { chromeState = it },
            trackListRenderer = LibraryGroupTrackListRenderer { trackListRequest = it }
        )

        bindings.selectLibraryGroup("artist:a", "Artist A")
        bindings.clearLibraryGroupSelection()
        bindings.closeLibraryGroup()
        bindings.openFavoritesCollection()
        bindings.playTrackList(tracks, 1)
        bindings.confirmDeleteGroup("Artist A", tracks)
        bindings.publishLibraryGroupsChrome(actions, "No artists", modeActions)
        bindings.renderTrackList("Artist A", ArrayList(tracks), headerMetrics, headerActions)

        assertEquals(listOf("clear", "favorites", "delete:Artist A:2"), calls)
        assertEquals(
            listOf(
                LibraryEvent.OpenGroup("artist:a", "Artist A"),
                LibraryEvent.BackFromGroup,
                LibraryEvent.PlayTrackList(tracks, 1)
            ),
            events
        )
        assertEquals("No artists", chromeState?.emptyText)
        assertEquals(actions, chromeState?.actions)
        assertEquals(modeActions, chromeState?.modeActions)
        assertNotSame(actions, chromeState?.actions)
        assertNotSame(modeActions, chromeState?.modeActions)
        assertEquals("Artist A", trackListRequest?.title)
        assertEquals(tracks, trackListRequest?.tracks)
        assertSame(headerMetrics, trackListRequest?.headerMetrics)
        assertSame(headerActions, trackListRequest?.headerActions)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }
}
