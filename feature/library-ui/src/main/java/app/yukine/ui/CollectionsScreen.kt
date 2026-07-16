package app.yukine.ui

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R

data class CollectionMetricUiState(val label: String, val value: String)
data class CollectionActionUiState(
    val label: String,
    /** Semantic icon supplied by the feature; labels remain display-only. */
    val icon: EchoIconKind = EchoIconKind.Action
)

data class CollectionTrackSectionUiState(
    val key: String,
    val title: String,
    val emptyText: String,
    val emptyDescription: String,
    val playActionLabel: String,
    val rows: List<TrackRowUiState>
)

data class CollectionTrackSectionActions(
    val onPlayAll: Runnable,
    val rowActions: List<TrackRowActions>
)

data class CollectionPlaylistFolderUiState(
    val key: String,
    val title: String,
    val subtitle: String,
    val playlists: List<PlaylistRowUiState>
)

data class FavoriteSyncUiState(
    val visible: Boolean = true,
    val title: String = "跨平台红心同步",
    val lastSyncText: String = "尚未同步",
    val pendingText: String = "待同步 0",
    val failureText: String = "失败 0",
    val syncNowLabel: String = "立即增量同步",
    val running: Boolean = false,
    val autoSync: Boolean = false,
    val syncOnForeground: Boolean = true,
    val periodicSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val propagateRemovals: Boolean = false,
    val confirmLowConfidence: Boolean = true
)

data class FavoriteSyncActions(
    val onSyncNow: Runnable? = null,
    val onAutoSyncChanged: (Boolean) -> Unit = {},
    val onForegroundChanged: (Boolean) -> Unit = {},
    val onPeriodicChanged: (Boolean) -> Unit = {},
    val onWifiOnlyChanged: (Boolean) -> Unit = {},
    val onPropagateRemovalsChanged: (Boolean) -> Unit = {},
    val onConfirmLowConfidenceChanged: (Boolean) -> Unit = {}
)

data class CollectionsUiState(
    val title: String,
    val backLabel: String = "",
    val metrics: List<CollectionMetricUiState>,
    val topActions: List<CollectionActionUiState>,
    val trackSections: List<CollectionTrackSectionUiState>,
    val playlistTitle: String,
    val playlistEmptyText: String,
    val playlistEmptyDescription: String,
    val playlists: List<PlaylistRowUiState>,
    val selectedPlaylistVisible: Boolean,
    val selectedPlaylistTitle: String,
    val selectedPlaylistEmptyText: String,
    val selectedPlaylistEmptyDescription: String,
    val selectedPlaylistTopActions: List<CollectionActionUiState>,
    val selectedPlaylistTracks: List<PlaylistTrackUiState>,
    val actions: CollectionsActions = emptyCollectionsActions(),
    val favoriteLabel: String = "\u6536\u85cf",
    val removeFavoriteLabel: String = "\u53d6\u6d88\u6536\u85cf",
    val addToPlaylistLabel: String = "\u52a0\u5165\u6b4c\u5355",
    val renameLabel: String = "\u91cd\u547d\u540d",
    val deleteLabel: String = "\u5220\u9664",
    val upLabel: String = "\u4e0a\u79fb",
    val downLabel: String = "\u4e0b\u79fb",
    val removeLabel: String = "\u79fb\u9664",
    val playlistFolders: List<CollectionPlaylistFolderUiState> = emptyList(),
    val favoriteSync: FavoriteSyncUiState = FavoriteSyncUiState()
)

fun emptyCollectionsActions(): CollectionsActions = CollectionsActions(
    onBack = null,
    topActions = emptyList(),
    trackSections = emptyList(),
    playlistActions = emptyList(),
    selectedPlaylistTopActions = emptyList(),
    selectedPlaylistTrackActions = emptyList(),
    favoriteSync = FavoriteSyncActions()
)

data class CollectionsActions(
    val onBack: Runnable?,
    val topActions: List<Runnable>,
    val trackSections: List<CollectionTrackSectionActions>,
    val playlistActions: List<PlaylistRowActions>,
    val selectedPlaylistTopActions: List<Runnable>,
    val selectedPlaylistTrackActions: List<PlaylistTrackActions>,
    val favoriteSync: FavoriteSyncActions = FavoriteSyncActions()
)

