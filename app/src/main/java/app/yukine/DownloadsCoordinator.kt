package app.yukine

import app.yukine.model.Track

internal class DownloadsCoordinator(
    val downloadRequestController: DownloadRequestController,
    private val trackDownloadManager: TrackDownloadManager,
    private val downloadsViewModel: DownloadsViewModel,
    private val statusMessageController: StatusMessageController
) {

    fun downloadTrack(track: Track) {
        downloadRequestController.downloadTrack(track)
    }

    fun setCustomDownloadFolder(treeUri: android.net.Uri?) {
        if (treeUri == null) {
            statusMessageController.showFeedback("无法保存下载目录")
            return
        }
        trackDownloadManager.setCustomDownloadDirectory(treeUri)
        downloadsViewModel.refresh(trackDownloadManager)
        statusMessageController.showFeedback("已设置下载目录：" + trackDownloadManager.downloadDirectoryLabel())
    }
}
