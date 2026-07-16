package app.yukine.ui

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.StreamingSearchState
import app.yukine.model.Track
import app.yukine.streaming.StreamingAlbum
import app.yukine.streaming.StreamingArtist
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingMvItem
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.streaming.StreamingTrack

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
    val onManageLuoxueSources: Runnable,
    val onInputCookie: Runnable,
    val onPlaybackEnabledChanged: ProviderToggleAction = ProviderToggleAction { _, _ -> },
    val onPlaybackPriorityUp: ProviderAction = ProviderAction { _ -> },
    val onPlaybackPriorityDown: ProviderAction = ProviderAction { _ -> }
) {
    companion object {
        @JvmStatic
        fun empty(): StreamingSearchActions = StreamingSearchActions(
            onBack = Runnable {},
            onSelectProvider = ProviderAction { _ -> },
            onSearch = QueryAction { _ -> },
            onLogin = ProviderAction { _ -> },
            onSignOut = ProviderAction { _ -> },
            onOpenAuthLaunch = Runnable {},
            onPlayTrack = StreamingTrackAction { _ -> },
            onPlayResolvedTrack = TrackAction { _ -> },
            onNextPage = Runnable {},
            onImportPlaylist = StreamingPlaylistAction { _ -> },
            onLoadUserPlaylists = Runnable {},
            onImportLikedTracks = Runnable {},
            onDailyRecommend = Runnable {},
            onHeartbeatRecommend = Runnable {},
            onPasteImport = Runnable {},
            onManageLuoxueSources = Runnable {},
            onInputCookie = Runnable {},
            onPlaybackEnabledChanged = ProviderToggleAction { _, _ -> },
            onPlaybackPriorityUp = ProviderAction { _ -> },
            onPlaybackPriorityDown = ProviderAction { _ -> }
        )
    }
}

