package app.yukine

import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.NetworkSourceUiState
import app.yukine.ui.PlaylistRowUiState
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.QueueTrackUiState
import app.yukine.ui.TrackRowUiState

internal class MainStatePublisher(
    private val viewModel: MainActivityViewModel
) {
    fun publishTrackList(title: String, rows: List<TrackRowUiState>) {
        viewModel.updateTrackList(title, rows)
    }

    fun publishLibraryGroups(title: String, rows: List<LibraryGroupUiState>) {
        viewModel.updateLibraryGroups(title, rows)
    }

    fun publishPlaylistTracks(title: String, rows: List<PlaylistTrackUiState>) {
        viewModel.updatePlaylistTracks(title, rows)
    }

    fun publishQueue(rows: List<QueueTrackUiState>) {
        viewModel.updateQueue(rows)
    }

    fun publishPlaylistList(title: String, rows: List<PlaylistRowUiState>) {
        viewModel.updatePlaylistList(title, rows)
    }

    fun publishNetworkSources(title: String, rows: List<NetworkSourceUiState>) {
        viewModel.updateNetworkSources(title, rows)
    }
}
