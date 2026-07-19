package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

sealed interface DownloadsEffect {
    data object OpenDirectoryPicker : DownloadsEffect
}

class DownloadsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<DownloadsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<DownloadsEffect> = mutableEffects.asSharedFlow()
    private var boundController: TrackDownloadController? = null
    private var changesJob: Job? = null
    private var legacyPollingJob: Job? = null

    fun bind(downloadManager: TrackDownloadController?) {
        if (boundController === downloadManager && changesJob?.isActive == true) return
        changesJob?.cancel()
        legacyPollingJob?.cancel()
        changesJob = null
        legacyPollingJob = null
        boundController = downloadManager
        if (downloadManager == null) {
            refresh(null)
            return
        }
        changesJob = viewModelScope.launch {
            downloadManager.changes
                .onStart { emit(Unit) }
                .conflate()
                .collect {
                    refresh(downloadManager)
                    delay(DOWNLOAD_CHANGE_WINDOW_MS)
                }
        }
    }

    @JvmOverloads
    fun refresh(downloadManager: TrackDownloadController?, message: String = mutableUiState.value.message) {
        val items = downloadManager?.snapshot().orEmpty()
        val directoryLabel = (downloadManager as? TrackDownloadDirectoryController)
            ?.downloadDirectoryLabel()
            .orEmpty()
        publish(items, directoryLabel, message)
        updateLegacyPolling(downloadManager, items)
    }

    @JvmOverloads
    fun refreshDirectory(
        downloadManager: TrackDownloadDirectoryController?,
        message: String = mutableUiState.value.message
    ) {
        val items = downloadManager?.snapshot().orEmpty()
        publish(items, downloadManager?.downloadDirectoryLabel().orEmpty(), message)
        updateLegacyPolling(downloadManager, items)
    }

    private fun publish(items: List<TrackDownloadItem>, directoryLabel: String, message: String) {
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
        setDirectory(downloadManager, DOWNLOAD_DIRECTORY_MUSIC)
    }

    fun useDownloadsDirectory(downloadManager: TrackDownloadDirectoryController?) {
        setDirectory(downloadManager, DOWNLOAD_DIRECTORY_DOWNLOADS)
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
        refreshDirectory(downloadManager, "已设置下载目录：${downloadManager.downloadDirectoryLabel()}")
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

    private fun updateLegacyPolling(
        downloadManager: TrackDownloadController?,
        items: List<TrackDownloadItem>
    ) {
        val shouldPoll = downloadManager != null &&
            boundController === downloadManager &&
            items.any(::isActiveLegacySystemDownload)
        if (!shouldPoll) {
            legacyPollingJob?.cancel()
            legacyPollingJob = null
            return
        }
        if (legacyPollingJob?.isActive == true) return
        val controller = downloadManager ?: return
        val job = viewModelScope.launch {
            while (boundController === controller) {
                delay(LEGACY_DOWNLOAD_POLL_MS)
                val latestItems = controller.snapshot()
                publish(
                    latestItems,
                    (controller as? TrackDownloadDirectoryController)
                        ?.downloadDirectoryLabel()
                        .orEmpty(),
                    mutableUiState.value.message
                )
                if (latestItems.none(::isActiveLegacySystemDownload)) break
            }
        }
        legacyPollingJob = job
        job.invokeOnCompletion {
            if (legacyPollingJob === job) {
                legacyPollingJob = null
            }
        }
    }

    private fun isActiveLegacySystemDownload(item: TrackDownloadItem): Boolean =
        item.downloadId >= 0L && (
            item.status == TrackDownloadStatus.Pending ||
                item.status == TrackDownloadStatus.Running ||
                item.status == TrackDownloadStatus.Unknown
            )

    private companion object {
        const val DOWNLOAD_CHANGE_WINDOW_MS = 250L
        const val LEGACY_DOWNLOAD_POLL_MS = 1_000L
    }
}
