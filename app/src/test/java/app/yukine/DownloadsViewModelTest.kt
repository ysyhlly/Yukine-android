package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

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
