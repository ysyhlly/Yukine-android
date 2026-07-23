package app.yukine.ui

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.TrackDownloadItem
import app.yukine.UnifiedSearchUiState
import app.yukine.model.LocalAudioFormatPolicy
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
            contentPadding = PaddingValues(start = 18.dp, top = 6.dp, end = 18.dp, bottom = echoPageBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (query.isBlank()) {
                item("search-hero") {
                    Box(Modifier.echoEnter(0)) {
                        SearchHero(
                            query = query,
                            localCount = searchState.localTracks.size,
                            onlineCount = onlineTracks.size,
                            searched = searchState.searched
                        )
                    }
                }
                item("search-scope") {
                    Box(Modifier.echoEnter(1)) {
                        SearchScopeCard()
                    }
                }
            } else {
                item("search-hero") {
                    Box(Modifier.echoEnter(0)) {
                        SearchHero(
                            query = query,
                            localCount = searchState.localTracks.size,
                            onlineCount = onlineTracks.size,
                            searched = searchState.searched
                        )
                    }
                }

                item("local-title") {
                    SearchSectionHeader(
                        title = "本地曲库",
                        subtitle = "先从设备里的收藏开始",
                        count = searchState.localTracks.size,
                        icon = EchoIconKind.Library
                    )
                }
                if (searchState.localTracks.isEmpty() && searchState.searched) {
                    item("local-empty") {
                        EmptyHint("本地没有找到匹配歌曲", EchoIconKind.Search)
                    }
                } else {
                    itemsIndexed(
                        items = searchState.localTracks,
                        key = { _, track -> track.id }
                    ) { index, track ->
                        LocalTrackResultRow(
                            track = track,
                            modifier = Modifier.echoEnter(index.coerceAtMost(8))
                        ) {
                            actions.onPlayLocalTrack.run(track)
                        }
                    }
                }

                item("online-title") {
                    SearchSectionHeader(
                        title = "在线音乐",
                        subtitle = if (streamingState.loading) "正在继续寻找更多声音" else "从已连接的音源继续找",
                        count = onlineTracks.size,
                        icon = EchoIconKind.Network
                    )
                }
                when {
                    streamingState.loading && onlineTracks.isEmpty() -> item("online-loading") {
                        EmptyHint("正在搜索在线音乐…", EchoIconKind.Sync)
                    }
                    !streamingState.errorMessage.isNullOrBlank() && onlineTracks.isEmpty() -> item("online-error") {
                        EmptyHint(streamingState.errorMessage ?: "在线搜索失败", EchoIconKind.Info)
                    }
                    onlineTracks.isEmpty() && searchState.searched -> item("online-empty") {
                        EmptyHint("在线音乐没有找到匹配歌曲", EchoIconKind.Search)
                    }
                    else -> {
                        itemsIndexed(
                            items = onlineTracks,
                            key = { _, track -> track.stableKey }
                        ) { index, track ->
                            StreamingTrackResultRow(
                                track = track,
                                streamingState = streamingState,
                                modifier = Modifier.echoEnter(index.coerceAtMost(8)),
                                onPlay = actions.onPlayStreamingTrack::run
                            )
                        }
                        if (!streamingState.errorMessage.isNullOrBlank()) {
                            item("online-playback-error") {
                                EmptyHint(
                                    streamingState.errorMessage ?: "在线歌曲解析失败",
                                    EchoIconKind.Info
                                )
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
}

@Composable
private fun SearchHero(
    query: String,
    localCount: Int,
    onlineCount: Int,
    searched: Boolean
) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(p.accent)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (query.isBlank()) "听见所想" else "搜索结果",
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent
            )
        }
        Text(
            if (query.isBlank()) "想听什么，就从这里开始。" else "关于「$query」",
            style = EchoTypography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = p.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            when {
                query.isBlank() -> "本地收藏与在线音乐，一次找齐。"
                !searched -> "按下搜索，同时去本地与在线寻找。"
                else -> "本地 $localCount 首 · 在线 $onlineCount 首"
            },
            style = EchoTypography.body,
            color = p.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchScopeCard() {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGaussianBackdrop(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = echoCardColor(p.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                "一次搜索，两处回声",
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.heading
            )
            Spacer(Modifier.height(10.dp))
            SearchScopeRow(
                icon = EchoIconKind.Library,
                title = "本地曲库",
                subtitle = "从设备里的歌曲、歌手与专辑开始"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp, top = 7.dp, bottom = 7.dp),
                color = p.border.copy(alpha = 0.48f)
            )
            SearchScopeRow(
                icon = EchoIconKind.Network,
                title = "在线音乐",
                subtitle = "再去已连接的音乐来源里继续寻找"
            )
        }
    }
}

@Composable
private fun SearchScopeRow(icon: EchoIconKind, title: String, subtitle: String) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = EchoShapes.medium,
            color = p.accentSoft.copy(alpha = 0.76f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                EchoIcon(icon, Modifier.size(19.dp), p.accent)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text
            )
            Text(
                subtitle,
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.muted)
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    icon: EchoIconKind
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = EchoShapes.medium,
            color = p.accentSoft.copy(alpha = 0.72f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                EchoIcon(icon, Modifier.size(18.dp), p.accent)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                color = p.heading
            )
            Text(
                subtitle,
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(shape = EchoShapes.full, color = p.accentSoft.copy(alpha = 0.66f)) {
            Text(
                "$count 首",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.accent
            )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
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
            if (query.isNotBlank()) {
                Surface(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(34.dp)
                        .semantics { contentDescription = "清空搜索" },
                    shape = CircleShape,
                    color = p.surfaceVariant.copy(alpha = 0.66f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(EchoIconKind.Remove, Modifier.size(15.dp), p.muted)
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Surface(
                onClick = onSearch,
                shape = EchoShapes.medium,
                color = p.accent,
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoIcon(EchoIconKind.Search, Modifier.size(16.dp), p.onAccent)
                    Spacer(Modifier.width(6.dp))
                    Text("搜索", style = EchoTypography.label, color = p.onAccent)
                }
            }
            Spacer(Modifier.width(8.dp))
            YukineDownloadOrb(
                item = activeDownload,
                playbackQuality = playbackQuality,
                audioMotion = audioMotion,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun LocalTrackResultRow(
    track: Track,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val playbackEnabled = LocalAudioFormatPolicy.isPlaybackAllowed(track)
    ResultRow(
        title = track.title,
        subtitle = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" / "),
        coverUri = track.albumArtUri,
        sourceLabel = if (playbackEnabled) "本地" else "不支持格式",
        modifier = modifier,
        onClick = { if (playbackEnabled) onClick() },
        playbackEnabled = playbackEnabled
    )
}

@Composable
private fun StreamingTrackResultRow(
    track: StreamingTrack,
    streamingState: UnifiedSearchStreamingState,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    playbackEnabled: Boolean = true,
    onSourceClick: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (!playbackEnabled) stateDescription = sourceLabel
            }
            .echoPressScale(interaction)
            .echoGaussianBackdrop(p, EchoShapes.large)
            .animateContentSize(),
        shape = EchoShapes.large,
        color = echoCardColor(p.surface)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncArtwork(
                    uri = coverUri,
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.size(56.dp),
                    cornerRadius = 10.dp,
                    fallbackTextSize = 15.sp,
                    targetSize = 56.dp,
                    backgroundColor = p.surfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title.ifBlank { "未知歌曲" },
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                EchoIcon(
                    EchoIconKind.Play,
                    Modifier.size(22.dp),
                    if (playbackEnabled) p.accent else p.muted.copy(alpha = 0.45f)
                )
            }
            if (expandedContent != null) {
                Box(
                    modifier = Modifier.padding(start = 80.dp, end = 12.dp, bottom = 12.dp)
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
private fun EmptyHint(text: String, icon: EchoIconKind = EchoIconKind.Info) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGaussianBackdrop(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = echoCardColor(p.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = EchoShapes.medium,
                color = p.accentSoft.copy(alpha = 0.68f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(icon, Modifier.size(17.dp), p.accent)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = EchoTypography.body, color = p.muted, modifier = Modifier.weight(1f))
        }
    }
}
