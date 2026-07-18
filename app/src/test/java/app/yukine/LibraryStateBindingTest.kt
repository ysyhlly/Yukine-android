package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.navigation.LibraryTab
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupSort
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStateBindingTest {
    @Test
    fun sortAndFilterActionsImmediatelyRepublishVisibleRows() {
        val viewModel = LibraryViewModel()
        val tracks = listOf(
            track(1L, "Zulu", "file:1"),
            track(2L, "Alpha", "stream:2")
        )
        viewModel.data.replaceLibrary(tracks, emptySet(), "")
        val binding = LibraryStateBinding(
            libraryStore = viewModel.data,
            viewModel = viewModel,
            trackListReducer = TrackListStateReducer(viewModel, NoOpTrackListener),
            groupsReducer = LibraryGroupsStateReducer(viewModel, NoOpGroupsListener),
            playlistsReducer = LibraryPlaylistsStateReducer(viewModel, NoOpPlaylistsListener),
            audioPermissionReader = LibraryAudioPermissionReader { true },
            statusSink = LibraryStatusSink { },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        binding.bindStateSources(
            MutableStateFlow(NavigationRouteState(selectedTab = LibraryTab)),
            viewModel.library,
            MutableStateFlow(SettingsState()),
            EmptyPlayback
        )

        assertEquals(listOf("Alpha", "Zulu"), viewModel.trackList.value.rows.map { it.title })

        viewModel.presentation.onAction(
            LibraryAction.SortChanged(app.yukine.ui.LibrarySort.TitleDescending)
        )
        assertEquals(listOf("Zulu", "Alpha"), viewModel.trackList.value.rows.map { it.title })

        viewModel.presentation.onAction(LibraryAction.FilterChanged(LibraryFilter.Network))
        assertEquals(listOf("Alpha"), viewModel.trackList.value.rows.map { it.title })

        binding.release()
    }

    @Test
    fun groupSortImmediatelyRepublishesAlbumRows() {
        val viewModel = LibraryViewModel()
        val tracks = listOf(
            track(1L, "One", "file:1", "Small"),
            track(2L, "Two", "file:2", "Large"),
            track(3L, "Three", "file:3", "Large")
        )
        viewModel.data.replaceLibrary(tracks, emptySet(), "")
        val binding = LibraryStateBinding(
            libraryStore = viewModel.data,
            viewModel = viewModel,
            trackListReducer = TrackListStateReducer(viewModel, NoOpTrackListener),
            groupsReducer = LibraryGroupsStateReducer(viewModel, NoOpGroupsListener),
            playlistsReducer = LibraryPlaylistsStateReducer(viewModel, NoOpPlaylistsListener),
            audioPermissionReader = LibraryAudioPermissionReader { true },
            statusSink = LibraryStatusSink { },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        binding.bindStateSources(
            MutableStateFlow(
                NavigationRouteState(
                    selectedTab = LibraryTab,
                    libraryMode = LibraryGrouping.ALBUMS
                )
            ),
            viewModel.library,
            MutableStateFlow(SettingsState()),
            EmptyPlayback
        )

        assertEquals(listOf("Large", "Small"), viewModel.libraryGroups.value.rows.map { it.title })

        viewModel.presentation.onAction(
            LibraryAction.GroupSortChanged(LibraryGroupSort.TrackCountAscending)
        )
        assertEquals(listOf("Small", "Large"), viewModel.libraryGroups.value.rows.map { it.title })

        binding.release()
    }

    private fun track(id: Long, title: String, path: String, album: String = "Album"): Track =
        Track(id, title, "Artist", album, 120_000L, Uri.EMPTY, path)

    private object EmptyPlayback : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }

    private object NoOpTrackListener : TrackListStateReducer.Listener {
        override fun playTrackList(tracks: List<Track>, index: Int) = Unit
        override fun toggleFavorite(track: Track) = Unit
        override fun showAddToPlaylist(track: Track) = Unit
        override fun downloadTrack(track: Track) = Unit
        override fun downloadTracks(tracks: List<Track>) = Unit
        override fun showEditStream(track: Track) = Unit
        override fun confirmDeleteTrack(track: Track) = Unit
    }

    private object NoOpGroupsListener : LibraryGroupsStateReducer.Listener {
        override fun selectLibraryGroup(key: String, title: String) = Unit
        override fun clearLibraryGroupSelection() = Unit
        override fun closeLibraryGroup() = Unit
        override fun openFavoritesCollection() = Unit
        override fun playTrackList(tracks: List<Track>, index: Int) = Unit
        override fun confirmDeleteGroup(title: String, tracks: List<Track>) = Unit
        override fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        ) = Unit

        override fun publishTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>,
            footerAlbums: ArrayList<TrackListAlbumCardUiState>,
            context: LibraryListContext
        ) = Unit
    }

    private object NoOpPlaylistsListener : LibraryPlaylistsStateReducer.Listener {
        override fun openFavoritePlaylist(title: String) = Unit
        override fun openPlayHistory(title: String) = Unit
        override fun openPlaylist(playlistId: Long, title: String) = Unit
        override fun playPlaylist(playlistId: Long) = Unit
        override fun confirmDeletePlaylist(playlist: Playlist) = Unit
        override fun backFromPlaylist() = Unit
        override fun playTrackList(tracks: List<Track>, index: Int) = Unit
        override fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState) = Unit
        override fun publishPlaylistTracks(request: LibraryPlaylistTrackListRequest) = Unit
    }
}
