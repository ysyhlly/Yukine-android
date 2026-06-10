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
import app.echo.next.MainActivityQueueUiState
import app.echo.next.R
import kotlinx.coroutines.flow.StateFlow

data class QueueTrackUiState(
    val key: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    val audioSpec: String,
    val duration: String,
    val albumArtUri: Uri?,
    val current: Boolean,
    val favorite: Boolean
)

data class QueueTrackActions(
    val onPlay: Runnable,
    val onFavorite: Runnable,
    val onAddToPlaylist: Runnable,
    val onRemove: Runnable
)

data class QueueScreenLabels(
    val title: String = "Queue",
    val back: String = "Back",
    val clearQueue: String = "Clear queue",
    val empty: String = "Queue is empty",
    val emptyDescription: String = "Play a track or add music to build the queue.",
    val tracks: String = "tracks",
    val favorite: String = "Favorite",
    val addToPlaylist: String = "Add to playlist",
    val remove: String = "Remove"
)

object QueueScreenFactory {
    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityQueueUiState>,
        actions: List<QueueTrackActions>,
        onClearQueue: Runnable
    ): ComposeView = create(context, state, actions, onClearQueue, QueueScreenLabels())

    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityQueueUiState>,
        actions: List<QueueTrackActions>,
        onClearQueue: Runnable,
        labels: QueueScreenLabels
    ): ComposeView = create(context, state, actions, onClearQueue, labels, null)

    @JvmStatic
    fun create(
        context: Context,
        state: StateFlow<MainActivityQueueUiState>,
        actions: List<QueueTrackActions>,
        onClearQueue: Runnable,
        labels: QueueScreenLabels,
        onBack: Runnable?
    ): ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                val uiState = state.collectAsState()
                QueueScreen(uiState.value.rows, actions, onClearQueue, labels, onBack)
            }
        }
    }

}

@Composable
private fun QueueScreen(
    tracks: List<QueueTrackUiState>,
    actions: List<QueueTrackActions>,
    onClearQueue: Runnable,
    labels: QueueScreenLabels,
    onBack: Runnable?
) {
    val p = EchoTheme.colors()
    LazyColumn(
        modifier = Modifier.echoPageBackground(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            EchoPageTitle(
                labels.title,
                subtitle = "${tracks.size} ${labels.tracks}",
                backLabel = labels.back,
                onBack = onBack
            )
        }
        if (tracks.isNotEmpty()) {
            item(key = "clear-queue") {
                Surface(
                    onClick = { onClearQueue.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoGlassLayer(p, EchoShapes.medium)
                        .semantics { contentDescription = labels.clearQueue },
                    shape = EchoShapes.medium,
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoIcon(EchoIconKind.Delete, Modifier.size(20.dp), p.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(labels.clearQueue, style = EchoTypography.bodyMedium, color = p.accent)
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item(key = "empty") {
                EchoStateCard(labels.empty, labels.emptyDescription, icon = EchoIconKind.Queue)
            }
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.key }
        ) { i, track ->
            actions.getOrNull(i)?.let { action ->
                QueueTrackRow(track, action, labels)
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: QueueTrackUiState,
    actions: QueueTrackActions,
    labels: QueueScreenLabels,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "queueRowBg"
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
                QueueArtwork(track.albumArtUri, track.title, track.subtitle)
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
                QueueIconButton(
                    EchoIconKind.Heart,
                    labels.favorite,
                    active = track.favorite
                ) { actions.onFavorite.run() }
                QueueIconButton(EchoIconKind.PlaylistAdd, labels.addToPlaylist) {
                    actions.onAddToPlaylist.run()
                }
                QueueIconButton(EchoIconKind.Remove, labels.remove) {
                    actions.onRemove.run()
                }
            }
        }
    }
}

@Composable
private fun QueueArtwork(uri: Uri?, title: String, subtitle: String) {
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
private fun QueueIconButton(
    icon: EchoIconKind,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = label },
        shape = EchoShapes.small,
        color = if (active) p.accentSoft.copy(alpha = 0.62f) else p.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(15.dp), if (active) p.accent else p.muted)
        }
    }
}