data class StreamingSearchLabels(
    val languageMode: String,
    val usageNotice: StreamingUsageNoticeLabels,
    val title: String,
    val back: String,
    val searchPrefix: String,
    val searchSuffix: String,
    val sourceDefault: String,
    val searchUnavailableSuffix: String,
    val importPlaylistFromStreaming: String,
    val importLuoxueSource: String,
    val manageLuoxueSources: String,
    val luoxueImportHint: String,
    val loadAccountPlaylists: String,
    val importLikedTracks: String,
    val dailyRecommendations: String,
    val heartbeatRecommendations: String,
    val backupAccountConnection: String,
    val accountActions: String,
    val discoverMusic: String,
    val advancedTools: String,
    val loadingAccountPlaylists: String,
    val accountPlaylists: String,
    val openLoginPrefix: String,
    val openLoginSuffix: String,
    val loading: String,
    val streamingRequestFailed: String,
    val playlistImportFailed: String,
    val accountPlaylistsFailed: String,
    val matchingLocalTracks: String,
    val playlistImportPrefix: String,
    val matched: String,
    val unresolved: String,
    val results: String,
    val matchedStreamingTracks: String,
    val songs: String,
    val albums: String,
    val artists: String,
    val playlists: String,
    val videos: String,
    val noResults: String,
    val loadMore: String,
    val playResolvedTrack: String,
    val signedIn: String,
    val onlineAuthenticated: String,
    val online: String,
    val unavailable: String,
    val ready: String,
    val needsAccount: String,
    val disabled: String,
    val error: String,
    val localLoginSaved: String,
    val notSignedIn: String,
    val sessionVerified: String,
    val sessionPendingVerification: String,
    val sessionInvalid: String,
    val localLoginComplete: String,
    val gatewayLocalLogin: String,
    val gatewayRequired: String,
    val loginEntryMissing: String,
    val openLoginPage: String,
    val neteaseLikedPlaylistEmpty: String,
    val neteaseAccountIdMissing: String,
    val neteaseLoginRequiredPlaylists: String,
    val trackCountSuffix: String
) {
    companion object {
        @JvmStatic
        fun empty(): StreamingSearchLabels = StreamingSearchLabels(
            languageMode = "system",
            usageNotice = StreamingUsageNoticeLabels.defaults(),
            title = "\u97f3\u6e90\u4e0e\u7f51\u7edc",
            back = "\u8fd4\u56de",
            searchPrefix = "\u641c\u7d22 ",
            searchSuffix = "",
            sourceDefault = "\u97f3\u6e90",
            searchUnavailableSuffix = "\u6682\u4e0d\u652f\u6301\u641c\u7d22",
            importPlaylistFromStreaming = "\u5bfc\u5165\u6b4c\u5355",
            importLuoxueSource = "\u5bfc\u5165 LX \u97f3\u6e90 / \u6b4c\u5355",
            manageLuoxueSources = "LX \u97f3\u6e90\u7ba1\u7406",
            luoxueImportHint = "\u53ef\u5bfc\u5165 LX \u97f3\u6e90 JS \u6587\u4ef6\u6216\u5206\u4eab\u94fe\u63a5\u3002",
            loadAccountPlaylists = "\u52a0\u8f7d\u8d26\u53f7\u6b4c\u5355",
            importLikedTracks = "\u5bfc\u5165\u6536\u85cf\u6b4c\u66f2",
            dailyRecommendations = "\u6bcf\u65e5\u63a8\u8350",
            heartbeatRecommendations = "\u5fc3\u52a8\u63a8\u8350",
            backupAccountConnection = "\u5907\u7528\u8d26\u53f7\u8fde\u63a5",
            accountActions = "\u8d26\u53f7",
            discoverMusic = "\u53d1\u73b0\u97f3\u4e50",
            advancedTools = "\u9ad8\u7ea7\u5de5\u5177",
            loadingAccountPlaylists = "\u6b63\u5728\u52a0\u8f7d\u6b4c\u5355",
            accountPlaylists = "\u8d26\u53f7\u6b4c\u5355",
            openLoginPrefix = "\u767b\u5f55 ",
            openLoginSuffix = "",
            loading = "\u52a0\u8f7d\u4e2d",
            streamingRequestFailed = "\u6d41\u5a92\u4f53\u8bf7\u6c42\u5931\u8d25",
            playlistImportFailed = "\u6b4c\u5355\u5bfc\u5165\u5931\u8d25",
            accountPlaylistsFailed = "\u65e0\u6cd5\u52a0\u8f7d\u8d26\u53f7\u6b4c\u5355",
            matchingLocalTracks = "\u6b63\u5728\u5339\u914d\u672c\u5730\u6b4c\u66f2",
            playlistImportPrefix = "\u5bfc\u5165\uff1a",
            matched = "\u5df2\u5339\u914d",
            unresolved = "\u672a\u5339\u914d",
            results = "\u7ed3\u679c",
            matchedStreamingTracks = "\u5df2\u5339\u914d\u6b4c\u66f2",
            songs = "\u6b4c\u66f2",
            albums = "\u4e13\u8f91",
            artists = "\u827a\u4eba",
            playlists = "\u6b4c\u5355",
            videos = "\u89c6\u9891",
            noResults = "\u6ca1\u6709\u627e\u5230\u6b4c\u66f2",
            loadMore = "\u52a0\u8f7d\u66f4\u591a",
            playResolvedTrack = "\u64ad\u653e\u5df2\u89e3\u6790\u6b4c\u66f2",
            signedIn = "\u5df2\u767b\u5f55",
            onlineAuthenticated = "\u5df2\u8fde\u63a5",
            online = "\u5728\u7ebf",
            unavailable = "\u4e0d\u53ef\u7528",
            ready = "\u5c31\u7eea",
            needsAccount = "\u9700\u8981\u767b\u5f55",
            disabled = "\u5df2\u7981\u7528",
            error = "\u9519\u8bef",
            localLoginSaved = "\u672c\u673a\u767b\u5f55\u5df2\u4fdd\u5b58",
            notSignedIn = "\u672a\u767b\u5f55",
            sessionVerified = "\u5df2\u9a8c\u8bc1",
            sessionPendingVerification = "\u767b\u5f55\u5f85\u9a8c\u8bc1",
            sessionInvalid = "\u767b\u5f55\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55",
            localLoginComplete = "\u672c\u673a\u767b\u5f55\u5b8c\u6210",
            gatewayLocalLogin = "\u7f51\u5173\u4e0d\u53ef\u7528\uff0c\u53ef\u4f7f\u7528\u672c\u673a\u767b\u5f55",
            gatewayRequired = "\u9700\u8981\u7f51\u5173",
            loginEntryMissing = "\u672a\u914d\u7f6e\u767b\u5f55\u5165\u53e3",
            openLoginPage = "\u6253\u5f00\u767b\u5f55\u9875",
            neteaseLikedPlaylistEmpty = "\u672a\u627e\u5230\u6536\u85cf\u6b4c\u5355",
            neteaseAccountIdMissing = "\u7f3a\u5c11\u7f51\u6613\u4e91\u8d26\u53f7 ID",
            neteaseLoginRequiredPlaylists = "\u8bf7\u5148\u767b\u5f55\u518d\u52a0\u8f7d\u6b4c\u5355",
            trackCountSuffix = " \u9996"
        )
    }
}

