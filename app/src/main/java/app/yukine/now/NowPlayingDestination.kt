package app.yukine.now

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.NowPlayingEvent
import app.yukine.NowPlayingViewModel
import app.yukine.TrackDownloadItem
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.EchoStateCard
import app.yukine.ui.NowPlayingGestureActions
import app.yukine.ui.NowPlayingSourceOption
import app.yukine.ui.NowPlayingUiState
import app.yukine.ui.YukineOrbAudioMotion
import org.json.JSONArray
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun NowPlayingDestination(
    viewModel: NowPlayingViewModel,
    defaultImmersive: Boolean = false,
    onDefaultImmersiveConsumed: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    onAppVolumeChanged: (Float) -> Unit = {},
    onEvent: (NowPlayingEvent) -> Unit = { viewModel.onEvent(it) },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val state by viewModel.uiState.collectAsState()
    val track = state.currentTrack
    if (track == null || state.trackId < 0L) {
        EchoStateCard(
            title = "还没有正在播放",
            description = "播放一首歌后，这里会显示封面、歌词和队列信息。"
        )
        return
    }
    app.yukine.ui.NowPlayingScreen(
        state = NowPlayingUiState(
            pageTitle = "正在播放",
            title = state.trackTitle,
            subtitle = listOfNotNull(state.artist.takeIf { it.isNotBlank() }, state.album).joinToString(" / "),
            queueMetricLabel = "已播放",
            queueLabel = state.overlayState.elapsed,
            durationMetricLabel = "总时长",
            durationLabel = Track.formatDuration(state.durationMs),
            statusLabel = state.errorMessage.orEmpty(),
            albumArtUri = track.albumArtUri,
            lyricsTitle = state.lyrics.title,
            lyricsStatus = state.lyrics.status,
            lyrics = state.lyrics.lines,
            artistName = state.artist,
            albumName = state.album.orEmpty(),
            audioSpec = track.audioSpecSummary(),
            songInfo = listOfNotNull(
                state.album?.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
                track.audioSpecSummary().takeIf { it.isNotBlank() }?.let { "规格：$it" }
            ).joinToString("\n"),
            sourceInfo = sourceInfo(track),
            sourceOptions = sourceOptions(track, viewModel),
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            appVolume = state.appVolume,
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
                onVolumeChange = onAppVolumeChanged
            )
        } else {
            NowPlayingGestureActions.Empty
        }
    )
}

private fun sourceInfo(track: Track): String {
    if (!track.dataPath.startsWith("streaming:")) {
        return ""
    }
    val provider = StreamingPlaybackAdapter.providerName(track.dataPath)
    val providerTrackId = StreamingPlaybackAdapter.providerTrackId(track.dataPath)
    return listOfNotNull(
        provider?.let { "当前音源：${providerLabel(it)}" },
        providerTrackId.takeIf { it.isNotBlank() }?.let { "ID：$it" }
    ).joinToString("\n")
}

private fun sourceOptions(track: Track, viewModel: NowPlayingViewModel): List<NowPlayingSourceOption> {
    if (!track.dataPath.startsWith("streaming:")) {
        return emptyList()
    }
    val currentProvider = StreamingPlaybackAdapter.providerName(track.dataPath)
    val currentTrackId = StreamingPlaybackAdapter.providerTrackId(track.dataPath)
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
                    viewModel.switchSource(track, provider, providerTrackId, quality)
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
