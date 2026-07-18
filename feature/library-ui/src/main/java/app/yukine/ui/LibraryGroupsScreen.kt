package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.TrackDownloadItem
import app.yukine.core.designsystem.R

data class LibraryGroupUiState @JvmOverloads constructor(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri? = null,
    val trackCount: Int = 0,
    val groupKey: String = ""
)

data class LibraryGroupActions @JvmOverloads constructor(
    val onOpen: Runnable,
    val onPlay: Runnable,
    val playEnabled: Boolean = true,
    val onDelete: Runnable? = null
)

data class LibraryPlaylistFolderEntryUiState(
    val group: LibraryGroupUiState,
    val actionIndex: Int
)

data class LibraryPlaylistFolderUiState(
    val key: String,
    val title: String,
    val subtitle: String,
    val entries: List<LibraryPlaylistFolderEntryUiState>
)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryGroupsScreen(
    title: String,
    groups: List<LibraryGroupUiState>,
    actions: List<LibraryGroupActions>,
    emptyText: String,
    modeActions: List<TrackListModeAction>,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    libraryUi: LibraryUiState = LibraryUiState(),
    libraryActionHandler: LibraryActionHandler = LibraryActionHandler { },
    libraryControlsEnabled: Boolean = false,
    playlistFolders: List<LibraryPlaylistFolderUiState> = emptyList(),
    onNavigateUp: Runnable? = null,
    compactCards: Boolean = true
) {
    val p = EchoTheme.colors()
    val density = libraryCardDensityTokens(compactCards)
    var sortSheetVisible by remember { mutableStateOf(false) }
    val resultCount = groups.size + playlistFolders.sumOf { it.entries.size }
    val selectable = libraryUi.mode != LibraryMode.Playlists
    val folderSections = remember(groups, libraryUi.mode) {
        if (libraryUi.mode == LibraryMode.Folders) buildFolderSections(groups) else emptyList()
    }
    var collapsedFolderRoots by remember { mutableStateOf(emptySet<String>()) }

    if (sortSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sortSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.surface,
            contentColor = p.text
        ) {
            LibraryGroupSortSheet(libraryUi) { sort ->
                sortSheetVisible = false
                libraryActionHandler.onAction(LibraryAction.GroupSortChanged(sort))
            }
        }
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
        LazyVerticalGrid(
            columns = if (libraryUi.mode == LibraryMode.Albums) GridCells.Fixed(2) else GridCells.Fixed(1),
            contentPadding = PaddingValues(top = 6.dp, bottom = echoPageBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(density.gridHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(density.gridVerticalSpacing)
        ) {
            if (selectable && libraryUi.selectionActive) {
                item(key = "selection", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibraryGroupSelectionToolbar(libraryUi, libraryActionHandler)
                    }
                }
            } else {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibraryGroupsHeader(
                            title = title,
                            resultCount = resultCount,
                            state = libraryUi,
                            onNavigateUp = onNavigateUp ?: Runnable { },
                            actionHandler = libraryActionHandler
                        )
                    }
                }
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LibraryGroupSearchField(libraryUi, libraryActionHandler)
                    }
                }
            }

            if (!libraryUi.selectionActive || !selectable) {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryGroupFilterBar(libraryUi, libraryActionHandler) {
                        sortSheetVisible = true
                    }
                }
            }

            when (libraryUi.mode) {
                LibraryMode.Albums -> itemsIndexed(
                    items = groups,
                    key = { _, group -> group.id }
                ) { index, group ->
                    actions.getOrNull(index)?.let { action ->
                        AlbumGroupCard(
                            group = group,
                            action = action,
                            selected = libraryUi.selectedGroupKeys.contains(group.id),
                            selectionActive = libraryUi.selectionActive,
                            onToggleSelection = {
                                libraryActionHandler.onAction(LibraryAction.ToggleGroupSelection(group.id))
                            },
                            playDescription = libraryUi.labels.play,
                            density = density,
                            independentCard = density.independentCards,
                            modifier = Modifier.padding(
                                start = if (index % 2 == 0) 8.dp else 0.dp,
                                end = if (index % 2 == 1) 8.dp else 0.dp
                            )
                        )
                    }
                }

                LibraryMode.Artists -> itemsIndexed(
                    items = groups,
                    key = { _, group -> group.id },
                    span = { _, _ -> GridItemSpan(maxLineSpan) }
                ) { index, group ->
                    actions.getOrNull(index)?.let { action ->
                        ArtistGroupRow(
                            group = group,
                            action = action,
                            selected = libraryUi.selectedGroupKeys.contains(group.id),
                            selectionActive = libraryUi.selectionActive,
                            onToggleSelection = {
                                libraryActionHandler.onAction(LibraryAction.ToggleGroupSelection(group.id))
                            },
                            playDescription = libraryUi.labels.play,
                            density = density
                        )
                        if (!density.independentCards) GroupDivider()
                    }
                }

                LibraryMode.Folders -> folderSections.forEach { section ->
                    item(key = "folder-root:${section.root}", span = { GridItemSpan(maxLineSpan) }) {
                        FolderRootHeader(
                            section = section,
                            expanded = section.root !in collapsedFolderRoots,
                            onClick = {
                                collapsedFolderRoots = if (section.root in collapsedFolderRoots) {
                                    collapsedFolderRoots - section.root
                                } else {
                                    collapsedFolderRoots + section.root
                                }
                            }
                        )
                    }
                    if (section.root !in collapsedFolderRoots) {
                        itemsIndexed(
                            items = section.entries,
                            key = { _, entry -> entry.group.id },
                            span = { _, _ -> GridItemSpan(maxLineSpan) }
                        ) { _, entry ->
                            val index = groups.indexOfFirst { it.id == entry.group.id }
                            actions.getOrNull(index)?.let { action ->
                                FolderGroupRow(
                                    entry = entry,
                                    action = action,
                                    selected = libraryUi.selectedGroupKeys.contains(entry.group.id),
                                    selectionActive = libraryUi.selectionActive,
                                    onToggleSelection = {
                                        libraryActionHandler.onAction(
                                            LibraryAction.ToggleGroupSelection(entry.group.id)
                                        )
                                    },
                                    playDescription = libraryUi.labels.play,
                                    density = density
                                )
                            }
                        }
                    }
                }

                LibraryMode.Playlists -> {
                    itemsIndexed(
                        items = groups,
                        key = { _, group -> group.id },
                        span = { _, _ -> GridItemSpan(maxLineSpan) }
                    ) { index, group ->
                        actions.getOrNull(index)?.let { action ->
                            PlaylistGroupRow(group, action, libraryUi.labels, density)
                            if (!density.independentCards) GroupDivider()
                        }
                    }
                    itemsIndexed(
                        items = playlistFolders,
                        key = { _, folder -> "playlist-source:${folder.key}" },
                        span = { _, _ -> GridItemSpan(maxLineSpan) }
                    ) { _, folder ->
                        PlaylistSourceSection(folder, actions, libraryUi.labels, density)
                    }
                }

                LibraryMode.Songs -> Unit
            }

            if (groups.isEmpty() && playlistFolders.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryGroupEmptyState(emptyText, libraryUi, libraryActionHandler)
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupsHeader(
    title: String,
    resultCount: Int,
    state: LibraryUiState,
    onNavigateUp: Runnable,
    actionHandler: LibraryActionHandler
) {
    val p = EchoTheme.colors()
    var menuExpanded by remember { mutableStateOf(false) }
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
            GroupIconButton(EchoIconKind.Back, state.labels.back) { onNavigateUp.run() }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$resultCount ${state.labels.groupCountSuffix}",
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
            Box {
                GroupIconButton(EchoIconKind.More, state.labels.more) { menuExpanded = true }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(if (state.operationInProgress) state.labels.syncingLibrary else state.labels.syncLibrary)
                                Text(
                                    state.labels.syncLibraryDescription,
                                    style = EchoTypography.caption,
                                    color = p.muted
                                )
                            }
                        },
                        leadingIcon = { EchoIcon(EchoIconKind.Library, Modifier.size(18.dp), p.accent) },
                        enabled = !state.operationInProgress,
                        onClick = {
                            menuExpanded = false
                            actionHandler.onAction(LibraryAction.SyncLibrary)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.labels.autoSync, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = state.autoSyncEnabled,
                                    onCheckedChange = null
                                )
                            }
                        },
                        onClick = {
                            actionHandler.onAction(
                                LibraryAction.SetAutoSyncEnabled(!state.autoSyncEnabled)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupSearchField(state: LibraryUiState, actionHandler: LibraryActionHandler) {
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
                    GroupIconButton(EchoIconKind.Remove, state.labels.clearSearch) {
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
private fun LibraryGroupFilterBar(
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
            GroupControlChip(
                label = groupFilterLabel(state, filter),
                selected = state.filter == filter
            ) {
                actionHandler.onAction(LibraryAction.FilterChanged(filter))
            }
        }
        GroupControlChip(
            label = groupSortLabel(state, state.groupSort),
            selected = true,
            onClick = onSortClick
        )
    }
}

@Composable
private fun LibraryGroupSelectionToolbar(
    state: LibraryUiState,
    actionHandler: LibraryActionHandler
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.large)
            .echoGlassLayer(p, EchoShapes.large),
        shape = EchoShapes.large,
        color = p.accentSoft.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.selectedGroupKeys.size.toString() + state.labels.selectedSuffix,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    modifier = Modifier.weight(1f)
                )
                GroupIconButton(EchoIconKind.Remove, state.labels.cancel) {
                    actionHandler.onAction(LibraryAction.ClearSelection)
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                GroupControlChip(state.labels.selectAll) {
                    actionHandler.onAction(LibraryAction.SelectAllVisible)
                }
                GroupControlChip(state.labels.play) {
                    actionHandler.onAction(LibraryAction.PlaySelected)
                }
                GroupControlChip(state.labels.delete, dangerous = true) {
                    actionHandler.onAction(LibraryAction.DeleteSelected)
                }
                GroupControlChip(state.labels.cancel) {
                    actionHandler.onAction(LibraryAction.ClearSelection)
                }
            }
        }
    }
}

