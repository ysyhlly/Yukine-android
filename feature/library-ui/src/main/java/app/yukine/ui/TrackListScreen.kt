package app.yukine.ui

import android.net.Uri
import app.yukine.LibraryListContext
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.material3.CircularProgressIndicator
import app.yukine.core.designsystem.R
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
    val key: String = id.toString(),
    val favoritePending: Boolean = false
)

data class TrackRowActions(
    val onPlay: Runnable,
    val onFavorite: Runnable,
    val onAddToPlaylist: Runnable,
    val onDownload: Runnable?,
    val onEdit: Runnable?,
    val onDelete: Runnable?,
    val onLongPress: Runnable?,
    val onMatchManagement: Runnable? = null
) {
    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable
    ) : this(onPlay, onFavorite, onAddToPlaylist, null, null, null, null, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onDownload: Runnable
    ) : this(onPlay, onFavorite, onAddToPlaylist, onDownload, null, null, null, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onEdit: Runnable?,
        onDelete: Runnable?
    ) : this(onPlay, onFavorite, onAddToPlaylist, null, onEdit, onDelete, onDelete, null)

    constructor(
        onPlay: Runnable,
        onFavorite: Runnable,
        onAddToPlaylist: Runnable,
        onDownload: Runnable,
        onEdit: Runnable?,
        onDelete: Runnable?
    ) : this(onPlay, onFavorite, onAddToPlaylist, onDownload, onEdit, onDelete, onDelete, null)
}

data class TrackListHeaderMetric(
    val label: String,
    val value: String,
    val artworkUri: Uri? = null,
    val artworkLabel: String = ""
)
enum class TrackListHeaderActionKind {
    Custom,
    PlayAll,
    Shuffle,
    DownloadCurrentList
}

