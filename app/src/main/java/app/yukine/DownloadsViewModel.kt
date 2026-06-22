package app.yukine

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadsUiState(
    val active: List<TrackDownloadItem> = emptyList(),
    val finished: List<TrackDownloadItem> = emptyList(),
    val message: String = ""
)

class DownloadsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    @JvmOverloads
    fun refresh(downloadManager: TrackDownloadController?, message: String = mutableUiState.value.message) {
        val items = downloadManager?.snapshot().orEmpty()
        mutableUiState.value = DownloadsUiState(
            active = items.filter { it.status != TrackDownloadStatus.Finished },
            finished = items.filter { it.status == TrackDownloadStatus.Finished },
            message = message
        )
    }

    fun pause(downloadManager: TrackDownloadController?, downloadId: Long) {
        applyAction(downloadManager) { it.pause(downloadId) }
    }

    fun resume(downloadManager: TrackDownloadController?, downloadId: Long) {
        applyAction(downloadManager) { it.resume(downloadId) }
    }

    fun pauseAll(downloadManager: TrackDownloadController?) {
        applyAction(downloadManager) { it.pauseAll() }
    }

    fun resumeAll(downloadManager: TrackDownloadController?) {
        applyAction(downloadManager) { it.resumeAll() }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = "")
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
