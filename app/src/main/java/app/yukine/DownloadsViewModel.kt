package app.yukine

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transform

sealed interface DownloadsEffect {
    data object OpenDirectoryPicker : DownloadsEffect
}

class DownloadsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<DownloadsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<DownloadsEffect> = mutableEffects.asSharedFlow()

    @JvmOverloads
    fun refresh(downloadManager: TrackDownloadController?, message: String = mutableUiState.value.message) {
        val items = downloadManager?.snapshot().orEmpty()
        val directoryLabel = (downloadManager as? TrackDownloadDirectoryController)
            ?.downloadDirectoryLabel()
            .orEmpty()
        mutableUiState.value = DownloadsUiState(
            active = items.filter { it.status != TrackDownloadStatus.Finished },
            finished = items.filter { it.status == TrackDownloadStatus.Finished },
            directoryLabel = directoryLabel,
            message = message
        )
    }

    fun pause(downloadManager: TrackDownloadController?, downloadId: Long) {
        applyAction(downloadManager) { it.pause(downloadId) }
    }

    fun resume(downloadManager: TrackDownloadController?, downloadId: Long) {
        applyAction(downloadManager) { it.resume(downloadId) }
    }

    fun remove(downloadManager: TrackDownloadController?, downloadId: Long) {
        applyAction(downloadManager) { it.remove(downloadId) }
    }

    fun pauseAll(downloadManager: TrackDownloadController?) {
        applyAction(downloadManager) { it.pauseAll() }
    }

    fun resumeAll(downloadManager: TrackDownloadController?) {
        applyAction(downloadManager) { it.resumeAll() }
    }

    fun useMusicDirectory(downloadManager: TrackDownloadDirectoryController?) {
        setDirectory(downloadManager, TrackDownloadManager.DOWNLOAD_DIRECTORY_MUSIC)
    }

    fun useDownloadsDirectory(downloadManager: TrackDownloadDirectoryController?) {
        setDirectory(downloadManager, TrackDownloadManager.DOWNLOAD_DIRECTORY_DOWNLOADS)
    }

    fun chooseDirectory(downloadManager: TrackDownloadDirectoryController?) {
        if (downloadManager == null) {
            mutableUiState.value = mutableUiState.value.copy(message = "下载服务暂不可用")
            return
        }
        mutableEffects.tryEmit(DownloadsEffect.OpenDirectoryPicker)
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = "")
    }

    fun openDirectoryRequests(): Flow<Unit> =
        effects.transform { effect ->
            when (effect) {
                DownloadsEffect.OpenDirectoryPicker -> emit(Unit)
            }
        }

    private fun setDirectory(
        downloadManager: TrackDownloadDirectoryController?,
        directory: String
    ) {
        if (downloadManager == null) {
            mutableUiState.value = mutableUiState.value.copy(message = "下载服务暂不可用")
            return
        }
        downloadManager.setDownloadDirectory(directory)
        refresh(downloadManager, "已设置下载目录：${downloadManager.downloadDirectoryLabel()}")
    }

    private fun applyAction(
        downloadManager: TrackDownloadController?,
        action: (TrackDownloadController) -> TrackDownloadActionResult
    ) {
        if (downloadManager == null) {
            mutableUiState.value = mutableUiState.value.copy(message = "下载服务暂不可用")
            return
        }
        val result = action(downloadManager)
        refresh(downloadManager, result.message)
    }
}
