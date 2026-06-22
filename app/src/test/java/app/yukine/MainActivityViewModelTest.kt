package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityViewModelTest {
    @Test
    fun setFavoriteAddsTrackToFavoritePlaylistSnapshot() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val first = track(1L)
        val second = track(2L)
        viewModel.updateLibrary(
            MainActivityLibraryState(
                allTracks = listOf(first, second),
                visibleTracks = listOf(first, second)
            )
        )

        viewModel.setFavorite(2L, true)

        val library = viewModel.library.value
        assertEquals(setOf(2L), library.favoriteTrackIds)
        assertEquals(listOf(second), library.favoriteTracks)
    }

    @Test
    fun toggleFavoriteRemovesTrackFromFavoritePlaylistSnapshot() {
        val viewModel = MainActivityViewModel(SavedStateHandle())
        val first = track(1L)
        val second = track(2L)
        viewModel.updateLibrary(
            MainActivityLibraryState(
                allTracks = listOf(first, second),
                visibleTracks = listOf(first, second),
                favoriteTrackIds = setOf(1L, 2L),
                favoriteTracks = listOf(first, second)
            )
        )

        val favorite = viewModel.toggleFavorite(1L)

        val library = viewModel.library.value
        assertEquals(false, favorite)
        assertEquals(setOf(2L), library.favoriteTrackIds)
        assertEquals(listOf(second), library.favoriteTracks)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
