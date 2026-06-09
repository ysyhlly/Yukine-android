package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.next.MainActivityStreamingState
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingAlbum
import app.echo.next.streaming.StreamingArtist
import app.echo.next.streaming.StreamingCapabilityResolver
import app.echo.next.streaming.StreamingMvItem
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderCapability
import app.echo.next.streaming.StreamingProviderHealth
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingProviderStatus
import app.echo.next.streaming.StreamingTrack
import kotlinx.coroutines.flow.StateFlow

data class StreamingSearchActions(
    val onBack: Runnable,
    val onSelectProvider: ProviderAction,
    val onSearch: QueryAction,
    val onLogin: ProviderAction,
    val onSignOut: ProviderAction,
    val onOpenAuthLaunch: Runnable,
    val onPlayTrack: StreamingTrackAction,
    val onPlayResolvedTrack: TrackAction,
    val onNextPage: Runnable,
    val onImportPlaylist: StreamingPlaylistAction,
    val onLoadUserPlaylists: Runnable,
    val onImportLikedTracks: Runnable,
    val onDailyRecommend: Runnable,
    val onHeartbeatRecommend: Runnable,
    val onPasteImport: Runnable,
    val onInputCookie: Runnable
)

fun interface ProviderAction {
    fun run(provider: StreamingProviderName)
}

fun interface QueryAction {
    fun run(query: String)
}

fun interface StreamingTrackAction {
    fun run(track: StreamingTrack)
}

fun interface StreamingPlaylistAction {
    fun run(playlist: StreamingPlaylist)
}

fun interface TrackAction {
    fun run(track: Track)
}

object StreamingSearchScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityStreamingState>,
        actions: StreamingSearchActions
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState = state.collectAsState()
                StreamingSearchScreen(uiState.value, actions)
            }
        }
    }
}

