package app.yukine.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.DownloadsEffect
import app.yukine.DownloadsViewModel
import app.yukine.TrackDownloadManager
import app.yukine.ui.DownloadsScreen
import kotlinx.coroutines.delay

@Composable
fun DownloadsDestination(
    viewModel: DownloadsViewModel,
    downloadManager: TrackDownloadManager?,
    onChooseDirectory: Runnable = Runnable { }
) {
    LaunchedEffect(viewModel, downloadManager) {
        while (true) {
            viewModel.refresh(downloadManager)
            delay(1000L)
        }
    }
    LaunchedEffect(viewModel, onChooseDirectory) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DownloadsEffect.OpenDirectoryPicker -> onChooseDirectory.run()
            }
        }
    }
    val state by viewModel.uiState.collectAsState()
    DownloadsScreen(
        state = state,
        directoryLabel = state.directoryLabel,
        onUseMusicDirectory = { viewModel.useMusicDirectory(downloadManager) },
        onUseDownloadsDirectory = { viewModel.useDownloadsDirectory(downloadManager) },
        onChooseDirectory = { viewModel.chooseDirectory(downloadManager) },
        onPauseItem = { id -> viewModel.pause(downloadManager, id) },
        onResumeItem = { id -> viewModel.resume(downloadManager, id) },
        onRemoveItem = { id -> viewModel.remove(downloadManager, id) },
        onPauseAll = { viewModel.pauseAll(downloadManager) },
        onResumeAll = { viewModel.resumeAll(downloadManager) }
    )
}
