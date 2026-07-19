package app.yukine.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.yukine.DownloadsDestinationActions
import app.yukine.DownloadsUiState
import app.yukine.ui.DownloadsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DownloadsDestination(
    state: StateFlow<DownloadsUiState>,
    openDirectoryRequests: Flow<Unit>,
    actions: DownloadsDestinationActions = DownloadsDestinationActions()
) {
    val latestActions by rememberUpdatedState(actions)
    LaunchedEffect(openDirectoryRequests) {
        openDirectoryRequests.collect {
            latestActions.openDirectoryPicker()
        }
    }
    val uiState by state.collectAsState()
    DownloadsScreen(
        state = uiState,
        directoryLabel = uiState.directoryLabel,
        onUseMusicDirectory = { latestActions.useMusicDirectory() },
        onUseDownloadsDirectory = { latestActions.useDownloadsDirectory() },
        onChooseDirectory = { latestActions.chooseDirectory() },
        onPauseItem = { id -> latestActions.pauseItem(id) },
        onResumeItem = { id -> latestActions.resumeItem(id) },
        onRemoveItem = { id -> latestActions.removeItem(id) },
        onPauseAll = { latestActions.pauseAll() },
        onResumeAll = { latestActions.resumeAll() }
    )
}
