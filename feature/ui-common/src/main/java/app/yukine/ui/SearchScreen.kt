package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.TrackDownloadItem
import app.yukine.UnifiedSearchUiState
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

fun interface UnifiedSearchQueryAction {
    fun run(query: String)
}

fun interface UnifiedLocalTrackAction {
    fun run(track: Track)
}

fun interface StreamingTrackAction {
    fun run(track: StreamingTrack)
}

data class UnifiedSearchActions(
    val onQueryChange: UnifiedSearchQueryAction,
    val onSearch: UnifiedSearchQueryAction,
    val onPlayLocalTrack: UnifiedLocalTrackAction,
    val onPlayStreamingTrack: StreamingTrackAction,
    val onLoadMoreStreaming: Runnable,
    val onExit: Runnable
) {
    companion object {
        @JvmStatic
        fun empty(): UnifiedSearchActions = UnifiedSearchActions(
            onQueryChange = UnifiedSearchQueryAction { _ -> },
            onSearch = UnifiedSearchQueryAction { _ -> },
            onPlayLocalTrack = UnifiedLocalTrackAction { _ -> },
            onPlayStreamingTrack = StreamingTrackAction { _ -> },
            onLoadMoreStreaming = Runnable {},
            onExit = Runnable {}
        )
    }
}

data class UnifiedSearchStreamingState(
    val tracks: List<StreamingTrack> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val hasMore: Boolean = false,
    val sourceLabels: Map<StreamingProviderName, String> = emptyMap()
) {
    fun sourceLabel(provider: StreamingProviderName): String {
        return sourceLabels[provider]?.takeIf { it.isNotBlank() } ?: fallbackSourceLabel(provider)
    }
}

@Composable
fun UnifiedSearchScreen(
    searchState: UnifiedSearchUiState,
    streamingState: UnifiedSearchStreamingState,
    actions: UnifiedSearchActions,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val query = searchState.query
    val onlineTracks = streamingState.tracks
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val currentOnExit by rememberUpdatedState(actions.onExit)

    DisposableEffect(Unit) {
        onDispose {
            currentOnExit.run()
        }
    }

    CollapsibleSearchHeader(
        header = {
            SearchInput(
                query = query,
                onQueryChange = { actions.onQueryChange.run(it) },
                onSearch = { actions.onSearch.run(query) },
                activeDownload = activeDownload,
                playbackQuality = playbackQuality,
                audioMotion = audioMotion
            )
        }
    ) { contentModifier, _ ->
        LazyColumn(
            state = listState,
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = echoPageBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("header") {
                EchoPageTitle("搜索音乐")
            }

            item("local-title") {
                SectionTitle("本地曲库", searchState.localTracks.size)
            }
            if (query.isBlank()) {
                item("local-empty-before") {
                    EmptyHint("输入歌名、歌手或专辑名，同时搜索本地和在线音乐。")
                }
            } else if (searchState.localTracks.isEmpty() && searchState.searched) {
                item("local-empty") { EmptyHint("本地没有找到匹配歌曲") }
            } else {
                items(searchState.localTracks, key = { it.id }) { track ->
                    LocalTrackResultRow(track) { actions.onPlayLocalTrack.run(track) }
                }
            }

            item("online-title") {
                SectionTitle("在线音乐", onlineTracks.size)
            }
            when {
                query.isBlank() -> item("online-empty-before") {
                    EmptyHint("按搜索后会使用当前已连接的流媒体账号查找在线歌曲。")
                }
                streamingState.loading && onlineTracks.isEmpty() -> item("online-loading") {
                    EmptyHint("正在搜索在线音乐...")
                }
                !streamingState.errorMessage.isNullOrBlank() && onlineTracks.isEmpty() -> item("online-error") {
                    EmptyHint(streamingState.errorMessage ?: "在线搜索失败")
                }
                onlineTracks.isEmpty() && searchState.searched -> item("online-empty") {
                    EmptyHint("在线音乐没有找到匹配歌曲")
                }
                else -> {
                    items(onlineTracks, key = { it.stableKey }) { track ->
                        StreamingTrackResultRow(
                            track = track,
                            streamingState = streamingState,
                            onPlay = actions.onPlayStreamingTrack::run
                        )
                    }
                    if (!streamingState.errorMessage.isNullOrBlank()) {
                        item("online-playback-error") {
                            EmptyHint(streamingState.errorMessage ?: "在线歌曲解析失败")
                        }
                    }
                    if (streamingState.hasMore) {
                        item("online-more") {
                            SearchActionButton("加载更多", EchoIconKind.Sync) {
                                actions.onLoadMoreStreaming.run()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .echoGlassLayer(p, EchoShapes.medium),
            contentAlignment = Alignment.Center
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("unified-search-input"),
                singleLine = true,
                placeholder = {
                    Text("搜索歌曲、歌手、专辑", style = EchoTypography.bodyMedium, color = p.muted)
                },
                leadingIcon = { EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent) },
                textStyle = EchoTypography.bodyMedium.copy(color = p.text, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    cursorColor = p.accent
                )
            )
        }
        Surface(
            onClick = onSearch,
            shape = EchoShapes.medium,
            color = Color.Transparent,
            modifier = Modifier
                .height(56.dp)
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent)
                Spacer(Modifier.width(6.dp))
                Text("搜索", style = EchoTypography.label, color = p.accent)
            }
        }
        YukineDownloadOrb(
            item = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = EchoTypography.title, color = p.heading)
        Spacer(Modifier.width(8.dp))
        Text("$count 首", style = EchoTypography.caption, color = p.muted)
    }
}

@Composable
private fun LocalTrackResultRow(track: Track, onClick: () -> Unit) {
    ResultRow(
        title = track.title,
        subtitle = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" / "),
        coverUri = track.albumArtUri,
        sourceLabel = "本地",
        onClick = onClick
    )
}

