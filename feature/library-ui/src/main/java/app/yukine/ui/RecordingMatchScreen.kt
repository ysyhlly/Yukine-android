package app.yukine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yukine.AppLanguage
import app.yukine.RecordingMatchDestinationStateProvider
import app.yukine.RecordingMatchUiState
import app.yukine.RecordingMergeUiState
import app.yukine.data.RecordingMatchSnapshot
import app.yukine.data.RecordingMatchVariant
import app.yukine.data.IdentityOperation
import app.yukine.data.RecordingMergeImpact
import app.yukine.data.RecordingMergeSearchResult
import app.yukine.data.RecordingMergeSummary
import app.yukine.data.RecordingMergeWarning
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityMatchStatus
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.RecordingVariantType
import app.yukine.identity.TrackSourceMapping
import app.yukine.model.Track
import org.json.JSONObject
import java.util.Locale
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingMatchScreen(
    state: RecordingMatchUiState,
    provider: RecordingMatchDestinationStateProvider,
    modifier: Modifier = Modifier
) {
    val languageMode = state.languageMode
    val snapshot = state.snapshot
    var alternateCandidate by remember(state.localTrackId) { mutableStateOf<IdentityCandidate?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = echoPagePadding(includeBottomChrome = true),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item {
            RecordingMatchHeader(
                languageMode = languageMode,
                title = snapshot?.track?.title.orEmpty(),
                busy = state.loading || state.mutating,
                onBack = provider::close,
                onRefresh = provider::requestCandidateRefresh,
                onMerge = provider::openMerge,
                onSplit = provider::openSplit
            )
        }
        if (state.loading && snapshot == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
        }
        statusText(state, languageMode)?.let { message ->
            item {
                MatchCard {
                    Text(message, Modifier.padding(14.dp), color = EchoTheme.colors().muted)
                }
            }
        }
        snapshot?.let { data ->
            if (data.recentOperations.isNotEmpty()) {
                item {
                    IdentityOperationSection(
                        languageMode = languageMode,
                        operations = data.recentOperations,
                        busy = state.mutating,
                        onUndo = provider::undoIdentityOperation
                    )
                }
            }
            item { IdentitySection(languageMode, data) }
            item { IdentifierSection(languageMode, data.identifiers) }
            item { ActiveSourceSection(languageMode, data.activeSource) }
            item { SectionTitle(text(languageMode, "recording.match.sources") + " ${data.sources.size}/${data.sourceTotal}") }
            items(data.sources, key = TrackSourceMapping::sourceId) { source ->
                SourceCard(
                    languageMode = languageMode,
                    source = source,
                    active = source.sourceId == data.activeSource?.sourceId,
                    busy = state.mutating,
                    onVerify = { provider.verifySource(source.sourceId) },
                    onPreferred = { provider.setPreferredSource(source.sourceId) },
                    onRemove = { provider.removeUnavailableSource(source.sourceId) },
                    onSearch = provider::requestCandidateRefresh
                )
            }
            item { SectionTitle(text(languageMode, "recording.match.versions")) }
            if (data.variants.isEmpty() && data.alternateVersions.isEmpty()) {
                item { EmptySection(languageMode) }
            } else {
                items(data.variants, key = { "variant:${it.groupId}:${it.variantType}" }) { variant ->
                    VariantCard(languageMode, variant)
                }
                items(data.alternateVersions, key = { "alternate:${it.candidateId}" }) { candidate ->
                    AlternateCandidateCard(languageMode, candidate)
                }
            }
            item {
                SectionTitle(
                    text(languageMode, "recording.match.candidates") +
                        " ${data.pendingCandidates.size}/${data.candidateTotal}"
                )
            }
            if (data.candidateTotal > 0) {
                item {
                    OutlinedButton(
                        onClick = provider::rejectObviousMismatches,
                        enabled = !state.mutating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text(languageMode, "recording.match.batch.reject"))
                    }
                }
            }
            if (data.pendingCandidates.isEmpty()) {
                item { EmptySection(languageMode) }
            } else {
                items(data.pendingCandidates, key = IdentityCandidate::candidateId) { candidate ->
                    CandidateCard(
                        languageMode = languageMode,
                        candidate = candidate,
                        busy = state.mutating,
                        onConfirm = { provider.confirmCandidate(candidate.candidateId) },
                        onReject = { provider.rejectCandidate(candidate.candidateId) },
                        onAlternate = { alternateCandidate = candidate }
                    )
                }
            }
            if (data.sources.size < data.sourceTotal || data.pendingCandidates.size < data.candidateTotal) {
                item {
                    Button(
                        onClick = provider::loadMore,
                        enabled = !state.loadingMore && !state.mutating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text(languageMode, "load.more"))
                        }
                    }
                }
            }
        }
    }
    alternateCandidate?.let { candidate ->
        AlternateVersionDialog(
            languageMode = languageMode,
            candidate = candidate,
            onDismiss = { alternateCandidate = null },
            onSelect = { variant ->
                alternateCandidate = null
                provider.markAsAlternateVersion(candidate.candidateId, variant.name)
            }
        )
    }
    if (state.merge.visible) {
        RecordingMergeDialog(
            languageMode = languageMode,
            state = state.merge,
            onQueryChange = provider::updateMergeQuery,
            onSearch = provider::searchMergeCandidates,
            onSelect = provider::previewMerge,
            onConfirm = provider::confirmMerge,
            onDismiss = provider::closeMerge
        )
    }
    if (state.split.visible) {
        RecordingSplitDialog(
            languageMode = languageMode,
            state = state.split,
            onToggleSource = provider::toggleSplitSource,
            onPreview = provider::previewSplit,
            onDestinationChange = provider::updateSplitDestination,
            onConfirm = provider::confirmSplit,
            onDismiss = provider::closeSplit
        )
    }
}

