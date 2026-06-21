package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkTrackListRenderBindingsTest {
    @Test
    fun forwardsNetworkTrackListActionsAndRenderRequests() {
        val calls = mutableListOf<String>()
        val tracks = listOf(track(1L), track(2L))
        val source = RemoteSource(9L, "webdav", "Source", "https://example.test", "", "", "", "", 0L)
        val headerMetrics = listOf(TrackListHeaderMetric("Tracks", "2"))
        val headerActions = listOf(TrackListHeaderAction("Back", Runnable { }))
        val labels = TrackListLabels()
        var request: NetworkTrackListRequest? = null
        val bindings = NetworkTrackListRenderBindings(
            navigateNetworkPageAction = NetworkPageAction { calls += "navigate:$it" },
            clearRemoteSourceAndNavigateAction = NetworkPageAction { calls += "clearNavigate:$it" },
            syncRemoteSourceAction = RemoteSourceIdAction { calls += "sync:$it" },
            playRemoteSourceTracksAction = RemoteSourceAction { calls += "source:${it.id}" },
            playTrackListAction = TrackListPlaybackAction { nextTracks, index ->
                calls += "play:${nextTracks.size}:$index"
            },
            trackListRenderer = NetworkTrackListRenderer { request = it }
        )

        bindings.navigateNetworkPage(MainRoutes.NETWORK_STREAMING)
        bindings.clearRemoteSourceAndNavigateNetworkPage(MainRoutes.NETWORK_SOURCES)
        bindings.syncRemoteSource(source.id)
        bindings.playRemoteSourceTracks(source)
        bindings.playTrackList(tracks, 1)
        bindings.renderTrackList(
            title = "Songs",
            tracks = tracks,
            showPlaylistAction = true,
            details = listOf("A", "B"),
            showStreamActions = false,
            headerMetrics = headerMetrics,
            headerActions = headerActions,
            emptyText = "Empty",
            labels = labels
        )

        assertEquals(
            listOf(
                "navigate:${MainRoutes.NETWORK_STREAMING}",
                "clearNavigate:${MainRoutes.NETWORK_SOURCES}",
                "sync:9",
                "source:9",
                "play:2:1"
            ),
            calls
        )
        assertEquals("Songs", request?.title)
        assertSame(tracks, request?.tracks)
        assertEquals(listOf("A", "B"), request?.details)
        assertEquals(true, request?.showPlaylistAction)
        assertEquals(false, request?.showStreamActions)
        assertSame(headerMetrics, request?.headerMetrics)
        assertSame(headerActions, request?.headerActions)
        assertEquals("Empty", request?.emptyText)
        assertSame(labels, request?.labels)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }
}
