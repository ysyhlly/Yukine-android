package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LyricsReloadBindingsTest {
    @Test
    fun forwardsReloadDependenciesToBoundOperations() {
        val track = track(5L)
        val calls = mutableListOf<String>()
        var loadedTrack: Track? = null
        val bindings = LyricsReloadBindings(
            currentTrackProvider = CurrentTrackProvider { track },
            providerTrackIdProvider = LyricsProviderTrackIdProvider {
                calls += "provider:${it?.id}"
                "netease:${it?.id}"
            },
            lyricsReloadLoader = LyricsReloadLoader { nextTrack, providerTrackId ->
                loadedTrack = nextTrack
                calls += "load:${nextTrack?.id}:$providerTrackId"
            },
            statusTextProvider = LyricsStatusTextProvider { key -> "text:$key" },
            statusSink = QueueStatusSink { calls += "status:$it" }
        )

        assertSame(track, bindings.currentTrack())
        assertEquals("netease:5", bindings.providerTrackId(track))
        bindings.loadLyrics(track, "netease:5")
        assertEquals("text:no.track.selected", bindings.noTrackSelectedStatus())
        assertEquals("text:reloading.lyrics", bindings.reloadingLyricsStatus())
        bindings.setStatus("ready")

        assertSame(track, loadedTrack)
        assertEquals(
            listOf("provider:5", "load:5:netease:5", "status:ready"),
            calls
        )
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
