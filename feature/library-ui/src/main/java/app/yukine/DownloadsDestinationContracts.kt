package app.yukine

data class TrackDownloadActionResult(
    val changed: Boolean,
    val message: String
)

interface TrackDownloadController {
    fun snapshot(): List<TrackDownloadItem>
    fun pause(downloadId: Long): TrackDownloadActionResult
    fun resume(downloadId: Long): TrackDownloadActionResult
    fun remove(downloadId: Long): TrackDownloadActionResult
    fun pauseAll(): TrackDownloadActionResult
    fun resumeAll(): TrackDownloadActionResult
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
