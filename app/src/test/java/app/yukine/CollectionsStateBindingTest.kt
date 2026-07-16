package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.navigation.CollectionsTab
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsStateBindingTest {
    @Test
    fun enteringCollectionsLoadsInsightsOffTheRenderPathAndPublishesThem() = runTest {
        val viewModel = CollectionsViewModel()
        val controller = CollectionsStateBinding(
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

        controller.bindStateSources(routes, library, settings, EmptyPlaybackReadModel) { _ ->
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

    @Test
    fun ordinaryPlaylistsAreGroupedBySourceWithoutChangingFavoriteSection() {
        val viewModel = CollectionsViewModel()
        val controller = CollectionsStateBinding(viewModel, NoOpCollectionsListener)
        val favorite = track(90L, "Favorite")
        val playlists = listOf(
            Playlist(1L, "Local list", 2, 0L, 0L),
            Playlist(2L, "Cloud A", 3, 0L, 0L),
            Playlist(3L, "QQ list", 4, 0L, 0L),
            Playlist(4L, "Cloud B", 5, 0L, 0L)
        )

        controller.reduceAndPublish(
            languageMode = AppLanguage.MODE_CHINESE,
            favoriteTracks = listOf(favorite),
            recentRecords = emptyList(),
            mostPlayedRecords = emptyList(),
            playlists = playlists,
            selectedPlaylistTracks = emptyList(),
            selectedPlaylistId = -1L,
            playbackState = null,
            favoriteIds = setOf(favorite.id),
            playlistSources = mapOf(
                2L to StreamingProviderName.NETEASE,
                3L to StreamingProviderName.QQ_MUSIC,
                4L to StreamingProviderName.NETEASE
            )
        )

        val screen = viewModel.screen.value
        assertEquals(listOf("local", "netease", "qqmusic"), screen.playlistFolders.map { it.key })
        assertEquals(listOf("Cloud A", "Cloud B"), screen.playlistFolders[1].playlists.map { it.name })
        assertEquals("2 \u4e2a\u6b4c\u5355 \u00b7 8 \u9996\u6b4c\u66f2", screen.playlistFolders[1].subtitle)
        assertEquals(listOf(favorite.id), screen.trackSections.first { it.key == "favorites" }.rows.map { it.id })

        controller.release()
    }

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 120_000L, null, "file:$id.mp3")

    private object EmptyPlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }

    private object NoOpCollectionsListener : CollectionsStateBinding.Listener {
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
