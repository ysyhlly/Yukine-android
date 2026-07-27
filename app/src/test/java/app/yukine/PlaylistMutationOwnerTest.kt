package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Playlist
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaylistMutationOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun deletingSelectedPlaylistPatchesStoreWithoutFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = 42L)
        fixture.store.patchAddPlaylist(Playlist(42L, "Mix", 3, 1L, 1L))
        fixture.store.patchSelectedPlaylistTracks(listOf(track(1L)))

        fixture.owner.onPlaylistDeleted(42L, "Mix", deleted = true)

        assertEquals(-1L, fixture.routeController.selectedPlaylistId())
        assertEquals(
            LibraryPlaylistStatusFactory.deleted("Mix", true, AppLanguage.MODE_ENGLISH).status,
            fixture.statuses.single()
        )
        assertEquals(0, fixture.collectionsLoads)
        assertTrue(fixture.store.state.value.playlists.none { it.id == 42L })
        assertTrue(fixture.store.state.value.selectedPlaylistTracks.isEmpty())
    }

    @Test
    fun movingTrackPatchesOrderWithoutFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = 9L)
        val first = track(1L)
        val second = track(7L)
        fixture.store.patchAddPlaylist(Playlist(9L, "Mix", 2, 1L, 1L))
        fixture.store.patchSelectedPlaylistTracks(listOf(first, second))

        fixture.owner.onSelectedPlaylistTrackMoved(
            playlistId = 9L,
            track = second,
            trackIndex = 1,
            direction = -1,
            moved = true
        )

        assertEquals(9L, fixture.routeController.selectedPlaylistId())
        assertEquals(
            LibraryPlaylistStatusFactory.moved(
                second,
                -1,
                true,
                AppLanguage.MODE_ENGLISH
            ).status,
            fixture.statuses.single()
        )
        assertEquals(0, fixture.collectionsLoads)
        assertEquals(listOf(7L, 1L), fixture.store.state.value.selectedPlaylistTracks.map { it.id })
    }

    @Test
    fun renamingPlaylistPatchesNameWithoutFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = 5L)
        fixture.store.patchAddPlaylist(Playlist(5L, "Old", 1, 1L, 1L))

        fixture.owner.onPlaylistRenamed(5L, "New Name", renamed = true)

        assertEquals(0, fixture.collectionsLoads)
        assertEquals("New Name", fixture.store.state.value.playlists.single().name)
        assertEquals(5L, fixture.routeController.selectedPlaylistId())
    }

    @Test
    fun removingTrackPatchesSelectionAndCountWithoutFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = 3L)
        val keep = track(1L)
        val drop = track(2L)
        fixture.store.patchAddPlaylist(Playlist(3L, "Mix", 2, 1L, 1L))
        fixture.store.patchSelectedPlaylistTracks(listOf(keep, drop))

        fixture.owner.onSelectedPlaylistTrackRemoved(3L, drop)

        assertEquals(0, fixture.collectionsLoads)
        assertEquals(listOf(1L), fixture.store.state.value.selectedPlaylistTracks.map { it.id })
        assertEquals(1, fixture.store.state.value.playlists.single().trackCount)
    }

    @Test
    fun creatingPlaylistPatchesListWithoutFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = -1L)

        fixture.owner.onPlaylistCreated(11L, "Fresh")

        assertEquals(0, fixture.collectionsLoads)
        assertEquals(11L, fixture.routeController.selectedPlaylistId())
        assertEquals(listOf(11L), fixture.store.state.value.playlists.map { it.id })
        assertTrue(fixture.store.state.value.selectedPlaylistTracks.isEmpty())
    }

    @Test
    fun failedMoveFallsBackToFullCollectionsLoad() {
        val fixture = fixture(selectedPlaylistId = 9L)
        fixture.store.patchSelectedPlaylistTracks(listOf(track(1L)))

        fixture.owner.onSelectedPlaylistTrackMoved(
            playlistId = 9L,
            track = track(1L),
            trackIndex = 0,
            direction = -1,
            moved = true
        )

        assertEquals(1, fixture.collectionsLoads)
    }

    private fun fixture(selectedPlaylistId: Long): Fixture {
        val navigation = NavigationViewModel(SavedStateHandle())
        val routeController = MainRouteController(navigation)
        routeController.setSelectedPlaylistId(selectedPlaylistId)
        val viewModel = LibraryViewModel()
        val statuses = mutableListOf<String>()
        var collectionsLoads = 0
        val owner = PlaylistMutationOwner(
            viewModel,
            routeController,
            PlaylistMutationOwner.LanguageModeSource { AppLanguage.MODE_ENGLISH },
            PlaylistMutationOwner.StatusSink(statuses::add),
            PlaylistMutationOwner.CollectionsLoader { collectionsLoads++ },
            viewModel.dataOwner()
        )
        return Fixture(owner, routeController, viewModel.dataOwner(), statuses) { collectionsLoads }
    }

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private class Fixture(
        val owner: PlaylistMutationOwner,
        val routeController: MainRouteController,
        val store: LibraryDataStateOwner,
        val statuses: List<String>,
        private val collectionsLoadCount: () -> Int
    ) {
        val collectionsLoads: Int
            get() = collectionsLoadCount()
    }
}
