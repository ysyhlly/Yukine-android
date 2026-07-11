package app.yukine.ui

import android.net.Uri
import app.yukine.TrackDownloadItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.yukine.feature.uicommon.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

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
    val onDownload: Runnable,
    val onEdit: Runnable?,
    val onDelete: Runnable?,
    val onLongPress: Runnable?
) {
    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable
    ) : this(onPlay, onFavorite, onAddToPlaylist, Runnable {}, null, null, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onDownload: Runnable
    ) : this(onPlay, onFavorite, onAddToPlaylist, onDownload, null, null, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onEdit: Runnable?,
        onDelete: Runnable?
    ) : this(onPlay, onFavorite, onAddToPlaylist, Runnable {}, onEdit, onDelete, onDelete)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onDownload: Runnable,
        onEdit: Runnable?,
        onDelete: Runnable?
    ) : this(onPlay, onFavorite, onAddToPlaylist, onDownload, onEdit, onDelete, onDelete)
}

data class TrackListHeaderMetric(val label: String, val value: String)
data class TrackListHeaderAction(val label: String, val onClick: Runnable)
data class TrackListModeAction(val label: String, val mode: String, val selected: Boolean, val onClick: Runnable)
data class TrackListAlbumCardUiState(
    val title: String,
    val subtitle: String,
    val coverUri: Uri?,
    val onClick: Runnable
)
data class TrackListLabels(
    val favoriteLabel: String = "\u6536\u85cf",
    val removeFavoriteLabel: String = "\u53d6\u6d88\u6536\u85cf",
    val addToPlaylistLabel: String = "\u52a0\u5165\u6b4c\u5355",
    val editLabel: String = "\u7f16\u8f91",
    val deleteLabel: String = "\u5220\u9664",
    val downloadLabel: String = "\u4e0b\u8f7d",
    val downloadCurrentListLabel: String = "\u4e0b\u8f7d\u5f53\u524d\u5217\u8868",
    val allAlbumsLabel: String = "\u5168\u90e8\u4e13\u8f91",
    val playAllLabel: String = "\u64ad\u653e\u5168\u90e8",
    val shuffleLabel: String = "\u968f\u673a\u64ad\u653e"
)

private data class TrackActionSheetState(
    val track: TrackRowUiState,
    val actions: TrackRowActions
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<TrackRowUiState>,
    actions: List<TrackRowActions>,
    headerMetrics: List<TrackListHeaderMetric>,
    headerActions: List<TrackListHeaderAction>,
    emptyText: String,
    modeActions: List<TrackListModeAction>,
    labels: TrackListLabels,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    footerAlbums: List<TrackListAlbumCardUiState> = emptyList(),
    libraryUi: LibraryUiState = LibraryUiState(),
    libraryActionHandler: LibraryActionHandler = LibraryActionHandler { },
    libraryControlsEnabled: Boolean = false
) {
    val p = EchoTheme.colors()
    var actionSheetState by remember { mutableStateOf<TrackActionSheetState?>(null) }
    val actionSheet = actionSheetState
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val titleBackAction = headerActions.firstOrNull { isBackAction(it.label) }
    val visibleHeaderActions = if (titleBackAction != null) headerActions.drop(1) else headerActions
    if (actionSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { actionSheetState = null },
            sheetState = sheetState,
            containerColor = p.surface,
            contentColor = p.text
        ) {
            TrackActionSheet(
                track = actionSheet.track,
                actions = actionSheet.actions,
                labels = labels,
                onDismiss = { actionSheetState = null }
            )
        }
    }
    LaunchedEffect(listState, libraryControlsEnabled) {
        if (!libraryControlsEnabled) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect { libraryActionHandler.onAction(LibraryAction.RevealTrack(null)) }
    }
    CollapsibleSearchHeader(
        enabled = !libraryControlsEnabled,
        header = { TrackListSearchRow(onSearch, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            if (!libraryControlsEnabled || titleBackAction != null) {
                item(key = "title") {
                    EchoPageTitle(
                        title,
                        backLabel = titleBackAction?.label,
                        onBack = titleBackAction?.onClick
                    )
                }
            }
            if (libraryControlsEnabled) {
                item(key = "libraryControls") {
                    LibraryTrackControls(libraryUi, libraryActionHandler)
                }
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
                if (metric.label == "歌手介绍" || metric.label == "Artist info") {
                    ArtistIntroRow(metric.label, metric.value)
                } else {
                    HeaderMetricRow(metric)
                }
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
                    if (libraryControlsEnabled) {
                        SwipeRevealTrackRow(
                            track = track,
                            actions = action,
                            labels = labels,
                            libraryUi = libraryUi,
                            actionHandler = libraryActionHandler,
                            modifier = Modifier.echoEnter(i.coerceAtMost(8)),
                            onMore = { actionSheetState = TrackActionSheetState(track, action) }
                        )
                    } else {
                        TrackRow(
                            track,
                            action,
                            labels,
                            Modifier.echoEnter(i.coerceAtMost(8)),
                            onLongPress = { actionSheetState = TrackActionSheetState(track, action) }
                        )
                    }
                }
            }
            if (tracks.isEmpty() && emptyText.isNotBlank()) {
                item(key = "empty") {
                    HeaderMessageRow(emptyText)
                }
            }
            if (footerAlbums.isNotEmpty()) {
                item(key = "artistAlbumsTitle") {
                    FooterAlbumsTitle(labels.allAlbumsLabel)
                }
                itemsIndexed(
                    items = footerAlbums,
                    key = { index, album -> "artistAlbum:${album.title}:$index" }
                ) { index, album ->
                    FooterAlbumCard(album, Modifier.echoEnter(index.coerceAtMost(8)))
                }
            }
        }
    }
}

