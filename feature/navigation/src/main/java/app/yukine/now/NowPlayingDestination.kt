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
import app.yukine.streaming.StreamingAudioCapabilityPolicy
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import app.yukine.streaming.PlaybackSourcePolicySnapshot
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
    streamingProviders: List<StreamingProviderDescriptor> = emptyList(),
    playbackSourcePolicy: PlaybackSourcePolicySnapshot = PlaybackSourcePolicySnapshot(),
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
                streamingProviders = streamingProviders,
                playbackSourcePolicy = playbackSourcePolicy,
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
    streamingProviders: List<StreamingProviderDescriptor>,
    playbackSourcePolicy: PlaybackSourcePolicySnapshot,
    onSwitchLocalSource: (Track, Track) -> Unit
): List<NowPlayingSourceOption> {
    val libraryCandidates = sourceCandidates(track)
    val streamingOptions = streamingSourceOptions(
        track = track,
        providers = streamingProviders,
        playbackSourcePolicy = playbackSourcePolicy,
        libraryCandidates = libraryCandidates,
        onSwitchSource = onSwitchSource,
        onSwitchLocalSource = onSwitchLocalSource
    )
    val localCandidates = (listOf(track) + libraryCandidates)
        .filter { StreamingDataPathMetadata.provider(it.dataPath) == null }
    val localOptions = if (localCandidates.size < 2) {
        emptyList()
    } else {
        localSourceOptions(
            current = track,
            candidates = localCandidates,
            onSwitchLocalSource = onSwitchLocalSource
        ).takeIf { options -> options.distinctBy(SourceOptionEntry::key).size > 1 }.orEmpty()
    }
    return (streamingOptions + localOptions)
        .distinctBy(SourceOptionEntry::key)
        .map(SourceOptionEntry::option)
}

private fun streamingSourceOptions(
    track: Track,
    providers: List<StreamingProviderDescriptor>,
    playbackSourcePolicy: PlaybackSourcePolicySnapshot,
    libraryCandidates: List<Track>,
    onSwitchSource: (Track, StreamingProviderName, String, StreamingAudioQuality?) -> Unit,
    onSwitchLocalSource: (Track, Track) -> Unit
): List<SourceOptionEntry> {
    val currentProvider = StreamingDataPathMetadata.provider(track.dataPath)
    val currentTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
    val embedded = runCatching {
        val raw = queryParam(track.dataPath, "sourceOptions")
        if (raw.isBlank()) return@runCatching emptyList()
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
            StreamingSourceCandidate(
                provider = provider,
                providerTrackId = providerTrackId,
                quality = quality,
                label = item.optString("label").ifBlank { providerLabel(provider) },
                available = item.optBoolean("available", true),
                sourceTrack = null
            )
        }
    }.getOrDefault(emptyList())
    val direct = listOfNotNull(
        currentProvider?.takeIf { currentTrackId.isNotBlank() }?.let { provider ->
            StreamingSourceCandidate(
                provider = provider,
                providerTrackId = currentTrackId,
                quality = null,
                label = providerLabel(provider),
                available = true,
                sourceTrack = track
            )
        }
    )
    val libraryStreaming = libraryCandidates.mapNotNull { candidateTrack ->
        val provider = StreamingDataPathMetadata.provider(candidateTrack.dataPath) ?: return@mapNotNull null
        val providerTrackId = StreamingDataPathMetadata.providerTrackId(candidateTrack.dataPath)
        if (providerTrackId.isBlank()) return@mapNotNull null
        StreamingSourceCandidate(
            provider = provider,
            providerTrackId = providerTrackId,
            quality = StreamingDataPathMetadata.quality(candidateTrack.dataPath)
                .takeIf { it.isNotBlank() }
                ?.let(StreamingAudioQuality::fromWireName),
            label = providerLabel(provider),
            available = true,
            sourceTrack = candidateTrack
        )
    }
    val groupedCandidates = (direct + embedded + libraryStreaming)
        .groupBy { candidate -> candidate.provider to candidate.providerTrackId }
        .mapNotNull { (_, values) ->
            val strongest = values.maxWithOrNull(
                compareBy<StreamingSourceCandidate> { it.available }
                    .thenBy { it.quality?.ordinal ?: -1 }
            )
            strongest?.copy(
                sourceTrack = values.firstNotNullOfOrNull(StreamingSourceCandidate::sourceTrack)
            )
        }
    val candidates = closestLuoxueCandidateOnly(track, groupedCandidates)
    val addedProviders = providers
        .filter {
            it.enabled && it.status != StreamingProviderStatus.DISABLED &&
                (it.capabilities.supportsAudioResolve ||
                    it.name == StreamingProviderName.QQ_MUSIC)
        }
        .distinctBy { it.name }
    val descriptorByProvider = addedProviders.associateBy { it.name }
    val providerOrder = buildList {
        addAll(addedProviders.map { it.name })
        currentProvider?.let(::add)
        addAll(candidates.map(StreamingSourceCandidate::provider))
    }.distinct()
    return providerOrder.flatMap { provider ->
        val providerCandidates = candidates.filter { it.provider == provider }
        val rows = providerCandidates.ifEmpty { listOf(null) }
        rows.map { candidate ->
            val selected = candidate != null && provider == currentProvider &&
                candidate.providerTrackId == currentTrackId
            val metadataOnly = StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(provider)
            val enabledBySettings = playbackSourcePolicy.isEnabled(provider)
            val descriptor = descriptorByProvider[provider]
            val playable = !metadataOnly && enabledBySettings &&
                candidate?.available == true && candidate.providerTrackId.isNotBlank()
            val status = when {
                metadataOnly -> "仅用于曲库与同步，不提供播放音源"
                !enabledBySettings -> "设置中未开启"
                descriptor?.capabilities?.supportsAuth == true && descriptor.auth.credentialState == app.yukine.streaming.StreamingCredentialState.INVALID -> "登录失效"
                descriptor?.status == StreamingProviderStatus.ERROR -> "网络错误"
                selected -> "当前音源"
                playable -> "可用"
                else -> "无对应音源"
            }
            SourceOptionEntry(
                key = candidate?.let {
                    streamingSourceKey(it.provider, it.providerTrackId)
                } ?: "streaming:${provider.wireName}",
                option = NowPlayingSourceOption(
                    label = streamingCandidateLabel(
                        provider = provider,
                        displayName = descriptor?.displayName ?: providerLabel(provider),
                        candidate = candidate,
                        multipleVersions = providerCandidates.size > 1
                    ),
                    description = streamingCandidateDescription(
                        status = status,
                        candidate = candidate,
                        showVersion = providerCandidates.size > 1 || provider == StreamingProviderName.LUOXUE
                    ),
                    selected = selected,
                    available = playable && !selected,
                    onClick = Runnable {
                        val replacement = candidate?.sourceTrack
                        if (replacement != null && replacement !== track) {
                            onSwitchLocalSource(track, replacement)
                        } else if (candidate != null) {
                            onSwitchSource(
                                track,
                                candidate.provider,
                                candidate.providerTrackId,
                                candidate.quality
                            )
                        }
                    }
                )
            )
        }
    }
}

