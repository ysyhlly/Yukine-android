package app.yukine.together

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yukine.ui.LocalEchoPageBottomChromeInset

data class TogetherLabels(
    val title: String,
    val createRoom: String,
    val joinRoom: String,
    val roomCode: String,
    val pasteRoomCode: String,
    val settings: String,
    val back: String,
    val confirmCreate: String,
    val emptyQueue: String,
    val addLocalAudio: String,
    val previewFiles: String,
    val confirmJoin: String,
    val matchLocal: String,
    val matchedLocal: String,
    val downloadRequired: String,
    val storageSpace: String,
    val notEnoughSpace: String,
    val remove: String,
    val moveUp: String,
    val moveDown: String,
    val connecting: String,
    val waitingReady: String,
    val leave: String,
    val members: String,
    val buffering: String,
    val ready: String,
    val drift: String,
    val transfer: String,
    val saveFile: String,
    val direct: String,
    val turn: String,
    val relay: String,
    val nickname: String,
    val relays: String,
    val turnUrl: String,
    val turnUsername: String,
    val turnPassword: String,
    val rememberPassword: String,
    val saveSettings: String,
    val connectionTest: String,
    val relayTestOk: String,
    val relayTurnConfigured: String,
    val copyCode: String,
    val shareCode: String,
    val invalidRoomCode: String,
    val fileSaved: String
)

@Composable
fun TogetherDestination(
    viewModel: TogetherViewModel,
    labels: TogetherLabels,
    onCopyRoomCode: (String) -> Unit,
    onShareRoomCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val bottomSafePadding = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        LocalEchoPageBottomChromeInset.current
    )
    var pendingMatchId by remember { mutableStateOf<String?>(null) }
    val addAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addDraft(uris.map { pickedAudio(context, it) })
    }
    val matchAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val remoteId = pendingMatchId
        pendingMatchId = null
        if (uri != null && remoteId != null) {
            viewModel.matchLocal(remoteId, pickedAudio(context, uri))
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.page !in listOf(TogetherPage.Home, TogetherPage.Room)) {
                    TextButton(onClick = viewModel::back) { Text(labels.back) }
                }
                Text(labels.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (state.page == TogetherPage.Home) {
                    TextButton(onClick = viewModel::openSettings) { Text(labels.settings) }
                }
            }
        }
    ) { padding ->
        val pageModifier = Modifier
            .padding(padding)
            .padding(bottom = bottomSafePadding)
        when (state.page) {
            TogetherPage.Home -> TogetherHome(
                state,
                labels,
                viewModel::openCreate,
                viewModel::openJoin,
                pageModifier
            )
            TogetherPage.Create -> TogetherCreate(
                state,
                labels,
                viewModel::removeDraft,
                viewModel::moveDraft,
                { addAudioLauncher.launch(arrayOf("audio/*")) },
                viewModel::create,
                pageModifier
            )
            TogetherPage.Join -> TogetherJoin(
                state,
                labels,
                viewModel::updateRoomCode,
                { remoteId ->
                    pendingMatchId = remoteId
                    matchAudioLauncher.launch(arrayOf("audio/*"))
                },
                viewModel::join,
                pageModifier
            )
            TogetherPage.Room -> TogetherRoom(
                state,
                labels,
                onCopyRoomCode,
                onShareRoomCode,
                viewModel::save,
                viewModel::leave,
                pageModifier
            )
            TogetherPage.Settings -> TogetherSettings(
                state,
                labels,
                viewModel::updateSettings,
                viewModel::testConnection,
                viewModel::saveSettings,
                pageModifier
            )
        }
    }
}

@Composable
private fun TogetherHome(
    state: TogetherUiState,
    labels: TogetherLabels,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(labels.createRoom) }
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text(labels.joinRoom) }
        Message(state, labels)
    }
}

@Composable
private fun TogetherCreate(
    state: TogetherUiState,
    labels: TogetherLabels,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddLocalAudio: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        if (state.draftQueue.isEmpty()) {
            Text(labels.emptyQueue, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(state.draftQueue, key = { _, item -> item.stableId }) { index, item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.artist, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                                    Text(labels.moveUp)
                                }
                                TextButton(
                                    onClick = { onMove(index, index + 1) },
                                    enabled = index < state.draftQueue.lastIndex
                                ) { Text(labels.moveDown) }
                                TextButton(onClick = { onRemove(index) }) { Text(labels.remove) }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onAddLocalAudio, modifier = Modifier.fillMaxWidth()) {
            Text(labels.addLocalAudio)
        }
        Button(
            onClick = onCreate,
            enabled = state.draftQueue.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(labels.confirmCreate) }
        Message(state, labels)
    }
}

@Composable
private fun TogetherJoin(
    state: TogetherUiState,
    labels: TogetherLabels,
    onCodeChanged: (String) -> Unit,
    onMatchLocal: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.roomCodeInput,
            onValueChange = onCodeChanged,
            label = { Text(labels.roomCode) },
            placeholder = { Text(labels.pasteRoomCode) },
            singleLine = true,
            enabled = state.joinPreview == null && !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        state.joinPreview?.let { preview ->
            Text(labels.previewFiles, style = MaterialTheme.typography.titleMedium)
            preview.queue.forEach { item ->
                val matched = state.localMatches[item.stableId]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(humanBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (matched != null) labels.matchedLocal else labels.downloadRequired,
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(onClick = { onMatchLocal(item.stableId) }) {
                            Text(labels.matchLocal)
                        }
                    }
                }
            }
            val unmatched = preview.queue
                .filterNot { state.localMatches.containsKey(it.stableId) }
                .sumOf(TogetherQueueItem::sizeBytes)
            Text(
                "${labels.storageSpace}: ${humanBytes(unmatched)} / ${humanBytes(preview.freeBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = onJoin,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.joinPreview == null) labels.previewFiles else labels.confirmJoin)
        }
        Message(state, labels)
    }
}