@Composable
private fun IdentityOperationSection(
    languageMode: String,
    operations: List<IdentityOperation>,
    busy: Boolean,
    onUndo: (Long) -> Unit
) {
    MatchCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text(languageMode, "recording.match.operation.recent"), fontWeight = FontWeight.Bold)
            operations.take(5).forEach { operation ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(operationLabel(languageMode, operation.operationType))
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(operation.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        operation.revertedAt != null -> Text(
                            text(languageMode, "recording.match.operation.reverted"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        operation.undoable -> TextButton(
                            onClick = { onUndo(operation.id) },
                            enabled = !busy
                        ) { Text(text(languageMode, "recording.match.operation.undo")) }
                    }
                }
            }
            Text(
                text(languageMode, "recording.match.operation.private"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun operationLabel(languageMode: String, operationType: String): String = text(
    languageMode,
    when (operationType) {
        "CONFIRM_CANDIDATE" -> "recording.match.operation.confirm"
        "REJECT_CANDIDATE" -> "recording.match.operation.reject"
        "MERGE_RECORDINGS" -> "recording.match.operation.merge"
        "SPLIT_RECORDING" -> "recording.match.operation.split"
        "SET_ACTIVE_SOURCE" -> "recording.match.operation.active.source"
        else -> "recording.match.operation.unknown"
    }
)

@Composable
private fun RecordingMatchHeader(
    languageMode: String,
    title: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EchoPageTitle(
            title = text(languageMode, "recording.match.title"),
            subtitle = title.takeIf(String::isNotBlank),
            backLabel = text(languageMode, "back"),
            onBack = Runnable { onBack() }
        )
        MatchCard {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(onClick = onMerge, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(text(languageMode, "recording.match.merge.action"))
                    }
                    TextButton(onClick = onSplit, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(text(languageMode, "recording.match.split.action"))
                    }
                }
                OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(text(languageMode, "recording.match.refresh.background"))
                }
            }
        }
    }
}