fun interface ProviderAction {
    fun run(provider: StreamingProviderName)
}

fun interface QueryAction {
    fun run(query: String)
}

fun interface StreamingPlaylistAction {
    fun run(playlist: StreamingPlaylist)
}

fun interface TrackAction {
    fun run(track: Track)
}

@Composable
fun StreamingSearchScreen(
    state: StreamingSearchState,
    labels: StreamingSearchLabels,
    actions: StreamingSearchActions
) {
    val provider = state.providers.firstOrNull { it.name == state.selectedProvider }
    val selectedCapability = state.providerCapabilities.firstOrNull { it.provider == state.selectedProvider }
    val selectedHealth = state.providerHealth.firstOrNull { it.provider == state.selectedProvider }
    val canSearch = selectedCapability?.supportsSearch ?: (provider?.let(StreamingCapabilityResolver::canSearch) == true)
    val hasLocalPendingProviders = state.providers.any { provider ->
        provider.name != StreamingProviderName.NETEASE && !provider.enabled
    }
    val result = state.searchResult
    val selectedAuthState = provider?.let { state.authStates[it.name] ?: it.auth }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(
                labels.title,
                subtitle = provider?.displayName,
                backLabel = labels.back,
                onBack = actions.onBack
            )
        }
        item(key = "search-hero") {
            StreamingSearchHero(
                providerName = provider?.displayName ?: labels.sourceDefault,
                providerStatus = provider?.let {
                    listOfNotNull(
                        streamingProviderStatusText(it.statusMessage, it.status, selectedHealth, selectedAuthState, labels),
                        selectedCapability?.let(::providerCapabilitySummary)
                    ).filter { text -> text.isNotBlank() }.distinct().joinToString(" · ")
                }.orEmpty(),
                query = state.searchQuery.ifBlank { "echo" },
                canSearch = canSearch,
                labels = labels,
                onSearch = actions.onSearch
            )
        }
        if (state.providers.isNotEmpty()) {
            item(key = "provider-picker-title") { SectionTitle(labels.sourceDefault) }
            item(key = "provider-picker") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.providers, key = { it.name.wireName }) { item ->
                        val health = state.providerHealth.firstOrNull { it.provider == item.name }
                        val authState = state.authStates[item.name] ?: item.auth
                        ProviderPickerChip(
                            name = item.displayName,
                            status = streamingProviderStatusText(item.statusMessage, item.status, health, authState, labels),
                            selected = item.name == state.selectedProvider,
                            connected = authState.connected,
                            onClick = { actions.onSelectProvider.run(item.name) }
                        )
                    }
                }
            }
            provider?.let { selected ->
                val supportsAuth = selectedCapability?.supportsAuth
                    ?: StreamingCapabilityResolver.canAuth(selected)
                if (supportsAuth) {
                    item(key = "selected-provider:${selected.name.wireName}") {
                        ProviderRow(
                            name = selected.displayName,
                            selected = true,
                            status = streamingProviderStatusText(
                                selected.statusMessage,
                                selected.status,
                                selectedHealth,
                                selectedAuthState,
                                labels
                            ),
                            supportsAuth = true,
                            connected = selectedAuthState?.connected == true,
                            onSelect = {},
                            onLogin = { actions.onLogin.run(selected.name) },
                            onSignOut = { actions.onSignOut.run(selected.name) }
                        )
                    }
                }
                item(key = "selected-provider-playback:${selected.name.wireName}") {
                    ProviderPlaybackPolicyRow(
                        provider = selected.name,
                        intrinsicallySupported = selected.capabilities.supportsAudioResolve,
                        enabled = state.playbackSourcePolicy.isEnabled(selected.name),
                        priorityIndex = state.playbackSourcePolicy.remotePriority.indexOf(selected.name),
                        priorityCount = state.playbackSourcePolicy.remotePriority.size,
                        onChanged = { enabled -> actions.onPlaybackEnabledChanged.run(selected.name, enabled) },
                        onMoveUp = { actions.onPlaybackPriorityUp.run(selected.name) },
                        onMoveDown = { actions.onPlaybackPriorityDown.run(selected.name) }
                    )
                }
            }
        }
        state.pendingAuthLaunch?.let { launch ->
            item(key = "auth-launch:${launch.provider.wireName}") {
                ActionRow(labels.openLoginPrefix + launch.provider.wireName + labels.openLoginSuffix, EchoIconKind.Action) {
                    actions.onOpenAuthLaunch.run()
                }
            }
        }
        item(key = "quick-actions-title") { SectionTitle(labels.discoverMusic) }
        item(key = "quick-actions") {
            StreamingQuickActions(state, labels, actions)
        }
        if (state.userPlaylistsLoading) {
            item(key = "account-playlists-loading") {
                MessageRow(labels.loadingAccountPlaylists)
            }
        }
        if (state.userPlaylists.isNotEmpty()) {
            item(key = "account-playlists-title") {
                SectionTitle(labels.accountPlaylists)
            }
            itemsIndexed(
                items = state.userPlaylists,
                key = { _, item -> "user-playlist:${item.provider.wireName}:${item.providerPlaylistId}" }
            ) { _, item ->
                StreamingPlaylistRow(item, labels) { actions.onImportPlaylist.run(item) }
            }
        }
        if (state.loading && !state.loadingMore) {
            item(key = "loading") {
                MessageRow(labels.loading)
            }
        }
        if (state.playlistImporting) {
            item(key = "playlist-importing") {
                MessageRow(labels.matchingLocalTracks)
            }
        }
        state.playlistImportSummary?.let { summary ->
            item(key = "playlist-import-title") {
                SectionTitle(labels.playlistImportPrefix + summary.playlistName)
            }
            item(key = "playlist-import-meta") {
                MetricRow(
                    labels.matched,
                    "${summary.matchedTracks.size} / ${summary.totalRequested} (${summary.matchRatePercent}%)"
                )
            }
            if (summary.unresolvedTracks.isNotEmpty()) {
                item(key = "playlist-import-unresolved") {
                    MetricRow(labels.unresolved, "${summary.unresolvedTracks.size}")
                }
            }
        }
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            item(key = "error") {
                MessageRow(streamingErrorMessage(message, labels))
            }
        }
        val tracks = result?.tracks.orEmpty()
        val displayedCount = tracks.size
        if (result != null) {
            item(key = "result-meta") {
                MetricRow(labels.results, "$displayedCount${result.total?.let { " / $it" }.orEmpty()}")
            }
        }
        val importedTracks = state.playlistImportSummary?.matchedTracks.orEmpty()
        if (importedTracks.isNotEmpty()) {
            item(key = "imported-tracks-title") {
                SectionTitle(labels.matchedStreamingTracks)
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
                SectionTitle(labels.songs)
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
        if (result != null && displayedCount == 0 && !state.loading) {
            item(key = "empty") {
                MessageRow(labels.noResults)
            }
        }
        if (state.loadingMore) {
            item(key = "loading-more") {
                MessageRow(labels.loadMore)
            }
        } else if (result?.hasMore == true && canSearch) {
            item(key = "next-page") {
                ActionRow(labels.loadMore, EchoIconKind.Next) { actions.onNextPage.run() }
            }
        }
        state.resolvedPlaybackTrack?.let { track ->
            item(key = "play-resolved") {
                ActionRow(labels.playResolvedTrack, EchoIconKind.Play) {
                    actions.onPlayResolvedTrack.run(track)
                }
            }
        }
        if (state.selectedProvider == StreamingProviderName.NETEASE) {
            item(key = "advanced-tools-title") {
                SectionTitle(labels.advancedTools)
            }
            item(key = "manual-account-connect") {
                ActionRow(labels.backupAccountConnection, EchoIconKind.Edit) { actions.onInputCookie.run() }
            }
        }
        if (hasLocalPendingProviders) {
            item(key = "local-first-hint") {
                MessageRow("本机音源优先：网易云、QQ 音乐可登录直连；LX 可管理多个自定义音源，网关仅作为补充。")
            }
        }
        item(key = "streaming-usage-notice") {
            StreamingUsageNotice(labels.usageNotice)
        }
    }
}

