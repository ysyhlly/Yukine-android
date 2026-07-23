package app.yukine.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val busy: Boolean = false,
    val message: String? = null
)

class TogetherViewModel(
    private val session: TogetherSessionPort,
    private val currentQueue: () -> List<TogetherQueueItem>,
    private val preferences: TogetherPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        TogetherUiState(settings = preferences.load())
    )
    val uiState: StateFlow<TogetherUiState> = mutableUiState.asStateFlow()

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
                    message = (next as? TogetherSessionState.Failed)?.message
                )
            }
        }
    }

    fun openCreate() {
        mutableUiState.value = mutableUiState.value.copy(
            page = TogetherPage.Create,
            draftQueue = currentQueue().filter(TogetherQueueItem::shareable),
            message = null
        )
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
        val next = (mutableUiState.value.draftQueue + items.filter(TogetherQueueItem::shareable))
            .distinctBy(TogetherQueueItem::sourceUri)
        mutableUiState.value = mutableUiState.value.copy(draftQueue = next, message = null)
    }

    fun removeDraft(index: Int) {
        val draft = mutableUiState.value.draftQueue.toMutableList()
        if (index in draft.indices) draft.removeAt(index)
        mutableUiState.value = mutableUiState.value.copy(draftQueue = draft)
    }

    fun moveDraft(from: Int, to: Int) {
        val draft = mutableUiState.value.draftQueue.toMutableList()
        if (from !in draft.indices || to !in draft.indices || from == to) return
        val item = draft.removeAt(from)
        draft.add(to, item)
        mutableUiState.value = mutableUiState.value.copy(draftQueue = draft)
    }

    fun create() {
        val state = mutableUiState.value
        if (state.draftQueue.isEmpty()) return
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

    private fun showError(error: Throwable) {
        mutableUiState.value = mutableUiState.value.copy(
            busy = false,
            message = error.message ?: "together_failed"
        )
    }

    private companion object {
        const val MINIMUM_FREE_BYTES = 512L * 1024L * 1024L
    }
}