@Composable
private fun StreamingTrackResultRow(
    track: StreamingTrack,
    streamingState: UnifiedSearchStreamingState,
    onPlay: (StreamingTrack) -> Unit
) {
    val sources = track.selectablePlaybackSources()
    var sourcesExpanded by rememberSaveable(track.stableKey) { mutableStateOf(false) }
    val additionalSourceCount = (sources.size - 1).coerceAtLeast(0)
    ResultRow(
        title = cleanSearchDisplayText(track.title),
        subtitle = listOf(track.artist, track.album.orEmpty())
            .map(::cleanSearchDisplayText)
            .filter { it.isNotBlank() }
            .joinToString(" / "),
        coverUri = (track.coverThumbUrl ?: track.coverUrl)?.let(Uri::parse),
        sourceLabel = streamingState.sourceLabel(track) + additionalSourceCount
            .takeIf { it > 0 }
            ?.let { " +$it" }
            .orEmpty(),
        onClick = { onPlay(track) },
        onSourceClick = if (additionalSourceCount > 0) {
            { sourcesExpanded = !sourcesExpanded }
        } else {
            null
        },
        expandedContent = if (sourcesExpanded && additionalSourceCount > 0) {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { source ->
                        PlaybackSourceOptionChip(
                            label = streamingState.sourceLabel(source),
                            selected = source.matches(track.provider, track.providerTrackId),
                            enabled = source.available
                        ) {
                            sourcesExpanded = false
                            onPlay(track.withSelectedPlaybackSource(source))
                        }
                    }
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    coverUri: Uri?,
    sourceLabel: String,
    onClick: () -> Unit,
    onSourceClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncArtwork(
                    uri = coverUri,
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 8.dp,
                    fallbackTextSize = 14.sp,
                    targetSize = 52.dp,
                    backgroundColor = p.surfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title.ifBlank { "未知歌曲" },
                        style = EchoTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle.ifBlank { "未知歌手" },
                        style = EchoTypography.caption,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SourceChip(sourceLabel, onSourceClick)
                Spacer(Modifier.width(8.dp))
                EchoIcon(EchoIconKind.Play, Modifier.size(22.dp), p.accent)
            }
            if (expandedContent != null) {
                Box(
                    modifier = Modifier.padding(start = 74.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, onClick: (() -> Unit)? = null) {
    val p = EchoTheme.colors()
    val content: @Composable () -> Unit = {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = p.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (onClick == null) {
        Surface(shape = EchoShapes.small, color = p.accent.copy(alpha = 0.10f), content = content)
    } else {
        Surface(
            onClick = onClick,
            shape = EchoShapes.small,
            color = p.accent.copy(alpha = 0.10f),
            content = content
        )
    }
}

@Composable
private fun PlaybackSourceOptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = EchoShapes.small,
        color = if (selected) p.accent.copy(alpha = 0.18f) else p.surfaceVariant
    ) {
        Text(
            if (selected) "$label · 当前" else label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) p.accent else p.muted,
            maxLines = 1
        )
    }
}

private fun fallbackSourceLabel(provider: StreamingProviderName): String = when (provider) {
    StreamingProviderName.NETEASE -> "网易云音乐"
    StreamingProviderName.QQ_MUSIC -> "QQ 音乐"
    StreamingProviderName.LUOXUE -> "LX/洛雪"
    StreamingProviderName.KUGOU -> "酷狗音乐"
    StreamingProviderName.BILIBILI -> "哔哩哔哩"
    StreamingProviderName.YOUTUBE -> "YouTube"
    StreamingProviderName.SOUNDCLOUD -> "SoundCloud"
    StreamingProviderName.SPOTIFY -> "Spotify"
    StreamingProviderName.TIDAL -> "TIDAL"
    StreamingProviderName.M3U8 -> "M3U8"
    StreamingProviderName.PLUGIN -> "自定义"
    StreamingProviderName.MOCK -> "在线"
}

private fun UnifiedSearchStreamingState.sourceLabel(track: StreamingTrack): String {
    if (track.provider != StreamingProviderName.LUOXUE) return sourceLabel(track.provider)
    val sourceKey = track.providerTrackId.substringBefore(':').trim().lowercase()
    return if (sourceKey in setOf("tx", "wy", "kw", "kg", "mg", "git", "local")) {
        "LX · ${sourceKey.uppercase()}"
    } else {
        sourceLabel(track.provider)
    }
}

private fun UnifiedSearchStreamingState.sourceLabel(candidate: StreamingPlaybackCandidate): String {
    if (candidate.provider != StreamingProviderName.LUOXUE) return sourceLabel(candidate.provider)
    val sourceKey = candidate.providerTrackId.orEmpty().substringBefore(':').trim().lowercase()
    return if (sourceKey in setOf("tx", "wy", "kw", "kg", "mg", "git", "local")) {
        "LX · ${sourceKey.uppercase()}"
    } else {
        sourceLabel(candidate.provider)
    }
}

private fun StreamingTrack.selectablePlaybackSources(): List<StreamingPlaybackCandidate> {
    val primary = StreamingPlaybackCandidate(
        provider = provider,
        label = provider.wireName,
        providerTrackId = providerTrackId,
        available = playable,
        luoxueMusicInfoJson = luoxueMusicInfoJson
    )
    val seen = linkedSetOf<String>()
    return (listOf(primary) + playbackCandidates).mapNotNull { candidate ->
        val candidateTrackId = candidate.providerTrackId?.trim().orEmpty()
        if (candidateTrackId.isBlank()) return@mapNotNull null
        candidate.copy(providerTrackId = candidateTrackId)
            .takeIf { seen.add("${candidate.provider.wireName}:$candidateTrackId") }
    }
}

private fun StreamingPlaybackCandidate.matches(
    provider: StreamingProviderName,
    providerTrackId: String
): Boolean = this.provider == provider && this.providerTrackId == providerTrackId

private fun StreamingTrack.withSelectedPlaybackSource(
    selected: StreamingPlaybackCandidate
): StreamingTrack {
    val selectedTrackId = selected.providerTrackId?.trim().orEmpty()
    if (selectedTrackId.isBlank()) return this
    return copy(
        provider = selected.provider,
        providerTrackId = selectedTrackId,
        qualities = selected.quality?.let(::setOf) ?: qualities,
        playable = selected.available,
        unavailableReason = null,
        playbackCandidates = selectablePlaybackSources(),
        luoxueMusicInfoJson = selected.luoxueMusicInfoJson
    )
}

private fun cleanSearchDisplayText(value: String?): String {
    return value
        ?.replace(Regex("<[^>]+>"), " ")
        ?.replace("&amp;", "&")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
        ?.replace("&quot;", "\"")
        ?.replace("&#39;", "'")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
}

@Composable
private fun SearchActionButton(label: String, icon: EchoIconKind, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        shape = EchoShapes.medium,
        color = Color.Transparent,
        modifier = Modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            EchoIcon(icon, Modifier.size(18.dp), p.accent)
            Spacer(Modifier.width(8.dp))
            Text(label, style = EchoTypography.label, color = p.heading)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, style = EchoTypography.body, color = p.muted)
    }
}
