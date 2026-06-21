package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMenuActionBindingsTest {
    @Test
    fun forwardsNetworkMenuDialogsAndPlayback() {
        val calls = mutableListOf<String>()
        val dialogs = NetworkMenuDialogBindings(
            showAddStreamAction = Runnable { calls += "addStream" },
            showImportM3uAction = Runnable { calls += "importM3u" },
            showAddWebDavAction = Runnable { calls += "addWebDav" }
        )
        val player = NetworkMenuPlayerBindings(
            TrackListPlaybackAction { tracks, index ->
                calls += "play:${tracks.size}:$index:${tracks.first().id}"
            }
        )

        dialogs.showAddStream()
        dialogs.showImportM3u()
        dialogs.showAddWebDav()
        player.playTrackList(listOf(track(4L)), 0)

        assertEquals(
            listOf(
                "addStream",
                "importM3u",
                "addWebDav",
                "play:1:0:4"
            ),
            calls
        )
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