@Composable
private fun StreamingSearchScreen(state: MainActivityStreamingState, actions: StreamingSearchActions) {
    val p = EchoTheme.colors()
    val provider = state.providers.firstOrNull { it.name == state.selectedProvider }
    val selectedCapability = state.providerCapabilities.firstOrNull { it.provider == state.selectedProvider }
    val selectedHealth = state.providerHealth.firstOrNull { it.provider == state.selectedProvider }
    val canSearch = selectedCapability?.supportsSearch ?: (provider?.let(StreamingCapabilityResolver::canSearch) == true)
    val result = state.searchResult
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle("流媒体", subtitle = provider?.displayName)
        }
        item(key = "back") {
            ActionRow("返回", EchoIconKind.Back) { actions.onBack.run() }
        }
        if (state.providers.isNotEmpty()) {
            itemsIndexed(
                items = state.providers,
                key = { _, item -> "provider:${item.name.wireName}" }
            ) { _, item ->
                val capability = state.providerCapabilities.firstOrNull { it.provider == item.name }
                val health = state.providerHealth.firstOrNull { it.provider == item.name }
                val authState = state.authStates[item.name] ?: item.auth
                ProviderRow(
                    name = item.displayName,
                    selected = item.name == state.selectedProvider,
                    status = providerStatusText(item.statusMessage, item.status, health, authState),
                    supportsAuth = capability?.supportsAuth ?: StreamingCapabilityResolver.canAuth(item),
                    connected = authState.connected,
                    onSelect = { actions.onSelectProvider.run(item.name) },
                    onLogin = { actions.onLogin.run(item.name) },
                    onSignOut = { actions.onSignOut.run(item.name) }
                )
            }
        }
        item(key = "search") {
            val query = state.searchQuery.ifBlank { "echo" }
            if (canSearch) {
                ActionRow("搜索 \"$query\"", EchoIconKind.Search) { actions.onSearch.run(query) }
            } else {
                MessageRow("${provider?.displayName ?: "音源"} 暂不可搜索")
            }
        }
        item(key = "import-from-streaming") {
            ActionRow("从流媒体导入歌单", EchoIconKind.PlaylistAdd) { actions.onPasteImport.run() }
        }
        item(key = "load-account-playlists") {
            ActionRow("加载账户歌单", EchoIconKind.Collections) { actions.onLoadUserPlaylists.run() }
        }
        item(key = "import-liked-tracks") {
            ActionRow("导入流媒体收藏", EchoIconKind.Heart) { actions.onImportLikedTracks.run() }
        }
        if (state.selectedProvider == StreamingProviderName.NETEASE) {
            item(key = "daily-recommend") {
                ActionRow("每日推荐", EchoIconKind.Sparkle) { actions.onDailyRecommend.run() }
            }
            item(key = "heartbeat-recommend") {
                ActionRow("心动推荐", EchoIconKind.Heart) { actions.onHeartbeatRecommend.run() }
            }
        }
        item(key = "manual-cookie") {
            ActionRow("手动填写 Cookie", EchoIconKind.Edit) { actions.onInputCookie.run() }
        }
        if (state.userPlaylistsLoading) {
            item(key = "account-playlists-loading") {
                MessageRow("正在加载账户歌单")
            }
        }
        if (state.userPlaylists.isNotEmpty()) {
            item(key = "account-playlists-title") {
                SectionTitle("账户歌单")
            }
            itemsIndexed(
                items = state.userPlaylists,
                key = { _, item -> "user-playlist:${item.provider.wireName}:${item.providerPlaylistId}" }
            ) { _, item ->
                StreamingPlaylistRow(item) { actions.onImportPlaylist.run(item) }
            }
        }
        state.pendingAuthLaunch?.let { launch ->
            item(key = "auth-launch:${launch.provider.wireName}") {
                ActionRow("打开 ${launch.provider.wireName} 登录", EchoIconKind.Action) {
                    actions.onOpenAuthLaunch.run()
                }
            }
        }
        if (provider != null) {
            item(key = "provider-metadata") {
                MetricRow("音源", provider.displayName)
            }
            item(key = "provider-capabilities") {
                val value = capabilityLabels(provider, selectedCapability).joinToString(" / ")
                MetricRow("能力", value.ifBlank { "无" })
            }
            item(key = "provider-actions") {
                val value = actionLabels(provider, selectedCapability).joinToString(" / ")
                MetricRow("操作", value.ifBlank { "无" })
            }
            selectedHealth?.let { health ->
                item(key = "provider-health") {
                    MetricRow("健康状态", providerHealthText(health))
                }
            }
            if (StreamingCapabilityResolver.canFavorites(provider)) {
                item(key = "provider-favorites") {
                    CapabilityRow("支持收藏同步", EchoIconKind.Heart)
                }
            }
            if (StreamingCapabilityResolver.canPlaylists(provider)) {
                item(key = "provider-playlists") {
                    CapabilityRow("支持歌单", EchoIconKind.PlaylistAdd)
                }
            }
        }
        if (state.loading && !state.loadingMore) {
            item(key = "loading") {
                MessageRow("加载中")
            }
        }
        if (state.playlistImporting) {
            item(key = "playlist-importing") {
                MessageRow("正在匹配本地歌曲到流媒体")
            }
        }
        state.playlistImportSummary?.let { summary ->
            item(key = "playlist-import-title") {
                SectionTitle("歌单导入：${summary.playlistName}")
            }
            item(key = "playlist-import-meta") {
                MetricRow(
                    "匹配",
                    "${summary.matchedTracks.size} / ${summary.totalRequested} (${summary.matchRatePercent}%)"
                )
            }
            if (summary.unresolvedTracks.isNotEmpty()) {
                item(key = "playlist-import-unresolved") {
                    MetricRow("未匹配", "${summary.unresolvedTracks.size}")
                }
            }
        }
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            item(key = "error") {
                MessageRow(message)
            }
        }
        val tracks = result?.tracks.orEmpty()
        val albums = result?.albums.orEmpty()
        val artists = result?.artists.orEmpty()
        val playlists = result?.playlists.orEmpty()
        val mvs = result?.mvs.orEmpty()
        val displayedCount = tracks.size + albums.size + artists.size + playlists.size + mvs.size
        if (result != null) {
            item(key = "result-meta") {
                MetricRow("结果", "$displayedCount${result.total?.let { " / $it" }.orEmpty()}")
            }
        }
        val importedTracks = state.playlistImportSummary?.matchedTracks.orEmpty()
        if (importedTracks.isNotEmpty()) {
            item(key = "imported-tracks-title") {
                SectionTitle("已匹配的流媒体歌曲")
            }
            itemsIndexed(
                items = importedTracks,
                key = { _, track -> "imported:${track.stableKey}" }
            ) { _, track ->
                StreamingTrackRow(track, trackProviderSupportsPlayback(state, track) && track.playable) {
                    actions.onPlayTrack.run(track)
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item(key = "tracks-title") {
                SectionTitle("歌曲")
            }
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.stableKey }
            ) { _, track ->
                StreamingTrackRow(track, trackProviderSupportsPlayback(state, track) && track.playable) {
                    actions.onPlayTrack.run(track)
                }
            }
        }
        if (albums.isNotEmpty()) {
            item(key = "albums-title") {
                SectionTitle("专辑")
            }
            itemsIndexed(
                items = albums,
                key = { _, album -> "album:${album.provider.wireName}:${album.providerAlbumId}" }
            ) { _, album ->
                StreamingAlbumRow(album)
            }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists-title") {
                SectionTitle("艺人")
            }
            itemsIndexed(
                items = artists,
                key = { _, artist -> "artist:${artist.provider.wireName}:${artist.providerArtistId}" }
            ) { _, artist ->
                StreamingArtistRow(artist)
            }
        }
        if (playlists.isNotEmpty()) {
            item(key = "playlists-title") {
                SectionTitle("歌单")
            }
            itemsIndexed(
                items = playlists,
                key = { _, playlist -> "playlist:${playlist.provider.wireName}:${playlist.providerPlaylistId}" }
            ) { _, playlist ->
                StreamingPlaylistRow(playlist) { actions.onImportPlaylist.run(playlist) }
            }
        }
        if (mvs.isNotEmpty()) {
            item(key = "mvs-title") {
                SectionTitle("视频")
            }
            itemsIndexed(
                items = mvs,
                key = { _, mv -> "mv:${mv.provider.wireName}:${mv.providerMvId}" }
            ) { _, mv ->
                StreamingMvRow(mv)
            }
        }
        if (result != null && displayedCount == 0 && !state.loading) {
            item(key = "empty") {
                MessageRow("没有找到流媒体结果")
            }
        }
        if (state.loadingMore) {
            item(key = "loading-more") {
                MessageRow("加载更多")
            }
        } else if (result?.hasMore == true && canSearch) {
            item(key = "next-page") {
                ActionRow("加载更多", EchoIconKind.Next) { actions.onNextPage.run() }
            }
        }
        state.resolvedPlaybackTrack?.let { track ->
            item(key = "play-resolved") {
                ActionRow("播放已解析歌曲", EchoIconKind.Play) {
                    actions.onPlayResolvedTrack.run(track)
                }
            }
        }
        if (state.diagnostics.totalRequests > 0 || state.diagnostics.recentLogs.isNotEmpty()) {
            item(key = "debug-title") {
                SectionTitle("调试")
            }
            item(key = "debug-requests") {
                MetricRow("请求数", state.diagnostics.totalRequests.toString())
            }
            item(key = "debug-cache") {
                MetricRow("缓存命中率", "${state.diagnostics.cacheHitRate}% (${state.diagnostics.cacheHits})")
            }
            itemsIndexed(
                items = state.diagnostics.recentLogs.take(5),
                key = { index, item -> "debug-log:$index:${item.timestampMs}:${item.operation}" }
            ) { _, log ->
                MetricRow("最近日志", streamingLogText(log))
            }
        }
    }
}

