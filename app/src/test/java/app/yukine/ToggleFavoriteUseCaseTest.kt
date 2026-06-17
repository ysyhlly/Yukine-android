package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {
    @Test
    fun setsFavoriteForTrack() {
        val operations = FakeFavoriteOperations()
        val track = Track(7L, "Song", "Artist", "Album", 1000L, Uri.EMPTY, "local:7")

        val handled = ToggleFavoriteUseCase(operations).execute(track, true)

        assertTrue(handled)
        assertEquals(listOf("favorite:7|true"), operations.events)
    }

    @Test
    fun ignoresMissingTrack() {
        val operations = FakeFavoriteOperations()

        val handled = ToggleFavoriteUseCase(operations).execute(null, true)

        assertFalse(handled)
        assertEquals(emptyList<String>(), operations.events)
    }

    private class FakeFavoriteOperations : FavoriteOperations {
        val events = mutableListOf<String>()

        override fun setFavorite(track: Track, favorite: Boolean) {
            events.add("favorite:${track.id}|$favorite")
        }
    }
}
