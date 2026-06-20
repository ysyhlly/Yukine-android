package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsReloadControllerTest {
    @Test
    fun reloadCurrentLyricsLoadsCurrentTrackAndPublishesReloadingStatus() {
        val track = track(7L)
        val listener = FakeLyricsReloadListener(track)
        val controller = LyricsReloadController(listener)

        controller.reloadCurrentLyrics()

        assertEquals(listOf("load:7:provider:7"), listener.calls)
        assertEquals(listOf("Reloading lyrics"), listener.statuses)
    }

    @Test
    fun reloadCurrentLyricsStillPublishesNoTrackStatusWhenTrackIsMissing() {
        val listener = FakeLyricsReloadListener(null)
        val controller = LyricsReloadController(listener)

        controller.reloadCurrentLyrics()

        assertEquals(listOf("load:null:"), listener.calls)
        assertEquals(emptyList<Track?>(), listener.providerTracks)
        assertEquals(listOf("No track selected"), listener.statuses)
    }

    private class FakeLyricsReloadListener(
        private val track: Track?
    ) : LyricsReloadController.Listener {
        val calls = mutableListOf<String>()
        val providerTracks = mutableListOf<Track?>()
        val statuses = mutableListOf<String>()

        override fun currentTrack(): Track? = track

        override fun providerTrackId(track: Track?): String {
            providerTracks += track
            return track?.let { "provider:${it.id}" }.orEmpty()
        }

        override fun loadLyrics(track: Track?, providerTrackId: String) {
            calls += "load:${track?.id ?: "null"}:$providerTrackId"
        }

        override fun noTrackSelectedStatus(): String = "No track selected"

        override fun reloadingLyricsStatus(): String = "Reloading lyrics"

        override fun setStatus(status: String) {
            statuses += status
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
