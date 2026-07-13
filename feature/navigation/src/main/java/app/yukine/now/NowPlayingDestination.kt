package app.yukine.now

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.NowPlayingEvent
import app.yukine.TrackDownloadItem
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
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
    immersive: Boolean = false,
    onImmersiveChanged: (Boolean) -> Unit = {},
    gesturesEnabled: Boolean = true,
    onClose: Runnable = Runnable {},
    onEvent: (NowPlayingEvent) -> Unit = {},
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit = { _, _, _, _ -> },
    sourceCandidates: (Track) -> List<Track> = { emptyList() },
    onSwitchLocalSource: (Track, Track) -> Unit = { _, _ -> },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val uiState by state.collectAsState()
    val track = uiState.track.currentTrack
    if (track == null || !TrackIdentity.isUsable(uiState.track.trackId)) {
        EchoStateCard(
            title = "还没有正在播放",
            description = "播放一首歌后，这里会显示封面、歌词和队列信息。"
        )
        return
    }
    NowPlayingScreen(
        state = NowPlayingUiState(
            pageTitle = "正在播放",
            title = uiState.track.title,
            subtitle = listOfNotNull(
                uiState.track.artist.takeIf { it.isNotBlank() },
                uiState.track.album
            ).joinToString(" / "),
            queueMetricLabel = "已播放",
            queueLabel = uiState.overlayState.progress.elapsed,
            durationMetricLabel = "总时长",
            durationLabel = Track.formatDuration(uiState.progress.durationMs),
            statusLabel = uiState.labels.errorMessage.orEmpty(),
            albumArtUri = track.albumArtUri,
            lyricsTitle = uiState.lyrics.title,
            lyricsStatus = uiState.lyrics.status,
            lyrics = uiState.lyrics.lines,
            artistName = uiState.track.artist,
            albumName = uiState.track.album.orEmpty(),
            audioSpec = track.audioSpecSummary(),
            songInfo = listOfNotNull(
                uiState.track.album?.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
                track.audioSpecSummary().takeIf { it.isNotBlank() }?.let { "规格：$it" }
            ).joinToString("\n"),
            sourceInfo = sourceInfo(track),
            sourceOptions = sourceOptions(
                track = track,
                onSwitchSource = onSwitchSource,
                sourceCandidates = sourceCandidates,
                onSwitchLocalSource = onSwitchLocalSource
            ),
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            appVolume = uiState.progress.appVolume,
            onShare = Runnable { onEvent(NowPlayingEvent.ShareCurrentTrack) },
            onDownload = Runnable { onEvent(NowPlayingEvent.DownloadCurrentTrack) }
        ),
        immersive = immersive,
        onImmersiveChanged = onImmersiveChanged,
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
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit,
    sourceCandidates: (Track) -> List<Track>,
    onSwitchLocalSource: (Track, Track) -> Unit
): List<NowPlayingSourceOption> {
    val streamingOptions = streamingSourceOptions(track, onSwitchSource)
    val libraryCandidates = sourceCandidates(track)
    val localOptions = if (libraryCandidates.isEmpty()) {
        emptyList()
    } else {
        localSourceOptions(
            current = track,
            candidates = listOf(track) + libraryCandidates,
            onSwitchLocalSource = onSwitchLocalSource
        ).takeIf { options -> options.distinctBy(SourceOptionEntry::key).size > 1 }.orEmpty()
    }
    return (streamingOptions + localOptions)
        .distinctBy(SourceOptionEntry::key)
        .map(SourceOptionEntry::option)
}

