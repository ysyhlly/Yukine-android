package app.yukine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yukine.AppLanguage
import app.yukine.RecordingSplitUiState
import app.yukine.data.RecordingSplitDestination
import app.yukine.data.RecordingSplitReference
import app.yukine.identity.TrackSourceMapping

@Composable
internal fun RecordingSplitDialog(
    languageMode: String,
    state: RecordingSplitUiState,
    onToggleSource: (Long) -> Unit,
    onPreview: () -> Unit,
    onDestinationChange: (RecordingSplitReference, RecordingSplitDestination) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = state.preview
    AlertDialog(
        onDismissRequest = { if (!state.committing) onDismiss() },
        title = { Text(splitText(languageMode, "recording.match.split.title")) },
        text = {
            if (preview == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(splitText(languageMode, "recording.match.split.choose.sources"))
                    if (state.loadingSources) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.availableSources, key = TrackSourceMapping::sourceId) { source ->
                                SplitSourceRow(
                                    languageMode = languageMode,
                                    source = source,
                                    selected = source.sourceId in state.selectedSourceIds,
                                    enabled = !state.previewing,
                                    onToggle = { onToggleSource(source.sourceId) }
                                )
                            }
                        }
                    }
                    Text(
                        splitText(languageMode, "recording.match.split.leave.one"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.errorMessage.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onPreview,
                        enabled = !state.loadingSources && !state.previewing &&
                            state.selectedSourceIds.isNotEmpty() &&
                            state.selectedSourceIds.size < state.availableSources.size,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.previewing) CircularProgressIndicator() else {
                            Text(splitText(languageMode, "recording.match.split.preview"))
                        }
                    }
                }
            } else {
                Column(
                    Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(splitText(languageMode, "recording.match.split.new.identity"), fontWeight = FontWeight.Bold)
                    preview.selectedSources.forEach { source ->
                        SplitSourceSummary(languageMode, source)
                    }
                    SplitInfoLine(
                        splitText(languageMode, "recording.match.split.remaining.sources"),
                        preview.remainingSourceCount.toString()
                    )
                    Text(
                        splitText(languageMode, "recording.match.split.strong.ids.cleared"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SplitDestinationRow(
                        languageMode,
                        RecordingSplitReference.FAVORITE,
                        state.options.favoriteDestination,
                        preview.impact.favoriteCount,
                        onDestinationChange
                    )
                    SplitDestinationRow(
                        languageMode,
                        RecordingSplitReference.PLAYLISTS,
                        state.options.playlistDestination,
                        preview.impact.playlistItemCount,
                        onDestinationChange
                    )
                    SplitDestinationRow(
                        languageMode,
                        RecordingSplitReference.QUEUE,
                        state.options.queueDestination,
                        preview.impact.queueItemCount,
                        onDestinationChange
                    )
                    Text(
                        splitText(languageMode, "recording.match.split.history.stays"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    state.errorMessage.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null) {
                Button(onClick = onConfirm, enabled = !state.committing) {
                    Text(splitText(languageMode, "recording.match.split.confirm"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.committing) {
                Text(splitText(languageMode, "cancel"))
            }
        }
    )
}

@Composable
private fun SplitSourceRow(
    languageMode: String,
    source: TrackSourceMapping,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(
                    RecordingMatchPresentation.providerLabel(languageMode, source.provider),
                    fontWeight = FontWeight.SemiBold
                )
                Text(source.title.ifBlank { source.providerTrackId })
                source.artist.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!source.playable) {
                    Text(
                        splitText(languageMode, "recording.match.not.playable"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitSourceSummary(languageMode: String, source: TrackSourceMapping) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(RecordingMatchPresentation.providerLabel(languageMode, source.provider), fontWeight = FontWeight.Bold)
            SplitInfoLine(splitText(languageMode, "platform.id"), source.providerTrackId)
            SplitInfoLine(splitText(languageMode, "songs"), source.title)
            SplitInfoLine(splitText(languageMode, "artists"), source.artist)
        }
    }
}

@Composable
private fun SplitDestinationRow(
    languageMode: String,
    reference: RecordingSplitReference,
    selected: RecordingSplitDestination,
    affectedCount: Int,
    onDestinationChange: (RecordingSplitReference, RecordingSplitDestination) -> Unit
) {
    val labelKey = when (reference) {
        RecordingSplitReference.FAVORITE -> "recording.match.merge.favorites"
        RecordingSplitReference.PLAYLISTS -> "recording.match.merge.playlists"
        RecordingSplitReference.QUEUE -> "recording.match.merge.queue"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("${splitText(languageMode, labelKey)} ($affectedCount)", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == RecordingSplitDestination.ORIGINAL,
                    onClick = { onDestinationChange(reference, RecordingSplitDestination.ORIGINAL) }
                )
                Text(splitText(languageMode, "recording.match.split.destination.original"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == RecordingSplitDestination.NEW_RECORDING,
                    onClick = { onDestinationChange(reference, RecordingSplitDestination.NEW_RECORDING) }
                )
                Text(splitText(languageMode, "recording.match.split.destination.new"))
            }
        }
    }
}

@Composable
private fun SplitInfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f))
    }
}

private fun splitText(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
