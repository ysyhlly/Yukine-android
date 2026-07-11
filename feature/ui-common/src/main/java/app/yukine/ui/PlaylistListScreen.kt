package app.yukine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PlaylistRowUiState(val name: String, val subtitle: String, val selected: Boolean)
data class PlaylistRowActions(
    val onSelect: Runnable,
    val onRename: Runnable,
    val onDelete: Runnable,
    val renameLabel: String = "\u91cd\u547d\u540d",
    val deleteLabel: String = "\u5220\u9664"
)

@Composable
private fun PlaylistListScreen(
    title: String, playlists: List<PlaylistRowUiState>, actions: List<PlaylistRowActions>
) {
    val p = EchoTheme.colors()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(title)
        }
        itemsIndexed(
            items = playlists,
            key = { index, playlist -> "${playlist.name}:$index" }
        ) { i, playlist ->
            actions.getOrNull(i)?.let { action ->
                PlaylistRow(playlist, action)
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistRowUiState, actions: PlaylistRowActions, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (playlist.selected) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "playlistListRowBg"
    )
    Surface(
        onClick = { actions.onSelect.run() },
        modifier = modifier
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (playlist.selected) bg else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .then(if (playlist.selected) Modifier else Modifier.echoGlassLayer(p, EchoShapes.small)),
                shape = EchoShapes.small,
                color = if (playlist.selected) p.accent else Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.PlaylistAdd, Modifier.size(22.dp),
                        if (playlist.selected) p.onAccent else p.muted)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (playlist.selected) p.accent else p.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playlist.subtitle, style = EchoTypography.caption, color = p.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlaylistIconBtn(EchoIconKind.Edit, actions.renameLabel) { actions.onRename.run() }
            Spacer(Modifier.width(4.dp))
            PlaylistIconBtn(EchoIconKind.Delete, actions.deleteLabel) { actions.onDelete.run() }
        }
    }
}

@Composable
private fun PlaylistIconBtn(icon: EchoIconKind, desc: String, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(31.dp)
            .semantics { contentDescription = desc },
        shape = EchoShapes.small,
        color = p.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(16.dp), p.muted)
        }
    }
}
