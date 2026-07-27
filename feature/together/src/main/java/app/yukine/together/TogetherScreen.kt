package app.yukine.together

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.ui.EchoGlassSurface
import app.yukine.ui.EchoIcon
import app.yukine.ui.EchoIconKind
import app.yukine.ui.EchoMotion
import app.yukine.ui.EchoPageDefaults
import app.yukine.ui.EchoPageTitle
import app.yukine.ui.EchoSectionTitle
import app.yukine.ui.EchoShapes
import app.yukine.ui.EchoStateCard
import app.yukine.ui.EchoTheme
import app.yukine.ui.EchoTypography
import app.yukine.ui.echoBreath
import app.yukine.ui.echoEnter
import app.yukine.ui.echoPageBottomPadding
import app.yukine.ui.echoPagePadding
import app.yukine.ui.echoPressScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val fileSaved: String,
    val homeSubtitle: String = "与朋友同步听同一首",
    val createRoomHint: String = "用当前队列或歌单开房",
    val joinRoomHint: String = "输入房间码一起听",
    val createTitle: String = "创建房间",
    val joinTitle: String = "加入房间",
    val roomTitle: String = "房间中",
    val choosePlaylist: String = "选择歌单",
    val closePlaylistPicker: String = "返回草稿",
    val playlistLoading: String = "正在加载歌单…",
    val replaceQueue: String = "替换当前草稿",
    val skippedTracks: String = "已跳过不可用歌曲",
    val playlistHasNoShareableTracks: String = "该歌单没有可用于一起听的歌曲",
    val replaceQueueConfirmation: String = "当前草稿不为空，确认后将用所选歌单替换",
    val currentQueueHint: String = "使用当前播放队列；在其他页面加入歌曲后会实时同步",
    val liveQueue: String = "实时队列",
    val emptyQueueHint: String = "去曲库或搜索加歌，或点「选择歌单」导入",
    val trackCountLabel: String = "首",
    val settingsSaved: String = "设置已保存",
    val noPlaylists: String = "暂无可用歌单",
    val nowPlayingBadge: String = "正在播放",
    val pasteFromClipboard: String = "粘贴",
    val joinStepInput: String = "1 · 输入房间码",
    val joinStepPreview: String = "2 · 确认文件",
    val matchedSummary: String = "已匹配本地",
    val editRoomCode: String = "修改房间码",
    val dragReorder: String = "长按拖动手柄排序"
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
    val matchScope = rememberCoroutineScope()
    var pendingMatchId by remember { mutableStateOf<String?>(null) }
    val matchAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val remoteId = pendingMatchId
        pendingMatchId = null
        if (uri != null && remoteId != null) {
            matchScope.launch(Dispatchers.IO) {
                val item = pickedAudio(context, uri)
                withContext(Dispatchers.Main.immediate) {
                    viewModel.matchLocal(remoteId, item)
                }
            }
        }
    }
    AnimatedContent(
        targetState = state.page,
        transitionSpec = { EchoMotion.pageContentTransition() },
        label = "togetherPage",
        modifier = modifier.fillMaxSize()
    ) { page ->
        val contentModifier = Modifier.fillMaxSize()
        when (page) {
            TogetherPage.Home -> TogetherHome(
                state = state,
                labels = labels,
                onCreate = viewModel::openCreate,
                onJoin = viewModel::openJoin,
                onSettings = viewModel::openSettings,
                modifier = contentModifier
            )
            TogetherPage.Create -> TogetherCreate(
                state = state,
                labels = labels,
                onBack = viewModel::back,
                onOpenPlaylistPicker = viewModel::openPlaylistPicker,
                onClosePlaylistPicker = viewModel::closePlaylistPicker,
                onChoosePlaylist = viewModel::choosePlaylist,
                onConfirmReplace = viewModel::confirmReplacePlaylist,
                onRemove = viewModel::removeDraft,
                onMove = viewModel::moveDraft,
                onCreate = viewModel::create,
                modifier = contentModifier
            )
            TogetherPage.Join -> TogetherJoin(
                state = state,
                labels = labels,
                onBack = viewModel::back,
                onCodeChanged = viewModel::updateRoomCode,
                onMatchLocal = { remoteId ->
                    pendingMatchId = remoteId
                    matchAudioLauncher.launch(arrayOf("audio/*"))
                },
                onJoin = viewModel::join,
                modifier = contentModifier
            )
            TogetherPage.Room -> TogetherRoom(
                ui = state,
                labels = labels,
                onCopyCode = onCopyRoomCode,
                onShareCode = onShareRoomCode,
                onSave = viewModel::save,
                onRemoveQueueItem = viewModel::removeRoomQueueItem,
                onMoveQueueItem = viewModel::moveRoomQueueItem,
                onLeave = viewModel::leave,
                modifier = contentModifier
            )
            TogetherPage.Settings -> TogetherSettings(
                state = state,
                labels = labels,
                onBack = viewModel::back,
                onChange = viewModel::updateSettings,
                onTest = viewModel::testConnection,
                onSave = viewModel::saveSettings,
                modifier = contentModifier
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
    onSettings: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EchoPageTitle(
                    title = labels.title,
                    subtitle = labels.homeSubtitle,
                    modifier = Modifier.weight(1f)
                )
                TogetherIconButton(
                    icon = EchoIconKind.Settings,
                    contentDescription = labels.settings,
                    onClick = onSettings
                )
            }
        }
        item(key = "create") {
            TogetherActionCard(
                title = labels.createRoom,
                subtitle = labels.createRoomHint,
                icon = EchoIconKind.PlaylistAdd,
                emphasized = true,
                onClick = onCreate,
                modifier = Modifier.echoEnter(0)
            )
        }
        item(key = "join") {
            TogetherActionCard(
                title = labels.joinRoom,
                subtitle = labels.joinRoomHint,
                icon = EchoIconKind.Network,
                emphasized = false,
                onClick = onJoin,
                modifier = Modifier.echoEnter(1)
            )
        }
        resolveMessage(state, labels)?.let { message ->
            item(key = "message") {
                TogetherMessageBar(message = message.text, success = message.success)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TogetherCreate(
    state: TogetherUiState,
    labels: TogetherLabels,
    onBack: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onClosePlaylistPicker: () -> Unit,
    onChoosePlaylist: (TogetherPlaylistRef) -> Unit,
    onConfirmReplace: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier
) {
    val p = EchoTheme.colors()
    val createInteraction = remember { MutableInteractionSource() }
    val message = resolveMessage(state, labels)
    val showReplaceConfirm = state.message == "replace_queue_confirmation_required"
    val listState = rememberLazyListState()
    val queueKeys = remember(state.draftQueue) {
        state.draftQueue.map { togetherQueueItemKey(it.stableId) }
    }
    val dragState = rememberTogetherQueueDragState(onMove = onMove)
    val dragAutoScroller = rememberTogetherQueueAutoScroller(dragState, listState)
    LaunchedEffect(state.draftQueue.map { it.stableId }) {
        dragAutoScroller.stop()
        dragState.bindKeys(queueKeys)
        dragState.clear()
    }

    if (state.playlistPickerVisible) {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = togetherScrollContentPadding(),
                verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
            ) {
                item(key = "picker-title") {
                    EchoPageTitle(
                        title = labels.choosePlaylist,
                        subtitle = labels.currentQueueHint,
                        backLabel = labels.closePlaylistPicker,
                        onBack = Runnable(onClosePlaylistPicker)
                    )
                }
                if (state.playlistLoading) {
                    item(key = "picker-loading") {
                        TogetherLoadingRow(labels.playlistLoading)
                    }
                } else if (state.availablePlaylists.isEmpty()) {
                    item(key = "picker-empty") {
                        EchoStateCard(
                            title = labels.noPlaylists,
                            description = labels.emptyQueueHint,
                            icon = EchoIconKind.Collections
                        )
                    }
                } else {
                    items(
                        items = state.availablePlaylists,
                        key = { playlistKey(it) }
                    ) { playlist ->
                        TogetherPlaylistRow(
                            playlist = playlist,
                            onClick = { onChoosePlaylist(playlist.ref) }
                        )
                    }
                }
            }
            TogetherStickyBar {
                when {
                    showReplaceConfirm -> {
                        TogetherMessageBar(
                            message = labels.replaceQueueConfirmation,
                            success = false
                        )
                        TogetherPrimaryButton(
                            label = labels.replaceQueue,
                            enabled = !state.busy,
                            busy = state.busy,
                            onClick = onConfirmReplace
                        )
                    }
                    message != null -> {
                        TogetherMessageBar(message = message.text, success = message.success)
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(togetherHeaderPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoPageTitle(
                title = labels.createTitle,
                subtitle = labels.currentQueueHint,
                backLabel = labels.back,
                onBack = Runnable(onBack)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoEnter(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TogetherChipButton(
                    label = labels.choosePlaylist,
                    icon = EchoIconKind.Collections,
                    onClick = onOpenPlaylistPicker,
                    emphasized = true
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.draftQueue.size} ${labels.trackCountLabel}",
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = togetherScrollContentPadding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            if (state.draftQueue.isEmpty()) {
                item(key = "empty") {
                    EchoStateCard(
                        title = labels.emptyQueue,
                        description = labels.emptyQueueHint,
                        icon = EchoIconKind.Queue,
                        modifier = Modifier
                            .echoEnter(1)
                            .echoBreath(enabled = true)
                    )
                }
            } else {
                item(key = "queue-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EchoSectionTitle(labels.liveQueue)
                        Text(
                            labels.dragReorder,
                            style = EchoTypography.caption,
                            color = p.muted
                        )
                    }
                }
                itemsIndexed(
                    items = state.draftQueue,
                    key = { _, item -> togetherQueueItemKey(item.stableId) }
                ) { index, item ->
                    val rowKey = togetherQueueItemKey(item.stableId)
                    val dragging = dragState.isDragging(rowKey)
                    TogetherQueueRow(
                        index = index,
                        item = item,
                        editable = true,
                        isCurrent = false,
                        nowPlayingBadge = labels.nowPlayingBadge,
                        labels = labels,
                        onRemove = { onRemove(index) },
                        dragHandleModifier = Modifier.pointerInput(rowKey, state.draftQueue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragState.bindKeys(queueKeys)
                                    dragState.start(listState.layoutInfo.visibleItemsInfo, rowKey)
                                },
                                onDragCancel = {
                                    dragAutoScroller.stop()
                                    dragState.clear()
                                },
                                onDragEnd = {
                                    dragAutoScroller.stop()
                                    dragState.drop()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragState.drag(listState.layoutInfo.visibleItemsInfo, dragAmount.y)
                                    dragAutoScroller.startIfNeeded()
                                }
                            )
                        },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                                fadeOutSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                                placementSpec = if (dragging) {
                                    null
                                } else {
                                    EchoMotion.layoutSpring<IntOffset>().spec()
                                }
                            )
                            .graphicsLayer {
                                translationY = dragState.dragOffsetFor(rowKey)
                                shadowElevation = if (dragging) 12.dp.toPx() else 0f
                            }
                            .echoEnter(index.coerceAtMost(8))
                    )
                }
            }
            if (state.skippedItems.isNotEmpty()) {
                item(key = "skipped") {
                    TogetherMessageBar(
                        message = "${labels.skippedTracks}: ${state.skippedItems.size}",
                        success = false
                    )
                }
            }
        }
        TogetherStickyBar {
            message?.let {
                TogetherMessageBar(message = it.text, success = it.success)
            }
            TogetherPrimaryButton(
                label = labels.confirmCreate,
                enabled = state.draftQueue.isNotEmpty() &&
                    !state.busy &&
                    !state.playlistLoading,
                busy = state.busy || state.playlistLoading,
                onClick = onCreate,
                interactionSource = createInteraction
            )
        }
    }
}

@Composable
private fun TogetherJoin(
    state: TogetherUiState,
    labels: TogetherLabels,
    onBack: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onMatchLocal: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier
) {
    val p = EchoTheme.colors()
    val joinInteraction = remember { MutableInteractionSource() }
    val clipboard = LocalClipboardManager.current
    val preview = state.joinPreview
    val message = resolveMessage(state, labels)
    val fieldColors = togetherFieldColors()
    val matchedCount = preview?.queue?.count { state.localMatches.containsKey(it.stableId) } ?: 0
    val totalCount = preview?.queue?.size ?: 0

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(togetherHeaderPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoPageTitle(
                title = labels.joinTitle,
                subtitle = labels.joinRoomHint,
                backLabel = labels.back,
                onBack = Runnable(onBack)
            )
            TogetherJoinSteps(
                stepInputLabel = labels.joinStepInput,
                stepPreviewLabel = labels.joinStepPreview,
                previewReady = preview != null
            )
            EchoGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoEnter(0),
                shape = EchoShapes.large,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.roomCodeInput,
                        onValueChange = onCodeChanged,
                        label = { Text(labels.roomCode) },
                        placeholder = { Text(labels.pasteRoomCode) },
                        singleLine = true,
                        enabled = !state.busy,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TogetherChipButton(
                            label = labels.pasteFromClipboard,
                            icon = EchoIconKind.Import,
                            onClick = {
                                val raw = clipboard.getText()?.text.orEmpty()
                                val extracted = TogetherRoomCode.extractFromText(raw)
                                    ?: raw.trim().takeIf { it.isNotEmpty() }
                                if (!extracted.isNullOrBlank()) {
                                    onCodeChanged(extracted)
                                }
                            },
                            emphasized = preview == null
                        )
                        if (preview != null) {
                            TogetherChipButton(
                                label = labels.editRoomCode,
                                icon = EchoIconKind.Edit,
                                onClick = { onCodeChanged(state.roomCodeInput) }
                            )
                        }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = togetherScrollContentPadding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            if (preview != null) {
                val unmatched = preview.queue
                    .filterNot { state.localMatches.containsKey(it.stableId) }
                    .sumOf(TogetherQueueItem::sizeBytes)
                item(key = "storage") {
                    EchoGlassSurface(
                        modifier = Modifier.fillMaxWidth().echoEnter(1),
                        shape = EchoShapes.medium,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EchoIcon(EchoIconKind.Folder, Modifier.size(18.dp), p.accent)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${labels.storageSpace}: ${humanBytes(unmatched)} / ${humanBytes(preview.freeBytes)}",
                                    style = EchoTypography.caption,
                                    color = p.text,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                "${labels.matchedSummary}: $matchedCount / $totalCount",
                                style = EchoTypography.caption,
                                color = p.muted
                            )
                        }
                    }
                }
                item(key = "preview-title") {
                    EchoSectionTitle(labels.previewFiles)
                }
                itemsIndexed(
                    items = preview.queue,
                    key = { _, item -> item.stableId }
                ) { index, item ->
                    val matched = state.localMatches[item.stableId]
                    TogetherJoinTrackRow(
                        item = item,
                        matched = matched != null,
                        labels = labels,
                        onMatchLocal = { onMatchLocal(item.stableId) },
                        modifier = Modifier.echoEnter((index + 2).coerceAtMost(8))
                    )
                }
            }
        }
        TogetherStickyBar {
            message?.let {
                TogetherMessageBar(message = it.text, success = it.success)
            }
            TogetherPrimaryButton(
                label = if (preview == null) labels.previewFiles else labels.confirmJoin,
                enabled = !state.busy && (preview != null || state.roomCodeInput.isNotBlank()),
                busy = state.busy,
                onClick = onJoin,
                interactionSource = joinInteraction
            )
        }
    }
}

