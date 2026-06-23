package app.yukine.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var directoryLabel by remember(downloadManager) {
        mutableStateOf(downloadManager?.downloadDirectoryLabel().orEmpty())
    }
    LaunchedEffect(viewModel, downloadManager) {
        while (true) {
            viewModel.refresh(downloadManager)
            directoryLabel = downloadManager?.downloadDirectoryLabel().orEmpty()
            delay(1000L)
        }
    }
    val state by viewModel.uiState.collectAsState()
    DownloadsScreen(
        state = state,
        directoryLabel = directoryLabel,
        onUseMusicDirectory = {
            downloadManager?.setDownloadDirectory(TrackDownloadManager.DOWNLOAD_DIRECTORY_MUSIC)
            directoryLabel = downloadManager?.downloadDirectoryLabel().orEmpty()
            viewModel.refresh(downloadManager)
        },
        onUseDownloadsDirectory = {
            downloadManager?.setDownloadDirectory(TrackDownloadManager.DOWNLOAD_DIRECTORY_DOWNLOADS)
            directoryLabel = downloadManager?.downloadDirectoryLabel().orEmpty()
            viewModel.refresh(downloadManager)
        },
        onChooseDirectory = {
            onChooseDirectory.run()
            directoryLabel = downloadManager?.downloadDirectoryLabel().orEmpty()
            viewModel.refresh(downloadManager)
        },
        onPauseItem = { id -> viewModel.pause(downloadManager, id) },
        onResumeItem = { id -> viewModel.resume(downloadManager, id) },
        onPauseAll = { viewModel.pauseAll(downloadManager) },
        onResumeAll = { viewModel.resumeAll(downloadManager) }
    )
}