@Composable
private fun AlbumGroupCard(
    group: LibraryGroupUiState,
    action: LibraryGroupActions,
    selected: Boolean,
    selectionActive: Boolean,
    onToggleSelection: () -> Unit,
    playDescription: String,
    density: LibraryCardDensityTokens,
    independentCard: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { if (selectionActive) onToggleSelection() else action.onOpen.run() },
                onLongClick = onToggleSelection
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
            ),
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft.copy(alpha = 0.82f) else Color.Transparent
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                LibraryGroupArtwork(group, Modifier.fillMaxSize(), EchoShapes.medium)
                if (action.playEnabled) {
                    Surface(
                        onClick = { action.onPlay.run() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(38.dp)
                            .semantics { contentDescription = playDescription },
                        shape = CircleShape,
                        color = p.accent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            EchoIcon(EchoIconKind.Play, Modifier.size(18.dp), p.onAccent)
                        }
                    }
                }
            }
            Column(
                Modifier.padding(
                    horizontal = density.albumTextHorizontalPadding,
                    vertical = density.albumTextVerticalPadding
                )
            ) {
                Text(
                    group.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    group.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistGroupRow(
    group: LibraryGroupUiState,
    action: LibraryGroupActions,
    selected: Boolean,
    selectionActive: Boolean,
    onToggleSelection: () -> Unit,
    playDescription: String,
    density: LibraryCardDensityTokens
) {
    GroupListRow(
        group = group,
        action = action,
        selected = selected,
        selectionActive = selectionActive,
        onToggleSelection = onToggleSelection,
        leading = {
            LibraryGroupArtwork(
                group = group,
                modifier = Modifier.size(density.artistArtworkSize).clip(CircleShape),
                shape = CircleShape,
                fallbackIcon = EchoIconKind.Artist
            )
        },
        playDescription = playDescription,
        independentCard = density.independentCards,
        density = density
    )
}

@Composable
private fun PlaylistGroupRow(
    group: LibraryGroupUiState,
    action: LibraryGroupActions,
    labels: LibraryUiLabels,
    density: LibraryCardDensityTokens
) {
    GroupListRow(
        group = group,
        action = action,
        selected = false,
        selectionActive = false,
        onToggleSelection = { },
        leading = {
            Surface(
                modifier = Modifier.size(density.playlistArtworkSize),
                shape = EchoShapes.medium,
                color = EchoTheme.colors().accentSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EchoIcon(EchoIconKind.PlaylistAdd, Modifier.size(24.dp), EchoTheme.colors().accent)
                }
            }
        },
        playDescription = labels.play,
        deleteLabel = labels.delete,
        independentCard = density.independentCards,
        density = density
    )
}

@Composable
private fun FolderGroupRow(
    entry: FolderDisplayEntry,
    action: LibraryGroupActions,
    selected: Boolean,
    selectionActive: Boolean,
    onToggleSelection: () -> Unit,
    playDescription: String,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(start = (8 + entry.depth.coerceAtMost(3) * 14).dp)
    ) {
        GroupListRow(
            group = entry.group.copy(
                subtitle = entry.relativePath.ifBlank { entry.group.subtitle }
            ),
            action = action,
            selected = selected,
            selectionActive = selectionActive,
            onToggleSelection = onToggleSelection,
            leading = {
                Surface(
                    modifier = Modifier.size(density.folderArtworkSize),
                    shape = EchoShapes.medium,
                    color = p.accentSoft
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(EchoIconKind.Folder, Modifier.size(23.dp), p.accent)
                    }
                }
            },
            playDescription = playDescription,
            independentCard = density.independentCards,
            density = density
        )
    }
}

