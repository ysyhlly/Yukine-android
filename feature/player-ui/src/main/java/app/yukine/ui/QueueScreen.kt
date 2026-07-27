package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.core.designsystem.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueTrackUiState(
    val key: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    val audioSpec: String,
    val duration: String,
    val albumArtUri: Uri?,
    val current: Boolean,
    val favorite: Boolean,
    val playbackEnabled: Boolean = true,
    val supportLabel: String? = null
)

data class QueueTrackActions(
    val onPlay: Runnable,
    val onFavorite: Runnable,
    val onAddToPlaylist: Runnable,
    val onRemove: Runnable,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }
)

data class QueueScreenLabels(
    val title: String = "\u64ad\u653e\u961f\u5217",
    val back: String = "\u8fd4\u56de",
    val clearQueue: String = "\u6e05\u7a7a\u961f\u5217",
    val empty: String = "\u961f\u5217\u4e3a\u7a7a",
    val emptyDescription: String = "\u64ad\u653e\u4e00\u9996\u6b4c\uff0c\u6216\u628a\u6b4c\u66f2\u52a0\u5165\u961f\u5217\u3002",
    val tracks: String = "\u9996\u6b4c",
    val favorite: String = "\u6536\u85cf",
    val addToPlaylist: String = "\u52a0\u5165\u6b4c\u5355",
    val remove: String = "\u79fb\u9664",
    val dragReorder: String = "\u62d6\u52a8\u6392\u5e8f"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    tracks: List<QueueTrackUiState>,
    actions: List<QueueTrackActions>,
    onClearQueue: Runnable,
    labels: QueueScreenLabels,
    onBack: Runnable?,
    queueEditable: Boolean = true
) {
    QueueScreen(
        trackCount = tracks.size,
        trackAt = { index -> tracks.getOrNull(index) },
        actionForIndex = { index -> actions.getOrNull(index) },
        onMove = { fromIndex, toIndex -> actions.getOrNull(fromIndex)?.onMove?.invoke(fromIndex, toIndex) },
        onClearQueue = onClearQueue,
        labels = labels,
        onBack = onBack,
        queueEditable = queueEditable
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    tracks: List<QueueTrackUiState>,
    actionForIndex: (Int) -> QueueTrackActions?,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onClearQueue: Runnable,
    labels: QueueScreenLabels,
    onBack: Runnable?,
    queueEditable: Boolean = true
) {
    QueueScreen(
        trackCount = tracks.size,
        trackAt = { index -> tracks.getOrNull(index) },
        actionForIndex = actionForIndex,
        onMove = onMove,
        onClearQueue = onClearQueue,
        labels = labels,
        onBack = onBack,
        queueEditable = queueEditable
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    trackCount: Int,
    trackAt: (Int) -> QueueTrackUiState?,
    actionForIndex: (Int) -> QueueTrackActions?,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onClearQueue: Runnable,
    labels: QueueScreenLabels,
    onBack: Runnable?,
    queueEditable: Boolean = true
) {
    val p = EchoTheme.colors()
    val listState = rememberLazyListState()
    val dragState = rememberQueueDragState(
        itemKeyPrefix = "track-",
        onMove = if (queueEditable) onMove else { _, _ -> }
    )
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 64.dp.toPx() }
    val autoScrollStepPx = with(density) { 18.dp.toPx() }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    fun startAutoScrollIfNeeded() {
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = dragScope.launch {
            while (isActive && dragState.dragging()) {
                val scrollDelta = dragState.autoScrollDelta(
                    listState.layoutInfo,
                    edgeThresholdPx,
                    autoScrollStepPx
                )
                if (scrollDelta == 0f) break
                val consumed = listState.scrollBy(scrollDelta)
                if (consumed == 0f) break
                dragState.onListScrolled(consumed)
                dragState.drag(listState.layoutInfo.visibleItemsInfo, 0f)
                withFrameNanos { }
            }
            autoScrollJob = null
        }
    }
    LaunchedEffect(trackCount) {
        stopAutoScroll()
        dragState.clear()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = echoPagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "title") {
            QueueHeader(labels, trackCount, onBack)
        }
        if (trackCount > 0 && queueEditable) {
            item(key = "clear-queue") {
                Surface(
                    onClick = { onClearQueue.run() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoFloatingLayer(p, EchoShapes.medium)
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
        if (trackCount == 0) {
            item(key = "empty") {
                EchoStateCard(labels.empty, labels.emptyDescription, icon = EchoIconKind.Queue)
            }
        }
        items(
            count = trackCount,
            key = { index -> "track-${trackAt(index)?.key ?: index}" }
        ) { index ->
            val track = trackAt(index) ?: return@items
            val rowKey = "track-${track.key}"
            val dragging = dragState.isDragging(rowKey)
            actionForIndex(index)?.let { action ->
                QueueTrackRow(
                    track = track,
                    actions = action,
                    labels = labels,
                    editable = queueEditable,
                    modifier = Modifier
                        // Placement animation for inserts/reorders; disabled while this row is
                        // manually dragged so graphicsLayer offset does not fight animateItem.
                        .animateItem(
                            fadeInSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                            fadeOutSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                            placementSpec = if (dragging) {
                                null
                            } else {
                                EchoMotion.layoutSpring<androidx.compose.ui.unit.IntOffset>().spec()
                            }
                        )
                        .graphicsLayer {
                            translationY = dragState.dragOffsetFor(rowKey)
                            shadowElevation = if (dragging) 12.dp.toPx() else 0f
                        },
                    dragHandleModifier = if (queueEditable) {
                        Modifier.pointerInput(track.key, trackCount) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragState.start(
                                        listState.layoutInfo.visibleItemsInfo,
                                        rowKey,
                                        index
                                    )
                                },
                                onDragCancel = {
                                    stopAutoScroll()
                                    dragState.clear()
                                },
                                onDragEnd = {
                                    stopAutoScroll()
                                    dragState.drop()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragState.drag(listState.layoutInfo.visibleItemsInfo, dragAmount.y)
                                    startAutoScrollIfNeeded()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueHeader(labels: QueueScreenLabels, trackCount: Int, onBack: Runnable?) {
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Surface(
                    onClick = { onBack.run() },
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = labels.back },
                    shape = EchoShapes.small,
                    color = p.surfaceVariant.copy(alpha = 0.24f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(EchoIconKind.Back, Modifier.size(17.dp), p.muted)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                labels.title,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.heading,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$trackCount ${labels.tracks}",
                style = EchoTypography.caption,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: QueueTrackUiState,
    actions: QueueTrackActions,
    labels: QueueScreenLabels,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    editable: Boolean = true
) {
    val p = EchoTheme.colors()
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "queueRowBg"
    )
    Surface(
        onClick = { if (track.playbackEnabled) actions.onPlay.run() },
        modifier = modifier
            .semantics { track.supportLabel?.let { stateDescription = it } }
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
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
                    track.supportLabel?.let { label ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            style = EchoTypography.small.copy(fontWeight = FontWeight.SemiBold),
                            color = p.accent,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    track.duration,
                    style = EchoTypography.small,
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                if (editable) {
                    QueueDragHandle(dragHandleModifier, labels.dragReorder)
                }
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
                if (editable) {
                    QueueIconButton(EchoIconKind.Remove, labels.remove) {
                        actions.onRemove.run()
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDragHandle(modifier: Modifier, label: String) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier
            .size(30.dp)
            .semantics { contentDescription = label },
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
    private var fromTrackIndex by mutableIntStateOf(-1)
    private var currentTrackIndex by mutableIntStateOf(-1)
    private var adapterOffset by mutableIntStateOf(0)
    private var draggedItemStart by mutableFloatStateOf(0f)
    private var draggedItemSize by mutableFloatStateOf(0f)
    private var dragOffset by mutableFloatStateOf(0f)

    fun start(visibleItems: List<LazyListItemInfo>, key: Any, trackIndex: Int) {
        val item = visibleItems.firstOrNull { it.key == key } ?: return
        draggingKey = key
        fromTrackIndex = trackIndex
        currentTrackIndex = trackIndex
        adapterOffset = item.index - trackIndex
        draggedItemStart = item.offset.toFloat()
        draggedItemSize = item.size.toFloat()
        dragOffset = 0f
    }

    fun drag(visibleItems: List<LazyListItemInfo>, deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val draggedCenter = draggedCenter()
        val queueItems = visibleItems
            .filter { it.key != key && it.key.toString().startsWith(itemKeyPrefix) }
        val target = queueItems.firstOrNull { item ->
                draggedCenter in item.offset.toFloat()..(item.offset + item.size).toFloat()
            } ?: when {
                queueItems.isEmpty() -> null
                draggedCenter < queueItems.first().offset -> queueItems.first()
                draggedCenter > queueItems.last().offset + queueItems.last().size -> queueItems.last()
                else -> null
            }
        target ?: return
        currentTrackIndex = target.index - adapterOffset
    }

    fun autoScrollDelta(
        layoutInfo: LazyListLayoutInfo,
        edgeThresholdPx: Float,
        maxStepPx: Float
    ): Float {
        if (draggingKey == null || edgeThresholdPx <= 0f || maxStepPx <= 0f) return 0f
        val center = draggedCenter()
        val start = layoutInfo.viewportStartOffset.toFloat()
        val end = layoutInfo.viewportEndOffset.toFloat()
        return when {
            center < start + edgeThresholdPx ->
                -maxStepPx * ((start + edgeThresholdPx - center) / edgeThresholdPx)
                    .coerceIn(0.2f, 1f)
            center > end - edgeThresholdPx ->
                maxStepPx * ((center - (end - edgeThresholdPx)) / edgeThresholdPx)
                    .coerceIn(0.2f, 1f)
            else -> 0f
        }
    }

    fun onListScrolled(consumedPx: Float) {
        if (draggingKey == null) return
        draggedItemStart -= consumedPx
        dragOffset += consumedPx
    }

    fun drop() {
        val from = fromTrackIndex
        val to = currentTrackIndex
        if (from >= 0 && to >= 0 && from != to) {
            onMove(from, to)
        }
        clear()
    }

    fun clear() {
        draggingKey = null
        fromTrackIndex = -1
        currentTrackIndex = -1
        adapterOffset = 0
        draggedItemStart = 0f
        draggedItemSize = 0f
        dragOffset = 0f
    }

    fun dragging(): Boolean = draggingKey != null

    fun isDragging(key: Any): Boolean = draggingKey == key

    fun dragOffsetFor(key: Any): Float = if (draggingKey == key) dragOffset else 0f

    private fun draggedCenter(): Float =
        draggedItemStart + dragOffset + draggedItemSize / 2f
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