@Composable
private fun RecordingMergeDialog(
    languageMode: String,
    state: RecordingMergeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = state.preview
    var acknowledged by remember(preview?.source?.recording?.recordingId) { mutableStateOf(false) }
    val nonBlockingWarnings = preview?.warnings?.any { !it.blocking } == true
    AlertDialog(
        onDismissRequest = { if (!state.committing) onDismiss() },
        title = { Text(text(languageMode, "recording.match.merge.title")) },
        text = {
            if (preview == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text(languageMode, "recording.match.merge.keep.current"))
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text(text(languageMode, "recording.match.merge.search.hint")) },
                        singleLine = true,
                        enabled = !state.searching,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = onSearch,
                        enabled = !state.searching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.searching) {
                            CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text(languageMode, "search"))
                        }
                    }
                    state.errorMessage.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (!state.searching && state.results.isEmpty()) {
                        EmptyText(languageMode)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.results, key = RecordingMergeSearchResult::recordingId) { result ->
                                MergeSearchResultCard(languageMode, result) { onSelect(result.recordingId) }
                            }
                        }
                    }
                    if (state.previewing) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text(languageMode, "recording.match.merge.target"), fontWeight = FontWeight.Bold)
                    MergeSummaryCard(languageMode, preview.target)
                    Text(text(languageMode, "recording.match.merge.source"), fontWeight = FontWeight.Bold)
                    MergeSummaryCard(languageMode, preview.source)
                    MergeImpactCard(languageMode, preview.impact)
                    if (preview.warnings.isNotEmpty()) {
                        Text(
                            text(languageMode, "recording.match.merge.warnings"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        preview.warnings.forEach { warning ->
                            MergeWarningLine(languageMode, warning)
                        }
                    }
                    if (nonBlockingWarnings && !preview.blocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                            Text(text(languageMode, "recording.match.merge.acknowledge"))
                        }
                    }
                    if (preview.blocked) {
                        Text(
                            text(languageMode, "recording.match.merge.blocked"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    state.errorMessage.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null) {
                Button(
                    onClick = onConfirm,
                    enabled = !state.committing && !preview.blocked && (!nonBlockingWarnings || acknowledged)
                ) {
                    Text(text(languageMode, "recording.match.merge.confirm"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.committing) {
                Text(text(languageMode, "cancel"))
            }
        }
    )
}

@Composable
private fun MergeSearchResultCard(
    languageMode: String,
    result: RecordingMergeSearchResult,
    onSelect: () -> Unit
) {
    MatchCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(result.title.ifBlank { result.canonicalId }, fontWeight = FontWeight.SemiBold)
            result.primaryArtistDisplay.takeIf(String::isNotBlank)?.let { Text(it) }
            if (result.durationMs > 0L) InfoLine(text(languageMode, "duration"), Track.formatDuration(result.durationMs))
            InfoLine("recording_id", result.recordingId.toString())
            InfoLine(text(languageMode, "recording.match.sources"), result.sourceCount.toString())
            if (result.variantTypes.isNotEmpty()) {
                InfoLine(
                    text(languageMode, "recording.match.versions"),
                    result.variantTypes.joinToString { RecordingMatchPresentation.variantLabel(languageMode, it) }
                )
            }
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text(text(languageMode, "recording.match.merge.preview"))
            }
        }
    }
}

@Composable
private fun MergeSummaryCard(languageMode: String, summary: RecordingMergeSummary) {
    MatchCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(summary.recording.title, fontWeight = FontWeight.SemiBold)
            InfoLine(text(languageMode, "artists"), summary.recording.primaryArtistDisplay)
            InfoLine("recording_id", summary.recording.recordingId.toString())
            InfoLine("canonical UUID", summary.recording.canonicalId)
            InfoLine(text(languageMode, "recording.match.sources"), summary.sources.size.toString())
            if (summary.identifiers.isNotEmpty()) {
                InfoLine(
                    text(languageMode, "recording.match.identifiers"),
                    summary.identifiers.joinToString { "${it.identifierType}:${it.identifierValue}" }
                )
            }
            if (summary.variants.isNotEmpty()) {
                InfoLine(
                    text(languageMode, "recording.match.versions"),
                    summary.variants.joinToString { RecordingMatchPresentation.variantLabel(languageMode, it.variantType) }
                )
            }
        }
    }
}

@Composable
private fun MergeImpactCard(languageMode: String, impact: RecordingMergeImpact) {
    MatchCard {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text(languageMode, "recording.match.merge.impact"), fontWeight = FontWeight.Bold)
            InfoLine(text(languageMode, "recording.match.merge.favorites"), impact.favoriteCount.toString())
            InfoLine(text(languageMode, "recording.match.merge.playlists"), impact.playlistItemCount.toString())
            InfoLine(text(languageMode, "recording.match.merge.history"), impact.playHistoryCount.toString())
            InfoLine(text(languageMode, "recording.match.merge.events"), impact.playEventCount.toString())
            InfoLine(text(languageMode, "recording.match.merge.queue"), impact.queueItemCount.toString())
            InfoLine(text(languageMode, "recording.match.sources"), impact.sourceCount.toString())
        }
    }
}

