package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingEffectBindingsTest {
    @Test
    fun forwardsNowPlayingEffectsToActivityEdges() {
        val calls = mutableListOf<String>()
        val track = Track(9L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:9")
        val bindings = NowPlayingEffectBindings(
            queueOpener = NowPlayingQueueOpener { calls += "queue" },
            addToPlaylistOpener = NowPlayingAddToPlaylistOpener { calls += "playlist:${it.track.id}" },
            trackSharer = NowPlayingTrackSharer { calls += "share:${it.track.id}" },
            trackDownloader = NowPlayingTrackDownloader { calls += "download:${it.track.id}" },
            statusSink = QueueStatusSink { calls += "status:$it" }
        )

        bindings.openQueue()
        bindings.openAddToPlaylist(NowPlayingEffect.OpenAddToPlaylist(track))
        bindings.shareTrack(NowPlayingEffect.ShareTrack(track))
        bindings.downloadTrack(NowPlayingEffect.DownloadTrack(track))
        bindings.showMessage("Ready")

        assertEquals(listOf("queue", "playlist:9", "share:9", "download:9", "status:Ready"), calls)
    }
}
