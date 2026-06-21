package app.yukine

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadsUiState(
    val active: List<TrackDownloadItem> = emptyList(),
    val finished: List<TrackDownloadItem> = emptyList()
)

class DownloadsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    fun refresh(downloadManager: TrackDownloadManager?) {
        val items = downloadManager?.snapshot().orEmpty()
        mutableUiState.value = DownloadsUiState(
            active = items.filter { it.status != TrackDownloadStatus.Finished },
            finished = items.filter { it.status == TrackDownloadStatus.Finished }
        )
    }
}