@Composable
private fun TrackListSearchRow(
    onSearch: Runnable,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion
) {
    YukineSearchBar(
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    ) { onSearch.run() }
}

@Composable
private fun LibraryTrackControls(
    state: LibraryUiState,
    actionHandler: LibraryActionHandler
) {
    val p = EchoTheme.colors()
    var sortExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { actionHandler.onAction(LibraryAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent) },
            placeholder = { Text(state.labels.search, color = p.muted) },
            shape = EchoShapes.medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                LibraryControlChip(
                    label = state.labels.sort + ": " + sortLabel(state, state.sort),
                    onClick = { sortExpanded = true }
                )
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(state, sort)) },
                            onClick = {
                                sortExpanded = false
                                actionHandler.onAction(LibraryAction.SortChanged(sort))
                            }
                        )
                    }
                }
            }
            Box {
                LibraryControlChip(
                    label = state.labels.filter + ": " + filterLabel(state),
                    onClick = { filterExpanded = true }
                )
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    LibraryFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filterLabel(state, filter)) },
                            onClick = {
                                filterExpanded = false
                                actionHandler.onAction(LibraryAction.FilterChanged(filter))
                            }
                        )
                    }
                }
            }
        }
        if (state.selectionActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoFloatingLayer(p, EchoShapes.medium),
                shape = EchoShapes.medium,
                color = p.accentSoft.copy(alpha = 0.72f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        (state.selectedTrackKeys.size + state.selectedGroupKeys.size).toString() + state.labels.selectedSuffix,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryControlChip(state.labels.selectAll) {
                            actionHandler.onAction(LibraryAction.SelectAllVisible)
                        }
                        LibraryControlChip(state.labels.play) {
                            actionHandler.onAction(LibraryAction.PlaySelected)
                        }
                        LibraryControlChip(state.labels.addToPlaylist) {
                            actionHandler.onAction(LibraryAction.AddSelectedToPlaylist)
                        }
                        LibraryControlChip(state.labels.favorite) {
                            actionHandler.onAction(LibraryAction.FavoriteSelected)
                        }
                        LibraryControlChip(state.labels.download) {
                            actionHandler.onAction(LibraryAction.DownloadSelected)
                        }
                        LibraryControlChip(state.labels.delete, dangerous = true) {
                            actionHandler.onAction(LibraryAction.DeleteSelected)
                        }
                        LibraryControlChip(state.labels.cancel) {
                            actionHandler.onAction(LibraryAction.ClearSelection)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryControlChip(
    label: String,
    dangerous: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val danger = Color(0xFFB3261E)
    Surface(
        onClick = onClick,
        shape = EchoShapes.small,
        color = if (dangerous) danger.copy(alpha = 0.16f) else p.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = EchoTypography.caption,
            color = if (dangerous) danger else p.text
        )
    }
}

private fun sortLabel(state: LibraryUiState, sort: LibrarySort): String = when (sort) {
    LibrarySort.TitleAscending -> state.labels.sortTitleAscending
    LibrarySort.TitleDescending -> state.labels.sortTitleDescending
    LibrarySort.Artist -> state.labels.sortArtist
    LibrarySort.Album -> state.labels.sortAlbum
    LibrarySort.DurationAscending -> state.labels.sortDurationAscending
    LibrarySort.DurationDescending -> state.labels.sortDurationDescending
}

private fun filterLabel(state: LibraryUiState): String = filterLabel(state, state.filter)

private fun filterLabel(state: LibraryUiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.All -> state.labels.all
    LibraryFilter.Favorites -> state.labels.favorites
    LibraryFilter.Local -> state.labels.local
    LibraryFilter.Network -> state.labels.network
}

@Composable
private fun SwipeRevealTrackRow(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels,
    libraryUi: LibraryUiState,
    actionHandler: LibraryActionHandler,
    modifier: Modifier,
    onMore: () -> Unit
) {
    val p = EchoTheme.colors()
    val danger = Color(0xFFB3261E)
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 152.dp.toPx() }
    val expanded = libraryUi.revealedRowKey == track.key
    var gestureOffset by remember(track.key) { mutableStateOf<Float?>(null) }
    val targetOffset = if (expanded) -revealWidthPx else 0f
    val animatedOffset by animateFloatAsState(targetOffset, label = "librarySwipeReveal")
    val offset = gestureOffset ?: animatedOffset
    val revealedWidth = with(density) { (-offset).coerceIn(0f, revealWidthPx).toDp() }
    val selected = libraryUi.selectedTrackKeys.contains(track.key)

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        if (revealedWidth > 0.dp) Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealedWidth)
                .height(62.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(152.dp)
                    .fillMaxHeight()
                    .clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = {
                        actionHandler.onAction(LibraryAction.RevealTrack(null))
                        onMore()
                    },
                    modifier = Modifier.width(74.dp).fillMaxHeight(),
                    shape = EchoShapes.small,
                    color = p.surfaceVariant
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        EchoIcon(EchoIconKind.More, Modifier.size(18.dp), p.accent)
                        Text(libraryUi.labels.more, style = EchoTypography.small, color = p.text)
                    }
                }
                Surface(
                    onClick = {
                        actionHandler.onAction(LibraryAction.RevealTrack(null))
                        actions.onDelete?.run()
                    },
                    modifier = Modifier.width(74.dp).fillMaxHeight(),
                    shape = EchoShapes.small,
                    color = danger.copy(alpha = 0.18f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        EchoIcon(EchoIconKind.Delete, Modifier.size(18.dp), danger)
                        Text(libraryUi.labels.delete, style = EchoTypography.small, color = danger)
                    }
                }
            }
        }
        TrackRow(
            track = track,
            actions = actions,
            labels = labels,
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(track.key, libraryUi.selectionActive) {
                    if (libraryUi.selectionActive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { gestureOffset = targetOffset },
                        onHorizontalDrag = { _, amount ->
                            gestureOffset = ((gestureOffset ?: targetOffset) + amount).coerceIn(-revealWidthPx, 0f)
                        },
                        onDragEnd = {
                            val reveal = (gestureOffset ?: targetOffset) <= -revealWidthPx * 0.25f
                            gestureOffset = null
                            actionHandler.onAction(LibraryAction.RevealTrack(if (reveal) track.key else null))
                        },
                        onDragCancel = { gestureOffset = null }
                    )
                },
            selected = selected,
            onClick = {
                when {
                    libraryUi.selectionActive -> actionHandler.onAction(LibraryAction.ToggleTrackSelection(track.key))
                    expanded -> actionHandler.onAction(LibraryAction.RevealTrack(null))
                    else -> actions.onPlay.run()
                }
            },
            onLongPress = { actionHandler.onAction(LibraryAction.ToggleTrackSelection(track.key)) }
        )
    }
}