data class TrackListHeaderAction(
    val label: String,
    val onClick: Runnable,
    val icon: EchoIconKind = EchoIconKind.Action,
    val isBack: Boolean = false,
    val kind: TrackListHeaderActionKind = TrackListHeaderActionKind.Custom
)
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
    val shuffleLabel: String = "\u968f\u673a\u64ad\u653e",
    val matchManagementLabel: String = "\u7ba1\u7406\u6b4c\u66f2\u5339\u914d",
    val songsLabel: String = "\u9996\u6b4c\u66f2",
    val moreActionsLabel: String = "\u66f4\u591a\u64cd\u4f5c",
    val favoriteUpdatingLabel: String = "\u6b63\u5728\u66f4\u65b0\u6536\u85cf"
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
    libraryControlsEnabled: Boolean = false,
    compactCards: Boolean = true,
    context: LibraryListContext = LibraryListContext.Songs,
    onNavigateUp: Runnable? = null
) {
    val p = EchoTheme.colors()
    val density = libraryCardDensityTokens(compactCards)
    var actionSheetTrackKey by remember { mutableStateOf<String?>(null) }
    val actionSheetIndex = actionSheetTrackKey
        ?.let { selectedKey -> tracks.indexOfFirst { it.key == selectedKey } }
        ?.takeIf { it >= 0 }
    val actionSheetTrack = actionSheetIndex?.let(tracks::getOrNull)
    val actionSheetActions = actionSheetIndex?.let(actions::getOrNull)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val titleBackAction = headerActions.firstOrNull { it.isBack }
    val visibleHeaderActions = if (titleBackAction != null) headerActions.drop(1) else headerActions
    var sortSheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(actionSheetTrackKey, actionSheetTrack, actionSheetActions) {
        if (actionSheetTrackKey != null && (actionSheetTrack == null || actionSheetActions == null)) {
            actionSheetTrackKey = null
        }
    }
    if (actionSheetTrack != null && actionSheetActions != null) {
        ModalBottomSheet(
            onDismissRequest = { actionSheetTrackKey = null },
            sheetState = sheetState,
            containerColor = p.surface,
            contentColor = p.text
        ) {
            TrackActionSheet(
                track = actionSheetTrack,
                actions = actionSheetActions,
                labels = labels,
                onDismiss = { actionSheetTrackKey = null }
            )
        }
    }
    if (sortSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sortSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.surface,
            contentColor = p.text
        ) {
            LibrarySortSheet(
                state = libraryUi,
                onSort = { sort ->
                    sortSheetVisible = false
                    libraryActionHandler.onAction(LibraryAction.SortChanged(sort))
                }
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
    if (libraryControlsEnabled) {
        LibraryTrackListContent(
            title = title,
            tracks = tracks,
            actions = actions,
            headerMetrics = headerMetrics,
            headerActions = headerActions,
            emptyText = emptyText,
            modeActions = modeActions,
            labels = labels,
            footerAlbums = footerAlbums,
            libraryUi = libraryUi,
            context = context,
            actionHandler = libraryActionHandler,
            listState = listState,
            onNavigateUp = titleBackAction?.onClick ?: onNavigateUp,
            onSortClick = { sortSheetVisible = true },
            onMore = { track, _ -> actionSheetTrackKey = track.key },
            density = density
        )
        return
    }
    CollapsibleSearchHeader(
        enabled = true,
        header = { TrackListSearchRow(onSearch, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = echoPageBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            if (titleBackAction != null || title.isNotBlank()) {
                item(key = "title") {
                    EchoPageTitle(
                        title,
                        backLabel = titleBackAction?.label,
                        onBack = titleBackAction?.onClick
                    )
                }
            }
            if (modeActions.isNotEmpty()) {
                item(key = "modes") {
                    LibraryModeSelectorRow(modeActions)
                }
            }
            itemsIndexed(
                items = headerMetrics,
                key = { index, metric -> "metric:${metric.label}:$index" }
            ) { _, metric ->
                if (metric.label == "歌手介绍" || metric.label == "Artist info") {
                    ArtistIntroRow(metric)
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
                    TrackRow(
                        track,
                        action,
                        labels,
                        Modifier.echoEnter(i.coerceAtMost(8)),
                        onLongPress = { actionSheetTrackKey = track.key }
                    )
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
private fun LibraryTrackListContent(
    title: String,
    tracks: List<TrackRowUiState>,
    actions: List<TrackRowActions>,
    headerMetrics: List<TrackListHeaderMetric>,
    headerActions: List<TrackListHeaderAction>,
    emptyText: String,
    modeActions: List<TrackListModeAction>,
    labels: TrackListLabels,
    footerAlbums: List<TrackListAlbumCardUiState>,
    libraryUi: LibraryUiState,
    context: LibraryListContext,
    actionHandler: LibraryActionHandler,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigateUp: Runnable?,
    onSortClick: () -> Unit,
    onMore: (TrackRowUiState, TrackRowActions) -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    val primaryActions = headerActions.filter { action ->
        !action.isBack && (
            action.kind == TrackListHeaderActionKind.PlayAll ||
                action.kind == TrackListHeaderActionKind.Shuffle ||
                action.icon == EchoIconKind.Play
            )
    }.take(2)
    val overflowActions = headerActions.filter { action ->
        !action.isBack && action !in primaryActions
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .then(
                if (density.independentCards) {
                    Modifier
                } else {
                    Modifier
                        .echoFloatingLayer(p, EchoShapes.large)
                        .echoGlassLayer(p, EchoShapes.large)
                }
            ),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 6.dp, bottom = echoPageBottomPadding()),
            verticalArrangement = if (density.independentCards) {
                Arrangement.spacedBy(8.dp)
            } else {
                Arrangement.Top
            }
        ) {
            item(key = "header") {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    LibraryTrackListHeader(
                        title = title,
                        subtitle = "${tracks.size} ${labels.songsLabel}",
                        backLabel = libraryUi.labels.back,
                        onNavigateUp = onNavigateUp ?: Runnable { },
                        overflowActions = overflowActions,
                        operationInProgress = libraryUi.operationInProgress
                    )
                }
            }
            if (modeActions.isNotEmpty()) {
                item(key = "modes") {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibraryModeSelectorRow(modeActions)
                    }
                }
            }
            if (libraryUi.selectionActive) {
                item(key = "selection") {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibrarySelectionToolbar(libraryUi, actionHandler)
                    }
                }
            } else {
                if (primaryActions.isNotEmpty()) {
                    item(key = "primary-actions") {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            LibraryPrimaryActions(primaryActions)
                        }
                    }
                }
                item(key = "search") {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibrarySearchField(libraryUi, actionHandler)
                    }
                }
                item(key = "filters") {
                    LibraryFilterBar(libraryUi, actionHandler, onSortClick)
                }
            }
            itemsIndexed(
                items = headerMetrics,
                key = { index, metric -> "metric:${metric.label}:$index" }
            ) { _, metric ->
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (metric.label == "歌手介绍" || metric.label == "Artist info") {
                        ArtistIntroRow(metric)
                    } else {
                        HeaderMetricRow(metric)
                    }
                }
            }
            itemsIndexed(
                items = tracks,
                key = { index, track -> track.key.ifBlank { "${track.id}:$index" } }
            ) { index, track ->
                actions.getOrNull(index)?.let { action ->
                    SwipeRevealTrackRow(
                        track = track,
                        actions = action,
                        labels = labels,
                        libraryUi = libraryUi,
                        actionHandler = actionHandler,
                        modifier = Modifier.echoEnter(index.coerceAtMost(8)),
                        onMore = { onMore(track, action) },
                        density = density
                    )
                    if (!density.independentCards && index < tracks.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = density.trackArtworkSize + 24.dp,
                                end = 12.dp
                            ),
                            color = p.border.copy(alpha = 0.16f)
                        )
                    }
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    LibraryTrackEmptyState(
                        context = context,
                        emptyText = emptyText,
                        state = libraryUi,
                        actionHandler = actionHandler
                    )
                }
            }
            if (footerAlbums.isNotEmpty()) {
                item(key = "artistAlbumsTitle") {
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        FooterAlbumsTitle(labels.allAlbumsLabel)
                    }
                }
                itemsIndexed(
                    items = footerAlbums,
                    key = { index, album -> "artistAlbum:${album.title}:$index" }
                ) { index, album ->
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        FooterAlbumCard(album, Modifier.echoEnter(index.coerceAtMost(8)))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackListHeader(
    title: String,
    subtitle: String,
    backLabel: String,
    onNavigateUp: Runnable,
    overflowActions: List<TrackListHeaderAction>,
    operationInProgress: Boolean = false
) {
    val p = EchoTheme.colors()
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniIconBtn(EchoIconKind.Back, backLabel) { onNavigateUp.run() }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subtitle, style = EchoTypography.caption, color = p.muted)
                    if (operationInProgress) {
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = p.accent
                        )
                    }
                }
            }
            if (overflowActions.isNotEmpty()) {
                Box {
                    MiniIconBtn(EchoIconKind.More, overflowActions.joinToString { it.label }) {
                        expanded = true
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        overflowActions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = { EchoIcon(action.icon, Modifier.size(18.dp), p.muted) },
                                onClick = {
                                    expanded = false
                                    action.onClick.run()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPrimaryActions(actions: List<TrackListHeaderAction>) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = { action.onClick.run() },
                interactionSource = interaction,
                modifier = Modifier
                    .echoPressScale(interaction)
                    .semantics { contentDescription = action.label },
                shape = EchoShapes.full,
                color = if (action.kind == TrackListHeaderActionKind.PlayAll || action.icon == EchoIconKind.Play) {
                    p.accent
                } else {
                    p.surfaceVariant.copy(alpha = 0.76f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoIcon(
                        action.icon,
                        Modifier.size(19.dp),
                        if (action.kind == TrackListHeaderActionKind.PlayAll || action.icon == EchoIconKind.Play) {
                            p.onAccent
                        } else {
                            p.accent
                        }
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        action.label,
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (action.kind == TrackListHeaderActionKind.PlayAll || action.icon == EchoIconKind.Play) {
                            p.onAccent
                        } else {
                            p.text
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchField(state: LibraryUiState, actionHandler: LibraryActionHandler) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { actionHandler.onAction(LibraryAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent) },
            trailingIcon = if (state.query.isNotBlank()) {
                {
                    MiniIconBtn(EchoIconKind.Remove, state.labels.clearSearch) {
                        actionHandler.onAction(LibraryAction.QueryChanged(""))
                    }
                }
            } else {
                null
            },
            placeholder = { Text(state.labels.search, color = p.muted) },
            shape = EchoShapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = p.accent.copy(alpha = 0.58f),
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun LibraryFilterBar(
    state: LibraryUiState,
    actionHandler: LibraryActionHandler,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        LibraryFilter.entries.forEach { filter ->
            LibraryControlChip(
                label = filterLabel(state, filter),
                selected = state.filter == filter
            ) {
                actionHandler.onAction(LibraryAction.FilterChanged(filter))
            }
        }
        LibraryControlChip(
            label = sortLabel(state, state.sort),
            selected = true,
            onClick = onSortClick
        )
        if (state.dedupCandidateCount > 0) {
            LibraryControlChip(
                label = state.labels.dedup + " · " + state.dedupCandidateCount,
                selected = false
            ) {
                actionHandler.onAction(LibraryAction.OpenDedupCenter)
            }
        }
    }
}

@Composable
private fun LibrarySelectionToolbar(state: LibraryUiState, actionHandler: LibraryActionHandler) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = p.accentSoft.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (state.selectedTrackKeys.size + state.selectedGroupKeys.size).toString() +
                        state.labels.selectedSuffix,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    modifier = Modifier.weight(1f)
                )
                MiniIconBtn(EchoIconKind.Remove, state.labels.cancel) {
                    actionHandler.onAction(LibraryAction.ClearSelection)
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LibraryControlChip(state.labels.selectAll) {
                    actionHandler.onAction(LibraryAction.SelectAllVisible)
                }
                LibraryControlChip(state.labels.play) {
                    actionHandler.onAction(LibraryAction.PlaySelected)
                }
                LibraryControlChip(state.labels.favorite) {
                    actionHandler.onAction(LibraryAction.FavoriteSelected)
                }
                LibraryControlChip(state.labels.addToPlaylist) {
                    actionHandler.onAction(LibraryAction.AddSelectedToPlaylist)
                }
                LibraryControlChip(state.labels.download) {
                    actionHandler.onAction(LibraryAction.DownloadSelected)
                }
                LibraryControlChip(state.labels.delete, dangerous = true) {
                    actionHandler.onAction(LibraryAction.DeleteSelected)
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackEmptyState(
    context: LibraryListContext,
    emptyText: String,
    state: LibraryUiState,
    actionHandler: LibraryActionHandler
) {
    val message = when {
        state.query.isNotBlank() -> state.labels.emptySearch
        state.filter != LibraryFilter.All -> state.labels.emptyFilter
        emptyText.isNotBlank() -> emptyText
        else -> state.labels.emptyLibrary
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderMessageRow(message)
        when {
            state.query.isNotBlank() -> LibraryControlChip(state.labels.clearSearch) {
                actionHandler.onAction(LibraryAction.QueryChanged(""))
            }
            state.filter != LibraryFilter.All -> LibraryControlChip(state.labels.resetFilter) {
                actionHandler.onAction(LibraryAction.FilterChanged(LibraryFilter.All))
            }
            context == LibraryListContext.Songs -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryControlChip(state.labels.scanLibrary) {
                    actionHandler.onAction(LibraryAction.ScanLibrary)
                }
                LibraryControlChip(state.labels.importFiles) {
                    actionHandler.onAction(LibraryAction.ImportFiles)
                }
            }
        }
    }
}

@Composable
private fun LibrarySortSheet(state: LibraryUiState, onSort: (LibrarySort) -> Unit) {
    val p = EchoTheme.colors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            state.labels.sort,
            style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
            color = p.text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        LibrarySort.entries.forEach { sort ->
            Surface(
                onClick = { onSort(sort) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = sortLabel(state, sort) },
                shape = EchoShapes.medium,
                color = if (state.sort == sort) p.accentSoft.copy(alpha = 0.74f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sortLabel(state, sort), style = EchoTypography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
                    if (state.sort == sort) {
                        EchoIcon(EchoIconKind.Check, Modifier.size(18.dp), p.accent)
                    }
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
        LibrarySyncControls(state, actionHandler)
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
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val danger = Color(0xFFB3261E)
    Surface(
        onClick = onClick,
        shape = EchoShapes.small,
        color = when {
            dangerous -> danger.copy(alpha = 0.16f)
            selected -> p.accentSoft.copy(alpha = 0.82f)
            else -> p.surfaceVariant.copy(alpha = 0.62f)
        },
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = EchoTypography.caption,
            color = when {
                dangerous -> danger
                selected -> p.accent
                else -> p.text
            }
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
    LibrarySort.DateAddedDescending -> state.labels.sortDateAddedDescending
    LibrarySort.DateAddedAscending -> state.labels.sortDateAddedAscending
    LibrarySort.PlayCountDescending -> state.labels.sortPlayCount
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
    onMore: () -> Unit,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    val danger = Color(0xFFB3261E)
    val localDensity = LocalDensity.current
    val revealWidth = if (actions.onDelete == null) 74.dp else 152.dp
    val revealWidthPx = with(localDensity) { revealWidth.toPx() }
    val expanded = libraryUi.revealedRowKey == track.key
    var gestureOffset by remember(track.key) { mutableStateOf<Float?>(null) }
    val targetOffset = if (expanded) -revealWidthPx else 0f
    val animatedOffset by animateFloatAsState(targetOffset, label = "librarySwipeReveal")
    val offset = gestureOffset ?: animatedOffset
    val revealedWidth = with(localDensity) { (-offset).coerceIn(0f, revealWidthPx).toDp() }
    val selected = libraryUi.selectedTrackKeys.contains(track.key)

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        if (revealedWidth > 0.dp) Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealedWidth)
                .height(density.trackRowHeight),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(revealWidth)
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
                actions.onDelete?.let { onDelete ->
                    Surface(
                        onClick = {
                            actionHandler.onAction(LibraryAction.RevealTrack(null))
                            onDelete.run()
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
            onLongPress = { actionHandler.onAction(LibraryAction.ToggleTrackSelection(track.key)) },
            flat = true,
            independentCard = density.independentCards,
            density = density
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
            label = if (track.favoritePending) {
                labels.favoriteUpdatingLabel
            } else if (track.favorite) {
                labels.removeFavoriteLabel
            } else {
                labels.favoriteLabel
            },
            enabled = !track.favoritePending,
            loading = track.favoritePending
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
        actions.onDownload?.let { onDownload ->
            TrackActionSheetRow(EchoIconKind.Import, labels.downloadLabel) {
                onDismiss()
                onDownload.run()
            }
        }
        actions.onMatchManagement?.let { onMatchManagement ->
            TrackActionSheetRow(EchoIconKind.Info, labels.matchManagementLabel) {
                onDismiss()
                onMatchManagement.run()
            }
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
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        color = p.surfaceVariant.copy(alpha = 0.64f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = p.accent,
                    strokeWidth = 2.dp
                )
            } else {
                EchoIcon(icon, Modifier.size(20.dp), p.accent)
            }
            Spacer(Modifier.width(12.dp))
            Text(label, style = EchoTypography.bodyMedium, color = p.text)
        }
    }
}

@Composable
private fun ArtistIntroRow(metric: TrackListHeaderMetric) {
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncArtwork(
                uri = metric.artworkUri,
                title = metric.artworkLabel.ifBlank { metric.label },
                subtitle = "",
                modifier = Modifier.size(76.dp),
                cornerRadius = 38.dp,
                fallbackTextSize = 24.sp,
                targetSize = 76.dp,
                backgroundColor = p.accentSoft,
                fallbackResId = if (metric.artworkUri == null) null else R.drawable.ic_stat_echo
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    metric.label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text
                )
                Text(
                    metric.value,
                    style = EchoTypography.body,
                    color = p.muted,
                    lineHeight = 21.sp
                )
            }
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
internal fun LibraryModeSelectorRow(modes: List<TrackListModeAction>) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (mode in modes) {
            Surface(
                onClick = { mode.onClick.run() },
                modifier = Modifier
                    .widthIn(min = 104.dp)
                    .height(48.dp)
                    .then(if (mode.selected) Modifier else Modifier.echoGlassLayer(p, EchoShapes.medium))
                    .semantics { contentDescription = mode.label },
                shape = EchoShapes.medium,
                color = if (mode.selected) p.accentSoft else Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
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
            EchoIcon(action.icon, Modifier.size(22.dp), p.accent)
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
    onLongPress: (() -> Unit)? = null,
    flat: Boolean = false,
    independentCard: Boolean = !flat,
    density: LibraryCardDensityTokens = libraryCardDensityTokens(true)
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (track.current || selected) p.accentSoft else p.surface,
        animationSpec = EchoMotion.colorSpring(),
        label = "trackRowBg"
    )
    val surfaceModifier = modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onClick?.invoke() ?: actions.onPlay.run() },
                onLongClick = { onLongPress?.invoke() ?: run { menuExpanded = true } }
            )
            .echoPressScale(interaction)
            .then(
                if (independentCard) {
                    Modifier
                        .echoFloatingLayer(p, EchoShapes.medium)
                        .echoGlassLayer(p, EchoShapes.medium)
                } else {
                    Modifier
                }
            )
    Surface(
        modifier = surfaceModifier,
        shape = EchoShapes.medium,
        color = if (track.current || selected) bg else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (flat) density.trackRowHeight else 62.dp)
                .padding(
                    horizontal = if (flat) density.trackHorizontalPadding else 8.dp,
                    vertical = if (flat) density.trackVerticalPadding else 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackCurrentIndicator(track.current, height = if (flat) density.trackArtworkSize else 44.dp)
            Spacer(Modifier.width(7.dp))
            TrackArtwork(
                track.albumArtUri,
                track.title,
                track.subtitle,
                if (flat) density.trackArtworkSize else 46.dp
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.title,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (track.current) p.accent else p.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.favoritePending) {
                        Spacer(Modifier.width(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(13.dp)
                                .semantics { contentDescription = labels.favoriteUpdatingLabel },
                            color = p.accent,
                            strokeWidth = 1.5.dp
                        )
                    } else if (track.favorite) {
                        Spacer(Modifier.width(4.dp))
                        EchoIcon(EchoIconKind.Heart, Modifier.size(13.dp), p.accent)
                    }
                }
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
            TrackMoreMenuContent(
                track = track,
                actions = actions,
                labels = labels,
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it }
            )
        }
    }
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
                desc = labels.moreActionsLabel,
                onClick = { onExpandedChange(true) }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (track.favoritePending) {
                            labels.favoriteUpdatingLabel
                        } else if (track.favorite) {
                            labels.removeFavoriteLabel
                        } else {
                            labels.favoriteLabel
                        }
                    )
                },
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
                },
                enabled = !track.favoritePending
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
            actions.onDownload?.let { onDownload ->
                DropdownMenuItem(
                    text = { Text(labels.downloadLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Import, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        onExpandedChange(false)
                        onDownload.run()
                    }
                )
            }
            actions.onMatchManagement?.let { onMatchManagement ->
                DropdownMenuItem(
                    text = { Text(labels.matchManagementLabel) },
                    leadingIcon = { EchoIcon(EchoIconKind.Info, Modifier.size(18.dp), EchoTheme.colors().muted) },
                    onClick = {
                        onExpandedChange(false)
                        onMatchManagement.run()
                    }
                )
            }
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
private fun TrackArtwork(uri: Uri?, title: String, subtitle: String, size: androidx.compose.ui.unit.Dp = 46.dp) {
    val p = EchoTheme.colors()
    AsyncArtwork(
        uri = uri,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.size(size),
        cornerRadius = if (size > 46.dp) 9.dp else 6.dp,
        fallbackTextSize = 16.sp,
        targetSize = size,
        backgroundColor = p.surfaceVariant,
        fallbackResId = R.drawable.ic_stat_echo,
        crossfadeEnabled = false
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
            .size(48.dp)
            .semantics { contentDescription = desc },
        shape = EchoShapes.small,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(31.dp),
                shape = EchoShapes.small,
                color = if (active) p.accentSoft.copy(alpha = 0.62f) else p.surfaceVariant.copy(alpha = 0.28f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(icon, Modifier.size(16.dp), if (active) p.accent else p.muted)
                }
            }
        }
    }
}

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
