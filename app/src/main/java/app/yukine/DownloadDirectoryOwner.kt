package app.yukine

import android.net.Uri
import java.util.function.Consumer

/** Owns custom download-directory validation, persistence and UI refresh. */
internal class DownloadDirectoryOwner(
    private val downloadManager: TrackDownloadDirectoryController?,
    private val downloadsViewModel: DownloadsViewModel,
    private val feedback: Consumer<String>
) {
    fun setCustomDirectory(treeUri: Uri?) {
        val manager = downloadManager
        if (manager == null || treeUri == null) {
            feedback.accept("无法保存下载目录")
            return
        }
        manager.setCustomDownloadDirectory(treeUri)
        downloadsViewModel.refreshDirectory(manager)
        feedback.accept("已设置下载目录：${manager.downloadDirectoryLabel()}")
    }
}