@Composable
private fun GroupListRow(
    group: LibraryGroupUiState,
    action: LibraryGroupActions,
    selected: Boolean,
    selectionActive: Boolean,
    onToggleSelection: () -> Unit,
    leading: @Composable () -> Unit,
    playDescription: String,
    deleteLabel: String? = null,
    independentCard: Boolean,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = density.groupRowMinHeight)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { if (selectionActive) onToggleSelection() else action.onOpen.run() },
                onLongClick = if (deleteLabel == null) onToggleSelection else null
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
            ),
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft.copy(alpha = 0.82f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = density.groupRowHorizontalPadding,
                vertical = density.groupRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    group.subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (action.playEnabled) {
                GroupIconButton(EchoIconKind.Play, playDescription) { action.onPlay.run() }
            }
            if (action.onDelete != null && deleteLabel != null) {
                Box {
                    GroupIconButton(EchoIconKind.More, deleteLabel) { menuExpanded = true }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(deleteLabel) },
                            leadingIcon = {
                                EchoIcon(EchoIconKind.Delete, Modifier.size(18.dp), Color(0xFFB3261E))
                            },
                            onClick = {
                                menuExpanded = false
                                action.onDelete.run()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSourceSection(
    folder: LibraryPlaylistFolderUiState,
    actions: List<LibraryGroupActions>,
    labels: LibraryUiLabels,
    density: LibraryCardDensityTokens
) {
    val p = EchoTheme.colors()
    var expanded by rememberSaveable(folder.key) { mutableStateOf(true) }
    Column(
        verticalArrangement = if (density.independentCards) {
            Arrangement.spacedBy(8.dp)
        } else {
            Arrangement.Top
        }
    ) {
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            shape = EchoShapes.medium,
            color = p.surfaceVariant.copy(alpha = 0.48f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EchoIcon(EchoIconKind.Collections, Modifier.size(22.dp), p.accent)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folder.title,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text
                    )
                    Text(folder.subtitle, style = EchoTypography.caption, color = p.muted)
                }
                EchoIcon(
                    EchoIconKind.Next,
                    Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                    p.muted
                )
            }
        }
        if (expanded) {
            folder.entries.forEachIndexed { index, entry ->
                actions.getOrNull(entry.actionIndex)?.let { action ->
                    Box(Modifier.padding(start = 18.dp)) {
                        PlaylistGroupRow(entry.group, action, labels, density)
                    }
                    if (!density.independentCards && index < folder.entries.lastIndex) {
                        GroupDivider(start = 78.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRootHeader(
    section: FolderDisplaySection,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = EchoShapes.medium,
        color = p.surfaceVariant.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoIcon(EchoIconKind.Folder, Modifier.size(21.dp), p.accent)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    section.root,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${section.entries.size}",
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
            EchoIcon(
                EchoIconKind.Next,
                Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                p.muted
            )
        }
    }
}

@Composable
private fun LibraryGroupEmptyState(
    emptyText: String,
    state: LibraryUiState,
    actionHandler: LibraryActionHandler
) {
    val message = when {
        state.query.isNotBlank() -> state.labels.emptyGroupSearch
        state.filter != LibraryFilter.All -> state.labels.emptyGroupFilter
        emptyText.isNotBlank() -> emptyText
        else -> state.labels.emptyLibrary
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GroupEmptyMessage(message)
        when {
            state.query.isNotBlank() -> GroupControlChip(state.labels.clearSearch) {
                actionHandler.onAction(LibraryAction.QueryChanged(""))
            }
            state.filter != LibraryFilter.All -> GroupControlChip(state.labels.resetFilter) {
                actionHandler.onAction(LibraryAction.FilterChanged(LibraryFilter.All))
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupControlChip(state.labels.scanLibrary) {
                    actionHandler.onAction(LibraryAction.ScanLibrary)
                }
                GroupControlChip(state.labels.importFiles) {
                    actionHandler.onAction(LibraryAction.ImportFiles)
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupSortSheet(
    state: LibraryUiState,
    onSort: (LibraryGroupSort) -> Unit
) {
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
        LibraryGroupSort.entries.forEach { sort ->
            Surface(
                onClick = { onSort(sort) },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = groupSortLabel(state, sort)
                },
                shape = EchoShapes.medium,
                color = if (state.groupSort == sort) p.accentSoft.copy(alpha = 0.74f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        groupSortLabel(state, sort),
                        style = EchoTypography.bodyMedium,
                        color = p.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.groupSort == sort) {
                        EchoIcon(EchoIconKind.Check, Modifier.size(18.dp), p.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupControlChip(
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

@Composable
private fun GroupIconButton(
    icon: EchoIconKind,
    description: String,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(40.dp)
            .echoPressScale(interaction)
            .semantics { contentDescription = description },
        shape = EchoShapes.small,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(icon, Modifier.size(19.dp), p.accent)
        }
    }
}

@Composable
private fun GroupEmptyMessage(message: String) {
    val p = EchoTheme.colors()
    Text(
        text = message,
        style = EchoTypography.bodyMedium,
        color = p.muted,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun LibraryGroupArtwork(
    group: LibraryGroupUiState,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    fallbackIcon: EchoIconKind = EchoIconKind.Collections
) {
    val p = EchoTheme.colors()
    if (group.artworkUri != null) {
        AsyncArtwork(
            uri = group.artworkUri,
            title = group.title,
            subtitle = group.subtitle,
            modifier = modifier,
            cornerRadius = if (shape == CircleShape) 80.dp else 14.dp,
            fallbackTextSize = 14.sp,
            targetSize = 160.dp,
            backgroundColor = p.surfaceVariant,
            fallbackResId = R.drawable.ic_stat_echo
        )
        return
    }
    Surface(modifier = modifier, shape = shape, color = p.accentSoft) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(fallbackIcon, Modifier.size(26.dp), p.accent)
        }
    }
}

@Composable
private fun GroupDivider(start: androidx.compose.ui.unit.Dp = 76.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 12.dp),
        color = EchoTheme.colors().border.copy(alpha = 0.16f)
    )
}

private fun groupFilterLabel(state: LibraryUiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.All -> state.labels.all
    LibraryFilter.Favorites -> state.labels.favorites
    LibraryFilter.Local -> state.labels.local
    LibraryFilter.Network -> state.labels.network
}

private fun groupSortLabel(state: LibraryUiState, sort: LibraryGroupSort): String = when (sort) {
    LibraryGroupSort.TitleAscending -> state.labels.sortTitleAscending
    LibraryGroupSort.TitleDescending -> state.labels.sortTitleDescending
    LibraryGroupSort.TrackCountDescending -> state.labels.sortTrackCountDescending
    LibraryGroupSort.TrackCountAscending -> state.labels.sortTrackCountAscending
}

private data class FolderDisplaySection(
    val root: String,
    val entries: List<FolderDisplayEntry>
)

private data class FolderDisplayEntry(
    val group: LibraryGroupUiState,
    val relativePath: String,
    val depth: Int
)

private fun buildFolderSections(groups: List<LibraryGroupUiState>): List<FolderDisplaySection> {
    val entries = groups.map { group ->
        val normalized = group.groupKey.replace('\\', '/').trimEnd('/')
        val root = storageRoot(normalized)
        val relative = normalized.removePrefix(root).trim('/').ifBlank { group.title }
        root to FolderDisplayEntry(
            group = group,
            relativePath = relative,
            depth = relative.count { it == '/' }
        )
    }
    return entries.groupBy({ it.first }, { it.second }).map { (root, groupedEntries) ->
        FolderDisplaySection(root.ifBlank { "/" }, groupedEntries)
    }
}

private fun storageRoot(path: String): String {
    Regex("^/storage/emulated/[^/]+").find(path)?.value?.let { return it }
    Regex("^/storage/[^/]+").find(path)?.value?.let { return it }
    if (path.startsWith("/sdcard")) return "/sdcard"
    if (path.startsWith("/")) {
        val first = path.trimStart('/').substringBefore('/')
        return if (first.isBlank()) "/" else "/$first"
    }
    return path.substringBefore('/').ifBlank { "/" }
}