private fun streamingSourceOptions(
    track: Track,
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit
): List<SourceOptionEntry> {
    if (!StreamingDataPathMetadata.isStreamingTrack(track.dataPath)) {
        return emptyList()
    }
    val currentProvider = StreamingDataPathMetadata.provider(track.dataPath)
    val currentTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
    val raw = queryParam(track.dataPath, "sourceOptions")
    if (raw.isBlank()) {
        return currentProvider?.let {
            listOf(
                SourceOptionEntry(
                    key = streamingSourceKey(it, currentTrackId),
                    option = NowPlayingSourceOption(
                        label = providerLabel(it),
                        description = currentTrackId,
                        selected = true,
                        available = false
                    )
                )
            )
        }.orEmpty()
    }
    return runCatching {
        val array = JSONArray(raw)
        val candidates = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val provider = StreamingProviderName.fromWireName(item.optString("provider")) ?: return@mapNotNull null
            val providerTrackId = item.optString("providerTrackId").trim()
            if (providerTrackId.isBlank()) {
                return@mapNotNull null
            }
            val quality = item.optString("quality")
                .takeIf { it.isNotBlank() }
                ?.let(StreamingAudioQuality::fromWireName)
            StreamingSourceCandidate(
                provider = provider,
                providerTrackId = providerTrackId,
                quality = quality,
                label = item.optString("label").ifBlank { providerLabel(provider) },
                available = item.optBoolean("available", true)
            )
        }
        candidates
            // A provider's STANDARD/HIGH/LOSSLESS variants are quality choices for one actual
            // source, not separate sources. Keep only the highest available one for a future
            // switch; the current provider+track remains one selected row.
            .groupBy { candidate -> candidate.provider to candidate.providerTrackId }
            .values
            .map { candidates ->
                requireNotNull(
                    candidates.maxWithOrNull(
                        compareBy<StreamingSourceCandidate> { candidate -> candidate.available }
                            .thenBy { candidate -> candidate.quality?.ordinal ?: -1 }
                    )
                )
            }
            .map { candidate ->
                val selected = candidate.provider == currentProvider && candidate.providerTrackId == currentTrackId
                SourceOptionEntry(
                    key = streamingSourceKey(candidate.provider, candidate.providerTrackId),
                    option = NowPlayingSourceOption(
                        label = candidate.label,
                        description = listOfNotNull(
                            providerLabel(candidate.provider),
                            candidate.quality?.wireName?.uppercase()
                        ).joinToString(" / "),
                        selected = selected,
                        available = candidate.available && !selected,
                        onClick = Runnable {
                            onSwitchSource(
                                track,
                                candidate.provider,
                                candidate.providerTrackId,
                                candidate.quality
                            )
                        }
                    )
                )
            }
    }.getOrDefault(emptyList())
}

private fun localSourceOptions(
    current: Track,
    candidates: List<Track>,
    onSwitchLocalSource: (Track, Track) -> Unit
): List<SourceOptionEntry> {
    return candidates.map { candidate ->
        val selected = candidate.id == current.id && candidate.dataPath == current.dataPath
        SourceOptionEntry(
            key = sourceCandidateKey(candidate),
            option = NowPlayingSourceOption(
                label = sourceCandidateLabel(candidate),
                description = sourceCandidateDescription(candidate),
                selected = selected,
                available = !selected,
                onClick = Runnable { onSwitchLocalSource(current, candidate) }
            )
        )
    }
}

private fun sourceCandidateLabel(track: Track): String {
    val provider = StreamingDataPathMetadata.provider(track.dataPath)
    if (provider != null) {
        return providerLabel(provider)
    }
    return sourceFileName(track.dataPath)
        .takeIf { it.isNotBlank() }
        ?: track.contentUri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "本地文件"
}

private fun sourceCandidateDescription(track: Track): String {
    val provider = StreamingDataPathMetadata.provider(track.dataPath)
    if (provider != null) {
        return listOfNotNull(
            StreamingDataPathMetadata.providerTrackId(track.dataPath).takeIf { it.isNotBlank() },
            StreamingDataPathMetadata.quality(track.dataPath).takeIf { it.isNotBlank() }?.uppercase()
        ).joinToString(" / ")
    }
    return track.audioSpecSummary()
}

private fun sourceCandidateKey(track: Track): String {
    val provider = StreamingDataPathMetadata.provider(track.dataPath)
    if (provider != null) {
        return streamingSourceKey(
            provider = provider,
            providerTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
        )
    }
    val path = track.dataPath.trim()
    return if (path.isNotBlank()) "path:$path" else "track:${track.id}"
}

private fun streamingSourceKey(
    provider: StreamingProviderName,
    providerTrackId: String
): String = "streaming:${provider.wireName}:${providerTrackId}"

private fun sourceFileName(dataPath: String): String {
    val normalized = dataPath.substringBefore('?').substringBefore('#').trim()
    return normalized
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf { it.isNotBlank() }
        .orEmpty()
}

private data class SourceOptionEntry(
    val key: String,
    val option: NowPlayingSourceOption
)

private data class StreamingSourceCandidate(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val quality: StreamingAudioQuality?,
    val label: String,
    val available: Boolean
)

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