private data class StreamingQuickAction(
    val label: String,
    val icon: EchoIconKind,
    val action: Runnable
)

@Composable
private fun StreamingSearchHero(
    providerName: String,
    providerStatus: String,
    query: String,
    canSearch: Boolean,
    labels: StreamingSearchLabels,
    onSearch: QueryAction
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(EchoIconKind.Network, Modifier.size(24.dp), p.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        providerName,
                        style = EchoTypography.title,
                        color = p.heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (providerStatus.isNotBlank()) {
                        Text(
                            providerStatus,
                            style = EchoTypography.caption,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (canSearch) {
                val searchLabel = labels.searchPrefix + query + labels.searchSuffix
                Surface(
                    onClick = { onSearch.run(query) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel },
                    shape = EchoShapes.medium,
                    color = p.accentSoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoIcon(EchoIconKind.Search, Modifier.size(22.dp), p.accent)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            searchLabel,
                            style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = p.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.accent)
                    }
                }
            } else {
                Text(
                    providerName + labels.searchUnavailableSuffix,
                    style = EchoTypography.bodyMedium,
                    color = p.muted
                )
            }
        }
    }
}

@Composable
private fun ProviderPickerChip(
    name: String,
    status: String,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 128.dp, max = 188.dp)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = name },
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(
                    if (connected) EchoIconKind.Heart else EchoIconKind.Network,
                    Modifier.size(16.dp),
                    if (selected || connected) p.accent else p.muted
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    name,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                status,
                style = EchoTypography.caption,
                color = if (connected) p.accent else p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StreamingQuickActions(
    state: StreamingSearchState,
    labels: StreamingSearchLabels,
    actions: StreamingSearchActions
) {
    val quickActions = buildList {
        if (state.selectedProvider == StreamingProviderName.NETEASE) {
            add(StreamingQuickAction(labels.dailyRecommendations, EchoIconKind.Sparkle, actions.onDailyRecommend))
            add(StreamingQuickAction(labels.heartbeatRecommendations, EchoIconKind.Heart, actions.onHeartbeatRecommend))
        }
        add(StreamingQuickAction(labels.loadAccountPlaylists, EchoIconKind.Collections, actions.onLoadUserPlaylists))
        add(StreamingQuickAction(labels.importLikedTracks, EchoIconKind.Heart, actions.onImportLikedTracks))
        if (state.selectedProvider == StreamingProviderName.LUOXUE) {
            add(StreamingQuickAction(labels.manageLuoxueSources, EchoIconKind.Network, actions.onManageLuoxueSources))
            add(StreamingQuickAction(labels.importLuoxueSource, EchoIconKind.PlaylistAdd, actions.onPasteImport))
        } else {
            add(StreamingQuickAction(labels.importPlaylistFromStreaming, EchoIconKind.PlaylistAdd, actions.onPasteImport))
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(quickActions, key = { it.label }) { item ->
            QuickActionCard(item)
        }
    }
}

@Composable
private fun QuickActionCard(item: StreamingQuickAction) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { item.action.run() },
        modifier = Modifier
            .width(176.dp)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = item.label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoIcon(item.icon, Modifier.size(22.dp), p.accent)
            Text(
                item.label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun trackProviderSupportsPlayback(state: StreamingSearchState, track: StreamingTrack): Boolean {
    if (track.provider == StreamingProviderName.QQ_MUSIC) {
        // QQ search results remain actionable: playback resolution uses metadata to find LX/other
        // allowed sources and never resolves a QQ URL.
        return true
    }
    state.providerCapabilities.firstOrNull { it.provider == track.provider }?.let { capability ->
        return capability.supportsPlayback
    }
    val descriptor = state.providers.firstOrNull { it.name == track.provider }
    return StreamingCapabilityResolver.canPlayback(descriptor)
}

fun interface ProviderToggleAction {
    fun run(provider: StreamingProviderName, enabled: Boolean)
}

@Composable
private fun ProviderPlaybackPolicyRow(
    provider: StreamingProviderName,
    intrinsicallySupported: Boolean,
    enabled: Boolean,
    priorityIndex: Int,
    priorityCount: Int,
    onChanged: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val colors = EchoTheme.colors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("允许用于播放", color = colors.text, fontWeight = FontWeight.SemiBold)
                Text(
                    when (provider) {
                        StreamingProviderName.QQ_MUSIC -> "仅用于搜索、红心和歌单同步"
                        StreamingProviderName.LUOXUE -> "默认远程播放音源，始终优先"
                        else -> if (enabled) "已加入解析、兜底、缓存和下载候选" else "默认关闭，不会请求该平台音频接口"
                    },
                    color = colors.muted,
                    fontSize = 12.sp
                )
            }
            if (provider != StreamingProviderName.QQ_MUSIC &&
                provider != StreamingProviderName.LUOXUE && intrinsicallySupported) {
                Switch(checked = enabled, onCheckedChange = onChanged)
            } else if (provider == StreamingProviderName.LUOXUE) {
                Switch(checked = true, onCheckedChange = null)
            }
            }
            if (enabled && provider != StreamingProviderName.LUOXUE && provider != StreamingProviderName.QQ_MUSIC) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    TextButton(onClick = onMoveUp, enabled = priorityIndex > 1) { Text("提高优先级") }
                    TextButton(onClick = onMoveDown, enabled = priorityIndex in 1 until priorityCount - 1) { Text("降低优先级") }
                }
            }
        }
    }
}

