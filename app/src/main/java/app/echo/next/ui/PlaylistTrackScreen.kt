package app.echo.next.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.MainActivityPlaylistTracksUiState
import app.echo.next.R
import kotlinx.coroutines.flow.StateFlow

data class PlaylistTrackUiState(
    val key: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    val audioSpec: String,
    val duration: String,
    val albumArtUri: Uri?,
    val current: Boolean,
    val favorite: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

data class PlaylistTrackActions(
    val onPlay: Runnable,
    val onFavorite: Runnable,
    val onMoveUp: Runnable,
    val onMoveDown: Runnable,
    val onRemove: Runnable
)

data class PlaylistTrackLabels(
    val favoriteLabel: String = "Favorite",
    val removeFavoriteLabel: String = "Remove favorite",
    val moveUpLabel: String = "Move up",
    val moveDownLabel: String = "Move down",
    val removeLabel: String = "Remove"
)

object PlaylistTrackScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityPlaylistTracksUiState>,
        actions: List<PlaylistTrackActions>
    ): ComposeView = create(context, state, actions, PlaylistTrackLabels())

    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityPlaylistTracksUiState>,
        actions: List<PlaylistTrackActions>,
        labels: PlaylistTrackLabels
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState = state.collectAsState()
                PlaylistTrackScreen(uiState.value.title, uiState.value.rows, actions, labels)
            }
        }
    }

}

@Composable
private fun PlaylistTrackScreen(
    title: String,
    tracks: List<PlaylistTrackUiState>,
    actions: List<PlaylistTrackActions>,
    labels: PlaylistTrackLabels
) {
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(title)
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.key }
        ) { i, track ->
            actions.getOrNull(i)?.let { action ->
                PlaylistTrackRow(track, action, labels)
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    track: PlaylistTrackUiState,
    actions: PlaylistTrackActions,
    labels: PlaylistTrackLabels,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "playlistRowBg"
    )
    Surface(
        onClick = { actions.onPlay.run() },
        modifier = modifier.echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (track.current) bg else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaylistArtwork(track.albumArtUri, track.title, track.subtitle)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
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
                    if (track.audioSpec.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            track.audioSpec,
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
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
            ) {
                PlIconButton(
                    EchoIconKind.Heart,
                    if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel,
                    active = track.favorite
                ) {
                    actions.onFavorite.run()
                }
                PlIconButton(
                    EchoIconKind.ArrowUp,
                    labels.moveUpLabel,
                    enabled = track.canMoveUp
                ) { actions.onMoveUp.run() }
                PlIconButton(
                    EchoIconKind.ArrowDown,
                    labels.moveDownLabel,
                    enabled = track.canMoveDown
                ) { actions.onMoveDown.run() }
                PlIconButton(EchoIconKind.Remove, labels.removeLabel) {
                    actions.onRemove.run()
                }
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(uri: Uri?, title: String, subtitle: String) {
    val p = EchoTheme.colors()
    AsyncArtwork(
        uri = uri,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.size(48.dp),
        cornerRadius = 6.dp,
        fallbackTextSize = 16.sp,
        targetSize = 48.dp,
        backgroundColor = p.surfaceVariant,
        fallbackResId = R.drawable.ic_stat_echo
    )
}

@Composable
private fun PlIconButton(
    icon: EchoIconKind,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = label },
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
                Modifier.size(15.dp),
                if (!enabled) p.muted.copy(alpha = 0.35f) else if (active) p.accent else p.muted
            )
        }
    }
}
