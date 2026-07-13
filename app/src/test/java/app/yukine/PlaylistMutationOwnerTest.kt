package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistMutationOwnerTest {
    @Test
    fun deletingSelectedPlaylistClearsRouteAndRefreshesCollections() {
        val fixture = fixture(selectedPlaylistId = 42L)

        fixture.owner.onPlaylistDeleted(42L, "Mix", deleted = true)

        assertEquals(-1L, fixture.routeController.selectedPlaylistId())
        assertEquals(
            fixture.viewModel.playlistDeletedPresentation("Mix", true, AppLanguage.MODE_ENGLISH).status,
            fixture.statuses.single()
        )
        assertEquals(1, fixture.collectionsLoads)
    }

    @Test
    fun movingTrackKeepsPlaylistSelectedAndPublishesMutationResult() {
        val fixture = fixture(selectedPlaylistId = -1L)
        val track = Track(7L, "Song", "Artist", "Album", 1_000L, Uri.EMPTY, "file:7")

        fixture.owner.onSelectedPlaylistTrackMoved(9L, track, direction = -1, moved = true)

        assertEquals(9L, fixture.routeController.selectedPlaylistId())
        assertEquals(
            fixture.viewModel.selectedPlaylistTrackMovedPresentation(
                track,
                -1,
                true,
                AppLanguage.MODE_ENGLISH
            ).status,
            fixture.statuses.single()
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
            PlaylistMutationOwner.CollectionsLoader { collectionsLoads++ }
        )
        return Fixture(owner, routeController, viewModel, statuses) { collectionsLoads }
    }

    private class Fixture(
        val owner: PlaylistMutationOwner,
        val routeController: MainRouteController,
        val viewModel: LibraryViewModel,
        val statuses: List<String>,
        private val collectionsLoadCount: () -> Int
    ) {
        val collectionsLoads: Int
            get() = collectionsLoadCount()
    }
}
