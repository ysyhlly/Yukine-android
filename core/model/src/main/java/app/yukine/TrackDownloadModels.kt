package app.yukine

enum class TrackDownloadStatus {
    Pending,
    Running,
    Paused,
    Finished,
    Failed,
    Unknown
}

data class TrackDownloadItem(
    val downloadId: Long,
    val title: String,
    val artist: String,
    val status: TrackDownloadStatus,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localUri: String,
    val reason: Int,
    val quality: String = "high"
)

data class DownloadsUiState(
    val active: List<TrackDownloadItem> = emptyList(),
    val finished: List<TrackDownloadItem> = emptyList(),
    val directoryLabel: String = "",
    val message: String = ""
)