@Composable
private fun TogetherRoom(
    ui: TogetherUiState,
    labels: TogetherLabels,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onSave: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier
) {
    when (val state = ui.session) {
        is TogetherSessionState.Preparing,
        is TogetherSessionState.Connecting,
        is TogetherSessionState.Leaving -> Column(
            modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(labels.connecting)
        }
        is TogetherSessionState.WaitingReady -> RoomBody(
            state.roomCode, state.members, null, null, labels, onCopyCode, onShareCode, onSave, onLeave, modifier
        )
        is TogetherSessionState.Active -> RoomBody(
            state.roomCode, state.members, state, state.transfer, labels, onCopyCode, onShareCode, onSave, onLeave, modifier
        )
        is TogetherSessionState.Reconnecting -> Column(modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
            Text(labels.connecting)
            OutlinedButton(onClick = onLeave) { Text(labels.leave) }
        }
        is TogetherSessionState.Failed -> Column(modifier.fillMaxSize().padding(24.dp)) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onLeave) { Text(labels.leave) }
        }
        TogetherSessionState.Idle -> TogetherHome(ui, labels, {}, {}, modifier)
    }
}

@Composable
private fun RoomBody(
    code: String,
    members: List<TogetherMember>,
    active: TogetherSessionState.Active?,
    transfer: TogetherTransfer?,
    labels: TogetherLabels,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onSave: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(code, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { onCopyCode(code) }) { Text(labels.copyCode) }
                TextButton(onClick = { onShareCode(code) }) { Text(labels.shareCode) }
            }
            val connection = when (active?.connectionKind) {
                TogetherConnectionKind.Direct -> labels.direct
                TogetherConnectionKind.Turn -> labels.turn
                else -> labels.relay
            }
            Text(connection, style = MaterialTheme.typography.labelMedium)
            active?.driftMs?.let { Text("${labels.drift}: ${it} ms") }
        }
        item { Text(labels.members, style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(members) { _, member ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(member.nickname)
                    Text(
                        when {
                            member.buffering -> labels.buffering
                            member.ready -> labels.ready
                            else -> "${member.downloadPercent}%"
                        }
                    )
                }
            }
        }
        transfer?.let { item ->
            item {
                HorizontalDivider()
                Text("${labels.transfer}: ${item.fileName}")
                LinearProgressIndicator(
                    progress = { item.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                if (item.complete) {
                    Button(onClick = { onSave(item.fileId) }) { Text(labels.saveFile) }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text(labels.leave)
            }
        }
    }
}

@Composable
private fun TogetherSettings(
    state: TogetherUiState,
    labels: TogetherLabels,
    onChange: (TogetherSavedSettings) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier
) {
    val settings = state.settings
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = settings.nickname,
                onValueChange = { onChange(settings.copy(nickname = it)) },
                label = { Text(labels.nickname) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = settings.relays.joinToString("\n"),
                onValueChange = {
                    onChange(settings.copy(relays = it.lineSequence().map(String::trim).filter(String::isNotBlank).toList()))
                },
                label = { Text(labels.relays) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
        item {
            OutlinedTextField(
                value = settings.turnUrl,
                onValueChange = { onChange(settings.copy(turnUrl = it)) },
                label = { Text(labels.turnUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = settings.turnUsername,
                onValueChange = { onChange(settings.copy(turnUsername = it)) },
                label = { Text(labels.turnUsername) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = settings.turnPassword,
                onValueChange = { onChange(settings.copy(turnPassword = it)) },
                label = { Text(labels.turnPassword) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.rememberTurnPassword,
                    onCheckedChange = { onChange(settings.copy(rememberTurnPassword = it)) }
                )
                Text(labels.rememberPassword, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            OutlinedButton(
                onClick = onTest,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(labels.connectionTest) }
        }
        item {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(labels.saveSettings) }
        }
    }
}

@Composable
private fun Message(state: TogetherUiState, labels: TogetherLabels) {
    state.message?.let { message ->
        Text(
            when (message) {
                "invalid_room_code" -> labels.invalidRoomCode
                "file_saved" -> labels.fileSaved
                "not_enough_space" -> labels.notEnoughSpace
                "relay_ok" -> labels.relayTestOk
                "relay_ok_turn_configured" -> labels.relayTurnConfigured
                else -> message
            },
            color = if (message == "file_saved") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}

private fun pickedAudio(context: Context, uri: Uri): TogetherQueueItem {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
    var size = 0L
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0).orEmpty().ifBlank { name }
            size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
        }
    }
    return TogetherQueueItem(
        stableId = uri.toString(),
        title = name.ifBlank { "Audio" },
        artist = "",
        sourceUri = uri.toString(),
        sizeBytes = size
    )
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}
