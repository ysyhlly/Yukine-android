package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class DownloadsViewModelTest {
    @Test
    fun refreshSplitsActiveAndFinishedDownloads() {
        val controller = FakeDownloadController(
            listOf(
                item(1L, TrackDownloadStatus.Running),
                item(2L, TrackDownloadStatus.Finished)
            )
        )
        val viewModel = DownloadsViewModel()

        viewModel.refresh(controller)

        assertEquals(listOf(1L), viewModel.uiState.value.active.map { it.downloadId })
        assertEquals(listOf(2L), viewModel.uiState.value.finished.map { it.downloadId })
    }

    @Test
    fun pauseAndResumeDelegateToDownloadControllerAndRefreshState() {
        val controller = FakeDownloadController(listOf(item(10L, TrackDownloadStatus.Running)))
        val viewModel = DownloadsViewModel()

        viewModel.pause(controller, 10L)

        assertEquals(listOf("pause:10"), controller.calls)
        assertEquals(TrackDownloadStatus.Paused, viewModel.uiState.value.active.single().status)
        assertEquals("已暂停下载", viewModel.uiState.value.message)

        viewModel.resume(controller, 10L)

        assertEquals(listOf("pause:10", "resume:10"), controller.calls)
        assertEquals(TrackDownloadStatus.Running, viewModel.uiState.value.active.single().status)
        assertEquals("已继续下载", viewModel.uiState.value.message)
    }

    @Test
    fun pauseAllAndResumeAllDelegateToDownloadController() {
        val controller = FakeDownloadController(
            listOf(
                item(10L, TrackDownloadStatus.Running),
                item(11L, TrackDownloadStatus.Pending),
                item(12L, TrackDownloadStatus.Finished)
            )
        )
        val viewModel = DownloadsViewModel()

        viewModel.pauseAll(controller)

        assertEquals(listOf("pauseAll"), controller.calls)
        assertEquals(
            listOf(TrackDownloadStatus.Paused, TrackDownloadStatus.Paused),
            viewModel.uiState.value.active.map { it.status }
        )
        assertEquals("已暂停全部下载", viewModel.uiState.value.message)

        viewModel.resumeAll(controller)

        assertEquals(listOf("pauseAll", "resumeAll"), controller.calls)
        assertEquals(
            listOf(TrackDownloadStatus.Running, TrackDownloadStatus.Running),
            viewModel.uiState.value.active.map { it.status }
        )
        assertEquals("已继续全部下载", viewModel.uiState.value.message)
    }

    @Test
    fun removeDelegatesToDownloadControllerAndRefreshesState() {
        val controller = FakeDownloadController(
            listOf(
                item(10L, TrackDownloadStatus.Running),
                item(11L, TrackDownloadStatus.Finished)
            )
        )
        val viewModel = DownloadsViewModel()

        viewModel.remove(controller, 10L)

        assertEquals(listOf("remove:10"), controller.calls)
        assertEquals(emptyList<Long>(), viewModel.uiState.value.active.map { it.downloadId })
        assertEquals(listOf(11L), viewModel.uiState.value.finished.map { it.downloadId })
        assertEquals("已删除下载任务", viewModel.uiState.value.message)
    }

    @Test
    fun directoryPresetActionsUpdateDirectoryControllerAndState() {
        val controller = FakeDownloadDirectoryController()
        val viewModel = DownloadsViewModel()

        viewModel.refresh(controller)

        assertEquals("music/Yukine", viewModel.uiState.value.directoryLabel)

        viewModel.useDownloadsDirectory(controller)

        assertEquals(listOf("set:downloads"), controller.calls)
        assertEquals("downloads/Yukine", viewModel.uiState.value.directoryLabel)
        assertEquals("已设置下载目录：downloads/Yukine", viewModel.uiState.value.message)

        viewModel.useMusicDirectory(controller)

        assertEquals(listOf("set:downloads", "set:music"), controller.calls)
        assertEquals("music/Yukine", viewModel.uiState.value.directoryLabel)
        assertEquals("已设置下载目录：music/Yukine", viewModel.uiState.value.message)
    }

    @Test
    fun chooseDirectoryEmitsPlatformEffect() = runBlocking {
        val controller = FakeDownloadDirectoryController()
        val viewModel = DownloadsViewModel()
        val effects = mutableListOf<DownloadsEffect>()
        val job = launch {
            viewModel.effects.take(1).toList(effects)
        }

        yield()
        viewModel.chooseDirectory(controller)

        job.join()
        assertEquals(listOf(DownloadsEffect.OpenDirectoryPicker), effects)
        assertEquals(emptyList<String>(), controller.calls)
    }

    @Test
    fun directoryActionsReportUnavailableWhenControllerMissing() {
        val viewModel = DownloadsViewModel()

        viewModel.useMusicDirectory(null)

        assertEquals("下载服务暂不可用", viewModel.uiState.value.message)

        viewModel.chooseDirectory(null)

        assertEquals("下载服务暂不可用", viewModel.uiState.value.message)
    }

    private class FakeDownloadController(
        initialItems: List<TrackDownloadItem>
    ) : TrackDownloadController {
        val calls = mutableListOf<String>()
        private var items = initialItems

        override fun snapshot(): List<TrackDownloadItem> = items

        override fun pause(downloadId: Long): TrackDownloadActionResult {
            calls += "pause:$downloadId"
            items = items.map {
                if (it.downloadId == downloadId) it.copy(status = TrackDownloadStatus.Paused) else it
            }
            return TrackDownloadActionResult(true, "已暂停下载")
        }

        override fun resume(downloadId: Long): TrackDownloadActionResult {
            calls += "resume:$downloadId"
            items = items.map {
                if (it.downloadId == downloadId) it.copy(status = TrackDownloadStatus.Running) else it
            }
            return TrackDownloadActionResult(true, "已继续下载")
        }

        override fun remove(downloadId: Long): TrackDownloadActionResult {
            calls += "remove:$downloadId"
            items = items.filterNot { it.downloadId == downloadId }
            return TrackDownloadActionResult(true, "已删除下载任务")
        }

        override fun pauseAll(): TrackDownloadActionResult {
            calls += "pauseAll"
            items = items.map {
                if (it.status == TrackDownloadStatus.Running || it.status == TrackDownloadStatus.Pending) {
                    it.copy(status = TrackDownloadStatus.Paused)
                } else {
                    it
                }
            }
            return TrackDownloadActionResult(true, "已暂停全部下载")
        }

        override fun resumeAll(): TrackDownloadActionResult {
            calls += "resumeAll"
            items = items.map {
                if (it.status == TrackDownloadStatus.Paused) {
                    it.copy(status = TrackDownloadStatus.Running)
                } else {
                    it
                }
            }
            return TrackDownloadActionResult(true, "已继续全部下载")
        }
    }

    private class FakeDownloadDirectoryController : TrackDownloadDirectoryController {
        val calls = mutableListOf<String>()
        private var directory = TrackDownloadManager.DOWNLOAD_DIRECTORY_MUSIC

        override fun downloadDirectoryLabel(): String = "$directory/Yukine"

        override fun setDownloadDirectory(directory: String) {
            this.directory = directory
            calls += "set:$directory"
        }

        override fun snapshot(): List<TrackDownloadItem> = emptyList()

        override fun pause(downloadId: Long): TrackDownloadActionResult =
            TrackDownloadActionResult(false, "")

        override fun resume(downloadId: Long): TrackDownloadActionResult =
            TrackDownloadActionResult(false, "")

        override fun remove(downloadId: Long): TrackDownloadActionResult =
            TrackDownloadActionResult(false, "")

        override fun pauseAll(): TrackDownloadActionResult =
            TrackDownloadActionResult(false, "")

        override fun resumeAll(): TrackDownloadActionResult =
            TrackDownloadActionResult(false, "")
    }

    private fun item(id: Long, status: TrackDownloadStatus): TrackDownloadItem =
        TrackDownloadItem(
            downloadId = id,
            title = "Song $id",
            artist = "Artist",
            status = status,
            progressPercent = if (status == TrackDownloadStatus.Finished) 100 else 20,
            bytesDownloaded = 20L,
            totalBytes = 100L,
            localUri = "",
            reason = 0
        )
}
