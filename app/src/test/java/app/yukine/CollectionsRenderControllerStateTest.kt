package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.navigation.CollectionsTab
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsRenderControllerStateTest {
    @Test
    fun enteringCollectionsLoadsInsightsOffTheRenderPathAndPublishesThem() = runTest {
        val viewModel = CollectionsViewModel()
        val controller = CollectionsRenderController(
            viewModel,
            NoOpCollectionsListener,
            this,
            StandardTestDispatcher(testScheduler)
        )
        val routes = MutableStateFlow(NavigationRouteState())
        val library = MutableStateFlow(LibraryStoreState())
        val settings = MutableStateFlow(SettingsState())
        var loads = 0
        val recentlyAdded = track(8L, "Recently Added")

        controller.bindStateSources(routes, library, settings, EmptyPlaybackReadModel) {
            loads += 1
            CollectionsInsightSnapshot(recentlyAdded = listOf(recentlyAdded))
        }
        advanceUntilIdle()
        assertEquals(0, loads)

        routes.value = NavigationRouteState(selectedTab = CollectionsTab)
        advanceUntilIdle()

        assertEquals(1, loads)
        val section = viewModel.screen.value.trackSections.first { it.key == "recently-added" }
        assertEquals(listOf(8L), section.rows.map { it.id })

        controller.bindStateSources(null, null, null, null, null)
    }

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 120_000L, null, "file:$id.mp3")

    private object EmptyPlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }

    private object NoOpCollectionsListener : CollectionsRenderController.Listener {
        override fun showCreatePlaylist() = Unit
        override fun openPlaylistM3uFilePicker() = Unit
        override fun confirmClearPlayHistory() = Unit
        override fun requestBack() = Unit
        override fun playTrackList(tracks: List<Track>, index: Int) = Unit
        override fun toggleFavorite(track: Track) = Unit
        override fun showAddToPlaylist(track: Track) = Unit
        override fun downloadTrack(track: Track) = Unit
        override fun downloadTracks(tracks: List<Track>) = Unit
        override fun selectPlaylist(playlistId: Long) = Unit
        override fun showRenamePlaylist(playlist: Playlist) = Unit
        override fun confirmDeletePlaylist(playlist: Playlist) = Unit
        override fun openSelectedPlaylistExportDocument() = Unit
        override fun importSelectedPlaylistToStreaming() = Unit
        override fun importFavoritesToStreaming() = Unit
        override fun importStreamingFavorites() = Unit
        override fun syncSelectedPlaylistFromStreaming() = Unit
        override fun moveSelectedPlaylistTrack(
            playlistId: Long,
            track: Track,
            trackIndex: Int,
            direction: Int
        ) = Unit

        override fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) = Unit
    }
}
