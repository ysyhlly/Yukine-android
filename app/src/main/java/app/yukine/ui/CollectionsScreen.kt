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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.R

data class CollectionMetricUiState(val label: String, val value: String)
data class CollectionActionUiState(val label: String)

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
    val favoriteLabel: String = "Favorite",
    val removeFavoriteLabel: String = "Remove favorite",
    val addToPlaylistLabel: String = "Add to playlist",
    val renameLabel: String = "Rename",
    val deleteLabel: String = "Delete",
    val upLabel: String = "Up",
    val downLabel: String = "Down",
    val removeLabel: String = "Remove"
)

data class CollectionsActions(
    val onBack: Runnable?,
    val topActions: List<Runnable>,
    val trackSections: List<CollectionTrackSectionActions>,
    val playlistActions: List<PlaylistRowActions>,
    val selectedPlaylistTopActions: List<Runnable>,
    val selectedPlaylistTrackActions: List<PlaylistTrackActions>
)

@Composable
internal fun CollectionsScreen(state: CollectionsUiState, actions: CollectionsActions) {
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
        itemsIndexed(state.topActions, key = { index, action -> "top:${action.label}:$index" }) { index, action ->
            ActionRow(action.label, actions.topActions.getOrNull(index))
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
                        section.playActionLabel,
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
        if (state.playlists.isEmpty()) {
            item(key = "playlists-empty") {
                MessageRow(state.playlistEmptyText, state.playlistEmptyDescription, EchoIconKind.Collections)
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
                    ActionRow(action.label, actions.selectedPlaylistTopActions.getOrNull(index))
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
private fun ActionRow(label: String, action: Runnable?) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { action?.run() },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(iconForCollectionAction(label), Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(label, style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
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
        modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (playlist.selected) bg else Color.Transparent
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(EchoIconKind.PlaylistAdd, Modifier.size(22.dp), if (playlist.selected) p.accent else p.muted)
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
        modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
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

private fun iconForCollectionAction(label: String): EchoIconKind = when {
    label.contains("Import", ignoreCase = true) || label.contains("\u5bfc\u5165") -> EchoIconKind.Import
    label.contains("Clear", ignoreCase = true) || label.contains("\u6e05\u7a7a") -> EchoIconKind.Delete
    label.contains("Play", ignoreCase = true) || label.contains("\u64ad\u653e") -> EchoIconKind.Play
    label.contains("Export", ignoreCase = true) || label.contains("\u5bfc\u51fa") -> EchoIconKind.Import
    else -> EchoIconKind.Action
}