@Composable
private fun TogetherJoinSteps(
    stepInputLabel: String,
    stepPreviewLabel: String,
    previewReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TogetherStepPill(
            label = stepInputLabel,
            active = !previewReady,
            done = previewReady,
            modifier = Modifier.weight(1f)
        )
        TogetherStepPill(
            label = stepPreviewLabel,
            active = previewReady,
            done = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TogetherStepPill(
    label: String,
    active: Boolean,
    done: Boolean,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val bg = when {
        active -> p.accentSoft.copy(alpha = 0.62f)
        done -> p.surfaceVariant.copy(alpha = 0.55f)
        else -> p.surfaceVariant.copy(alpha = 0.28f)
    }
    val fg = when {
        active -> p.accent
        done -> p.text
        else -> p.muted
    }
    Surface(
        modifier = modifier,
        shape = EchoShapes.pill,
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (done) {
                EchoIcon(EchoIconKind.Check, Modifier.size(12.dp), p.accent)
            }
            Text(
                label,
                style = EchoTypography.small.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TogetherRoom(
    ui: TogetherUiState,
    labels: TogetherLabels,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onSave: (String) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier
) {
    val p = EchoTheme.colors()
    when (val state = ui.session) {
        is TogetherSessionState.Preparing,
        is TogetherSessionState.Connecting,
        is TogetherSessionState.Leaving -> {
            TogetherCenteredStatus(
                message = labels.connecting,
                modifier = modifier,
                breathing = true
            )
        }
        is TogetherSessionState.WaitingReady -> RoomBody(
            code = state.roomCode,
            queue = state.queue,
            queueEditable = ui.queueEditable,
            members = state.members,
            active = null,
            transfer = null,
            waiting = true,
            labels = labels,
            message = resolveMessage(ui, labels),
            onCopyCode = onCopyCode,
            onShareCode = onShareCode,
            onSave = onSave,
            onRemoveQueueItem = onRemoveQueueItem,
            onMoveQueueItem = onMoveQueueItem,
            onLeave = onLeave,
            modifier = modifier
        )
        is TogetherSessionState.Active -> RoomBody(
            code = state.roomCode,
            queue = state.queue,
            queueEditable = ui.queueEditable,
            members = state.members,
            active = state,
            transfer = state.transfer,
            waiting = false,
            labels = labels,
            message = resolveMessage(ui, labels),
            onCopyCode = onCopyCode,
            onShareCode = onShareCode,
            onSave = onSave,
            onRemoveQueueItem = onRemoveQueueItem,
            onMoveQueueItem = onMoveQueueItem,
            onLeave = onLeave,
            modifier = modifier
        )
        is TogetherSessionState.Reconnecting -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(echoPagePadding()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = p.accent, modifier = Modifier.echoBreath(enabled = true))
                Spacer(Modifier.height(14.dp))
                Text(labels.connecting, style = EchoTypography.body, color = p.text)
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onLeave, shape = EchoShapes.medium) {
                    Text(labels.leave, style = EchoTypography.label)
                }
            }
        }
        is TogetherSessionState.Failed -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = echoPagePadding(),
                verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
            ) {
                item {
                    EchoPageTitle(title = labels.roomTitle, subtitle = labels.title)
                }
                item {
                    TogetherMessageBar(message = state.message, success = false)
                }
                item {
                    OutlinedButton(
                        onClick = onLeave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoShapes.medium
                    ) {
                        Text(labels.leave, style = EchoTypography.label)
                    }
                }
            }
        }
        TogetherSessionState.Idle -> {
            TogetherHome(
                state = ui,
                labels = labels,
                onCreate = {},
                onJoin = {},
                onSettings = {},
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomBody(
    code: String,
    queue: List<TogetherQueueItem>,
    queueEditable: Boolean,
    members: List<TogetherMember>,
    active: TogetherSessionState.Active?,
    transfer: TogetherTransfer?,
    waiting: Boolean,
    labels: TogetherLabels,
    message: ResolvedMessage?,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onSave: (String) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier
) {
    val p = EchoTheme.colors()
    val leaveInteraction = remember { MutableInteractionSource() }
    val listState = rememberLazyListState()
    val queueKeys = remember(queue) { queue.map { togetherQueueItemKey(it.stableId) } }
    val dragState = rememberTogetherQueueDragState(onMove = onMoveQueueItem)
    val dragAutoScroller = rememberTogetherQueueAutoScroller(dragState, listState)
    LaunchedEffect(queue.map { it.stableId }, queueEditable) {
        dragAutoScroller.stop()
        dragState.bindKeys(queueKeys)
        dragState.clear()
    }
    val readyCount = members.count { it.ready && !it.buffering }
    val connection = when (active?.connectionKind) {
        TogetherConnectionKind.Direct -> labels.direct
        TogetherConnectionKind.Turn -> labels.turn
        else -> labels.relay
    }
    val currentIndex = active?.currentIndex ?: -1

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(togetherHeaderPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoPageTitle(
                title = labels.roomTitle,
                subtitle = if (waiting) labels.waitingReady else labels.title
            )
            EchoGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .echoEnter(0),
                shape = EchoShapes.large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        labels.roomCode,
                        style = EchoTypography.caption,
                        color = p.muted
                    )
                    Text(
                        code,
                        style = EchoTypography.headline.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        color = p.heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TogetherChipButton(
                            label = labels.copyCode,
                            icon = EchoIconKind.Action,
                            onClick = { onCopyCode(code) },
                            emphasized = true
                        )
                        TogetherChipButton(
                            label = labels.shareCode,
                            icon = EchoIconKind.Upload,
                            onClick = { onShareCode(code) }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TogetherStatusChip(text = connection)
                        if (waiting) {
                            TogetherStatusChip(text = labels.waitingReady)
                        }
                        active?.driftMs?.let {
                            TogetherStatusChip(text = "${labels.drift}: ${it} ms")
                        }
                        TogetherStatusChip(text = "${labels.members} $readyCount/${members.size}")
                    }
                }
            }
        }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = togetherScrollContentPadding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "queue-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EchoSectionTitle(labels.liveQueue)
                if (queueEditable) {
                    Text(
                        labels.dragReorder,
                        style = EchoTypography.caption,
                        color = p.muted
                    )
                }
            }
        }
        if (queue.isEmpty()) {
            item(key = "queue-empty") {
                EchoStateCard(
                    title = labels.emptyQueue,
                    description = labels.currentQueueHint,
                    icon = EchoIconKind.Queue
                )
            }
        } else {
            itemsIndexed(
                items = queue,
                key = { _, item -> togetherQueueItemKey(item.stableId) }
            ) { index, item ->
                val rowKey = togetherQueueItemKey(item.stableId)
                val dragging = dragState.isDragging(rowKey)
                val dragHandle = if (queueEditable) {
                    Modifier.pointerInput(rowKey, queue.size, queueEditable) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragState.bindKeys(queueKeys)
                                dragState.start(listState.layoutInfo.visibleItemsInfo, rowKey)
                            },
                            onDragCancel = {
                                dragAutoScroller.stop()
                                dragState.clear()
                            },
                            onDragEnd = {
                                dragAutoScroller.stop()
                                dragState.drop()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.drag(listState.layoutInfo.visibleItemsInfo, dragAmount.y)
                                dragAutoScroller.startIfNeeded()
                            }
                        )
                    }
                } else {
                    Modifier
                }
                TogetherQueueRow(
                    index = index,
                    item = item,
                    editable = queueEditable,
                    isCurrent = index == currentIndex,
                    nowPlayingBadge = labels.nowPlayingBadge,
                    labels = labels,
                    onRemove = { onRemoveQueueItem(index) },
                    dragHandleModifier = dragHandle,
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                            fadeOutSpec = if (dragging) null else EchoMotion.layoutSpring<Float>().spec(),
                            placementSpec = if (dragging) {
                                null
                            } else {
                                EchoMotion.layoutSpring<IntOffset>().spec()
                            }
                        )
                        .graphicsLayer {
                            translationY = dragState.dragOffsetFor(rowKey)
                            shadowElevation = if (dragging) 12.dp.toPx() else 0f
                        }
                        .echoEnter((index + 1).coerceAtMost(8))
                )
            }
        }
        item(key = "members-title") {
            EchoSectionTitle(labels.members)
        }
        itemsIndexed(
            items = members,
            key = { _, member -> member.idHash.ifBlank { member.nickname } }
        ) { index, member ->
            TogetherMemberRow(
                member = member,
                labels = labels,
                modifier = Modifier.echoEnter((index + 2).coerceAtMost(8))
            )
        }
        transfer?.let { item ->
            item(key = "transfer") {
                EchoGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = EchoShapes.medium,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EchoIcon(EchoIconKind.Download, Modifier.size(18.dp), p.accent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${labels.transfer}: ${item.fileName}",
                                style = EchoTypography.bodyMedium,
                                color = p.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { item.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(EchoShapes.pill),
                            color = p.accent,
                            trackColor = p.surfaceVariant.copy(alpha = 0.45f)
                        )
                        if (item.complete) {
                            Button(
                                onClick = { onSave(item.fileId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = p.accent,
                                    contentColor = p.onAccent
                                ),
                                shape = EchoShapes.medium
                            ) {
                                Text(labels.saveFile, style = EchoTypography.label)
                            }
                        }
                    }
                }
            }
        }
    }
        TogetherStickyBar {
            message?.let {
                TogetherMessageBar(message = it.text, success = it.success)
            }
            OutlinedButton(
                onClick = onLeave,
                interactionSource = leaveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .echoPressScale(leaveInteraction),
                shape = EchoShapes.medium
            ) {
                Text(labels.leave, style = EchoTypography.label)
            }
        }
    }
}

