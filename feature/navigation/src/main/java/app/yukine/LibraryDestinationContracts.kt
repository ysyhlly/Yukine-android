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

data class LibraryTrackListDestinationState(
    val title: String = "",
    val rows: List<TrackRowUiState> = emptyList(),
    val footerAlbums: List<TrackListAlbumCardUiState> = emptyList(),
    val actions: List<TrackRowActions> = emptyList(),
    val headerMetrics: List<TrackListHeaderMetric> = emptyList(),
    val headerActions: List<TrackListHeaderAction> = emptyList(),
    val emptyText: String = "",
    val modeActions: List<TrackListModeAction> = emptyList(),
    val labels: TrackListLabels = TrackListLabels()
)

data class LibraryGroupsDestinationState(
    val title: String = "",
    val rows: List<LibraryGroupUiState> = emptyList(),
    val actions: List<LibraryGroupActions> = emptyList(),
    val emptyText: String = "",
    val modeActions: List<TrackListModeAction> = emptyList()
)
