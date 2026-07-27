package app.yukine

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.yukine.model.Playlist
import app.yukine.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsIntentOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun toggleFavoriteRoutesThroughLibraryViewModelWithoutAdapterGraph() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        val gateway = RecordingGateway()
        viewModel.bindGateway(gateway)
        viewModel.bindFavoriteWriter { _, _ -> true }
        viewModel.bindFavoriteIdsProvider { emptySet() }
        viewModel.data.replaceLibrary(listOf(track(7L)), emptySet(), null)

        val owner = CollectionsIntentOwner(
            viewModel = viewModel,
            playlistMutationOwner = mutationOwner(viewModel)
        )
        owner.toggleFavorite(track(7L))
        advanceUntilIdle()

        assertTrue(gateway.calls.any { it.startsWith("favorite:7:") })
        assertEquals(setOf(7L), viewModel.data.favoriteIds())
    }

    @Test
    fun commitRenamePatchesStoreWithoutFullCollectionsLoad() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LibraryViewModel(dispatcher)
        var collectionsLoads = 0
        val mutationOwner = PlaylistMutationOwner(
            viewModel,
            MainRouteController(NavigationViewModel(SavedStateHandle())),
            PlaylistMutationOwner.LanguageModeSource { AppLanguage.MODE_ENGLISH },
            PlaylistMutationOwner.StatusSink { },
            PlaylistMutationOwner.CollectionsLoader { collectionsLoads++ },
            viewModel.dataOwner()
        )
        viewModel.data.patchAddPlaylist(Playlist(4L, "Old", 1, 1L, 1L))
        viewModel.bindPlaylistActionGateway(object : LibraryPlaylistActionGateway {
            override fun addToDefaultPlaylist(track: Track?) = null
            override fun createPlaylist(name: String) = -1L
            override fun renamePlaylist(playlistId: Long, name: String) = true
            override fun deletePlaylist(playlistId: Long) = false
            override fun removeTrackFromPlaylist(playlistId: Long, track: Track?) = false
            override fun movePlaylistTrack(
                playlistId: Long,
                track: Track?,
                trackIndex: Int,
                direction: Int
            ) = false
            override fun addTrackToPlaylist(playlistId: Long, trackId: Long) = false
        })

        val owner = CollectionsIntentOwner(viewModel, mutationOwner)
        owner.commitRenamePlaylist(4L, "Renamed")
        advanceUntilIdle()

        assertEquals(0, collectionsLoads)
        assertEquals("Renamed", viewModel.data.state.value.playlists.single().name)
    }

    @Test
    fun playDelegatesToInjectedPlayer() {
        val played = mutableListOf<String>()
        val viewModel = LibraryViewModel()
        val owner = CollectionsIntentOwner(
            viewModel = viewModel,
            playlistMutationOwner = mutationOwner(viewModel),
            playTrackList = { tracks, index -> played += "play:${tracks.size}:$index" }
        )
        owner.play(listOf(track(1L), track(2L)), 1)
        assertEquals(listOf("play:2:1"), played)
    }

    private fun mutationOwner(viewModel: LibraryViewModel): PlaylistMutationOwner =
        PlaylistMutationOwner(
            viewModel,
            MainRouteController(NavigationViewModel(SavedStateHandle())),
            PlaylistMutationOwner.LanguageModeSource { AppLanguage.MODE_ENGLISH },
            PlaylistMutationOwner.StatusSink { },
            PlaylistMutationOwner.CollectionsLoader { }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")

    private class RecordingGateway : LibraryGateway {
        val calls = mutableListOf<String>()
        override fun playTrackList(tracks: List<Track>, index: Int) {
            calls += "play:${tracks.size}:$index"
        }
        override fun showStatusKey(key: String) {
            calls += "status:$key"
        }
        override fun applyFavorite(trackId: Long, favorite: Boolean) {
            calls += "favorite:$trackId:$favorite"
        }
        override fun addToPlaylist(track: Track) {
            calls += "add:${track.id}"
        }
        override fun changeGroupMode(mode: String) = Unit
        override fun openGroup(key: String, title: String) = Unit
        override fun openPlaylist(playlistId: Long, title: String) = Unit
        override fun backFromGroup() = Unit
        override fun search(query: String) = Unit
        override fun importFiles() = Unit
        override fun scanLibrary() = Unit
    }
}