@Composable
private fun TogetherSettings(
    state: TogetherUiState,
    labels: TogetherLabels,
    onBack: () -> Unit,
    onChange: (TogetherSavedSettings) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier
) {
    val p = EchoTheme.colors()
    val settings = state.settings
    val testInteraction = remember { MutableInteractionSource() }
    val saveInteraction = remember { MutableInteractionSource() }
    val fieldColors = togetherFieldColors()
    val message = resolveMessage(state, labels)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(togetherHeaderPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoPageTitle(
                title = labels.settings,
                subtitle = labels.title,
                backLabel = labels.back,
                onBack = Runnable(onBack)
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = togetherScrollContentPadding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            item(key = "form") {
                EchoGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .echoEnter(0),
                    shape = EchoShapes.large,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = settings.nickname,
                            onValueChange = { onChange(settings.copy(nickname = it)) },
                            label = { Text(labels.nickname) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.relays.joinToString("\n"),
                            onValueChange = {
                                onChange(
                                    settings.copy(
                                        relays = it.lineSequence()
                                            .map(String::trim)
                                            .filter(String::isNotBlank)
                                            .toList()
                                    )
                                )
                            },
                            label = { Text(labels.relays) },
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        OutlinedTextField(
                            value = settings.turnUrl,
                            onValueChange = { onChange(settings.copy(turnUrl = it)) },
                            label = { Text(labels.turnUrl) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.turnUsername,
                            onValueChange = { onChange(settings.copy(turnUsername = it)) },
                            label = { Text(labels.turnUsername) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.turnPassword,
                            onValueChange = { onChange(settings.copy(turnPassword = it)) },
                            label = { Text(labels.turnPassword) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Switch(
                                checked = settings.rememberTurnPassword,
                                onCheckedChange = {
                                    onChange(settings.copy(rememberTurnPassword = it))
                                }
                            )
                            Text(
                                labels.rememberPassword,
                                style = EchoTypography.caption,
                                color = p.text,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        TogetherStickyBar {
            message?.let {
                TogetherMessageBar(message = it.text, success = it.success)
            }
            OutlinedButton(
                onClick = onTest,
                enabled = !state.busy,
                interactionSource = testInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .echoPressScale(testInteraction),
                shape = EchoShapes.medium
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = p.accent
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(labels.connectionTest, style = EchoTypography.label)
            }
            TogetherPrimaryButton(
                label = labels.saveSettings,
                enabled = true,
                busy = false,
                onClick = onSave,
                interactionSource = saveInteraction
            )
        }
    }
}

@Composable
private fun TogetherActionCard(
    title: String,
    subtitle: String,
    icon: EchoIconKind,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    EchoGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(EchoShapes.medium)
                    .background(
                        if (emphasized) p.accentSoft.copy(alpha = 0.55f)
                        else p.surfaceVariant.copy(alpha = 0.45f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                EchoIcon(
                    icon,
                    Modifier.size(22.dp),
                    if (emphasized) p.accent else p.text
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                    color = p.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(16.dp), p.muted)
        }
    }
}

@Composable
private fun TogetherQueueRow(
    index: Int,
    item: TogetherQueueItem,
    editable: Boolean,
    isCurrent: Boolean,
    nowPlayingBadge: String,
    labels: TogetherLabels,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(EchoShapes.pill)
                    .background(if (isCurrent) p.accent else p.border.copy(alpha = 0.35f))
            )
            Text(
                "${index + 1}",
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) p.accent else p.muted,
                modifier = Modifier.width(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (item.artist.isNotBlank()) append(item.artist)
                    if (isCurrent) {
                        if (isNotEmpty()) append(" · ")
                        append(nowPlayingBadge)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = EchoTypography.caption,
                        color = if (isCurrent) p.accent else p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (editable) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TogetherDragHandle(
                        modifier = dragHandleModifier,
                        label = labels.dragReorder
                    )
                    TogetherIconButton(
                        icon = EchoIconKind.Remove,
                        contentDescription = labels.remove,
                        onClick = onRemove,
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TogetherDragHandle(
    modifier: Modifier,
    label: String
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier
            .size(32.dp)
            .semantics { contentDescription = label },
        shape = EchoShapes.small,
        color = p.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(EchoIconKind.More, Modifier.size(16.dp), p.muted)
        }
    }
}

/** Top chrome (title / fixed controls) — horizontal + top only; bottom chrome lives in sticky bar. */
@Composable
private fun togetherHeaderPadding(): PaddingValues = PaddingValues(
    start = EchoPageDefaults.horizontalPadding,
    top = EchoPageDefaults.topPadding,
    end = EchoPageDefaults.horizontalPadding,
    bottom = 0.dp
)

/** Scroll body padding without the large NowBar reserve (sticky bar owns that inset). */
@Composable
private fun togetherScrollContentPadding(top: Dp = EchoPageDefaults.topPadding): PaddingValues =
    PaddingValues(
        start = EchoPageDefaults.horizontalPadding,
        top = top,
        end = EchoPageDefaults.horizontalPadding,
        bottom = 12.dp
    )

@Composable
private fun TogetherStickyBar(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EchoPageDefaults.horizontalPadding)
            .padding(top = 8.dp, bottom = echoPageBottomPadding(extra = 8.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun TogetherPrimaryButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val p = EchoTheme.colors()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interactionSource),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            disabledContainerColor = p.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = p.muted
        ),
        shape = EchoShapes.medium
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = p.onAccent
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = EchoTypography.label)
    }
}

/**
 * Key-based long-press drag reorder for LazyColumn queue rows.
 * Maps by item keys so mixed header/footer items do not need a fixed adapter offset.
 */
private class TogetherQueueDragState(
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    private var keys: List<Any> = emptyList()
    private var draggingKey by mutableStateOf<Any?>(null)
    private var fromIndex by mutableIntStateOf(-1)
    private var currentIndex by mutableIntStateOf(-1)
    private var draggedItemStart by mutableFloatStateOf(0f)
    private var draggedItemSize by mutableFloatStateOf(0f)
    private var dragOffset by mutableFloatStateOf(0f)

    fun bindKeys(itemKeys: List<Any>) {
        keys = itemKeys
    }

    fun start(visibleItems: List<LazyListItemInfo>, key: Any) {
        val item = visibleItems.firstOrNull { it.key == key } ?: return
        val listIndex = keys.indexOf(key)
        if (listIndex < 0) return
        draggingKey = key
        fromIndex = listIndex
        currentIndex = listIndex
        draggedItemStart = item.offset.toFloat()
        draggedItemSize = item.size.toFloat()
        dragOffset = 0f
    }

    fun drag(visibleItems: List<LazyListItemInfo>, deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val draggedCenter = draggedCenter()
        val keySet = keys.toSet()
        val queueItems = visibleItems.filter { it.key != key && it.key in keySet }
        val target = queueItems.firstOrNull { info ->
            info.key != key &&
                info.key in keySet &&
                draggedCenter in info.offset.toFloat()..(info.offset + info.size).toFloat()
        } ?: when {
            queueItems.isEmpty() -> null
            draggedCenter < queueItems.first().offset -> queueItems.first()
            draggedCenter > queueItems.last().offset + queueItems.last().size -> queueItems.last()
            else -> null
        }
        target ?: return
        val to = keys.indexOf(target.key)
        if (to >= 0) currentIndex = to
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
        val from = fromIndex
        val to = currentIndex
        if (from >= 0 && to >= 0 && from != to) {
            onMove(from, to)
        }
        clear()
    }

    fun clear() {
        draggingKey = null
        fromIndex = -1
        currentIndex = -1
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
private fun rememberTogetherQueueDragState(
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): TogetherQueueDragState = remember(onMove) {
    TogetherQueueDragState(onMove)
}

private class TogetherQueueAutoScroller(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val dragState: TogetherQueueDragState,
    private val edgeThresholdPx: Float,
    private val maxStepPx: Float
) {
    private var job: Job? = null

    fun startIfNeeded() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive && dragState.dragging()) {
                val delta = dragState.autoScrollDelta(
                    listState.layoutInfo,
                    edgeThresholdPx,
                    maxStepPx
                )
                if (delta == 0f) break
                val consumed = listState.scrollBy(delta)
                if (consumed == 0f) break
                dragState.onListScrolled(consumed)
                dragState.drag(listState.layoutInfo.visibleItemsInfo, 0f)
                withFrameNanos { }
            }
            job = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

@Composable
private fun rememberTogetherQueueAutoScroller(
    dragState: TogetherQueueDragState,
    listState: LazyListState
): TogetherQueueAutoScroller {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 64.dp.toPx() }
    val maxStepPx = with(density) { 18.dp.toPx() }
    return remember(dragState, listState, scope, edgeThresholdPx, maxStepPx) {
        TogetherQueueAutoScroller(
            scope = scope,
            listState = listState,
            dragState = dragState,
            edgeThresholdPx = edgeThresholdPx,
            maxStepPx = maxStepPx
        )
    }
}

private fun togetherQueueItemKey(stableId: String): String = "together-q:$stableId"

@Composable
private fun TogetherJoinTrackRow(
    item: TogetherQueueItem,
    matched: Boolean,
    labels: TogetherLabels,
    onMatchLocal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        humanBytes(item.sizeBytes),
                        style = EchoTypography.caption,
                        color = p.muted
                    )
                }
                TogetherStatusChip(
                    text = if (matched) labels.matchedLocal else labels.downloadRequired,
                    accent = matched
                )
            }
            TogetherChipButton(
                label = labels.matchLocal,
                icon = EchoIconKind.Folder,
                onClick = onMatchLocal
            )
        }
    }
}

@Composable
private fun TogetherMemberRow(
    member: TogetherMember,
    labels: TogetherLabels,
    modifier: Modifier = Modifier
) {
    val p = EchoTheme.colors()
    val status = when {
        member.buffering -> labels.buffering
        member.ready -> labels.ready
        else -> "${member.downloadPercent}%"
    }
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(EchoShapes.pill)
                        .background(
                            when {
                                member.buffering -> p.highlight
                                member.ready -> p.accent
                                else -> p.muted
                            }
                        )
                )
                Text(
                    member.nickname.ifBlank { "—" },
                    style = EchoTypography.bodyMedium,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(status, style = EchoTypography.caption, color = p.muted)
        }
    }
}

