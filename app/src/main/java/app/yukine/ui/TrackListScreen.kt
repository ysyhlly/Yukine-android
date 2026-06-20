package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.yukine.R

data class TrackRowUiState(
    val id: Long,
    val title: String,
    val subtitle: String,
    val detail: String,
    val duration: String,
    val albumArtUri: Uri?,
    val current: Boolean,
    val favorite: Boolean,
    val showPlaylistAction: Boolean,
    val key: String = id.toString()
)

data class TrackRowActions(
    val onPlay: Runnable,
    val onFavorite: Runnable,
    val onAddToPlaylist: Runnable,
    val onEdit: Runnable?,
    val onDelete: Runnable?,
    val onLongPress: Runnable?
) {
    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable
    ) : this(onPlay, onFavorite, onAddToPlaylist, null, null, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onEdit: Runnable?,
        onDelete: Runnable?
    ) : this(onPlay, onFavorite, onAddToPlaylist, onEdit, onDelete, onDelete)
}

data class TrackListHeaderMetric(val label: String, val value: String)
data class TrackListHeaderAction(val label: String, val onClick: Runnable)
data class TrackListModeAction(val label: String, val mode: String, val selected: Boolean, val onClick: Runnable)
data class TrackListLabels(
    val favoriteLabel: String = "Favorite",
    val removeFavoriteLabel: String = "Remove favorite",
    val addToPlaylistLabel: String = "Add to playlist",
    val editLabel: String = "Edit",
    val deleteLabel: String = "Delete"
)

@Composable
internal fun TrackListScreen(
    title: String,
    tracks: List<TrackRowUiState>,
    actions: List<TrackRowActions>,
    headerMetrics: List<TrackListHeaderMetric>,
    headerActions: List<TrackListHeaderAction>,
    emptyText: String,
    modeActions: List<TrackListModeAction>,
    labels: TrackListLabels
) {
    val p = EchoTheme.colors()
    val titleBackAction = headerActions.firstOrNull { isBackAction(it.label) }
    val visibleHeaderActions = if (titleBackAction != null) headerActions.drop(1) else headerActions
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(
                title,
                backLabel = titleBackAction?.label,
                onBack = titleBackAction?.onClick
            )
        }
        if (modeActions.isNotEmpty()) {
            item(key = "modes") {
                ModeSelectorRow(modeActions)
            }
        }
        itemsIndexed(
            items = headerMetrics,
            key = { index, metric -> "metric:${metric.label}:$index" }
        ) { _, metric ->
            HeaderMetricRow(metric)
        }
        itemsIndexed(
            items = visibleHeaderActions,
            key = { index, action -> "action:${action.label}:$index" }
        ) { _, action ->
            HeaderActionRow(action)
        }
        itemsIndexed(
            items = tracks,
            key = { index, track -> track.key.ifBlank { "${track.id}:$index" } }
        ) { i, track ->
            actions.getOrNull(i)?.let { action ->
                TrackRow(track, action, labels, Modifier.echoEnter(i.coerceAtMost(8)))
            }
        }
        if (tracks.isEmpty() && emptyText.isNotBlank()) {
            item(key = "empty") {
                HeaderMessageRow(emptyText)
            }
        }
    }
}

