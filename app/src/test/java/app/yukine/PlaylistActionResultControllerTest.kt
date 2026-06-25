package app.yukine

import android.net.Uri
import app.yukine.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistActionResultControllerTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

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

    @Test
    fun playlistActionEntrypointsDelegateThroughViewModelAndPublishResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = fixture(viewModel = LibraryViewModel(dispatcher), selectedPlaylistId = 8L)
        val gateway = FakePlaylistActionGateway()
        fixture.viewModel.bindPlaylistActionGateway(gateway)
        val track = Track(7L, "Moon", "Artist", "Album", 1000L, Uri.EMPTY, "file:7")

        fixture.controller.addToDefaultPlaylist(track)
        fixture.controller.createPlaylist("Daily")
        fixture.controller.renamePlaylist(12L, "Renamed")
        fixture.controller.deletePlaylist(8L, "Old")
        fixture.controller.removeSelectedPlaylistTrack(12L, track)
        fixture.controller.moveSelectedPlaylistTrack(12L, track, 2, -1)
        fixture.controller.addTrackToPlaylist(12L, 7L)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "default:7",
                "create:Daily",
                "rename:12:Renamed",
                "delete:8",
                "remove:12:7",
                "move:12:7:2:-1",
                "add:12:7"
            ),
            gateway.calls
        )
        assertEquals(listOf(30L, 31L, 12L, -1L, 12L, 12L, 12L), fixture.selectedPlaylistWrites)
        assertEquals(
            listOf(
                "Added to playlist",
                "Playlist created",
                "Playlist renamed",
                "Deleted playlist: Old",
                "Removed from playlist: Moon",
                "Moved up: Moon",
                "Added to playlist"
            ),
            fixture.statuses
        )
        assertEquals(7, fixture.reloads)
    }

    private fun fixture(
        viewModel: LibraryViewModel = LibraryViewModel(),
        selectedPlaylistId: Long = -1L
    ): Fixture {
        val selectedPlaylistWrites = mutableListOf<Long>()
        val statuses = mutableListOf<String>()
        var reloads = 0
        val controller = PlaylistActionResultController(
            viewModel = viewModel,
            languageModeProvider = PlaylistActionLanguageModeProvider { AppLanguage.MODE_ENGLISH },
            selectedPlaylistIdProvider = SelectedPlaylistIdProvider { selectedPlaylistId },
            selectedPlaylistSink = SelectedPlaylistSink { selectedPlaylistWrites += it },
            statusSink = PlaylistActionStatusSink { statuses += it },
            collectionsReloader = CollectionsReloader { reloads += 1 }
        )
        return Fixture(viewModel, controller, selectedPlaylistWrites, statuses) { reloads }
    }

    private class Fixture(
        val viewModel: LibraryViewModel,
        val controller: PlaylistActionResultController,
        val selectedPlaylistWrites: List<Long>,
        val statuses: List<String>,
        private val reloadProvider: () -> Int
    ) {
        val reloads: Int
            get() = reloadProvider()
    }

    private class FakePlaylistActionGateway : LibraryPlaylistActionGateway {
        val calls = ArrayList<String>()

        override fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi? {
            calls.add("default:${track?.id}")
            return LibraryDefaultPlaylistAddResultUi(30L, true)
        }

        override fun createPlaylist(name: String): Long {
            calls.add("create:$name")
            return 31L
        }

        override fun renamePlaylist(playlistId: Long, name: String): Boolean {
            calls.add("rename:$playlistId:$name")
            return true
        }

        override fun deletePlaylist(playlistId: Long): Boolean {
            calls.add("delete:$playlistId")
            return true
        }

        override fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean {
            calls.add("remove:$playlistId:${track?.id}")
            return true
        }

        override fun movePlaylistTrack(
            playlistId: Long,
            track: Track?,
            trackIndex: Int,
            direction: Int
        ): Boolean {
            calls.add("move:$playlistId:${track?.id}:$trackIndex:$direction")
            return true
        }

        override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
            calls.add("add:$playlistId:$trackId")
            return true
        }
    }
}