private fun providerCapabilitySummary(capability: app.yukine.streaming.StreamingProviderCapability): String {
    if (capability.provider == StreamingProviderName.QQ_MUSIC) {
        return "红心与歌单双向同步已启用；播放音源已禁用"
    }
    return listOf(
        "曲库同步" to (capability.supportsPlaylistImport || capability.supportsPlaylistReadSync),
        "红心读写" to (capability.supportsFavoritesRead && capability.supportsFavoritesWrite),
        "歌单读写" to (capability.supportsPlaylistReadSync && capability.supportsPlaylistWrite),
        "音频播放" to capability.supportsAudioResolve
    ).joinToString(" / ") { (label, enabled) -> "$label${if (enabled) "✓" else "—"}" }
}

fun streamingProviderStatusText(
    message: String?,
    status: StreamingProviderStatus,
    health: StreamingProviderHealth?,
    authState: app.yukine.streaming.StreamingAuthState? = null,
    labels: StreamingSearchLabels
): String {
    when (authState?.credentialState) {
        app.yukine.streaming.StreamingCredentialState.PENDING_VERIFICATION -> {
            return labels.sessionPendingVerification
        }
        app.yukine.streaming.StreamingCredentialState.INVALID -> {
            return labels.sessionInvalid
        }
        else -> Unit
    }
    if (authState?.connected == true) {
        val name = authState.accountDisplayName?.takeIf { it.isNotBlank() }
        val signedIn = if (name != null) "${labels.signedIn} - $name" else labels.signedIn
        return if (
            authState.credentialState == app.yukine.streaming.StreamingCredentialState.VALID &&
            authState.lastVerifiedAtEpochMs != null
        ) {
            "$signedIn · ${labels.sessionVerified}"
        } else {
            signedIn
        }
    }
    streamingStatusMessage(message, labels)?.let { return it }
    health?.let {
        if (it.available && it.authenticated) {
            return labels.onlineAuthenticated
        }
        if (!it.available) {
            return it.errorMessage ?: labels.unavailable
        }
    }
    return when (status) {
        StreamingProviderStatus.READY -> labels.ready
        StreamingProviderStatus.NEEDS_ACCOUNT -> labels.needsAccount
        StreamingProviderStatus.DISABLED -> labels.disabled
        StreamingProviderStatus.ERROR -> labels.error
    }
}