@Composable
fun CollectionsScreen(state: CollectionsUiState, actions: CollectionsActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "page-title") {
            EchoPageTitle(
                state.title.ifBlank { "Collections" },
                backLabel = state.backLabel,
                onBack = if (state.selectedPlaylistVisible) actions.onBack else null
            )
        }
        if (state.metrics.isNotEmpty()) {
            item(key = "overview-title") {
                EchoSectionTitle(state.playlistTitle.ifBlank { "Collections" })
            }
            item(key = "metrics") {
                MetricGrid(state.metrics)
            }
        }
        if (state.favoriteSync.visible && !state.selectedPlaylistVisible) {
            item(key = "favorite-sync") {
                FavoriteSyncPanel(state.favoriteSync, actions.favoriteSync)
            }
        }
        itemsIndexed(state.topActions, key = { index, action -> "top:${action.label}:$index" }) { index, action ->
            ActionRow(action, actions.topActions.getOrNull(index))
        }
        for (sectionIndex in state.trackSections.indices) {
            val section = state.trackSections[sectionIndex]
            item(key = "section-title:${section.key}") {
                EchoSectionTitle(section.title)
            }
            if (section.rows.isEmpty()) {
                item(key = "section-empty:${section.key}") {
                    MessageRow(section.emptyText, section.emptyDescription)
                }
            } else {
                item(key = "section-action:${section.key}") {
                    ActionRow(
                        CollectionActionUiState(section.playActionLabel, EchoIconKind.Play),
                        actions.trackSections.getOrNull(sectionIndex)?.onPlayAll
                    )
                }
                itemsIndexed(
                    items = section.rows,
                    key = { index, track -> "section-track:${section.key}:${track.id}:$index" }
                ) { rowIndex, track ->
                    val rowAction = actions.trackSections.getOrNull(sectionIndex)?.rowActions?.getOrNull(rowIndex)
                    if (rowAction != null) {
                        CollectionTrackRow(track, rowAction, state)
                    }
                }
            }
        }
        item(key = "playlists-title") {
            EchoSectionTitle(state.playlistTitle)
        }
        if (state.playlists.isEmpty() && state.playlistFolders.isEmpty()) {
            item(key = "playlists-empty") {
                MessageRow(state.playlistEmptyText, state.playlistEmptyDescription, EchoIconKind.Collections)
            }
        } else if (state.playlistFolders.isNotEmpty()) {
            itemsIndexed(
                items = state.playlistFolders,
                key = { _, folder -> "playlist-folder:${folder.key}" }
            ) { _, folder ->
                CollectionPlaylistFolderSection(folder, actions, state)
            }
        } else {
            itemsIndexed(
                items = state.playlists,
                key = { index, playlist -> "playlist:${playlist.name}:$index" }
            ) { index, playlist ->
                val action = actions.playlistActions.getOrNull(index)
                if (action != null) {
                    CollectionPlaylistRow(playlist, action, state)
                }
            }
        }
        if (state.selectedPlaylistVisible) {
            item(key = "selected-playlist-title") {
                EchoSectionTitle(state.selectedPlaylistTitle)
            }
            if (state.selectedPlaylistTracks.isEmpty()) {
                item(key = "selected-playlist-empty") {
                    MessageRow(state.selectedPlaylistEmptyText, state.selectedPlaylistEmptyDescription, EchoIconKind.Queue)
                }
            } else {
                    itemsIndexed(
                        items = state.selectedPlaylistTopActions,
                        key = { index, action -> "selected-action:${action.label}:$index" }
                    ) { index, action ->
                    ActionRow(action, actions.selectedPlaylistTopActions.getOrNull(index))
                }
                itemsIndexed(
                    items = state.selectedPlaylistTracks,
                    key = { _, track -> "selected-track:${track.key}" }
                ) { index, track ->
                    val action = actions.selectedPlaylistTrackActions.getOrNull(index)
                    if (action != null) {
                        CollectionPlaylistTrackRow(track, action, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionPlaylistFolderSection(
    folder: CollectionPlaylistFolderUiState,
    actions: CollectionsActions,
    labels: CollectionsUiState
) {
    var expanded by rememberSaveable(folder.key) { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollectionPlaylistFolderRow(folder, expanded) { expanded = !expanded }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                folder.playlists.forEach { playlist ->
                    val action = actions.playlistActions.getOrNull(playlist.actionIndex)
                    if (action != null) {
                        CollectionPlaylistRow(playlist, action, labels)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSyncPanel(state: FavoriteSyncUiState, actions: FavoriteSyncActions) {
    val colors = EchoTheme.colors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.title, color = colors.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.lastSyncText}  ·  ${state.pendingText}  ·  ${state.failureText}",
                        color = colors.muted,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = { actions.onSyncNow?.run() }, enabled = !state.running) {
                    Text(if (state.running) "同步中…" else state.syncNowLabel)
                }
            }
            FavoriteSyncSwitch("自动同步", state.autoSync, actions.onAutoSyncChanged)
            if (state.autoSync) {
                FavoriteSyncSwitch("App 启动或回到前台时同步", state.syncOnForeground, actions.onForegroundChanged)
                FavoriteSyncSwitch("定时增量同步", state.periodicSync, actions.onPeriodicChanged)
                FavoriteSyncSwitch("仅 Wi-Fi", state.wifiOnly, actions.onWifiOnlyChanged)
                FavoriteSyncSwitch("传播取消红心", state.propagateRemovals, actions.onPropagateRemovalsChanged)
                FavoriteSyncSwitch("低置信度匹配要求确认", state.confirmLowConfidence, actions.onConfirmLowConfidenceChanged)
            }
        }
    }
}

@Composable
private fun FavoriteSyncSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = EchoTheme.colors().muted)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun CollectionPlaylistFolderRow(
    folder: CollectionPlaylistFolderUiState,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large)
            .semantics { contentDescription = folder.title },
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoGlassSurface(
                modifier = Modifier.size(48.dp),
                shape = EchoShapes.medium,
                contentPadding = PaddingValues(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.Folder, Modifier.size(26.dp), p.accent)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    folder.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EchoIcon(
                EchoIconKind.Next,
                Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                p.muted
            )
        }
    }
}
@Composable
private fun MetricGrid(metrics: List<CollectionMetricUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { metric ->
                    MetricRow(metric, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metric: CollectionMetricUiState, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.height(78.dp),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(metric.value, style = EchoTypography.headline, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metric.label, style = EchoTypography.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionRow(action: CollectionActionUiState, onClick: Runnable?) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { onClick?.run() },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = action.label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(action.icon, Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(action.label, style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            EchoIcon(EchoIconKind.Next, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    EchoSectionTitle(title)
}

@Composable
private fun MessageRow(message: String, description: String, icon: EchoIconKind = EchoIconKind.Library) {
    EchoStateCard(message, description, icon = icon)
}

@Composable
private fun CollectionTrackRow(track: TrackRowUiState, actions: TrackRowActions, labels: CollectionsUiState, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "collectionTrackBg"
    )
    Surface(
        onClick = { actions.onPlay.run() },
        modifier = modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (track.current) bg else Color.Transparent
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackCurrentIndicator(track.current, height = 46.dp)
            Spacer(Modifier.width(7.dp))
            AsyncArtwork(
                uri = track.albumArtUri,
                title = track.title,
                subtitle = track.subtitle,
                modifier = Modifier.size(48.dp),
                cornerRadius = 6.dp,
                fallbackTextSize = 16.sp,
                targetSize = 48.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (track.current) p.accent else p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.subtitle, style = EchoTypography.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.detail.isNotBlank()) {
                    Text(track.detail, style = EchoTypography.small, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(track.duration, style = EchoTypography.small, color = p.muted, modifier = Modifier.padding(horizontal = 6.dp))
            MiniIconButton(EchoIconKind.Heart, if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel, track.favorite) { actions.onFavorite.run() }
            if (track.showPlaylistAction) {
                MiniIconButton(EchoIconKind.PlaylistAdd, labels.addToPlaylistLabel) { actions.onAddToPlaylist.run() }
            }
        }
    }
}

@Composable
private fun CollectionPlaylistRow(playlist: PlaylistRowUiState, actions: PlaylistRowActions, labels: CollectionsUiState, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (playlist.selected) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "collectionPlaylistBg"
    )
    Surface(
        onClick = { actions.onSelect.run() },
        modifier = modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (playlist.selected) bg else Color.Transparent
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(EchoIconKind.Folder, Modifier.size(22.dp), if (playlist.selected) p.accent else p.muted)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (playlist.selected) p.accent else p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playlist.subtitle, style = EchoTypography.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniIconButton(EchoIconKind.Edit, labels.renameLabel) { actions.onRename.run() }
            MiniIconButton(EchoIconKind.Delete, labels.deleteLabel) { actions.onDelete.run() }
        }
    }
}

@Composable
private fun CollectionPlaylistTrackRow(track: PlaylistTrackUiState, actions: PlaylistTrackActions, labels: CollectionsUiState, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "collectionPlaylistTrackBg"
    )
    Surface(
        onClick = { actions.onPlay.run() },
        modifier = modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (track.current) bg else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackCurrentIndicator(track.current, height = 46.dp)
                Spacer(Modifier.width(7.dp))
                AsyncArtwork(
                    uri = track.albumArtUri,
                    title = track.title,
                    subtitle = track.subtitle,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 6.dp,
                    fallbackTextSize = 16.sp,
                    targetSize = 48.dp,
                    backgroundColor = p.surfaceVariant,
                    fallbackResId = R.drawable.ic_stat_echo
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (track.current) p.accent else p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.subtitle, style = EchoTypography.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(track.duration, style = EchoTypography.small, color = p.muted, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
            ) {
                MiniIconButton(
                    EchoIconKind.Heart,
                    if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel,
                    active = track.favorite
                ) { actions.onFavorite.run() }
                MiniIconButton(EchoIconKind.Import, "下载") { actions.onDownload.run() }
                MiniIconButton(EchoIconKind.ArrowUp, labels.upLabel, enabled = track.canMoveUp) {
                    actions.onMoveUp.run()
                }
                MiniIconButton(EchoIconKind.ArrowDown, labels.downLabel, enabled = track.canMoveDown) {
                    actions.onMoveDown.run()
                }
                MiniIconButton(EchoIconKind.Remove, labels.removeLabel) { actions.onRemove.run() }
            }
        }
    }
}

@Composable
private fun MiniIconButton(
    icon: EchoIconKind,
    desc: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(31.dp).semantics { contentDescription = desc },
        shape = EchoShapes.small,
        color = when {
            active -> p.accentSoft.copy(alpha = 0.62f)
            enabled -> p.surfaceVariant.copy(alpha = 0.28f)
            else -> p.surfaceVariant.copy(alpha = 0.14f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(
                icon,
                Modifier.size(16.dp),
                if (!enabled) p.muted.copy(alpha = 0.35f) else if (active) p.accent else p.muted
            )
        }
    }
}
