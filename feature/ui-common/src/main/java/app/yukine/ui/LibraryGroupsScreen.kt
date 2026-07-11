package app.yukine.ui

import android.net.Uri
import app.yukine.TrackDownloadItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.yukine.feature.uicommon.R

data class LibraryGroupUiState @JvmOverloads constructor(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri? = null
)
data class LibraryGroupActions @JvmOverloads constructor(
    val onOpen: Runnable,
    val onPlay: Runnable,
    val playEnabled: Boolean = true,
    val onLongPress: Runnable? = null
)

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
    libraryControlsEnabled: Boolean = false
) {
    val p = EchoTheme.colors()
    CollapsibleSearchHeader(
        enabled = !libraryControlsEnabled,
        header = { LibrarySearchRow(onSearch, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            if (!libraryControlsEnabled) {
                item(key = "title") {
                    EchoPageTitle(title)
                }
            }
            if (libraryControlsEnabled) {
                item(key = "libraryGroupControls") {
                    LibraryGroupControls(libraryUi, libraryActionHandler)
                }
            }
            if (modeActions.isNotEmpty()) {
                item(key = "modes") {
                    GroupModeSelector(modeActions)
                }
            }
            itemsIndexed(
                items = groups,
                key = { index, group -> "group:${group.id}:$index" }
            ) { i, group ->
                actions.getOrNull(i)?.let { action ->
                    val selectableGroup = libraryControlsEnabled && libraryUi.mode != LibraryMode.Playlists
                    LibraryGroupRow(
                        group = group,
                        actions = action,
                        selected = libraryUi.selectedGroupKeys.contains(group.id),
                        onClick = {
                            if (selectableGroup && libraryUi.selectionActive) {
                                libraryActionHandler.onAction(LibraryAction.ToggleGroupSelection(group.id))
                            } else {
                                action.onOpen.run()
                            }
                        },
                        onLongPress = if (selectableGroup) {
                            { libraryActionHandler.onAction(LibraryAction.ToggleGroupSelection(group.id)) }
                        } else action.onLongPress?.let { callback -> { callback.run() } },
                        playDescription = libraryUi.labels.play
                    )
                }
            }
            if (groups.isEmpty() && emptyText.isNotBlank()) {
                item(key = "empty") {
                    GroupMessage(emptyText)
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchRow(
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
private fun LibraryGroupControls(state: LibraryUiState, actionHandler: LibraryActionHandler) {
    val p = EchoTheme.colors()
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
        Box {
            Surface(
                onClick = { filterExpanded = true },
                shape = EchoShapes.small,
                color = p.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Text(
                    state.labels.filter + ": " + groupFilterLabel(state, state.filter),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = EchoTypography.caption,
                    color = p.text
                )
            }
            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                LibraryFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(groupFilterLabel(state, filter)) },
                        onClick = {
                            filterExpanded = false
                            actionHandler.onAction(LibraryAction.FilterChanged(filter))
                        }
                    )
                }
            }
        }
        if (state.selectedGroupKeys.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoFloatingLayer(p, EchoShapes.medium),
                shape = EchoShapes.medium,
                color = p.accentSoft.copy(alpha = 0.72f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.selectedGroupKeys.size.toString() + state.labels.selectedSuffix,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroupActionChip(state.labels.selectAll) {
                            actionHandler.onAction(LibraryAction.SelectAllVisible)
                        }
                        GroupActionChip(state.labels.play) {
                            actionHandler.onAction(LibraryAction.PlaySelected)
                        }
                        GroupActionChip(state.labels.delete, dangerous = true) {
                            actionHandler.onAction(LibraryAction.DeleteSelected)
                        }
                        GroupActionChip(state.labels.cancel) {
                            actionHandler.onAction(LibraryAction.ClearSelection)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupActionChip(label: String, dangerous: Boolean = false, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    val danger = Color(0xFFB3261E)
    Surface(
        onClick = onClick,
        shape = EchoShapes.small,
        color = if (dangerous) danger.copy(alpha = 0.16f) else p.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = EchoTypography.caption,
            color = if (dangerous) danger else p.text
        )
    }
}

private fun groupFilterLabel(state: LibraryUiState, filter: LibraryFilter): String = when (filter) {
    LibraryFilter.All -> state.labels.all
    LibraryFilter.Favorites -> state.labels.favorites
    LibraryFilter.Local -> state.labels.local
    LibraryFilter.Network -> state.labels.network
}

@Composable
private fun GroupModeSelector(modes: List<TrackListModeAction>) {
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
                        iconForGroupMode(mode.mode),
                        Modifier.size(if (mode.selected) 18.dp else 20.dp),
                        if (mode.selected) p.accent else p.muted
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

private fun iconForGroupMode(mode: String): EchoIconKind = when (mode) {
    "albums" -> EchoIconKind.Collections
    "artists" -> EchoIconKind.Artist
    "folders" -> EchoIconKind.Folder
    "playlists" -> EchoIconKind.PlaylistAdd
    else -> EchoIconKind.Library
}

@Composable
private fun GroupMessage(message: String) {
    EchoEmptyCard(message)
}

@Composable
private fun LibraryGroupRow(
    group: LibraryGroupUiState,
    actions: LibraryGroupActions,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    playDescription: String = "\u64ad\u653e"
) {
    val p = EchoTheme.colors()
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onClick?.invoke() ?: actions.onOpen.run() },
                onLongClick = onLongPress ?: actions.onLongPress?.let { action -> { action.run() } }
            )
            .echoPressScale(interaction)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibraryGroupArtwork(group, Modifier.size(44.dp))
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
            if (actions.playEnabled) {
                Surface(
                    onClick = { actions.onPlay.run() },
                    modifier = Modifier
                        .size(40.dp)
                        .echoGlassLayer(p, EchoShapes.small)
                        .semantics { contentDescription = playDescription },
                    shape = EchoShapes.small,
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(EchoIconKind.Play, Modifier.size(18.dp), p.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGroupArtwork(group: LibraryGroupUiState, modifier: Modifier) {
    val p = EchoTheme.colors()
    if (group.artworkUri != null) {
        AsyncArtwork(
            uri = group.artworkUri,
            title = group.title,
            subtitle = group.subtitle,
            modifier = modifier,
            cornerRadius = 8.dp,
            fallbackTextSize = 14.sp,
            targetSize = 56.dp,
            backgroundColor = p.surfaceVariant,
            fallbackResId = R.drawable.ic_stat_echo
        )
        return
    }
    Surface(
        modifier = modifier,
        shape = EchoShapes.small,
        color = p.accentSoft
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(EchoIconKind.Collections, Modifier.size(22.dp), p.accent)
        }
    }
}