private fun closestLuoxueCandidateOnly(
    track: Track,
    candidates: List<StreamingSourceCandidate>
): List<StreamingSourceCandidate> {
    val luoxueCandidates = candidates.filter { it.provider == StreamingProviderName.LUOXUE }
    if (luoxueCandidates.size <= 1) return candidates

    val currentProvider = StreamingDataPathMetadata.provider(track.dataPath)
    val currentTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
    val current = luoxueCandidates.firstOrNull { candidate ->
        currentProvider == StreamingProviderName.LUOXUE &&
            candidate.providerTrackId == currentTrackId
    }
    val rankedTrackId = luoxueCandidates
        .mapNotNull { candidate ->
            candidate.sourceTrack?.let { sourceTrack ->
                StreamingTrack(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = candidate.providerTrackId,
                    title = sourceTrack.title,
                    artist = sourceTrack.artist,
                    album = sourceTrack.album,
                    durationMs = sourceTrack.durationMs.takeIf { it > 0L }
                )
            }
        }
        .takeIf { it.isNotEmpty() }
        ?.let { metadataCandidates ->
            StreamingTrackMatchPolicy.rankCandidates(
                StreamingTrackMatchPolicy.reference(track),
                metadataCandidates
            ).firstOrNull()?.track?.providerTrackId
        }
    val closestTrackId = current?.providerTrackId
        ?: rankedTrackId
        ?: luoxueCandidates.first().providerTrackId
    return candidates.filter { candidate ->
        candidate.provider != StreamingProviderName.LUOXUE ||
            candidate.providerTrackId == closestTrackId
    }
}

private fun streamingCandidateLabel(
    provider: StreamingProviderName,
    displayName: String,
    candidate: StreamingSourceCandidate?,
    multipleVersions: Boolean
): String {
    candidate ?: return displayName
    val genericLabels = setOf(provider.wireName, providerLabel(provider), displayName)
    val customLabel = candidate.label.trim().takeUnless { it.isBlank() || it in genericLabels }
    if (!multipleVersions) return customLabel ?: displayName
    val sourceKey = candidate.providerTrackId
        .substringBefore(':', "")
        .uppercase()
        .takeIf { provider == StreamingProviderName.LUOXUE && it.isNotBlank() }
    val title = candidate.sourceTrack?.title?.takeIf { it.isNotBlank() }
    return customLabel ?: listOfNotNull(displayName, sourceKey, title)
        .distinct()
        .joinToString(" · ")
}

private fun streamingCandidateDescription(
    status: String,
    candidate: StreamingSourceCandidate?,
    showVersion: Boolean
): String {
    val sourceTrack = candidate?.sourceTrack
    return listOfNotNull(
        status,
        sourceTrack?.artist?.takeIf { showVersion && it.isNotBlank() },
        sourceTrack?.album?.takeIf { showVersion && it.isNotBlank() },
        sourceTrack?.durationMs?.takeIf { showVersion && it > 0L }?.let { Track.formatDuration(it) },
        candidate?.quality?.wireName?.uppercase()
    ).distinct().joinToString(" / ")
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
    val available: Boolean,
    val sourceTrack: Track?
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
