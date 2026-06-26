package app.yukine

import android.net.Uri
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList

class NetworkMenuEventControllerPlaybackTest {
    @Test
    fun playAllMenuActionsUseBoundPlaybackActionDirectly() {
        val calls = mutableListOf<String>()
        val controller = NetworkMenuEventController(
            navigator = { _ -> },
            showAddStreamAction = Runnable { Unit },
            showImportM3uAction = Runnable { Unit },
            showAddWebDavAction = Runnable { Unit },
            documentPicker = { },
            streamTracksProvider = { arrayListOf(track(1L)) },
            streamTrackCountProvider = { 1 },
            webDavTracksProvider = { arrayListOf(track(2L)) },
            remoteSourcesProvider = { listOf(remoteSource(3L, RemoteSource.TYPE_WEBDAV)) },
            requests = { _ -> },
            deleteConfirmation = { },
            player = TrackListPlaybackAction { tracks, index ->
                calls += "play:${tracks.first().id}:$index"
            },
            labels = { it },
            statusSink = { calls += "status:$it" },
            contentSink = { _, _, _ -> }
        )

        controller.playAllStreams()
        controller.playAllWebDavTracks()

        assertEquals(listOf("play:1:0", "play:2:0"), calls)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private fun remoteSource(id: Long, type: String): RemoteSource =
        RemoteSource(id, type, "Source $id", "https://example.test", "", "", "/", "", 0L)
}
