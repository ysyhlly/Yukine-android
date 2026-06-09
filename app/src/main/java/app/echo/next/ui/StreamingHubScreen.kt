package app.echo.next.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingProviderHealth
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingProviderStatus
import app.echo.next.streaming.StreamingTrack
import kotlinx.coroutines.flow.StateFlow

// ── Data Classes ────────────────────────────────────────────────────────────

data class StreamingHubUiState(
    val providers: List<StreamingProviderDescriptor> = emptyList(),
    val providerHealth: Map<StreamingProviderName, StreamingProviderHealth> = emptyMap(),
    val authStates: Map<StreamingProviderName, StreamingAuthState> = emptyMap(),
    val selectedProvider: StreamingProviderName? = null,
    val searchQuery: String = "",
    val searchResults: List<StreamingTrack> = emptyList(),
    val playlistResults: List<StreamingPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StreamingHubActions(
    val onSelectProvider: (StreamingProviderName) -> Unit,
    val onLogin: (StreamingProviderName) -> Unit,
    val onLogout: (StreamingProviderName) -> Unit,
    val onSearch: (String) -> Unit,
    val onPlayTrack: (StreamingTrack) -> Unit,
    val onImportPlaylist: (StreamingPlaylist) -> Unit,
    val onOpenPlaylist: (StreamingPlaylist) -> Unit,
    val onBack: () -> Unit
)

// ── Factory ─────────────────────────────────────────────────────────────────

object StreamingHubScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<StreamingHubUiState>,
        actions: StreamingHubActions
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState by state.collectAsState()
                StreamingHubScreen(uiState, actions)
            }
        }
    }
}

// ── Main Screen ─────────────────────────────────────────────────────────────

@Composable
private fun StreamingHubScreen(state: StreamingHubUiState, actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    LazyColumn(
        modifier = Modifier
            .echoPageBackground(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item("header") {
            StreamingHeader(actions)
        }
        // Provider cards (horizontal scroll)
        item("providers") {
            ProviderSection(state, actions)
        }
        // Search bar (if provider selected)
        if (state.selectedProvider != null) {
            item("search") {
                SearchSection(state, actions)
            }
        }
        // Results
        if (state.searchResults.isNotEmpty()) {
            item("results-header") {
                Text(
                    "搜索结果",
                    style = EchoTypography.title,
                    color = p.text
                )
            }
            itemsIndexed(state.searchResults, key = { _, t -> t.providerTrackId }) { _, track ->
                StreamingTrackRow(track, actions)
            }
        }
        // Playlists
        if (state.playlistResults.isNotEmpty()) {
            item("playlists-header") {
                Text(
                    "歌单",
                    style = EchoTypography.title,
                    color = p.text
                )
            }
            itemsIndexed(state.playlistResults, key = { _, p -> p.providerPlaylistId }) { _, playlist ->
                PlaylistRow(playlist, actions)
            }
        }
        // Loading indicator
        if (state.isLoading) {
            item("loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...", style = EchoTypography.body, color = p.muted)
                }
            }
        }
        // Error message
        if (state.errorMessage != null) {
            item("error") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoGlassLayer(p, EchoShapes.medium),
                    shape = EchoShapes.medium,
                    color = Color.Transparent
                ) {
                    Text(
                        state.errorMessage,
                        style = EchoTypography.body,
                        color = p.muted,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Empty state
        if (state.selectedProvider != null && state.searchResults.isEmpty() && state.playlistResults.isEmpty() && !state.isLoading && state.searchQuery.isNotBlank()) {
            item("empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoGlassLayer(p, EchoShapes.medium),
                    shape = EchoShapes.medium,
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "未找到结果",
                            style = EchoTypography.title,
                            color = p.text
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "尝试其他关键词或更换源",
                            style = EchoTypography.body,
                            color = p.muted
                        )
                    }
                }
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun StreamingHeader(actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "流媒体",
                    style = EchoTypography.display.copy(fontSize = 24.sp),
                    color = p.heading
                )
                Text(
                    "搜索和导入在线歌单",
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
            Surface(
                onClick = { actions.onBack() },
                modifier = Modifier
                    .size(40.dp)
                    .echoGlassLayer(p, CircleShape),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.Back, Modifier.size(20.dp), p.text)
                }
            }
        }
    }
}

// ── Provider Section ────────────────────────────────────────────────────────