@Composable
private fun MergeWarningLine(languageMode: String, warning: RecordingMergeWarning) {
    val key = "recording.match.merge.warning.${warning.code.name.lowercase(Locale.ROOT)}"
    Text(
        (if (warning.blocking) "⛔ " else "⚠ ") + text(languageMode, key),
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun IdentitySection(languageMode: String, snapshot: RecordingMatchSnapshot) {
    SectionCard(languageMode, "recording.match.identity") {
        val track = snapshot.track
        InfoLine(text(languageMode, "songs"), track.title)
        InfoLine(text(languageMode, "artists"), track.artist)
        InfoLine(text(languageMode, "albums"), track.album)
        InfoLine(text(languageMode, "duration"), Track.formatDuration(track.durationMs))
        InfoLine("recording_id", snapshot.recording.recordingId.toString())
        InfoLine("canonical UUID", snapshot.recording.canonicalId)
    }
}

@Composable
private fun IdentifierSection(languageMode: String, identifiers: List<RecordingIdentifier>) {
    SectionCard(languageMode, "recording.match.identifiers") {
        if (identifiers.isEmpty()) {
            Text(text(languageMode, "recording.match.none"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            identifiers.forEach { identifier ->
                InfoLine(
                    identifier.identifierType + identifier.namespace.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                    identifier.identifierValue
                )
            }
        }
    }
}

@Composable
private fun ActiveSourceSection(languageMode: String, source: TrackSourceMapping?) {
    SectionCard(languageMode, "recording.match.active.source") {
        if (source == null) EmptyText(languageMode) else SourceContent(languageMode, source, active = true)
    }
}

@Composable
private fun SourceCard(
    languageMode: String,
    source: TrackSourceMapping,
    active: Boolean,
    busy: Boolean,
    onVerify: () -> Unit,
    onPreferred: () -> Unit,
    onRemove: () -> Unit,
    onSearch: () -> Unit
) {
    MatchCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SourceContent(languageMode, source, active)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onVerify, enabled = !busy) {
                    Text(text(languageMode, "recording.match.source.verify"))
                }
                TextButton(
                    onClick = onPreferred,
                    enabled = !busy && !active && source.isPreferredEligible()
                ) { Text(text(languageMode, "recording.match.source.set.preferred")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRemove, enabled = !busy && !source.playable) {
                    Text(text(languageMode, "recording.match.source.remove"))
                }
                TextButton(onClick = onSearch, enabled = !busy) {
                    Text(text(languageMode, "recording.match.source.search"))
                }
            }
        }
    }
}

@Composable
private fun SourceContent(languageMode: String, source: TrackSourceMapping, active: Boolean) {
    Text(
        (if (active) "★ " else "") + RecordingMatchPresentation.providerLabel(languageMode, source.provider),
        fontWeight = FontWeight.SemiBold
    )
    InfoLine(text(languageMode, "platform.id"), source.providerTrackId)
    source.title.takeIf(String::isNotBlank)?.let { InfoLine(text(languageMode, "songs"), it) }
    source.artist.takeIf(String::isNotBlank)?.let { InfoLine(text(languageMode, "artists"), it) }
    source.quality.takeIf(String::isNotBlank)?.let { InfoLine(text(languageMode, "quality"), it) }
    source.codec.takeIf(String::isNotBlank)?.let { InfoLine(text(languageMode, "recording.match.source.codec"), it) }
    source.bitrateKbps.takeIf { it > 0 }?.let {
        InfoLine(text(languageMode, "recording.match.source.bitrate"), "$it kbps")
    }
    InfoLine(
        text(languageMode, "recording.match.playable"),
        if (source.playable) text(languageMode, "yes") else text(languageMode, "no")
    )
    if (source.matchStatus != IdentityMatchStatus.CONFIRMED) {
        InfoLine(text(languageMode, "status"), source.matchStatus.name)
    }
    source.lastSuccessfulAt.takeIf { it > 0L }?.let {
        InfoLine(text(languageMode, "recording.match.source.last.success"), formatTime(it))
    }
    source.lastFailureAt.takeIf { it > 0L }?.let {
        InfoLine(text(languageMode, "recording.match.source.last.failure"), formatTime(it))
    }
    source.failureReason.takeIf(String::isNotBlank)?.let {
        InfoLine(text(languageMode, "recording.match.source.failure.reason"), it)
    }
    if (source.failureCount > 0) {
        InfoLine(text(languageMode, "recording.match.source.failure.count"), source.failureCount.toString())
    }
}

private fun formatTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    .format(Date(value))

private fun TrackSourceMapping.isPreferredEligible(): Boolean =
    playable &&
        matchStatus == IdentityMatchStatus.CONFIRMED &&
        (provider.lowercase(Locale.ROOT) in setOf("local", "document", "webdav") ||
            lastVerifiedAt > 0L || lastSuccessfulAt > 0L)

@Composable
private fun CandidateCard(
    languageMode: String,
    candidate: IdentityCandidate,
    busy: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onAlternate: () -> Unit
) {
    var detailsVisible by remember(candidate.candidateId) { mutableStateOf(false) }
    val hardConflict = RecordingMatchPresentation.hasHardConflict(candidate)
    MatchCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(RecordingMatchPresentation.candidateLabel(languageMode, candidate), fontWeight = FontWeight.SemiBold)
            InfoLine(text(languageMode, "recording.match.score"), String.format(Locale.ROOT, "%.3f", candidate.score))
            if (hardConflict) {
                Text(text(languageMode, "recording.match.hard.conflict"), color = MaterialTheme.colorScheme.error)
            }
            if (detailsVisible) {
                Text(
                    RecordingMatchPresentation.candidateDetails(languageMode, candidate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(text(languageMode, "recording.match.details"))
                }
                TextButton(onClick = onAlternate, enabled = !busy) {
                    Text(text(languageMode, "recording.match.mark.alternate"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = !busy && !hardConflict, modifier = Modifier.weight(1f)) {
                    Text(text(languageMode, "recording.match.confirm"))
                }
                OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(text(languageMode, "recording.match.reject"))
                }
            }
        }
    }
}

@Composable
private fun AlternateCandidateCard(languageMode: String, candidate: IdentityCandidate) {
    MatchCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(RecordingMatchPresentation.variantLabel(languageMode, candidate.variantType), fontWeight = FontWeight.SemiBold)
            Text(RecordingMatchPresentation.candidateLabel(languageMode, candidate))
            Text(text(languageMode, "recording.match.alternate.not.source"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VariantCard(languageMode: String, variant: RecordingMatchVariant) {
    MatchCard {
        Column(Modifier.padding(14.dp)) {
            Text(RecordingMatchPresentation.variantLabel(languageMode, variant.variantType), fontWeight = FontWeight.SemiBold)
            if (variant.displayName.isNotBlank()) Text(variant.displayName)
            Text("${text(languageMode, "recording.match.score")}: ${String.format(Locale.ROOT, "%.3f", variant.confidence)}")
        }
    }
}

@Composable
private fun AlternateVersionDialog(
    languageMode: String,
    candidate: IdentityCandidate,
    onDismiss: () -> Unit,
    onSelect: (RecordingVariantType) -> Unit
) {
    val options = listOf(
        RecordingVariantType.LIVE,
        RecordingVariantType.REMIX,
        RecordingVariantType.COVER,
        RecordingVariantType.ACOUSTIC,
        RecordingVariantType.INSTRUMENTAL,
        RecordingVariantType.KARAOKE,
        RecordingVariantType.DEMO,
        RecordingVariantType.EDIT
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(languageMode, "recording.match.mark.alternate")) },
        text = {
            Column {
                Text(candidate.title.ifBlank { candidate.providerItemId }, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                options.forEach { variant ->
                    TextButton(onClick = { onSelect(variant) }, modifier = Modifier.fillMaxWidth()) {
                        Text(RecordingMatchPresentation.variantLabel(languageMode, variant.name))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(text(languageMode, "cancel")) } }
    )
}

@Composable
private fun SectionCard(languageMode: String, titleKey: String, content: @Composable ColumnScope.() -> Unit) {
    MatchCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(text(languageMode, titleKey), fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    EchoSectionTitle(title)
}

@Composable
private fun EmptySection(languageMode: String) {
    EchoEmptyCard(text(languageMode, "recording.match.none"))
}

@Composable
private fun EmptyText(languageMode: String) {
    Text(text(languageMode, "recording.match.none"), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f))
    }
}

@Composable
private fun MatchCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(0.dp)
    ) {
        content()
    }
}

private fun statusText(state: RecordingMatchUiState, languageMode: String): String? = when {
    state.errorMessage == "recording.match.not.found" -> text(languageMode, state.errorMessage)
    state.errorMessage.isNotBlank() -> text(languageMode, "recording.match.update.failed") + ": " + state.errorMessage
    state.messageKey.isNotBlank() -> text(languageMode, state.messageKey)
    else -> null
}

internal object RecordingMatchPresentation {
    fun hasHardConflict(candidate: IdentityCandidate): Boolean = runCatching {
        JSONObject(candidate.evidenceJson).optBoolean("hardConflict", false)
    }.getOrDefault(false)

    fun candidateLabel(languageMode: String, candidate: IdentityCandidate): String = buildString {
        append((candidate.score.coerceIn(0.0, 1.0) * 100).toInt()).append("% · ")
        append(providerLabel(languageMode, candidate.provider)).append(" · ")
        append(candidate.title.ifBlank { candidate.providerItemId })
        if (candidate.artist.isNotBlank()) append(" — ").append(candidate.artist)
    }

    fun candidateDetails(languageMode: String, candidate: IdentityCandidate): String = buildString {
        append(text(languageMode, "platform.id")).append(": ").append(candidate.providerItemId)
        if (candidate.album.isNotBlank()) append("\n").append(text(languageMode, "albums")).append(": ").append(candidate.album)
        if (candidate.durationMs > 0) append("\n").append(text(languageMode, "duration")).append(": ").append(Track.formatDuration(candidate.durationMs))
        if (candidate.isrc.isNotBlank()) append("\nISRC: ").append(candidate.isrc)
        append("\n").append(text(languageMode, "recording.match.variant")).append(": ").append(variantLabel(languageMode, candidate.variantType))
        evidenceSummary(candidate.evidenceJson).takeIf(String::isNotBlank)?.let { append("\n\n").append(it) }
    }

    fun providerLabel(languageMode: String, provider: String): String {
        val english = languageMode == "en"
        return when (provider.lowercase(Locale.ROOT)) {
            "local" -> if (english) "Local" else "本地"
            "webdav" -> "WebDAV"
            "netease" -> if (english) "NetEase Cloud Music" else "网易云音乐"
            "qqmusic" -> if (english) "QQ Music" else "QQ音乐"
            "luoxue" -> if (english) "LX source" else "LX 音源"
            else -> provider
        }
    }

    fun variantLabel(languageMode: String, variantType: String): String {
        val key = "recording.match.variant.${variantType.lowercase(Locale.ROOT)}"
        val localized = text(languageMode, key)
        return if (localized == key) variantType else localized
    }

    private fun evidenceSummary(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val json = JSONObject(raw)
            buildList {
                json.optJSONArray("reasons")?.let { reasons ->
                    for (index in 0 until reasons.length()) {
                        reasons.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                if (json.has("hardConflict")) add("hardConflict: ${json.optBoolean("hardConflict")}")
                if (json.has("margin")) add("margin: ${json.optDouble("margin")}")
                if (json.has("providerScore")) add("providerScore: ${json.optDouble("providerScore")}")
                val standardKeys = setOf("reasons", "hardConflict", "margin", "providerScore")
                json.keys().asSequence().filterNot(standardKeys::contains).sorted().forEach { key ->
                    val sensitive = listOf(
                        "authorization", "body", "cookie", "header", "password",
                        "response", "secret", "token", "url"
                    ).any { marker -> key.contains(marker, ignoreCase = true) }
                    val value = if (sensitive) "[redacted]" else json.opt(key)?.toString().orEmpty()
                    add("$key: $value")
                }
            }.joinToString("\n")
        }.getOrDefault(raw)
    }
}

private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
