package app.echo.next

import app.echo.next.ui.LibraryGroupUiState
import app.echo.next.ui.NetworkSourceUiState
import app.echo.next.ui.PlaylistRowUiState
import app.echo.next.ui.PlaylistTrackUiState
import app.echo.next.ui.QueueTrackUiState
import app.echo.next.ui.TrackRowUiState

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
