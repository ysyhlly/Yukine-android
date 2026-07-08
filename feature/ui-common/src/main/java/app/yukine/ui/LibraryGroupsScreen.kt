package app.yukine.ui

import android.net.Uri
import app.yukine.TrackDownloadItem
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val p = EchoTheme.colors()
    CollapsibleSearchHeader(
        header = { LibrarySearchRow(onSearch, activeDownload, playbackQuality, audioMotion) }
    ) { contentModifier, _ ->
        LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            item(key = "title") {
                EchoPageTitle(title)
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
                    LibraryGroupRow(group, action)
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
private fun GroupModeSelector(modes: List<TrackListModeAction>) {
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
                        iconForGroupMode(mode.mode),
                        Modifier.size(if (mode.selected) 18.dp else 20.dp),
                        if (mode.selected) p.accent else p.muted
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
private fun LibraryGroupRow(group: LibraryGroupUiState, actions: LibraryGroupActions) {
    val p = EchoTheme.colors()
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { actions.onOpen.run() },
                onLongClick = actions.onLongPress?.let { action -> { action.run() } }
            )
            .echoPressScale(interaction)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
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
                        .semantics { contentDescription = "播放" },
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
