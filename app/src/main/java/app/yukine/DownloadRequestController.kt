package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality

internal fun interface StreamingDownloadResolver {
    fun resolve(request: StreamingDownloadResolveRequest, quality: StreamingAudioQuality, callback: StreamingDownloadResolvedCallback)
}

internal fun interface StreamingDownloadResolvedCallback {
    fun onResolved(track: Track?)
}

internal class DownloadRequestController(
    private val downloadManagerProvider: () -> TrackDownloadRequestQueue?,
    private val downloadsViewModel: DownloadsViewModel,
    private val resolveStreamingPlaybackUseCase: StreamingPlaybackResolvePlanner,
    private val qualityChooser: DownloadQualityChooser,
    private val streamingResolver: StreamingDownloadResolver,
    private val statusSink: (String) -> Unit
) {
    fun downloadTrack(track: Track?) {
        if (track == null) {
            statusSink("未选择歌曲")
            return
        }
        qualityChooser.choose("选择下载音质") { quality ->
            downloadTrackWithQuality(track, quality, silent = false)
        }
    }

    fun downloadTracks(tracks: List<Track>?) {
        if (tracks.isNullOrEmpty()) {
            statusSink("当前歌单没有可下载的歌曲")
            return
        }
        val queuedTracks = tracks.filterNotNull()
        qualityChooser.choose("选择歌单下载音质") { quality ->
            statusSink("已创建下载队列：${queuedTracks.size} 首，音质：${downloadQualityLabel(quality)}")
            queuedTracks.forEach { track ->
                downloadTrackWithQuality(track, quality, silent = true)
            }
            statusSink("下载队列已创建：${queuedTracks.size} 首。可到“下载管理”查看进度。")
        }
    }

    fun downloadTrackWithQuality(track: Track, quality: StreamingAudioQuality, silent: Boolean) {
        val request = resolveStreamingPlaybackUseCase.prepareDownload(track)
        if (request != null) {
            if (!silent) {
                statusSink("正在解析 ${downloadQualityLabel(quality)} 下载地址：${track.title}")
            }
            streamingResolver.resolve(request, quality) { resolved ->
                if (resolved == null) {
                    if (!silent) {
                        statusSink("下载地址解析失败，请先确认该音源可以播放")
                    }
                    return@resolve
                }
                enqueueTrackDownload(resolved, quality, silent)
            }
            return
        }
        enqueueTrackDownload(track, quality, silent)
    }

    private fun enqueueTrackDownload(track: Track, quality: StreamingAudioQuality, silent: Boolean) {
        val downloadManager = downloadManagerProvider()
        if (downloadManager == null) {
            if (!silent) {
                statusSink("下载服务暂不可用")
            }
            return
        }
        val result = downloadManager.enqueue(track, quality)
        if (!silent) {
            statusSink(result.message)
        }
        downloadsViewModel.refresh(downloadManager)
        if (result.started && !silent) {
            statusSink("${result.message}。可到“下载管理”查看进度。")
        }
    }

    private fun downloadQualityLabel(quality: StreamingAudioQuality): String =
        when (quality) {
            StreamingAudioQuality.STANDARD -> "标准"
            StreamingAudioQuality.HIGH -> "高音质"
            StreamingAudioQuality.LOSSLESS -> "无损"
            StreamingAudioQuality.HIRES -> "Hi-Res"
        }
}
