package app.yukine

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.function.Consumer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadDirectoryOwnerTest {
    @Test
    fun nullDirectoryReportsFailureWithoutMutation() {
        val controller = FakeDownloadDirectoryController()
        val feedback = mutableListOf<String>()
        val owner = DownloadDirectoryOwner(
            controller,
            DownloadsViewModel(),
            Consumer { feedback += it }
        )

        owner.setCustomDirectory(null)

        assertEquals(null, controller.customUri)
        assertEquals(listOf("无法保存下载目录"), feedback)
    }

    @Test
    fun selectedDirectoryPersistsRefreshesAndReportsResolvedLabel() {
        val controller = FakeDownloadDirectoryController()
        val viewModel = DownloadsViewModel()
        val feedback = mutableListOf<String>()
        val owner = DownloadDirectoryOwner(controller, viewModel, Consumer { feedback += it })
        val uri = Uri.parse("content://downloads/tree/music")

        owner.setCustomDirectory(uri)

        assertEquals(uri, controller.customUri)
        assertEquals("自定义目录/Yukine", viewModel.uiState.value.directoryLabel)
        assertEquals(listOf("已设置下载目录：自定义目录/Yukine"), feedback)
    }

    private class FakeDownloadDirectoryController : TrackDownloadDirectoryController {
        var customUri: Uri? = null

        override fun downloadDirectoryLabel(): String =
            if (customUri == null) "音乐/Yukine" else "自定义目录/Yukine"

        override fun setDownloadDirectory(directory: String) = Unit

        override fun setCustomDownloadDirectory(treeUri: Uri) {
            customUri = treeUri
        }

        override fun snapshot(): List<TrackDownloadItem> = emptyList()

        override fun pause(downloadId: Long) = TrackDownloadActionResult(false, "")

        override fun resume(downloadId: Long) = TrackDownloadActionResult(false, "")

        override fun remove(downloadId: Long) = TrackDownloadActionResult(false, "")

        override fun pauseAll() = TrackDownloadActionResult(false, "")

        override fun resumeAll() = TrackDownloadActionResult(false, "")
    }
}
