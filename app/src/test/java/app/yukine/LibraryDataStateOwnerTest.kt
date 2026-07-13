package app.yukine

import android.net.Uri
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDataStateOwnerTest {
    private val owner = LibraryDataStateOwner(
        CoroutineScope(Dispatchers.Unconfined),
        Dispatchers.Unconfined
    )

    @Test
    fun favoriteMutationsUpdateTheOwnedLibrarySnapshot() {
        val first = track(1L)
        val second = track(2L)
        owner.replaceLibrary(listOf(first, second), emptySet(), null)

        owner.setFavorite(second.id, true)
        assertEquals(setOf(second.id), owner.state.value.favoriteTrackIds)
        assertEquals(listOf(second), owner.state.value.favoriteTracks)

        assertEquals(false, owner.toggleFavorite(second.id))
        assertEquals(emptySet<Long>(), owner.state.value.favoriteTrackIds)
        assertEquals(emptyList<Track>(), owner.state.value.favoriteTracks)
    }

    @Test
    fun searchCombinesLibraryAndSelectedPlaylistWithoutDuplicateTracks() {
        val shared = track(2L, "Shared Echo")
        owner.replaceLibrary(listOf(track(1L, "Alpha"), shared), emptySet(), null)
        owner.applyCollections(
            LibraryCollectionsResult(
                selectedPlaylistTracks = listOf(shared, track(3L, "Playlist Echo"))
            )
        )

        owner.applySearch("echo")

        assertEquals(listOf(2L, 3L), owner.visibleTracks().map { it.id })
    }

    private fun track(id: Long, title: String = "Track $id"): Track =
        Track(id, title, "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
