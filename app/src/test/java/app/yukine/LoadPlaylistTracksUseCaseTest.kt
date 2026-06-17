package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadPlaylistTracksUseCaseTest {
    @Test
    fun loadsTracksForValidPlaylist() {
        val operations = FakePlaylistTrackOperations()
        operations.tracks = listOf(track(1L), track(2L))

        val tracks = LoadPlaylistTracksUseCase(operations).execute(5L)

        assertEquals(listOf(1L, 2L), tracks.map { it.id })
        assertEquals(listOf("load:5"), operations.events)
    }

    @Test
    fun ignoresInvalidPlaylistId() {
        val operations = FakePlaylistTrackOperations()

        val tracks = LoadPlaylistTracksUseCase(operations).execute(-1L)

        assertEquals(emptyList<Track>(), tracks)
        assertEquals(emptyList<String>(), operations.events)
    }

    private class FakePlaylistTrackOperations : PlaylistTrackOperations {
        var tracks: List<Track> = emptyList()
        val events = mutableListOf<String>()

        override fun loadPlaylistTracks(playlistId: Long): List<Track> {
            events.add("load:$playlistId")
            return tracks
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "local:$id")
}