fun streamingStatusMessage(message: String?, labels: StreamingSearchLabels): String? {
    val normalized = message?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return null
    }
    return when (normalized) {
        "Local login saved",
        "Saved local login" -> labels.localLoginSaved
        "Not signed in" -> labels.notSignedIn
        "Local login complete" -> labels.localLoginComplete
        "Gateway unavailable; using local login" -> labels.gatewayLocalLogin
        "Streaming gateway required" -> labels.gatewayRequired
        "No sign-in entry is configured for this source" -> labels.loginEntryMissing
        "Open the sign-in page" -> labels.openLoginPage
        "NetEase liked playlist is empty; heartbeat recommendations cannot be generated." -> labels.neteaseLikedPlaylistEmpty
        "Could not read NetEase account ID; sign in again before loading account playlists." -> labels.neteaseAccountIdMissing
        "Sign in to NetEase before loading playlists." -> labels.neteaseLoginRequiredPlaylists
        else -> normalized
    }
}

private fun streamingErrorMessage(message: String, labels: StreamingSearchLabels): String {
    return when (message.trim()) {
        "Streaming request failed" -> labels.streamingRequestFailed
        "Playlist import failed" -> labels.playlistImportFailed
        "Could not load account playlists" -> labels.accountPlaylistsFailed
        else -> message
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
            .echoFloatingLayer(p, EchoShapes.medium)
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
private fun StreamingAlbumRow(album: StreamingAlbum, labels: StreamingSearchLabels) {
    StreamingInfoRow(
        title = album.title,
        subtitle = listOfNotNull(album.artist, album.trackCount?.let { "$it${labels.trackCountSuffix}" }).joinToString(" - "),
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
private fun StreamingPlaylistRow(
    playlist: StreamingPlaylist,
    labels: StreamingSearchLabels,
    onImport: (() -> Unit)? = null
) {
    StreamingInfoRow(
        title = playlist.title,
        subtitle = listOfNotNull(
            playlist.creator,
            playlist.trackCount?.let { "$it${labels.trackCountSuffix}" },
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
            modifier = rowModifier
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingInfoRowBody(title, subtitle, icon, true, trailingIcon)
        }
    } else {
        Surface(
            modifier = rowModifier
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium),
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
private fun StreamingTrackRow(track: StreamingTrack, playable: Boolean, onPlay: () -> Unit) {
    val p = EchoTheme.colors()
    val modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = track.title }
    if (playable) {
        Surface(
            onClick = onPlay,
            modifier = modifier
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            StreamingTrackRowContent(track, playable)
        }
    } else {
        Surface(
            modifier = modifier
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium),
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
    val additionalSources = (track.playbackSourceCount - 1).coerceAtLeast(0)
    val sourceSummary = track.provider.wireName + if (additionalSources > 0) " +$additionalSources" else ""
    val trackDetail = listOf(track.artist, track.album.orEmpty(), sourceSummary)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val subtitle = track.unavailableReason?.takeIf { !playable && it.isNotBlank() } ?: trackDetail
    val coverUri = (track.coverThumbUrl ?: track.coverUrl)?.let(Uri::parse)
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncArtwork(
            uri = coverUri,
            title = track.title,
            subtitle = subtitle,
            modifier = Modifier.size(46.dp),
            cornerRadius = 8.dp,
            fallbackTextSize = 12.sp,
            targetSize = 46.dp,
            backgroundColor = p.surfaceVariant
        )
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
                subtitle,
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
            .echoFloatingLayer(p, EchoShapes.medium)
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
            .echoFloatingLayer(p, EchoShapes.medium)
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
            .echoFloatingLayer(p, EchoShapes.medium)
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

