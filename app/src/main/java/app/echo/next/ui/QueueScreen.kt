package app.echo.next.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.next.R

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
    val onRemove: Runnable,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueScreen(
    tracks: List<QueueTrackUiState>,
    actions: List<QueueTrackActions>,
    onClearQueue: Runnable,
    labels: QueueScreenLabels,
    onBack: Runnable?
) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val dragState = rememberQueueDragState(
        itemKeyPrefix = "track-",
        onMove = { fromIndex, toIndex ->
            actions.getOrNull(fromIndex)?.onMove?.invoke(fromIndex, toIndex)
        }
    )
    LaunchedEffect(tracks.map { it.key }) {
        dragState.clear()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
            key = { _, track -> "track-${track.key}" }
        ) { i, track ->
            actions.getOrNull(i)?.let { action ->
                QueueTrackRow(
                    track = track,
                    actions = action,
                    labels = labels,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = dragState.dragOffsetFor("track-${track.key}")
                            shadowElevation = if (dragState.isDragging("track-${track.key}")) 12.dp.toPx() else 0f
                        },
                    dragHandleModifier = Modifier.pointerInput(track.key, tracks.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                dragState.start(listState.layoutInfo.visibleItemsInfo, "track-${track.key}", offset)
                            },
                            onDragCancel = { dragState.clear() },
                            onDragEnd = { dragState.drop() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.drag(listState.layoutInfo.visibleItemsInfo, dragAmount.y)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: QueueTrackUiState,
    actions: QueueTrackActions,
    labels: QueueScreenLabels,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
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
                QueueDragHandle(dragHandleModifier)
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
private fun QueueDragHandle(modifier: Modifier) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier
            .size(30.dp)
            .semantics { contentDescription = "Drag to reorder" },
        shape = EchoShapes.small,
        color = p.surfaceVariant.copy(alpha = 0.24f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(EchoIconKind.More, Modifier.size(15.dp), p.muted)
        }
    }
}

private class QueueDragState(
    private val itemKeyPrefix: String,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    private var draggingKey by mutableStateOf<Any?>(null)
    private var fromAdapterIndex by mutableIntStateOf(-1)
    private var currentAdapterIndex by mutableIntStateOf(-1)
    private var draggedItemStart by mutableIntStateOf(0)
    private var draggedItemSize by mutableIntStateOf(0)
    private var dragOffset by mutableFloatStateOf(0f)

    fun start(visibleItems: List<LazyListItemInfo>, key: Any, pointerOffset: Offset) {
        val item = visibleItems.firstOrNull { it.key == key } ?: return
        draggingKey = key
        fromAdapterIndex = item.index
        currentAdapterIndex = item.index
        draggedItemStart = item.offset
        draggedItemSize = item.size
        dragOffset = 0f
    }

    fun drag(visibleItems: List<LazyListItemInfo>, deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val draggedCenter = draggedItemStart + dragOffset + draggedItemSize / 2f
        val target = visibleItems
            .filter { it.key != key && it.key.toString().startsWith(itemKeyPrefix) }
            .firstOrNull { item ->
                draggedCenter in item.offset.toFloat()..(item.offset + item.size).toFloat()
            } ?: return
        currentAdapterIndex = target.index
    }

    fun drop() {
        val from = adapterIndexToTrackIndex(fromAdapterIndex)
        val to = adapterIndexToTrackIndex(currentAdapterIndex)
        if (from >= 0 && to >= 0 && from != to) {
            onMove(from, to)
        }
        clear()
    }

    fun clear() {
        draggingKey = null
        fromAdapterIndex = -1
        currentAdapterIndex = -1
        draggedItemStart = 0
        draggedItemSize = 0
        dragOffset = 0f
    }

    fun isDragging(key: Any): Boolean = draggingKey == key

    fun dragOffsetFor(key: Any): Float = if (draggingKey == key) dragOffset else 0f

    private fun adapterIndexToTrackIndex(index: Int): Int = index - 2
}

@Composable
private fun rememberQueueDragState(
    itemKeyPrefix: String,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): QueueDragState = remember(itemKeyPrefix, onMove) {
    QueueDragState(itemKeyPrefix, onMove)
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
