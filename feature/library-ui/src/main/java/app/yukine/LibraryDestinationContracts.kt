package app.yukine

import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import app.yukine.ui.LibraryUiState
import app.yukine.ui.LibraryPlaylistFolderUiState

enum class LibraryListContext {
    Songs,
    Album,
    Artist,
    Folder,
    Playlist
}

data class LibraryTrackListDestinationState @JvmOverloads constructor(
    val title: String = "",
    val rows: List<TrackRowUiState> = emptyList(),
    val footerAlbums: List<TrackListAlbumCardUiState> = emptyList(),
    val actions: List<TrackRowActions> = emptyList(),
    val headerMetrics: List<TrackListHeaderMetric> = emptyList(),
    val headerActions: List<TrackListHeaderAction> = emptyList(),
    val emptyText: String = "",
    val modeActions: List<TrackListModeAction> = emptyList(),
    val labels: TrackListLabels = TrackListLabels(),
    val libraryUi: LibraryUiState = LibraryUiState(),
    val context: LibraryListContext = LibraryListContext.Songs
)

data class LibraryGroupsDestinationState @JvmOverloads constructor(
    val title: String = "",
    val rows: List<LibraryGroupUiState> = emptyList(),
    val actions: List<LibraryGroupActions> = emptyList(),
    val emptyText: String = "",
    val modeActions: List<TrackListModeAction> = emptyList(),
    val libraryUi: LibraryUiState = LibraryUiState(),
    val playlistFolders: List<LibraryPlaylistFolderUiState> = emptyList()
)