@Composable
private fun TrackActionSheet(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels,
    onDismiss: () -> Unit
) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, top = 8.dp, end = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            track.title,
            style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
            color = p.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            track.subtitle,
            style = EchoTypography.body,
            color = p.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        TrackActionSheetRow(
            icon = EchoIconKind.Heart,
            label = if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel
        ) {
            onDismiss()
            actions.onFavorite.run()
        }
        if (track.showPlaylistAction) {
            TrackActionSheetRow(EchoIconKind.PlaylistAdd, labels.addToPlaylistLabel) {
                onDismiss()
                actions.onAddToPlaylist.run()
            }
        }
        TrackActionSheetRow(EchoIconKind.Import, labels.downloadLabel) {
            onDismiss()
            actions.onDownload.run()
        }
        actions.onEdit?.let { onEdit ->
            TrackActionSheetRow(EchoIconKind.Edit, labels.editLabel) {
                onDismiss()
                onEdit.run()
            }
        }
        actions.onDelete?.let { onDelete ->
            TrackActionSheetRow(EchoIconKind.Delete, labels.deleteLabel) {
                onDismiss()
                onDelete.run()
            }
        }
    }
}

@Composable
private fun TrackActionSheetRow(
    icon: EchoIconKind,
    label: String,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        color = p.surfaceVariant.copy(alpha = 0.64f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(icon, Modifier.size(20.dp), p.accent)
            Spacer(Modifier.width(12.dp))
            Text(label, style = EchoTypography.bodyMedium, color = p.text)
        }
    }
}

