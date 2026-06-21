package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySmallGatewayBindingsTest {
    @Test
    fun favoriteWriterForwardsToToggleFavoriteUseCase() {
        val operations = FakeFavoriteOperations()
        val writer = LibraryFavoriteWriterBindings(ToggleFavoriteUseCase(operations))
        val track = track(7L)

        val handled = writer.writeFavorite(track, true)

        assertTrue(handled)
        assertEquals(listOf("favorite:7|true"), operations.events)
    }

    @Test
    fun playlistTrackLoaderForwardsToLoadPlaylistTracksUseCase() {
        val operations = FakePlaylistTrackOperations()
        operations.tracks = listOf(track(1L), track(2L))
        val loader = LibraryPlaylistTrackLoaderBindings(LoadPlaylistTracksUseCase(operations))

        val tracks = loader.loadPlaylistTracks(5L)

        assertEquals(listOf(1L, 2L), tracks.map { it.id })
        assertEquals(listOf("load:5"), operations.events)
    }

    @Test
    fun playlistTrackLoaderKeepsUseCaseInvalidIdGuard() {
        val operations = FakePlaylistTrackOperations()
        val loader = LibraryPlaylistTrackLoaderBindings(LoadPlaylistTracksUseCase(operations))

        val tracks = loader.loadPlaylistTracks(-1L)

        assertEquals(emptyList<Track>(), tracks)
        assertEquals(emptyList<String>(), operations.events)
    }

    private class FakeFavoriteOperations : FavoriteOperations {
        val events = mutableListOf<String>()

        override fun setFavorite(track: Track, favorite: Boolean) {
            events.add("favorite:${track.id}|$favorite")
        }
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
