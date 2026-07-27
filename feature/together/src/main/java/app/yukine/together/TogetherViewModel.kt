package app.yukine.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class TogetherPage {
    Home,
    Create,
    Join,
    Room,
    Settings
}

data class TogetherUiState(
    val page: TogetherPage = TogetherPage.Home,
    val session: TogetherSessionState = TogetherSessionState.Idle,
    val draftQueue: List<TogetherQueueItem> = emptyList(),
    val roomCodeInput: String = "",
    val joinPreview: TogetherJoinPreview? = null,
    val localMatches: Map<String, TogetherQueueItem> = emptyMap(),
    val settings: TogetherSavedSettings = TogetherSavedSettings(),
    val playlistPickerVisible: Boolean = false,
    val availablePlaylists: List<TogetherPlaylistSummary> = emptyList(),
    val playlistLoading: Boolean = false,
    val pendingPlaylistRef: TogetherPlaylistRef? = null,
    val skippedItems: List<TogetherSkippedItem> = emptyList(),
    val queueEditable: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

class TogetherViewModel(
    private val session: TogetherSessionPort,
    private val currentQueue: () -> List<TogetherQueueItem>,
    private val preferences: TogetherSettingsPort,
    private val playlistCatalog: TogetherPlaylistCatalogPort = EmptyTogetherPlaylistCatalog,
    private val currentQueueUpdates: Flow<List<TogetherQueueItem>> = emptyFlow(),
    private val queueEditPort: TogetherQueueEditPort = EmptyTogetherQueueEditPort
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        TogetherUiState(settings = preferences.load())
    )
    val uiState: StateFlow<TogetherUiState> = mutableUiState.asStateFlow()

    /**
     * When true, Create draft mirrors the live Media3 queue. Catalog/playlist loads and
     * explicit draft-only mutations detach so playback ticks cannot wipe user intent.
     */
    private var draftFollowsPlayback: Boolean = true
    private var lastFollowedQueueSignature: String = ""

    init {
        viewModelScope.launch {
            session.state.collect { next ->
                val page = when (next) {
                    TogetherSessionState.Idle -> mutableUiState.value.page
                    is TogetherSessionState.Preparing,
                    is TogetherSessionState.Connecting,
                    is TogetherSessionState.WaitingReady,
                    is TogetherSessionState.Active,
                    is TogetherSessionState.Reconnecting,
                    is TogetherSessionState.Leaving -> TogetherPage.Room
                    is TogetherSessionState.Failed -> mutableUiState.value.page
                }
                mutableUiState.value = mutableUiState.value.copy(
                    session = next,
                    page = page,
                    busy = next is TogetherSessionState.Preparing ||
                        next is TogetherSessionState.Connecting ||
                        next is TogetherSessionState.Leaving,
                    queueEditable = session.canEditQueue(),
                    message = (next as? TogetherSessionState.Failed)?.message
                )
            }
        }
        viewModelScope.launch {
            currentQueueUpdates.collect { queue ->
                applyLiveQueueToDraftIfFollowing(queue)
            }
        }
    }

    fun openCreate() {
        val draft = currentQueue().filter(TogetherQueueItem::shareable)
        draftFollowsPlayback = true
        lastFollowedQueueSignature = draftQueueSignature(draft)
        mutableUiState.value = mutableUiState.value.copy(
            page = TogetherPage.Create,
            draftQueue = draft,
            playlistPickerVisible = false,
            pendingPlaylistRef = null,
            skippedItems = emptyList(),
            message = null
        )
    }

    fun openCreateFromPlaylist(ref: TogetherPlaylistRef) {
        draftFollowsPlayback = false
        lastFollowedQueueSignature = ""
        // Clear stale draft immediately so Create cannot commit a previous queue while loading.
        mutableUiState.value = mutableUiState.value.copy(
            page = TogetherPage.Create,
            draftQueue = emptyList(),
            playlistLoading = true,
            playlistPickerVisible = false,
            pendingPlaylistRef = null,
            skippedItems = emptyList(),
            message = null
        )
        loadPlaylist(ref)
    }

    fun openPlaylistPicker() {
        val state = mutableUiState.value
        mutableUiState.value = state.copy(
            playlistPickerVisible = true,
            playlistLoading = true,
            pendingPlaylistRef = null,
            message = null
        )
        viewModelScope.launch {
            runCatching { playlistCatalog.listPlaylists() }
                .onSuccess { result ->
                    mutableUiState.value = mutableUiState.value.copy(
                        availablePlaylists = result.playlists,
                        playlistLoading = false,
                        message = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    )
                }
                .onFailure(::showPlaylistError)
        }
    }

    fun closePlaylistPicker() {
        mutableUiState.value = mutableUiState.value.copy(
            playlistPickerVisible = false,
            pendingPlaylistRef = null,
            message = null
        )
    }

    fun choosePlaylist(ref: TogetherPlaylistRef) {
        val state = mutableUiState.value
        if (state.draftQueue.isNotEmpty()) {
            mutableUiState.value = state.copy(
                pendingPlaylistRef = ref,
                message = "replace_queue_confirmation_required"
            )
            return
        }
        loadPlaylist(ref)
    }

    fun confirmReplacePlaylist() {
        val ref = mutableUiState.value.pendingPlaylistRef ?: return
        loadPlaylist(ref)
    }

    fun openJoin() {
        mutableUiState.value = mutableUiState.value.copy(
            page = TogetherPage.Join,
            joinPreview = null,
            localMatches = emptyMap(),
            message = null
        )
    }

    fun openSettings() {
        mutableUiState.value = mutableUiState.value.copy(page = TogetherPage.Settings, message = null)
    }

    fun back() {
        val next = when (mutableUiState.value.page) {
            TogetherPage.Home -> TogetherPage.Home
            TogetherPage.Room -> TogetherPage.Room
            else -> TogetherPage.Home
        }
        mutableUiState.value = mutableUiState.value.copy(page = next, message = null)
    }

    fun updateRoomCode(value: String) {
        mutableUiState.value = mutableUiState.value.copy(
            roomCodeInput = value.take(96),
            joinPreview = null,
            localMatches = emptyMap(),
            message = null
        )
    }

    fun addDraft(items: List<TogetherQueueItem>) {
        detachDraftFromPlayback()
        val next = (mutableUiState.value.draftQueue + items.filter(TogetherQueueItem::shareable))
            .distinctBy(TogetherQueueItem::dedupeKey)
        mutableUiState.value = mutableUiState.value.copy(
            draftQueue = next,
            skippedItems = emptyList(),
            message = null
        )
    }

    fun removeDraft(index: Int) {
        val draft = mutableUiState.value.draftQueue.toMutableList()
        if (index !in draft.indices) return
        if (draftFollowsPlayback) {
            queueEditPort.remove(draft[index].stableId)
        }
        draft.removeAt(index)
        mutableUiState.value = mutableUiState.value.copy(draftQueue = draft)
        if (draftFollowsPlayback) {
            lastFollowedQueueSignature = draftQueueSignature(draft)
        }
    }

    fun moveDraft(from: Int, to: Int) {
        val draft = mutableUiState.value.draftQueue.toMutableList()
        if (from !in draft.indices || to !in draft.indices || from == to) return
        if (draftFollowsPlayback) {
            // Map by stableId — draft is shareable-filtered and indices must not hit Media3 raw.
            queueEditPort.move(draft[from].stableId, draft[to].stableId)
        }
        val item = draft.removeAt(from)
        draft.add(to, item)
        mutableUiState.value = mutableUiState.value.copy(draftQueue = draft)
        if (draftFollowsPlayback) {
            lastFollowedQueueSignature = draftQueueSignature(draft)
        }
    }

    fun removeRoomQueueItem(index: Int) {
        if (!mutableUiState.value.queueEditable) return
        val queue = when (val room = mutableUiState.value.session) {
            is TogetherSessionState.Active -> room.queue
            is TogetherSessionState.WaitingReady -> room.queue
            else -> emptyList()
        }
        queue.getOrNull(index)?.let { queueEditPort.remove(it.stableId) }
    }

    fun moveRoomQueueItem(from: Int, to: Int) {
        if (!mutableUiState.value.queueEditable) return
        val queue = when (val room = mutableUiState.value.session) {
            is TogetherSessionState.Active -> room.queue
            is TogetherSessionState.WaitingReady -> room.queue
            else -> emptyList()
        }
        val fromId = queue.getOrNull(from)?.stableId ?: return
        val toId = queue.getOrNull(to)?.stableId ?: return
        queueEditPort.move(fromId, toId)
    }

    fun create() {
        val state = mutableUiState.value
        if (state.draftQueue.isEmpty() || state.playlistLoading || state.busy) return
        mutableUiState.value = state.copy(busy = true, message = null)
        viewModelScope.launch {
            session.create(state.draftQueue, state.settings.connectOptions())
                .onFailure { showError(it) }
        }
    }

    fun join() {
        val state = mutableUiState.value
        val code = TogetherRoomCode.normalize(state.roomCodeInput)
        if (!TogetherRoomCode.isValid(code)) {
            mutableUiState.value = state.copy(message = "invalid_room_code")
            return
        }
        if (state.joinPreview == null) {
            mutableUiState.value = state.copy(busy = true, message = null)
            viewModelScope.launch {
                session.previewJoin(code, state.settings.connectOptions())
                    .onSuccess { preview ->
                        mutableUiState.value = mutableUiState.value.copy(
                            busy = false,
                            joinPreview = preview,
                            localMatches = emptyMap(),
                            message = null
                        )
                    }
                    .onFailure(::showError)
            }
            return
        }
        val unmatchedBytes = state.joinPreview.queue
            .filterNot { state.localMatches.containsKey(it.stableId) }
            .sumOf(TogetherQueueItem::sizeBytes)
        if (unmatchedBytes > (state.joinPreview.freeBytes - MINIMUM_FREE_BYTES).coerceAtLeast(0L)) {
            mutableUiState.value = state.copy(message = "not_enough_space")
            return
        }
        mutableUiState.value = state.copy(busy = true, message = null)
        viewModelScope.launch {
            session.join(code, state.localMatches.values.toList(), state.settings.connectOptions())
                .onFailure { showError(it) }
        }
    }

    fun matchLocal(remoteId: String, picked: TogetherQueueItem) {
        val state = mutableUiState.value
        val remote = state.joinPreview?.queue?.firstOrNull { it.stableId == remoteId } ?: return
        mutableUiState.value = state.copy(
            localMatches = state.localMatches + (
                remoteId to picked.copy(
                    stableId = remote.stableId,
                    contentRoot = remote.contentRoot,
                    receivedFileId = remote.receivedFileId
                )
            ),
            message = null
        )
    }

    fun leave() {
        viewModelScope.launch {
            session.leave("user")
            mutableUiState.value = mutableUiState.value.copy(
                page = TogetherPage.Home,
                joinPreview = null,
                localMatches = emptyMap()
            )
        }
    }

    fun save(fileId: String) {
        viewModelScope.launch {
            session.saveReceived(fileId)
                .onSuccess {
                    mutableUiState.value = mutableUiState.value.copy(message = "file_saved")
                }
                .onFailure(::showError)
        }
    }

    fun updateSettings(settings: TogetherSavedSettings) {
        mutableUiState.value = mutableUiState.value.copy(settings = settings)
    }

    fun saveSettings() {
        preferences.save(mutableUiState.value.settings)
        mutableUiState.value = mutableUiState.value.copy(
            page = TogetherPage.Home,
            message = "settings_saved"
        )
    }

    fun testConnection() {
        val state = mutableUiState.value
        mutableUiState.value = state.copy(busy = true, message = null)
        viewModelScope.launch {
            session.testConnection(state.settings.connectOptions())
                .onSuccess { result ->
                    mutableUiState.value = mutableUiState.value.copy(
                        busy = false,
                        message = result
                    )
                }
                .onFailure(::showError)
        }
    }

    private fun applyLiveQueueToDraftIfFollowing(queue: List<TogetherQueueItem>) {
        val current = mutableUiState.value
        if (current.page != TogetherPage.Create || current.session !is TogetherSessionState.Idle) {
            return
        }
        if (!draftFollowsPlayback) {
            return
        }
        val shareable = queue.filter(TogetherQueueItem::shareable)
        val signature = draftQueueSignature(shareable)
        // Index-only / identical identity+order emissions must not rewrite draft or clear messages.
        if (signature == lastFollowedQueueSignature) {
            return
        }
        lastFollowedQueueSignature = signature
        mutableUiState.value = current.copy(draftQueue = shareable)
    }

    private fun loadPlaylist(ref: TogetherPlaylistRef) {
        detachDraftFromPlayback()
        mutableUiState.value = mutableUiState.value.copy(
            // Drop residual draft so Create stays disabled until catalog returns.
            draftQueue = emptyList(),
            playlistLoading = true,
            pendingPlaylistRef = null,
            skippedItems = emptyList(),
            message = null
        )
        viewModelScope.launch {
            runCatching { playlistCatalog.loadPlaylist(ref) }
                .onSuccess { result ->
                    val items = result.items
                        .filter(TogetherQueueItem::shareable)
                        .distinctBy(TogetherQueueItem::dedupeKey)
                    detachDraftFromPlayback()
                    mutableUiState.value = mutableUiState.value.copy(
                        page = TogetherPage.Create,
                        draftQueue = items,
                        playlistPickerVisible = false,
                        playlistLoading = false,
                        skippedItems = result.skipped,
                        message = when {
                            items.isEmpty() -> "playlist_has_no_shareable_tracks"
                            result.skipped.isNotEmpty() -> "playlist_tracks_skipped"
                            else -> null
                        }
                    )
                }
                .onFailure(::showPlaylistError)
        }
    }

    private fun detachDraftFromPlayback() {
        draftFollowsPlayback = false
        lastFollowedQueueSignature = ""
    }

    private fun showPlaylistError(error: Throwable) {
        mutableUiState.value = mutableUiState.value.copy(
            playlistLoading = false,
            message = error.message ?: "playlist_load_failed"
        )
    }

    private fun showError(error: Throwable) {
        mutableUiState.value = mutableUiState.value.copy(
            busy = false,
            message = error.message ?: "together_failed"
        )
    }

    private companion object {
        const val MINIMUM_FREE_BYTES = 512L * 1024L * 1024L

        fun draftQueueSignature(queue: List<TogetherQueueItem>): String =
            queue.joinToString("\u001f") { it.stableId }
    }
}