private fun trackProviderSupportsPlayback(state: MainActivityStreamingState, track: StreamingTrack): Boolean {
    state.providerCapabilities.firstOrNull { it.provider == track.provider }?.let { capability ->
        return capability.supportsPlayback
    }
    val descriptor = state.providers.firstOrNull { it.name == track.provider }
    return StreamingCapabilityResolver.canPlayback(descriptor)
}

private fun capabilityLabels(
    provider: app.echo.next.streaming.StreamingProviderDescriptor,
    capability: StreamingProviderCapability?
): List<String> {
    return listOfNotNull(
        "搜索".takeIf { capability?.supportsSearch ?: provider.capabilities.supportsSearch },
        "播放".takeIf { capability?.supportsPlayback ?: provider.capabilities.supportsPlayback },
        "歌词".takeIf { capability?.supportsLyrics ?: provider.capabilities.supportsLyrics },
        "视频".takeIf { capability?.supportsMv ?: provider.capabilities.supportsMv },
        "登录".takeIf { capability?.supportsAuth ?: provider.capabilities.supportsAuth },
        "收藏".takeIf { capability?.supportsFavorites ?: provider.capabilities.supportsFavorites },
        "歌单".takeIf { capability?.supportsPlaylists ?: provider.capabilities.supportsPlaylists }
    )
}

