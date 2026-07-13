package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsDestinationOwnerTest {
    @Test
    fun destinationActionsUseOneDownloadControllerPath() {
        val controller = RecordingDownloadController()
        val owner = DownloadsDestinationOwner(
            DownloadsViewModel(),
            controller,
            openDirectoryPicker = { controller.calls += "picker" }
        )
        val actions = owner.actions()

        actions.refresh()
        actions.useDownloadsDirectory()
        actions.useMusicDirectory()
        actions.pauseItem(1L)
        actions.resumeItem(1L)
        actions.removeItem(1L)
        actions.pauseAll()
        actions.resumeAll()
        actions.openDirectoryPicker()

        assertEquals(
            listOf(
                "snapshot",
                "set:downloads",
                "snapshot",
                "set:music",
                "snapshot",
                "pause:1",
                "snapshot",
                "resume:1",
                "snapshot",
                "remove:1",
                "snapshot",
                "pauseAll",
                "snapshot",
                "resumeAll",
                "snapshot",
                "picker"
            ),
            controller.calls
        )
    }

    private class RecordingDownloadController : TrackDownloadDirectoryController {
        val calls = mutableListOf<String>()
        private var directory = "music"

        override fun downloadDirectoryLabel(): String = "$directory/Yukine"

        override fun setDownloadDirectory(directory: String) {
            this.directory = directory
            calls += "set:$directory"
        }

        override fun setCustomDownloadDirectory(treeUri: android.net.Uri) {
            directory = "custom"
            calls += "custom:$treeUri"
        }

        override fun snapshot(): List<TrackDownloadItem> {
            calls += "snapshot"
            return emptyList()
        }

        override fun pause(downloadId: Long) = changed("pause:$downloadId")
        override fun resume(downloadId: Long) = changed("resume:$downloadId")
        override fun remove(downloadId: Long) = changed("remove:$downloadId")
        override fun pauseAll() = changed("pauseAll")
        override fun resumeAll() = changed("resumeAll")

        private fun changed(call: String): TrackDownloadActionResult {
            calls += call
            return TrackDownloadActionResult(true, call)
        }
    }
}