@Composable
private fun ModeSelectorRow(modes: List<TrackListModeAction>) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (mode in modes) {
            Surface(
                onClick = { mode.onClick.run() },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (mode.selected) Modifier else Modifier.echoGlassLayer(p, EchoShapes.medium))
                    .semantics { contentDescription = mode.label },
                shape = EchoShapes.medium,
                color = if (mode.selected) p.accentSoft else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoIcon(
                        kind = iconForLibraryMode(mode.mode),
                        modifier = Modifier.size(if (mode.selected) 18.dp else 20.dp),
                        color = if (mode.selected) p.accent else p.muted
                    )
                    if (mode.selected) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            mode.label,
                            style = EchoTypography.caption,
                            color = p.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderMetricRow(metric: TrackListHeaderMetric) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                metric.label,
                style = EchoTypography.bodyMedium,
                color = p.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                metric.value,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeaderActionRow(action: TrackListHeaderAction) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { action.onClick.run() },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoGlassLayer(p, EchoShapes.medium)
            .semantics { contentDescription = action.label },
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(iconForHeaderAction(action.label), Modifier.size(22.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                action.label,
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
private fun HeaderMessageRow(message: String) {
    EchoEmptyCard(message)
}

@Composable
private fun TrackRow(track: TrackRowUiState, actions: TrackRowActions, labels: TrackListLabels, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "trackRowBg"
    )
    Surface(
        modifier = modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { actions.onPlay.run() },
                onLongClick = actions.onLongPress?.let { action -> { action.run() } }
            )
            .echoPressScale(interaction)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (track.current) bg else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackCurrentIndicator(track.current)
            Spacer(Modifier.width(7.dp))
            TrackArtwork(track.albumArtUri, track.title, track.subtitle)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (track.current) p.accent else p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        track.detail,
                        style = EchoTypography.small,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                track.duration,
                style = EchoTypography.small,
                color = p.muted,
                modifier = Modifier
                    .width(34.dp)
                    .padding(start = 3.dp, end = 2.dp),
                maxLines = 1
            )
            Row(
                modifier = Modifier.width(actionRailWidth(track, actions)),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniIconBtn(
                    icon = EchoIconKind.Heart,
                    desc = if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel,
                    active = track.favorite,
                    onClick = { actions.onFavorite.run() }
                )
                if (track.showPlaylistAction) {
                    MiniIconBtn(
                        icon = EchoIconKind.PlaylistAdd,
                        desc = labels.addToPlaylistLabel,
                        onClick = { actions.onAddToPlaylist.run() }
                    )
                }
                if (actions.onEdit != null || actions.onDelete != null) {
                    TrackMoreMenu(actions, labels)
                }
            }
        }
    }
}

private fun actionRailWidth(track: TrackRowUiState, actions: TrackRowActions): androidx.compose.ui.unit.Dp {
    var count = 1
    if (track.showPlaylistAction) {
        count += 1
    }
    if (actions.onEdit != null || actions.onDelete != null) {
        count += 1
    }
    return (count * 35 - 4).dp
}

@Composable
private fun TrackMoreMenu(actions: TrackRowActions, labels: TrackListLabels) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MiniIconBtn(
            icon = EchoIconKind.More,
            desc = labels.editLabel + " / " + labels.deleteLabel,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            actions.onEdit?.let { onEdit ->
                DropdownMenuItem(
                    text = { Text(labels.editLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Edit, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        expanded = false
                        onEdit.run()
                    }
                )
            }
            actions.onDelete?.let { onDelete ->
                DropdownMenuItem(
                    text = { Text(labels.deleteLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Delete, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        expanded = false
                        onDelete.run()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackArtwork(uri: Uri?, title: String, subtitle: String) {
    val p = EchoTheme.colors()
    AsyncArtwork(
        uri = uri,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.size(46.dp),
        cornerRadius = 6.dp,
        fallbackTextSize = 16.sp,
        targetSize = 46.dp,
        backgroundColor = p.surfaceVariant,
        fallbackResId = R.drawable.ic_stat_echo
    )
}

@Composable
private fun MiniIconBtn(
    icon: EchoIconKind, desc: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(31.dp)
            .semantics { contentDescription = desc },
        shape = EchoShapes.small,
        color = if (active) p.accentSoft.copy(alpha = 0.62f) else p.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(16.dp), if (active) p.accent else p.muted)
        }
    }
}

private fun iconForHeaderAction(label: String): EchoIconKind = when {
    isBackAction(label) -> EchoIconKind.Back
    label.contains("Play", ignoreCase = true) || label.contains("\u64ad\u653e") -> EchoIconKind.Play
    label.contains("Sync", ignoreCase = true) || label.contains("\u540c\u6b65") -> EchoIconKind.Sync
    label.contains("Delete", ignoreCase = true) || label.contains("\u5220\u9664") -> EchoIconKind.Delete
    label.contains("Import", ignoreCase = true) || label.contains("\u5bfc\u5165") || label.contains("\u5bfc\u51fa") -> EchoIconKind.Import
    else -> EchoIconKind.Action
}

private fun isBackAction(label: String): Boolean =
    label.contains("Back", ignoreCase = true) || label.contains("\u8fd4\u56de")

private fun iconForLibraryMode(mode: String): EchoIconKind = when (mode) {
    "albums" -> EchoIconKind.Collections
    "artists" -> EchoIconKind.Artist
    "folders" -> EchoIconKind.Folder
    "playlists" -> EchoIconKind.PlaylistAdd
    else -> EchoIconKind.Library
}

fun wrapTheme(content: @Composable () -> Unit): @Composable () -> Unit = {
    EchoTheme.EchoTheme { content() }
}