private fun actionLabels(
    provider: app.echo.next.streaming.StreamingProviderDescriptor,
    capability: StreamingProviderCapability?
): List<String> {
    return capability?.actions?.map { actionLabel(it) } ?: StreamingCapabilityResolver.actionLabels(provider).map { actionLabel(it) }
}

private fun actionLabel(value: String): String {
    val normalized = value.trim()
        .replace("-", "_")
        .replace(" ", "_")
        .lowercase()
    return when (normalized) {
        "search", "cansearch" -> "搜索"
        "play", "playback", "resolveplayback", "stream", "source" -> "播放"
        "auth", "login", "signin", "oauth", "connectaccount" -> "登录"
        "logout", "signout", "disconnect" -> "退出登录"
        "favorites", "favorite", "likes", "like" -> "收藏"
        "playlists", "playlist" -> "歌单"
        "lyrics", "lyric" -> "歌词"
        "mv", "video", "videos" -> "视频"
        "health", "status" -> "健康"
        "capabilities", "capability" -> "能力"
        "account", "profile" -> "账号"
        "refresh", "sync" -> "刷新"
        else -> value.takeIf { it.any { char -> char in '\u4e00'..'\u9fff' } } ?: "扩展操作"
    }
}

private fun providerStatusText(
    message: String?,
    status: StreamingProviderStatus,
    health: StreamingProviderHealth?,
    authState: app.echo.next.streaming.StreamingAuthState? = null
): String {
    if (authState?.connected == true) {
        val name = authState.accountDisplayName?.takeIf { it.isNotBlank() }
        return if (name != null) "已登录 · $name" else "已登录"
    }
    health?.let {
        if (it.available) {
            return if (it.authenticated) "在线，已认证" else "在线"
        }
        return it.errorMessage ?: "不可用"
    }
    return message ?: when (status) {
        StreamingProviderStatus.READY -> "就绪"
        StreamingProviderStatus.NEEDS_ACCOUNT -> "需要登录"
        StreamingProviderStatus.DISABLED -> "已停用"
        StreamingProviderStatus.ERROR -> "异常"
    }
}

private fun providerHealthText(health: StreamingProviderHealth): String {
    return if (health.available) {
        listOfNotNull(
            "可用",
            "已认证".takeIf { health.authenticated },
            health.latencyMs?.let { "${it}ms" }
        ).joinToString(" / ")
    } else {
        listOfNotNull(
            "不可用",
            health.errorCode?.wireName,
            health.errorMessage
        ).joinToString(" / ")
    }
}

private fun streamingLogText(log: app.echo.next.streaming.StreamingGatewayLogEntry): String {
    val source = log.provider?.wireName ?: "gateway"
    val outcome = when {
        log.cacheHit -> "缓存命中"
        log.errorCode != null -> "错误 ${log.errorCode.wireName}"
        else -> "成功"
    }
    return "${operationLabel(log.operation)} / $source / ${log.durationMs}ms / $outcome"
}

private fun operationLabel(value: String): String {
    return when (value) {
        "providers" -> "音源"
        "capabilities" -> "能力"
        "health" -> "健康"
        "search" -> "搜索"
        "playlist" -> "歌单"
        "playback" -> "播放"
        "auth" -> "认证"
        "auth_start" -> "开始登录"
        "auth_complete" -> "完成登录"
        "auth_sign_out" -> "退出登录"
        else -> value
    }
}

@Composable
private fun SectionTitle(title: String) {
    EchoSectionTitle(title)
}

