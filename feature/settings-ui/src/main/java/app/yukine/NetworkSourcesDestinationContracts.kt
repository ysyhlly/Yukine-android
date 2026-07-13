package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.NetworkSourceUiState
import app.yukine.ui.TrackListHeaderAction

data class NetworkSourcesUiState(
    val title: String = "",
    val sources: List<RemoteSource> = emptyList(),
    val rows: List<NetworkSourceUiState> = emptyList(),
    val actions: List<NetworkSourceActions> = emptyList(),
    val headerActions: List<TrackListHeaderAction> = emptyList(),
    val emptyText: String = "",
    val labels: NetworkSourceLabels = NetworkSourceLabels(),
    val selectedSourceId: Long = -1L,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null
)
