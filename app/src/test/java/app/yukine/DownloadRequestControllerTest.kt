package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestControllerTest {
    @Test
    fun nullTrackShowsStatusWithoutOpeningQualityChooser() {
        val fixture = Fixture()

        fixture.controller.downloadTrack(null)

        assertEquals(listOf("未选择歌曲"), fixture.statuses)
        assertEquals(0, fixture.chooser.requests.size)
        assertEquals(emptyList<String>(), fixture.queue.calls)
    }

    @Test
    fun directTrackIsQueuedWithSelectedQuality() {
        val fixture = Fixture()
        val track = track(1L, "Song")

        fixture.controller.downloadTrack(track)
        fixture.chooser.select(StreamingAudioQuality.LOSSLESS)

        assertEquals(listOf("enqueue:1:LOSSLESS"), fixture.queue.calls)
        assertEquals(listOf(1L), fixture.downloadsViewModel.uiState.value.active.map { it.downloadId })
        assertTrue(fixture.statuses.last().contains("可到“下载管理”查看进度"))
    }

    @Test
    fun unresolvedStreamingTrackIsResolvedBeforeQueueing() {
        val resolved = track(9L, "Resolved")
        val fixture = Fixture(resolvedTrack = resolved)
        val unresolved = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "163",
                title = "Cloud",
                artist = "Artist",
                artists = emptyList(),
                album = "Album",
                albumId = null,
                durationMs = 1000L,
                coverUrl = "",
                coverThumbUrl = "",
                qualities = emptySet(),
                explicit = false,
                playable = true,
                unavailableReason = null,
                playbackCandidates = listOf(
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.NETEASE,
                        quality = null,
                        label = "netease",
                        providerTrackId = "163",
                        available = true
                    )
                )
            )
        )

        fixture.controller.downloadTrack(unresolved)
        fixture.chooser.select(StreamingAudioQuality.HIGH)

        assertEquals(listOf("resolve:netease:163:HIGH"), fixture.resolver.calls)
        assertEquals(listOf("enqueue:9:HIGH"), fixture.queue.calls)
    }

    @Test
    fun playlistDownloadsEveryTrackSilentlyAfterOneQualitySelection() {
        val fixture = Fixture()

        fixture.controller.downloadTracks(listOf(track(1L, "One"), track(2L, "Two")))
        fixture.chooser.select(StreamingAudioQuality.STANDARD)

        assertEquals(listOf("选择歌单下载音质"), fixture.chooser.requests.map { it.title })
        assertEquals(listOf("enqueue:1:STANDARD", "enqueue:2:STANDARD"), fixture.queue.calls)
        assertTrue(fixture.statuses.first().contains("已创建下载队列：2 首"))
        assertTrue(fixture.statuses.last().contains("下载队列已创建：2 首"))
    }

    private class Fixture(
        resolvedTrack: Track? = null
    ) {
        val queue = FakeDownloadQueue()
        val chooser = FakeQualityChooser()
        val statuses = mutableListOf<String>()
        val downloadsViewModel = DownloadsViewModel()
        val resolver = FakeStreamingResolver(resolvedTrack)
        val controller = DownloadRequestController(
            downloadManagerProvider = { queue },
            downloadsViewModel = downloadsViewModel,
            resolveStreamingPlaybackUseCase = ResolveStreamingPlaybackUseCase(),
            qualityChooser = chooser,
            streamingResolver = resolver,
            statusSink = { statuses += it }
        )
    }

    private class FakeQualityChooser : DownloadQualityChooser {
        data class Request(val title: String, val callback: (StreamingAudioQuality) -> Unit)

        val requests = mutableListOf<Request>()

        override fun choose(title: String, onQualitySelected: (StreamingAudioQuality) -> Unit) {
            requests += Request(title, onQualitySelected)
        }

        fun select(quality: StreamingAudioQuality) {
            requests.last().callback(quality)
        }
    }

    private class FakeStreamingResolver(
        private val resolvedTrack: Track?
    ) : StreamingDownloadResolver {
        val calls = mutableListOf<String>()

        override fun resolve(
            request: StreamingDownloadResolveRequest,
            quality: StreamingAudioQuality,
            callback: StreamingDownloadResolvedCallback
        ) {
            calls += "resolve:${request.provider.wireName}:${request.providerTrackId}:${quality.name}"
            callback.onResolved(resolvedTrack)
        }
    }

    private class FakeDownloadQueue : TrackDownloadRequestQueue {
        val calls = mutableListOf<String>()
        private val items = mutableListOf<TrackDownloadItem>()

        override fun enqueue(track: Track, quality: StreamingAudioQuality): TrackDownloadResult {
            calls += "enqueue:${track.id}:${quality.name}"
            items += TrackDownloadItem(
                downloadId = track.id,
                title = track.title,
                artist = track.artist,
                status = TrackDownloadStatus.Running,
                progressPercent = 0,
                bytesDownloaded = 0L,
                totalBytes = 100L,
                localUri = "",
                reason = 0,
                quality = quality.name.lowercase()
            )
            return TrackDownloadResult(true, track.id, "已加入下载队列：${track.title}")
        }

        override fun snapshot(): List<TrackDownloadItem> = items

        override fun downloadDirectoryLabel(): String = "music/Yukine"

        override fun setDownloadDirectory(directory: String) {
        }

        override fun pause(downloadId: Long): TrackDownloadActionResult = TrackDownloadActionResult(false, "")

        override fun resume(downloadId: Long): TrackDownloadActionResult = TrackDownloadActionResult(false, "")

        override fun remove(downloadId: Long): TrackDownloadActionResult = TrackDownloadActionResult(false, "")

        override fun pauseAll(): TrackDownloadActionResult = TrackDownloadActionResult(false, "")

        override fun resumeAll(): TrackDownloadActionResult = TrackDownloadActionResult(false, "")
    }

    private fun track(id: Long, title: String): Track =
        Track(
            id,
            title,
            "Artist",
            "Album",
            1000L,
            Uri.parse("https://example.com/$id.mp3"),
            "streaming:direct:$id"
        )
}