@Composable
private fun ProviderRow(
    name: String,
    selected: Boolean,
    status: String,
    supportsAuth: Boolean,
    connected: Boolean,
    onSelect: () -> Unit,
    onLogin: () -> Unit,
    onSignOut: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = name },
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(
                if (connected) EchoIconKind.Heart else EchoIconKind.Network,
                Modifier.size(22.dp),
                if (connected) p.accent else if (selected) p.accent else p.muted
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    style = EchoTypography.caption,
                    color = if (connected) p.accent else p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (supportsAuth) {
                if (connected) {
                    Surface(
                        onClick = onSignOut,
                        modifier = Modifier.echoGlassLayer(p, EchoShapes.small),
                        shape = EchoShapes.small,
                        color = Color.Transparent
                    ) {
                        EchoIcon(
                            EchoIconKind.Remove,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(18.dp),
                            color = p.muted
                        )
                    }
                } else {
                    Surface(
                        onClick = onLogin,
                        modifier = Modifier.echoGlassLayer(p, EchoShapes.small),
                        shape = EchoShapes.small,
                        color = Color.Transparent
                    ) {
                        EchoIcon(
                            EchoIconKind.Action,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(18.dp),
                            color = p.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAlbumRow(album: StreamingAlbum) {
    StreamingInfoRow(
        title = album.title,
        subtitle = listOfNotNull(album.artist, album.trackCount?.let { "$it 首" }).joinToString(" - "),
        icon = EchoIconKind.Collections
    )
}

@Composable
private fun StreamingArtistRow(artist: StreamingArtist) {
    StreamingInfoRow(
        title = artist.name,
        subtitle = artist.provider.wireName,
        icon = EchoIconKind.Artist
    )
}

@Composable
private fun StreamingPlaylistRow(playlist: StreamingPlaylist, onImport: (() -> Unit)? = null) {
    StreamingInfoRow(
        title = playlist.title,
        subtitle = listOfNotNull(
            playlist.creator,
            playlist.trackCount?.let { "$it 首" },
            playlist.description
        ).joinToString(" - ").ifBlank { playlist.provider.wireName },
        icon = EchoIconKind.PlaylistAdd,
        onClick = onImport,
        trailingIcon = if (onImport != null) EchoIconKind.Next else null
    )
}

@Composable
private fun StreamingMvRow(mv: StreamingMvItem) {
    StreamingInfoRow(
        title = mv.title,
        subtitle = "${mv.artist} - ${mv.provider.wireName}",
        icon = EchoIconKind.Play
    )
}

@Composable
private fun StreamingInfoRow(
    title: String,
    subtitle: String,
    icon: EchoIconKind,
    onClick: (() -> Unit)? = null,
    trailingIcon: EchoIconKind? = null
) {
    val p = EchoTheme.colors()
    val rowModifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = title }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier.echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingInfoRowBody(title, subtitle, icon, true, trailingIcon)
        }
    } else {
        Surface(
            modifier = rowModifier.echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingInfoRowBody(title, subtitle, icon, false, trailingIcon)
        }
    }
}

@Composable
private fun StreamingInfoRowBody(
    title: String,
    subtitle: String,
    icon: EchoIconKind,
    clickable: Boolean,
    trailingIcon: EchoIconKind?
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoIcon(icon, Modifier.size(22.dp), if (clickable) p.accent else p.muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingIcon != null) {
            EchoIcon(trailingIcon, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun CapabilityRow(label: String, icon: EchoIconKind) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StreamingTrackRow(track: StreamingTrack, playable: Boolean, onPlay: () -> Unit) {
    val p = EchoTheme.colors()
    val modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = track.title }
    if (playable) {
        Surface(
            onClick = onPlay,
            modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingTrackRowContent(track, playable)
        }
    } else {
        Surface(
            modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingTrackRowContent(track, playable)
        }
    }
}

@Composable
private fun StreamingTrackRowContent(track: StreamingTrack, playable: Boolean) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoIcon(EchoIconKind.Play, Modifier.size(22.dp), if (playable) p.accent else p.muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.unavailableReason?.takeIf { !playable && it.isNotBlank() }
                    ?: "${track.artist} - ${track.provider.wireName}",
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (playable) {
            EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: EchoIconKind, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = EchoTypography.bodyMedium,
                color = p.muted,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = EchoTypography.bodyMedium,
                color = p.text,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageRow(message: String) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Text(
            message,
            style = EchoTypography.bodyMedium,
            color = p.muted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
