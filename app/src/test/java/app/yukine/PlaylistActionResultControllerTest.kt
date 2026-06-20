package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistActionResultControllerTest {
    @Test
    fun handlesCreateRenameAndDeleteResults() {
        val fixture = fixture(selectedPlaylistId = 9L)

        fixture.controller.onPlaylistCreated(12L)
        fixture.controller.onPlaylistCreated(-1L)
        fixture.controller.onPlaylistRenamed(13L, true)
        fixture.controller.onPlaylistRenamed(14L, false)
        fixture.controller.onPlaylistDeleted(9L, "Old", true)
        fixture.controller.onPlaylistDeleted(10L, "Other", true)
        fixture.controller.onPlaylistDeleted(11L, "Failed", false)

        assertEquals(listOf(12L, 13L, -1L), fixture.selectedPlaylistWrites)
        assertEquals(
            listOf(
                "Playlist created",
                "Playlist created",
                "Playlist renamed",
                "Could not rename playlist",
                "Deleted playlist: Old",
                "Deleted playlist: Other",
                "Could not delete playlist"
            ),
            fixture.statuses
        )
        assertEquals(7, fixture.reloads)
    }

    @Test
    fun handlesTrackPlaylistResults() {
        val fixture = fixture()
        val track = Track(7L, "Moon", "Artist", "Album", 1000L, Uri.EMPTY, "file:7")

        fixture.controller.onDefaultPlaylistTrackAdded(21L, true)
        fixture.controller.onDefaultPlaylistTrackAdded(22L, false)
        fixture.controller.onSelectedPlaylistTrackRemoved(23L, track)
        fixture.controller.onSelectedPlaylistTrackMoved(24L, track, -1, true)
        fixture.controller.onSelectedPlaylistTrackMoved(25L, track, 1, false)
        fixture.controller.onTrackAddedToPlaylist(26L, true)

        assertEquals(listOf(21L, 22L, 23L, 24L, 25L, 26L), fixture.selectedPlaylistWrites)
        assertEquals(
            listOf(
                "Added to playlist",
                "Could not add to playlist",
                "Removed from playlist: Moon",
                "Moved up: Moon",
                "Could not move track",
                "Added to playlist"
            ),
            fixture.statuses
        )
        assertEquals(6, fixture.reloads)
    }

    private fun fixture(selectedPlaylistId: Long = -1L): Fixture {
        val selectedPlaylistWrites = mutableListOf<Long>()
        val statuses = mutableListOf<String>()
        var reloads = 0
        val controller = PlaylistActionResultController(
            viewModel = LibraryViewModel(),
            languageModeProvider = PlaylistActionLanguageModeProvider { AppLanguage.MODE_ENGLISH },
            selectedPlaylistIdProvider = SelectedPlaylistIdProvider { selectedPlaylistId },
            selectedPlaylistSink = SelectedPlaylistSink { selectedPlaylistWrites += it },
            statusSink = PlaylistActionStatusSink { statuses += it },
            collectionsReloader = CollectionsReloader { reloads += 1 }
        )
        return Fixture(controller, selectedPlaylistWrites, statuses) { reloads }
    }

    private class Fixture(
        val controller: PlaylistActionResultController,
        val selectedPlaylistWrites: List<Long>,
        val statuses: List<String>,
        private val reloadProvider: () -> Int
    ) {
        val reloads: Int
            get() = reloadProvider()
    }
}
