package app.yukine.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yukine.CollectionsDestinationStateProvider
import app.yukine.DownloadsDestinationActions
import app.yukine.DownloadsUiState
import app.yukine.HomeDashboardDestinationState
import app.yukine.LibraryGroupsDestinationState
import app.yukine.LibraryStoreState
import app.yukine.NavigationRouteState
import app.yukine.StreamingSearchState
import app.yukine.LibraryTrackListDestinationState
import app.yukine.RecordingMatchDestinationStateProvider
import app.yukine.ui.LibraryActionHandler
import app.yukine.NetworkMenuUiState
import app.yukine.NetworkSourcesUiState
import app.yukine.NowPlayingScreenStateProvider
import app.yukine.PlaybackSnapshotProvider
import app.yukine.QueueDestinationState
import app.yukine.QueueDestinationStateProvider
import app.yukine.SettingsChromeState
import app.yukine.SettingsDestinationState
import app.yukine.TrackDownloadController
import app.yukine.UnifiedSearchUiState
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.SettingsListScrollState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

private val EmptyRealtimeBands = FloatArray(0)

fun interface QueueSheetVisibilityListener {
    fun onQueueSheetVisibilityChanged(visible: Boolean)
}

data class PlayerNavBinding(
    val nowPlayingStateProvider: NowPlayingScreenStateProvider,
    val queueStateProvider: QueueDestinationStateProvider = EmptyQueueDestinationStateProvider,
    val playbackSnapshotProvider: PlaybackSnapshotProvider,
    val trackDownloadController: TrackDownloadController? = null,
    val realtimeBeatProvider: () -> Float = { 0f },
    val realtimeBandsProvider: () -> FloatArray = { EmptyRealtimeBands },
    val visualMotionEnabled: Boolean = true
)

data class LibraryNavBinding(
    val homeDashboardState: StateFlow<HomeDashboardDestinationState>,
    val libraryGroupsState: StateFlow<LibraryGroupsDestinationState>,
    val libraryTrackListState: StateFlow<LibraryTrackListDestinationState>,
    val libraryStoreState: StateFlow<LibraryStoreState> = MutableStateFlow(LibraryStoreState()),
    val collectionsStateProvider: CollectionsDestinationStateProvider,
    val downloadsState: StateFlow<DownloadsUiState> = MutableStateFlow(DownloadsUiState()),
    val downloadsOpenDirectoryRequests: Flow<Unit> = emptyFlow(),
    val downloadsActions: DownloadsDestinationActions = DownloadsDestinationActions(),
    val searchState: StateFlow<UnifiedSearchUiState> = MutableStateFlow(UnifiedSearchUiState()),
    val libraryActionHandler: LibraryActionHandler = LibraryActionHandler { },
    val openPlayHistoryAction: Runnable = Runnable { },
    val openNetworkSourcesAction: Runnable = Runnable { },
    val recordingMatchStateProvider: RecordingMatchDestinationStateProvider? = null
)

data class SettingsNavBinding(
    val settingsState: StateFlow<SettingsDestinationState>,
    val settingsChromeState: StateFlow<SettingsChromeState>,
    val settingsScrollState: SettingsListScrollState,
    val networkMenuState: StateFlow<NetworkMenuUiState>,
    val networkSourcesState: StateFlow<NetworkSourcesUiState>
)

data class StreamingNavBinding(
    val streamingState: StateFlow<StreamingSearchState>
)

class EchoNavHostState @JvmOverloads constructor(
    val routeState: StateFlow<NavigationRouteState>,
    val player: PlayerNavBinding,
    val library: LibraryNavBinding,
    val settings: SettingsNavBinding,
    val streaming: StreamingNavBinding,
    private val queueSheetVisibilityListener: QueueSheetVisibilityListener =
        QueueSheetVisibilityListener { }
) {
    var queueSheetVisible by mutableStateOf(false)
        private set

    fun setQueueSheetVisibility(visible: Boolean) {
        if (queueSheetVisible == visible) {
            return
        }
        queueSheetVisible = visible
        queueSheetVisibilityListener.onQueueSheetVisibilityChanged(visible)
    }
}

private object EmptyQueueDestinationStateProvider : QueueDestinationStateProvider {
    override val uiState: StateFlow<QueueDestinationState> = MutableStateFlow(QueueDestinationState())
    override val labels: StateFlow<QueueScreenLabels> = MutableStateFlow(QueueScreenLabels())

    override fun onPlayAt(index: Int) = Unit
    override fun onToggleFavorite(index: Int) = Unit
    override fun onAddToPlaylist(index: Int) = Unit
    override fun onRemove(index: Int) = Unit
    override fun onMove(fromIndex: Int, toIndex: Int) = Unit
    override fun onClearQueue() = Unit
    override fun onBack() = Unit
}
