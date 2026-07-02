package app.yukine.now

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.NowPlayingEvent
import app.yukine.TrackDownloadItem
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.EchoStateCard
import app.yukine.ui.NowPlayingGestureActions
import app.yukine.ui.NowPlayingScreen
import app.yukine.ui.NowPlayingSourceOption
import app.yukine.ui.NowPlayingUiState
import app.yukine.ui.YukineOrbAudioMotion
import org.json.JSONArray
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NowPlayingDestination(
    state: StateFlow<app.yukine.NowPlayingUiState>,
    defaultImmersive: Boolean = false,
    onDefaultImmersiveConsumed: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    onClose: Runnable = Runnable {},
    onEvent: (NowPlayingEvent) -> Unit = {},
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit = { _, _, _, _ -> },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val uiState by state.collectAsState()
    val track = uiState.currentTrack
    if (track == null || uiState.trackId < 0L) {
        EchoStateCard(
            title = "还没有正在播放",
            description = "播放一首歌后，这里会显示封面、歌词和队列信息。"
        )
        return
    }
    NowPlayingScreen(
        state = NowPlayingUiState(
            pageTitle = "正在播放",
            title = uiState.trackTitle,
            subtitle = listOfNotNull(uiState.artist.takeIf { it.isNotBlank() }, uiState.album).joinToString(" / "),
            queueMetricLabel = "已播放",
            queueLabel = uiState.overlayState.elapsed,
            durationMetricLabel = "总时长",
            durationLabel = Track.formatDuration(uiState.durationMs),
            statusLabel = uiState.errorMessage.orEmpty(),
            albumArtUri = track.albumArtUri,
            lyricsTitle = uiState.lyrics.title,
            lyricsStatus = uiState.lyrics.status,
            lyrics = uiState.lyrics.lines,
            artistName = uiState.artist,
            albumName = uiState.album.orEmpty(),
            audioSpec = track.audioSpecSummary(),
            songInfo = listOfNotNull(
                uiState.album?.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
                track.audioSpecSummary().takeIf { it.isNotBlank() }?.let { "规格：$it" }
            ).joinToString("\n"),
            sourceInfo = sourceInfo(track),
            sourceOptions = sourceOptions(track, onSwitchSource),
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            appVolume = uiState.appVolume,
            onShare = Runnable { onEvent(NowPlayingEvent.ShareCurrentTrack) },
            onDownload = Runnable { onEvent(NowPlayingEvent.DownloadCurrentTrack) }
        ),
        defaultImmersive = defaultImmersive,
        onDefaultImmersiveConsumed = onDefaultImmersiveConsumed,
        onLyricSeek = { positionMs -> onEvent(NowPlayingEvent.SeekTo(positionMs)) },
        gestureActions = if (gesturesEnabled) {
            NowPlayingGestureActions(
                onPrevious = Runnable { onEvent(NowPlayingEvent.Previous) },
                onNext = Runnable { onEvent(NowPlayingEvent.Next) },
                onClose = onClose
            )
        } else {
            NowPlayingGestureActions.Empty
        }
    )
}

private fun sourceInfo(track: Track): String {
    if (!StreamingDataPathMetadata.isStreamingTrack(track.dataPath)) {
        return ""
    }
    val provider = StreamingDataPathMetadata.provider(track.dataPath)
    val providerTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
    return listOfNotNull(
        provider?.let { "当前音源：${providerLabel(it)}" },
        providerTrackId.takeIf { it.isNotBlank() }?.let { "ID：$it" }
    ).joinToString("\n")
}

private fun sourceOptions(
    track: Track,
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit
): List<NowPlayingSourceOption> {
    if (!StreamingDataPathMetadata.isStreamingTrack(track.dataPath)) {
        return emptyList()
    }
    val currentProvider = StreamingDataPathMetadata.provider(track.dataPath)
    val currentTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
    val raw = queryParam(track.dataPath, "sourceOptions")
    if (raw.isBlank()) {
        return currentProvider?.let {
            listOf(
                NowPlayingSourceOption(
                    label = providerLabel(it),
                    description = currentTrackId,
                    selected = true,
                    available = false
                )
            )
        }.orEmpty()
    }
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val provider = StreamingProviderName.fromWireName(item.optString("provider")) ?: return@mapNotNull null
            val providerTrackId = item.optString("providerTrackId").trim()
            if (providerTrackId.isBlank()) {
                return@mapNotNull null
            }
            val quality = item.optString("quality")
                .takeIf { it.isNotBlank() }
                ?.let(StreamingAudioQuality::fromWireName)
            val selected = provider == currentProvider && providerTrackId == currentTrackId
            val label = item.optString("label").ifBlank { providerLabel(provider) }
            NowPlayingSourceOption(
                label = label,
                description = listOfNotNull(providerLabel(provider), quality?.wireName?.uppercase()).joinToString(" / "),
                selected = selected,
                available = item.optBoolean("available", true) && !selected,
                onClick = Runnable {
                    onSwitchSource(track, provider, providerTrackId, quality)
                }
            )
        }
    }.getOrDefault(emptyList())
}

private fun queryParam(dataPath: String, key: String): String {
    val query = dataPath.substringAfter('?', "")
    if (query.isBlank()) {
        return ""
    }
    return query.split('&')
        .firstOrNull { it.substringBefore('=') == key }
        ?.substringAfter('=', "")
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        .orEmpty()
}

private fun providerLabel(provider: StreamingProviderName): String = when (provider) {
    StreamingProviderName.NETEASE -> "网易云音乐"
    StreamingProviderName.QQ_MUSIC -> "QQ 音乐"
    StreamingProviderName.KUGOU -> "酷狗音乐"
    StreamingProviderName.BILIBILI -> "哔哩哔哩"
    StreamingProviderName.LUOXUE -> "洛雪音源"
    StreamingProviderName.YOUTUBE -> "YouTube"
    StreamingProviderName.SOUNDCLOUD -> "SoundCloud"
    StreamingProviderName.SPOTIFY -> "Spotify"
    StreamingProviderName.TIDAL -> "TIDAL"
    StreamingProviderName.M3U8 -> "M3U8"
    StreamingProviderName.PLUGIN -> "自定义插件"
    StreamingProviderName.MOCK -> "Mock"
}
