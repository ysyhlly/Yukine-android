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

    @Test
    fun togglesFavoriteForTrack() {
        val operations = FakeFavoriteOperations(favoriteIds = mutableSetOf(7L))
        val track = Track(7L, "Song", "Artist", "Album", 1000L, Uri.EMPTY, "local:7")

        val handled = ToggleFavoriteUseCase(operations).toggle(track)

        assertTrue(handled)
        assertEquals(listOf("isFavorite:7", "favorite:7|false"), operations.events)
    }

    @Test
    fun togglesMissingTrackSafely() {
        val operations = FakeFavoriteOperations(favoriteIds = mutableSetOf(7L))

        val handled = ToggleFavoriteUseCase(operations).toggle(null)

        assertFalse(handled)
        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun checksFavoriteStateForTrack() {
        val operations = FakeFavoriteOperations(favoriteIds = mutableSetOf(7L))
        val favoriteTrack = Track(7L, "Song", "Artist", "Album", 1000L, Uri.EMPTY, "local:7")
        val otherTrack = Track(8L, "Other", "Artist", "Album", 1000L, Uri.EMPTY, "local:8")
        val useCase = ToggleFavoriteUseCase(operations)

        assertTrue(useCase.isFavorite(favoriteTrack))
        assertFalse(useCase.isFavorite(otherTrack))
        assertFalse(useCase.isFavorite(null))
        assertEquals(listOf("isFavorite:7", "isFavorite:8"), operations.events)
    }

    private class FakeFavoriteOperations : FavoriteOperations {
        constructor(favoriteIds: MutableSet<Long> = mutableSetOf()) {
            this.favoriteIds = favoriteIds
        }

        val events = mutableListOf<String>()
        private val favoriteIds: MutableSet<Long>

        override fun isFavorite(trackId: Long): Boolean {
            events.add("isFavorite:$trackId")
            return favoriteIds.contains(trackId)
        }

        override fun setFavorite(track: Track, favorite: Boolean) {
            events.add("favorite:${track.id}|$favorite")
            if (favorite) {
                favoriteIds.add(track.id)
            } else {
                favoriteIds.remove(track.id)
            }
        }
    }
}
