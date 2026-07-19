package app.yukine

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

const val DOWNLOAD_DIRECTORY_MUSIC = "music"
const val DOWNLOAD_DIRECTORY_DOWNLOADS = "downloads"

data class TrackDownloadActionResult(
    val changed: Boolean,
    val message: String
)

interface TrackDownloadController {
    val changes: Flow<Unit>
        get() = emptyFlow()

    fun snapshot(): List<TrackDownloadItem>
    fun pause(downloadId: Long): TrackDownloadActionResult
    fun resume(downloadId: Long): TrackDownloadActionResult
    fun remove(downloadId: Long): TrackDownloadActionResult
    fun pauseAll(): TrackDownloadActionResult
    fun resumeAll(): TrackDownloadActionResult
}

interface TrackDownloadDirectoryController : TrackDownloadController {
    fun downloadDirectoryLabel(): String
    fun setDownloadDirectory(directory: String)
    fun setCustomDownloadDirectory(treeUri: Uri)
}

data class DownloadsDestinationActions(
    val refresh: () -> Unit = {},
    val useMusicDirectory: () -> Unit = {},
    val useDownloadsDirectory: () -> Unit = {},
    val chooseDirectory: () -> Unit = {},
    val pauseItem: (Long) -> Unit = {},
    val resumeItem: (Long) -> Unit = {},
    val removeItem: (Long) -> Unit = {},
    val pauseAll: () -> Unit = {},
    val resumeAll: () -> Unit = {},
    val openDirectoryPicker: () -> Unit = {}
)
