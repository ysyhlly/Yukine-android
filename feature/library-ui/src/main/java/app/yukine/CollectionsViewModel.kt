package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.CollectionsActions
import app.yukine.ui.emptyCollectionsActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaylistDetailUiState(
    val playlistId: Long,
    val title: String,
    val trackCount: Int
)

data class CollectionsViewState(
    val selectedPlaylist: PlaylistDetailUiState? = null,
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val recentlyPlayed: List<TrackPlayRecord> = emptyList(),
    val mostPlayed: List<TrackPlayRecord> = emptyList()
)

class CollectionsViewModel : ViewModel(), CollectionsDestinationStateProvider {
    private val _uiState = MutableStateFlow(CollectionsViewState())
    val uiState: StateFlow<CollectionsViewState> = _uiState.asStateFlow()

    private val screenState = MutableStateFlow(emptyScreenState())
    override val screen: StateFlow<CollectionsUiState> = screenState.asStateFlow()

    fun updateCollections(
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        mostPlayedRecords: List<TrackPlayRecord>,
        playlists: List<Playlist>,
        selectedPlaylistTracks: List<Track>,
        selectedPlaylistId: Long,
        fallbackPlaylistTitle: String
    ) {
        _uiState.value = CollectionsViewState(
            selectedPlaylist = selectedPlaylist(playlists, selectedPlaylistTracks, selectedPlaylistId, fallbackPlaylistTitle),
            playlists = playlists.toList(),
            favorites = favoriteTracks.toList(),
            recentlyPlayed = recentRecords.toList(),
            mostPlayed = mostPlayedRecords.toList()
        )
    }

    fun updateScreen(state: CollectionsUiState) {
        screenState.value = state
    }

    fun updateScreenWithActions(state: CollectionsUiState, actions: CollectionsActions) {
        screenState.value = state.copy(actions = actions)
    }

    fun updateActions(actions: CollectionsActions) {
        screenState.value = screenState.value.copy(actions = actions)
    }

    private fun selectedPlaylist(
        playlists: List<Playlist>,
        selectedPlaylistTracks: List<Track>,
        selectedPlaylistId: Long,
        fallbackPlaylistTitle: String
    ): PlaylistDetailUiState? {
        if (selectedPlaylistId < 0L) {
            return null
        }
        val playlist = playlists.firstOrNull { it.id == selectedPlaylistId }
        return PlaylistDetailUiState(
            playlistId = selectedPlaylistId,
            title = playlist?.name ?: fallbackPlaylistTitle,
            trackCount = selectedPlaylistTracks.size
        )
    }

    private companion object {
        fun emptyScreenState(): CollectionsUiState {
            return CollectionsUiState(
                title = "",
                backLabel = "",
                metrics = emptyList(),
                topActions = emptyList(),
                trackSections = emptyList(),
                playlistTitle = "",
                playlistEmptyText = "",
                playlistEmptyDescription = "",
                playlists = emptyList(),
                selectedPlaylistVisible = false,
                selectedPlaylistTitle = "",
                selectedPlaylistEmptyText = "",
                selectedPlaylistEmptyDescription = "",
                selectedPlaylistTopActions = emptyList(),
                selectedPlaylistTracks = emptyList(),
                actions = emptyCollectionsActions()
            )
        }
    }
}