@Composable
private fun ProviderSection(state: StreamingHubUiState, actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "选择来源",
            style = EchoTypography.title,
            color = p.text
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(state.providers, key = { it.name }) { provider ->
                val health = state.providerHealth[provider.name]
                val authState = state.authStates[provider.name]
                val isSelected = state.selectedProvider == provider.name
                ProviderCard(
                    provider = provider,
                    health = health,
                    authState = authState,
                    isSelected = isSelected,
                    onSelect = { actions.onSelectProvider(provider.name) },
                    onLogin = { actions.onLogin(provider.name) },
                    onLogout = { actions.onLogout(provider.name) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: StreamingProviderDescriptor,
    health: StreamingProviderHealth?,
    authState: StreamingAuthState?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val p = EchoTheme.colors()
    val isConnected = authState?.connected == true || health?.authenticated == true
    val isDisabled = !provider.enabled || provider.status == StreamingProviderStatus.DISABLED
    val needsAuth = !isDisabled &&
        provider.status == StreamingProviderStatus.NEEDS_ACCOUNT && !isConnected

    Surface(
        onClick = {
            when {
                isDisabled -> {}
                needsAuth -> onLogin()
                else -> onSelect()
            }
        },
        enabled = !isDisabled,
        modifier = Modifier
            .width(140.dp)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = provider.displayName },
        shape = EchoShapes.medium,
        color = when {
            isDisabled -> p.backgroundAlt
            isSelected -> p.accentSoft
            else -> Color.Transparent
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Provider icon placeholder and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(EchoShapes.small)
                        .then(
                            if (isSelected && !isDisabled) {
                                Modifier.background(p.accent)
                            } else {
                                Modifier.echoGlassLayer(p, EchoShapes.small)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        provider.displayName.take(1).uppercase(),
                        style = EchoTypography.label.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isDisabled -> p.muted
                            isSelected -> p.onAccent
                            else -> p.text
                        }
                    )
                }
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDisabled -> p.border
                                isConnected -> p.accent
                                needsAuth -> p.muted
                                else -> p.border
                            }
                        )
                )
            }
            // Name
            Text(
                provider.displayName,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = when {
                    isDisabled -> p.muted
                    isSelected -> p.accent
                    else -> p.text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Status text
            Text(
                when {
                    isDisabled -> provider.statusMessage ?: "暂不可用"
                    isConnected -> "已连接"
                    needsAuth -> "需要登录"
                    else -> "可用"
                },
                style = EchoTypography.small,
                color = p.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Login/Logout button
            if (!isDisabled && (needsAuth || isConnected)) {
                Surface(
                    onClick = { if (isConnected) onLogout() else onLogin() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .then(if (isConnected) Modifier.echoGlassLayer(p, EchoShapes.small) else Modifier),
                    shape = EchoShapes.small,
                    color = if (isConnected) Color.Transparent else p.accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isConnected) "退出" else "登录",
                            style = EchoTypography.caption.copy(fontWeight = FontWeight.Medium),
                            color = if (isConnected) p.text else p.onAccent
                        )
                    }
                }
            }
        }
    }
}

// ── Search Section ──────────────────────────────────────────────────────────

@Composable
private fun SearchSection(state: StreamingHubUiState, actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    var query by remember { mutableStateOf(state.searchQuery) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "搜索",
            style = EchoTypography.title,
            color = p.text
        )
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
                EchoIcon(EchoIconKind.Search, Modifier.size(20.dp), p.muted)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    textStyle = EchoTypography.body.copy(color = p.text),
                    singleLine = true,
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.onSearch(query) }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜索歌曲、歌单...",
                                    style = EchoTypography.body,
                                    color = p.muted
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Surface(
                        onClick = { actions.onSearch(query) },
                        modifier = Modifier
                            .height(32.dp)
                            .padding(start = 8.dp),
                        shape = EchoShapes.small,
                        color = p.accent
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "搜索",
                                style = EchoTypography.caption.copy(fontWeight = FontWeight.Medium),
                                color = p.onAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Track Row ───────────────────────────────────────────────────────────────

@Composable
private fun StreamingTrackRow(track: StreamingTrack, actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { actions.onPlayTrack(track) },
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = track.title },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Artwork
            AsyncArtwork(
                uri = (track.coverThumbUrl ?: track.coverUrl)?.let { Uri.parse(it) },
                title = track.title,
                subtitle = track.artist,
                modifier = Modifier.size(52.dp),
                cornerRadius = 6.dp,
                fallbackTextSize = 14.sp,
                targetSize = 52.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = app.echo.next.R.drawable.ic_stat_echo
            )
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album).joinToString(" · "),
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Duration
            Text(
                formatDuration(track.durationMs ?: 0L),
                style = EchoTypography.caption,
                color = p.muted
            )
            // Play button
            Surface(
                onClick = { actions.onPlayTrack(track) },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = p.accentSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.Play, Modifier.size(16.dp), p.accent)
                }
            }
        }
    }
}

// ── Playlist Row ────────────────────────────────────────────────────────────

@Composable
private fun PlaylistRow(playlist: StreamingPlaylist, actions: StreamingHubActions) {
    val p = EchoTheme.colors()
    Surface(
        onClick = { actions.onOpenPlaylist(playlist) },
        modifier = Modifier
            .fillMaxWidth()
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = playlist.title },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Artwork
            AsyncArtwork(
                uri = playlist.coverUrl?.let { Uri.parse(it) },
                title = playlist.title,
                subtitle = playlist.creator ?: "",
                modifier = Modifier.size(56.dp),
                cornerRadius = 8.dp,
                fallbackTextSize = 16.sp,
                targetSize = 56.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = app.echo.next.R.drawable.ic_stat_echo
            )
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(playlist.creator, playlist.trackCount?.let { "$it 首" }).joinToString(" · "),
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Import button
            Surface(
                onClick = { actions.onImportPlaylist(playlist) },
                modifier = Modifier.height(36.dp),
                shape = EchoShapes.small,
                color = p.accent
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "导入",
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.Medium),
                        color = p.onAccent
                    )
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
