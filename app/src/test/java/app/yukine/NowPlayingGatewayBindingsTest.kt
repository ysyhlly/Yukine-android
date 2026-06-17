package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingGatewayBindingsTest {
    @Test
    fun delegatesNowPlayingGatewayCallsToBindings() {
        val calls = mutableListOf<String>()
        var currentTrack: Track? = Track(
            12L,
            "Song",
            "Artist",
            "Album",
            180000L,
            Uri.EMPTY,
            "file:song.mp3"
        )
        val gateway = NowPlayingGatewayBindings(
            playPauseAction = Runnable { calls += "playPause" },
            nextAction = Runnable { calls += "next" },
            previousAction = Runnable { calls += "previous" },
            seekAction = NowPlayingLongAction { positionMs -> calls += "seek:$positionMs" },
            currentTrackProvider = NowPlayingCurrentTrackProvider { currentTrack },
            favoriteAction = NowPlayingTrackAction { track -> calls += "favorite:${track.id}" },
            shuffleAction = Runnable { calls += "shuffle" },
            repeatAction = Runnable { calls += "repeat" },
            statusMessageProvider = NowPlayingStatusMessageProvider { key -> "text:$key" }
        )

        gateway.playPause()
        gateway.next()
        gateway.previous()
        gateway.seekTo(42_000L)
        gateway.toggleFavorite()
        currentTrack = null
        gateway.toggleFavorite()
        gateway.toggleShuffle()
        gateway.cycleRepeatMode()
        val status = gateway.statusMessage("no.track.selected")

        assertEquals("text:no.track.selected", status)
        assertEquals(
            listOf(
                "playPause",
                "next",
                "previous",
                "seek:42000",
                "favorite:12",
                "shuffle",
                "repeat"
            ),
            calls
        )
    }
}