@Composable
private fun TogetherPlaylistRow(
    playlist: TogetherPlaylistSummary,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    EchoGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EchoIcon(EchoIconKind.Collections, Modifier.size(20.dp), p.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.title,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (playlist.subtitle.isNotBlank()) add(playlist.subtitle)
                    if (playlist.trackCount > 0) add("${playlist.trackCount}")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = EchoTypography.caption, color = p.muted, maxLines = 1)
                }
            }
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(14.dp), p.muted)
        }
    }
}

@Composable
private fun TogetherMessageBar(message: String, success: Boolean) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(
                if (success) EchoIconKind.Check else EchoIconKind.Info,
                Modifier.size(16.dp),
                if (success) p.accent else p.highlight
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                style = EchoTypography.caption,
                color = if (success) p.accent else p.text
            )
        }
    }
}

@Composable
private fun TogetherStatusChip(text: String, accent: Boolean = false) {
    val p = EchoTheme.colors()
    Surface(
        shape = EchoShapes.pill,
        color = if (accent) p.accentSoft.copy(alpha = 0.55f) else p.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Text(
            text,
            style = EchoTypography.small,
            color = if (accent) p.accent else p.muted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TogetherChipButton(
    label: String,
    icon: EchoIconKind,
    onClick: () -> Unit,
    emphasized: Boolean = false
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = EchoShapes.small,
        color = if (emphasized) p.accentSoft.copy(alpha = 0.66f) else p.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EchoIcon(icon, Modifier.size(14.dp), if (emphasized) p.accent else p.text)
            Text(
                label,
                style = EchoTypography.label,
                color = if (emphasized) p.accent else p.text,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TogetherIconButton(
    icon: EchoIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val p = EchoTheme.colors()
    val size = if (compact) 32.dp else 40.dp
    val iconSize = if (compact) 16.dp else 20.dp
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        shape = EchoShapes.small,
        color = p.surfaceVariant.copy(alpha = if (enabled) 0.42f else 0.18f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            EchoIcon(
                icon,
                Modifier.size(iconSize),
                if (enabled) p.muted else p.muted.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TogetherLoadingRow(label: String) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        shape = EchoShapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .echoBreath(enabled = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = p.accent
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = EchoTypography.caption, color = p.muted)
        }
    }
}

@Composable
private fun TogetherCenteredStatus(
    message: String,
    modifier: Modifier = Modifier,
    breathing: Boolean = false
) {
    val p = EchoTheme.colors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(echoPagePadding())
            .then(if (breathing) Modifier.echoBreath(enabled = true) else Modifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = p.accent)
        Spacer(Modifier.height(14.dp))
        Text(message, style = EchoTypography.body, color = p.text)
    }
}

@Composable
private fun togetherFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EchoTheme.colors().text,
    unfocusedTextColor = EchoTheme.colors().text,
    focusedBorderColor = EchoTheme.colors().accent,
    unfocusedBorderColor = EchoTheme.colors().border.copy(alpha = 0.55f),
    focusedLabelColor = EchoTheme.colors().accent,
    unfocusedLabelColor = EchoTheme.colors().muted,
    cursorColor = EchoTheme.colors().accent,
    focusedContainerColor = EchoTheme.colors().surface.copy(alpha = 0.22f),
    unfocusedContainerColor = EchoTheme.colors().surface.copy(alpha = 0.12f)
)

private data class ResolvedMessage(val text: String, val success: Boolean)

private fun resolveMessage(state: TogetherUiState, labels: TogetherLabels): ResolvedMessage? {
    val message = state.message ?: return null
    val text = when (message) {
        "invalid_room_code" -> labels.invalidRoomCode
        "file_saved" -> labels.fileSaved
        "not_enough_space" -> labels.notEnoughSpace
        "relay_ok" -> labels.relayTestOk
        "relay_ok_turn_configured" -> labels.relayTurnConfigured
        "playlist_has_no_shareable_tracks" -> labels.playlistHasNoShareableTracks
        "replace_queue_confirmation_required" -> labels.replaceQueueConfirmation
        "playlist_tracks_skipped" -> "${labels.skippedTracks}: ${state.skippedItems.size}"
        "settings_saved" -> labels.settingsSaved
        else -> message
    }
    val success = message in setOf(
        "file_saved",
        "relay_ok",
        "relay_ok_turn_configured",
        "settings_saved"
    )
    return ResolvedMessage(text, success)
}

private fun playlistKey(playlist: TogetherPlaylistSummary): String = when (val ref = playlist.ref) {
    is TogetherPlaylistRef.Local -> "local:${ref.playlistId}"
    is TogetherPlaylistRef.Streaming -> "streaming:${ref.provider}:${ref.providerPlaylistId}"
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