@Composable
private fun ArtistIntroRow(label: String, intro: String) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EchoIcon(EchoIconKind.Artist, Modifier.size(20.dp), p.accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text
                )
            }
            Text(
                intro,
                style = EchoTypography.body,
                color = p.muted,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun FooterAlbumsTitle(label: String) {
    val p = EchoTheme.colors()
    Text(
        label,
        style = EchoTypography.title,
        color = p.text,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun FooterAlbumCard(album: TrackListAlbumCardUiState, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { album.onClick.run() },
        interactionSource = interaction,
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .echoPressScale(interaction)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncArtwork(
                uri = album.coverUri,
                title = album.title,
                subtitle = album.subtitle,
                modifier = Modifier.size(58.dp),
                cornerRadius = 8.dp,
                fallbackTextSize = 17.sp,
                targetSize = 58.dp,
                backgroundColor = p.surfaceVariant,
                fallbackResId = R.drawable.ic_stat_echo
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    album.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    album.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EchoIcon(EchoIconKind.Play, Modifier.size(20.dp), p.accent)
        }
    }
}

@Composable
private fun ModeSelectorRow(modes: List<TrackListModeAction>) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (mode in modes) {
            Surface(
                onClick = { mode.onClick.run() },
                modifier = Modifier
                    .widthIn(min = 104.dp)
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
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mode.label,
                        style = EchoTypography.caption,
                        color = if (mode.selected) p.accent else p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            .echoFloatingLayer(p, EchoShapes.medium)
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
            EchoIcon(iconForTrackHeaderAction(action.label), Modifier.size(22.dp), p.accent)
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
private fun TrackRow(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current || selected) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "trackRowBg"
    )
    Surface(
        modifier = modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onClick?.invoke() ?: actions.onPlay.run() },
                onLongClick = { onLongPress?.invoke() ?: run { menuExpanded = true } }
            )
            .echoPressScale(interaction)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (track.current || selected) bg else Color.Transparent
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
            Spacer(Modifier.width(8.dp))
            Text(
                track.duration,
                style = EchoTypography.small,
                color = p.muted,
                modifier = Modifier.width(38.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Box {
                TrackMoreMenu(track, actions, labels)
                TrackMoreMenuAnchor(
                    track = track,
                    actions = actions,
                    labels = labels,
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it }
                )
            }
        }
    }
}

@Composable
private fun TrackMoreMenu(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels
) {
    var expanded by remember { mutableStateOf(false) }
    TrackMoreMenuContent(
        track = track,
        actions = actions,
        labels = labels,
        expanded = expanded,
        onExpandedChange = { expanded = it }
    )
}

@Composable
private fun TrackMoreMenuAnchor(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    TrackMoreMenuContent(
        track = track,
        actions = actions,
        labels = labels,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        showButton = false
    )
}

@Composable
private fun TrackMoreMenuContent(
    track: TrackRowUiState,
    actions: TrackRowActions,
    labels: TrackListLabels,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showButton: Boolean = true
) {
    Box {
        if (showButton) {
            MiniIconBtn(
                icon = EchoIconKind.More,
                desc = labels.editLabel + " / " + labels.deleteLabel,
                onClick = { onExpandedChange(true) }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(if (track.favorite) labels.removeFavoriteLabel else labels.favoriteLabel) },
                leadingIcon = {
                    EchoIcon(
                        EchoIconKind.Heart,
                        Modifier.size(18.dp),
                        if (track.favorite) EchoTheme.colors().accent else EchoTheme.colors().muted
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    actions.onFavorite.run()
                }
            )
            if (track.showPlaylistAction) {
                DropdownMenuItem(
                    text = { Text(labels.addToPlaylistLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.PlaylistAdd, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        onExpandedChange(false)
                        actions.onAddToPlaylist.run()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(labels.downloadLabel) },
                leadingIcon = { EchoIcon(EchoIconKind.Import, Modifier.size(18.dp), EchoTheme.colors().muted) },
                onClick = {
                    onExpandedChange(false)
                    actions.onDownload.run()
                }
            )
            actions.onEdit?.let { onEdit ->
                DropdownMenuItem(
                    text = { Text(labels.editLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Edit, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        onExpandedChange(false)
                        onEdit.run()
                    }
                )
            }
            actions.onDelete?.let { onDelete ->
                DropdownMenuItem(
                    text = { Text(labels.deleteLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Delete, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        onExpandedChange(false)
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

internal fun iconForTrackHeaderAction(label: String): EchoIconKind = when {
    isBackAction(label) -> EchoIconKind.Back
    label.contains("Play", ignoreCase = true) || label.contains("\u64ad\u653e") -> EchoIconKind.Play
    label.contains("Shuffle", ignoreCase = true) || label.contains("\u968f\u673a") -> EchoIconKind.Shuffle
    label.contains("Download", ignoreCase = true) || label.contains("\u4e0b\u8f7d") -> EchoIconKind.Download
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
