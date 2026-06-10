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

data class StreamingSearchLabels(
    val title: String,
    val back: String,
    val searchPrefix: String,
    val searchSuffix: String,
    val sourceDefault: String,
    val searchUnavailableSuffix: String,
    val importPlaylistFromStreaming: String,
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
    val trackCountSuffix: String
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
        labels: StreamingSearchLabels,
        actions: StreamingSearchActions
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState = state.collectAsState()
                StreamingSearchScreen(uiState.value, labels, actions)
            }
        }
    }
}

@Composable
private fun StreamingSearchScreen(
    state: MainActivityStreamingState,
    labels: StreamingSearchLabels,
    actions: StreamingSearchActions
) {
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
            EchoPageTitle(
                labels.title,
                subtitle = provider?.displayName,
                backLabel = labels.back,
                onBack = actions.onBack
            )
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
                    status = providerStatusText(item.statusMessage, item.status, health, authState, labels),
                    supportsAuth = capability?.supportsAuth ?: StreamingCapabilityResolver.canAuth(item),
                    connected = authState.connected,
                    onSelect = { actions.onSelectProvider.run(item.name) },
                    onLogin = { actions.onLogin.run(item.name) },
                    onSignOut = { actions.onSignOut.run(item.name) }
                )
            }
        }
        state.pendingAuthLaunch?.let { launch ->
            item(key = "auth-launch:${launch.provider.wireName}") {
                ActionRow(labels.openLoginPrefix + launch.provider.wireName + labels.openLoginSuffix, EchoIconKind.Action) {
                    actions.onOpenAuthLaunch.run()
                }
            }
        }
        item(key = "account-actions-title") {
            SectionTitle(labels.accountActions)
        }
        if (state.selectedProvider == StreamingProviderName.NETEASE) {
            item(key = "daily-recommend") {
                ActionRow(labels.dailyRecommendations, EchoIconKind.Sparkle) { actions.onDailyRecommend.run() }
            }
            item(key = "heartbeat-recommend") {
                ActionRow(labels.heartbeatRecommendations, EchoIconKind.Heart) { actions.onHeartbeatRecommend.run() }
            }
        }
        item(key = "load-account-playlists") {
            ActionRow(labels.loadAccountPlaylists, EchoIconKind.Collections) { actions.onLoadUserPlaylists.run() }
        }
        item(key = "import-liked-tracks") {
            ActionRow(labels.importLikedTracks, EchoIconKind.Heart) { actions.onImportLikedTracks.run() }
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
        item(key = "discover-title") {
            SectionTitle(labels.discoverMusic)
        }
        item(key = "search") {
            val query = state.searchQuery.ifBlank { "echo" }
            if (canSearch) {
                ActionRow(labels.searchPrefix + query + labels.searchSuffix, EchoIconKind.Search) { actions.onSearch.run(query) }
            } else {
                MessageRow((provider?.displayName ?: labels.sourceDefault) + labels.searchUnavailableSuffix)
            }
        }
        item(key = "import-from-streaming") {
            ActionRow(labels.importPlaylistFromStreaming, EchoIconKind.PlaylistAdd) { actions.onPasteImport.run() }
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
        val albums = result?.albums.orEmpty()
        val artists = result?.artists.orEmpty()
        val playlists = result?.playlists.orEmpty()
        val mvs = result?.mvs.orEmpty()
        val displayedCount = tracks.size + albums.size + artists.size + playlists.size + mvs.size
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
        if (albums.isNotEmpty()) {
            item(key = "albums-title") {
                SectionTitle(labels.albums)
            }
            itemsIndexed(
                items = albums,
                key = { _, album -> "album:${album.provider.wireName}:${album.providerAlbumId}" }
            ) { _, album ->
                StreamingAlbumRow(album, labels)
            }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists-title") {
                SectionTitle(labels.artists)
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
                SectionTitle(labels.playlists)
            }
            itemsIndexed(
                items = playlists,
                key = { _, playlist -> "playlist:${playlist.provider.wireName}:${playlist.providerPlaylistId}" }
            ) { _, playlist ->
                StreamingPlaylistRow(playlist, labels) { actions.onImportPlaylist.run(playlist) }
            }
        }
        if (mvs.isNotEmpty()) {
            item(key = "mvs-title") {
                SectionTitle(labels.videos)
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
    }
}

private fun trackProviderSupportsPlayback(state: MainActivityStreamingState, track: StreamingTrack): Boolean {
    state.providerCapabilities.firstOrNull { it.provider == track.provider }?.let { capability ->
        return capability.supportsPlayback
    }
    val descriptor = state.providers.firstOrNull { it.name == track.provider }
    return StreamingCapabilityResolver.canPlayback(descriptor)
}

private fun providerStatusText(
    message: String?,
    status: StreamingProviderStatus,
    health: StreamingProviderHealth?,
    authState: app.echo.next.streaming.StreamingAuthState? = null,
    labels: StreamingSearchLabels
): String {
    if (authState?.connected == true) {
        val name = authState.accountDisplayName?.takeIf { it.isNotBlank() }
        return if (name != null) "${labels.signedIn} - $name" else labels.signedIn
    }
    health?.let {
        if (it.available) {
            return if (it.authenticated) labels.onlineAuthenticated else labels.online
        }
        return it.errorMessage ?: labels.unavailable
    }
    return message ?: when (status) {
        StreamingProviderStatus.READY -> labels.ready
        StreamingProviderStatus.NEEDS_ACCOUNT -> labels.needsAccount
        StreamingProviderStatus.DISABLED -> labels.disabled
        StreamingProviderStatus.ERROR -> labels.error
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
