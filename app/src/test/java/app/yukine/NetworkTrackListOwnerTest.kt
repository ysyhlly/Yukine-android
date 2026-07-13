package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.ui.TrackListLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkTrackListOwnerTest {
    @Test
    fun ownsTypedNetworkTrackListActionsAndPublication() {
        val calls = mutableListOf<String>()
        val published = mutableListOf<NetworkTrackListRequest>()
        val source = RemoteSource(
            8L,
            RemoteSource.TYPE_WEBDAV,
            "NAS",
            "https://example.test",
            "",
            "",
            "",
            "",
            0L
        )
        val request = NetworkTrackListRequest(
            title = "Songs",
            tracks = emptyList(),
            showPlaylistAction = true,
            details = emptyList(),
            showStreamActions = false,
            headerMetrics = emptyList(),
            headerActions = emptyList(),
            emptyText = "Empty",
            labels = TrackListLabels()
        )
        val owner = NetworkTrackListOwner(
            navigate = { calls += "navigate:$it" },
            clearSelectedSource = { calls += "clear" },
            syncSource = { calls += "sync:$it" },
            playSource = { calls += "play:${it.id}" },
            publishRequest = published::add
        )

        owner.navigateNetworkPage("streams")
        owner.clearRemoteSourceAndNavigateNetworkPage("sources")
        owner.syncRemoteSource(source.id)
        owner.playRemoteSourceTracks(source)
        owner.publish(request)

        assertEquals(
            listOf("navigate:streams", "clear", "navigate:sources", "sync:8", "play:8"),
            calls
        )
        assertSame(request, published.single())
    }
}
