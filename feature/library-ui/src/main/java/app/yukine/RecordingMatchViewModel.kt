package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.data.RecordingMatchDataSource
import app.yukine.data.RecordingMatchSnapshot
import app.yukine.data.RecordingMergePreview
import app.yukine.data.RecordingMergeSearchResult
import app.yukine.data.RecordingSplitDestination
import app.yukine.data.RecordingSplitOptions
import app.yukine.data.RecordingSplitPreview
import app.yukine.data.RecordingSplitReference
import app.yukine.identity.TrackSourceMapping
import app.yukine.model.TrackIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordingMatchUiState(
    val visible: Boolean = false,
    val localTrackId: Long = -1L,
    val languageMode: String = AppLanguage.MODE_SYSTEM,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val mutating: Boolean = false,
    val snapshot: RecordingMatchSnapshot? = null,
    val merge: RecordingMergeUiState = RecordingMergeUiState(),
    val split: RecordingSplitUiState = RecordingSplitUiState(),
    val messageKey: String = "",
    val errorMessage: String = ""
)

data class RecordingMergeUiState(
    val visible: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<RecordingMergeSearchResult> = emptyList(),
    val previewing: Boolean = false,
    val preview: RecordingMergePreview? = null,
    val committing: Boolean = false,
    val errorMessage: String = ""
)

data class RecordingSplitUiState(
    val visible: Boolean = false,
    val loadingSources: Boolean = false,
    val availableSources: List<TrackSourceMapping> = emptyList(),
    val selectedSourceIds: Set<Long> = emptySet(),
    val previewing: Boolean = false,
    val preview: RecordingSplitPreview? = null,
    val options: RecordingSplitOptions = RecordingSplitOptions(),
    val committing: Boolean = false,
    val errorMessage: String = ""
)

interface RecordingMatchDestinationStateProvider {
    val uiState: StateFlow<RecordingMatchUiState>

    fun close()
    fun loadMore()
    fun confirmCandidate(candidateId: String)
    fun rejectCandidate(candidateId: String)
    fun rejectObviousMismatches()
    fun markAsAlternateVersion(candidateId: String, variantType: String)
    fun requestCandidateRefresh()
    fun openMerge()
    fun closeMerge()
    fun updateMergeQuery(query: String)
    fun searchMergeCandidates()
    fun previewMerge(sourceRecordingId: Long)
    fun confirmMerge()
    fun openSplit()
    fun closeSplit()
    fun toggleSplitSource(sourceId: Long)
    fun previewSplit()
    fun updateSplitDestination(reference: RecordingSplitReference, destination: RecordingSplitDestination)
    fun confirmSplit()
    fun undoIdentityOperation(operationId: Long)
    fun verifySource(sourceId: Long)
    fun setPreferredSource(sourceId: Long)
    fun removeUnavailableSource(sourceId: Long)
}

fun interface RecordingIdentityChangedListener {
    fun onChanged()
}

class RecordingMatchViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(), RecordingMatchDestinationStateProvider {
    private val mutableState = MutableStateFlow(RecordingMatchUiState())
    override val uiState: StateFlow<RecordingMatchUiState> = mutableState.asStateFlow()
    private var dataSource: RecordingMatchDataSource? = null
    private var loadGeneration = 0L
    private var mergeGeneration = 0L
    private var splitGeneration = 0L
    private var identityChangedListener: RecordingIdentityChangedListener? = null

    fun bindDataSource(next: RecordingMatchDataSource?) {
        dataSource = next
        if (next == null) close()
    }

    fun bindIdentityChangedListener(listener: RecordingIdentityChangedListener?) {
        identityChangedListener = listener
    }

    fun open(localTrackId: Long, languageMode: String) {
        if (!TrackIdentity.isUsable(localTrackId)) return
        val generation = ++loadGeneration
        mutableState.value = RecordingMatchUiState(
            visible = true,
            localTrackId = localTrackId,
            languageMode = languageMode,
            loading = true
        )
        loadPage(generation, reset = true)
    }

    override fun close() {
        loadGeneration++
        mergeGeneration++
        splitGeneration++
        mutableState.value = RecordingMatchUiState()
    }

    override fun loadMore() {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        if (current.loading || current.loadingMore || current.mutating) return
        if (snapshot.sources.size >= snapshot.sourceTotal &&
            snapshot.pendingCandidates.size >= snapshot.candidateTotal
        ) return
        val generation = loadGeneration
        mutableState.value = current.copy(loadingMore = true, messageKey = "", errorMessage = "")
        loadPage(generation, reset = false)
    }

    override fun confirmCandidate(candidateId: String) {
        mutate("recording.match.updated") { confirmCandidate(candidateId) }
    }

    override fun rejectCandidate(candidateId: String) {
        mutate("recording.match.updated") { rejectCandidate(candidateId) }
    }

    override fun rejectObviousMismatches() {
        val current = mutableState.value
        val recordingId = current.snapshot?.recording?.recordingId ?: return
        if (!current.visible || current.mutating) return
        val generation = loadGeneration
        mutableState.value = current.copy(mutating = true, messageKey = "", errorMessage = "")
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().rejectObviousMismatches(recordingId) }
            val latest = mutableState.value
            if (generation != loadGeneration || !latest.visible) return@launch
            if (result.isFailure) {
                mutableState.value = latest.copy(
                    mutating = false,
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
                return@launch
            }
            mutableState.value = latest.copy(
                mutating = false,
                messageKey = if (result.getOrDefault(0) > 0) {
                    "recording.match.batch.reject.completed"
                } else {
                    "recording.match.batch.reject.none"
                }
            )
            loadPage(generation, reset = true, preserveMessage = true)
        }
    }

    override fun markAsAlternateVersion(candidateId: String, variantType: String) {
        mutate("recording.match.alternate.updated") {
            markAsAlternateVersion(candidateId, variantType)
        }
    }

    override fun requestCandidateRefresh() {
        val current = mutableState.value
        val recordingId = current.snapshot?.recording?.recordingId ?: return
        if (current.mutating) return
        mutableState.value = current.copy(mutating = true, messageKey = "", errorMessage = "")
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().requestCandidateRefresh(recordingId) }
            val latest = mutableState.value
            if (!latest.visible || latest.localTrackId != current.localTrackId) return@launch
            mutableState.value = latest.copy(
                mutating = false,
                messageKey = if (result.isSuccess) "recording.match.refresh.queued" else "",
                errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
            )
        }
    }

    override fun openMerge() {
        val current = mutableState.value
        if (!current.visible || current.snapshot == null || current.mutating) return
        mutableState.value = current.copy(
            merge = RecordingMergeUiState(visible = true, searching = true),
            messageKey = "",
            errorMessage = ""
        )
        searchMergeCandidatesInternal()
    }

    override fun closeMerge() {
        mergeGeneration++
        val current = mutableState.value
        mutableState.value = current.copy(merge = RecordingMergeUiState())
    }

    override fun updateMergeQuery(query: String) {
        val current = mutableState.value
        if (!current.merge.visible || current.merge.searching || current.merge.committing) return
        mutableState.value = current.copy(
            merge = current.merge.copy(query = query, preview = null, errorMessage = "")
        )
    }

    override fun searchMergeCandidates() {
        val current = mutableState.value
        if (!current.merge.visible || current.merge.searching || current.merge.committing) return
        mutableState.value = current.copy(
            merge = current.merge.copy(searching = true, preview = null, errorMessage = "")
        )
        searchMergeCandidatesInternal()
    }

    override fun previewMerge(sourceRecordingId: Long) {
        val current = mutableState.value
        val targetId = current.snapshot?.recording?.recordingId ?: return
        if (!current.merge.visible || current.merge.previewing || current.merge.committing) return
        val generation = ++mergeGeneration
        mutableState.value = current.copy(
            merge = current.merge.copy(previewing = true, preview = null, errorMessage = "")
        )
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().previewMerge(sourceRecordingId, targetId) }
            val latest = mutableState.value
            if (generation != mergeGeneration || !latest.merge.visible) return@launch
            mutableState.value = latest.copy(
                merge = latest.merge.copy(
                    previewing = false,
                    preview = result.getOrNull(),
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
            )
        }
    }

    override fun confirmMerge() {
        val current = mutableState.value
        val preview = current.merge.preview ?: return
        if (preview.blocked || current.mutating || current.merge.committing) return
        val generation = ++mergeGeneration
        mutableState.value = current.copy(
            mutating = true,
            merge = current.merge.copy(committing = true, errorMessage = "")
        )
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                requireDataSource().mergeRecordings(
                    sourceRecordingId = preview.source.recording.recordingId,
                    targetRecordingId = preview.target.recording.recordingId
                )
            }
            val latest = mutableState.value
            if (generation != mergeGeneration || !latest.visible) return@launch
            if (result.isFailure) {
                mutableState.value = latest.copy(
                    mutating = false,
                    merge = latest.merge.copy(
                        committing = false,
                        errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                    )
                )
                return@launch
            }
            mutableState.value = latest.copy(
                mutating = false,
                merge = RecordingMergeUiState(),
                messageKey = "recording.match.merge.completed"
            )
            identityChangedListener?.onChanged()
            loadPage(loadGeneration, reset = true, preserveMessage = true)
        }
    }

    override fun openSplit() {
        val current = mutableState.value
        val recordingId = current.snapshot?.recording?.recordingId ?: return
        if (!current.visible || current.mutating || current.merge.visible) return
        val generation = ++splitGeneration
        mutableState.value = current.copy(
            split = RecordingSplitUiState(visible = true, loadingSources = true),
            messageKey = "",
            errorMessage = ""
        )
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().sourcesForSplit(recordingId) }
            val latest = mutableState.value
            if (generation != splitGeneration || !latest.split.visible) return@launch
            mutableState.value = latest.copy(
                split = latest.split.copy(
                    loadingSources = false,
                    availableSources = result.getOrDefault(emptyList()),
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
            )
        }
    }

    override fun closeSplit() {
        splitGeneration++
        val current = mutableState.value
        mutableState.value = current.copy(split = RecordingSplitUiState())
    }

    override fun toggleSplitSource(sourceId: Long) {
        val current = mutableState.value
        if (!current.split.visible || current.split.previewing || current.split.committing) return
        val selected = current.split.selectedSourceIds.toMutableSet()
        if (!selected.add(sourceId)) selected.remove(sourceId)
        mutableState.value = current.copy(
            split = current.split.copy(
                selectedSourceIds = selected,
                preview = null,
                errorMessage = ""
            )
        )
    }

    override fun previewSplit() {
        val current = mutableState.value
        val recordingId = current.snapshot?.recording?.recordingId ?: return
        if (!current.split.visible || current.split.previewing || current.split.committing ||
            current.split.selectedSourceIds.isEmpty()
        ) return
        val generation = ++splitGeneration
        val selected = current.split.selectedSourceIds
        mutableState.value = current.copy(
            split = current.split.copy(previewing = true, preview = null, errorMessage = "")
        )
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().previewSplit(recordingId, selected) }
            val latest = mutableState.value
            if (generation != splitGeneration || !latest.split.visible) return@launch
            mutableState.value = latest.copy(
                split = latest.split.copy(
                    previewing = false,
                    preview = result.getOrNull(),
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
            )
        }
    }

    override fun updateSplitDestination(
        reference: RecordingSplitReference,
        destination: RecordingSplitDestination
    ) {
        val current = mutableState.value
        if (!current.split.visible || current.split.committing) return
        val options = when (reference) {
            RecordingSplitReference.FAVORITE -> current.split.options.copy(favoriteDestination = destination)
            RecordingSplitReference.PLAYLISTS -> current.split.options.copy(playlistDestination = destination)
            RecordingSplitReference.QUEUE -> current.split.options.copy(queueDestination = destination)
        }
        mutableState.value = current.copy(split = current.split.copy(options = options))
    }

    override fun confirmSplit() {
        val current = mutableState.value
        val preview = current.split.preview ?: return
        if (current.mutating || current.split.committing) return
        val generation = ++splitGeneration
        val sourceIds = preview.selectedSources.mapTo(linkedSetOf(), TrackSourceMapping::sourceId)
        mutableState.value = current.copy(
            mutating = true,
            split = current.split.copy(committing = true, errorMessage = "")
        )
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().splitSources(sourceIds, current.split.options) }
            val latest = mutableState.value
            if (generation != splitGeneration || !latest.visible) return@launch
            if (result.isFailure) {
                mutableState.value = latest.copy(
                    mutating = false,
                    split = latest.split.copy(
                        committing = false,
                        errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                    )
                )
                return@launch
            }
            mutableState.value = latest.copy(
                mutating = false,
                split = RecordingSplitUiState(),
                messageKey = "recording.match.split.completed"
            )
            identityChangedListener?.onChanged()
            loadPage(loadGeneration, reset = true, preserveMessage = true)
        }
    }

    override fun undoIdentityOperation(operationId: Long) {
        mutate("recording.match.operation.undo.completed", identityChanged = true) {
            undoIdentityOperation(operationId)
        }
    }

    override fun verifySource(sourceId: Long) {
        mutateSuspend("recording.match.source.verified", identityChanged = true) {
            val result = verifySource(sourceId)
            require(result.success) { result.failureReason }
        }
    }

    override fun setPreferredSource(sourceId: Long) {
        mutate("recording.match.source.preferred", identityChanged = true) {
            setPreferredSource(sourceId)
        }
    }

    override fun removeUnavailableSource(sourceId: Long) {
        mutate("recording.match.source.removed", identityChanged = true) {
            removeUnavailableSource(sourceId)
        }
    }

    private fun searchMergeCandidatesInternal() {
        val before = mutableState.value
        val targetId = before.snapshot?.recording?.recordingId ?: return
        val query = before.merge.query
        val generation = ++mergeGeneration
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                requireDataSource().searchMergeCandidates(targetId, query, MERGE_RESULT_LIMIT)
            }
            val latest = mutableState.value
            if (generation != mergeGeneration || !latest.merge.visible) return@launch
            mutableState.value = latest.copy(
                merge = latest.merge.copy(
                    searching = false,
                    results = result.getOrDefault(emptyList()),
                    preview = null,
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
            )
        }
    }

    private fun mutate(
        successKey: String,
        identityChanged: Boolean = false,
        action: RecordingMatchDataSource.() -> Unit
    ) {
        val current = mutableState.value
        if (!current.visible || current.mutating) return
        val generation = loadGeneration
        mutableState.value = current.copy(mutating = true, messageKey = "", errorMessage = "")
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().action() }
            val latest = mutableState.value
            if (generation != loadGeneration || !latest.visible) return@launch
            if (result.isFailure) {
                mutableState.value = latest.copy(
                    mutating = false,
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
                return@launch
            }
            mutableState.value = latest.copy(mutating = false, messageKey = successKey)
            if (identityChanged) identityChangedListener?.onChanged()
            loadPage(generation, reset = true, preserveMessage = true)
        }
    }

    private fun mutateSuspend(
        successKey: String,
        identityChanged: Boolean = false,
        action: suspend RecordingMatchDataSource.() -> Unit
    ) {
        val current = mutableState.value
        if (!current.visible || current.mutating) return
        val generation = loadGeneration
        mutableState.value = current.copy(mutating = true, messageKey = "", errorMessage = "")
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { requireDataSource().action() }
            val latest = mutableState.value
            if (generation != loadGeneration || !latest.visible) return@launch
            if (result.isFailure) {
                mutableState.value = latest.copy(
                    mutating = false,
                    errorMessage = sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                )
                loadPage(generation, reset = true, preserveError = true)
                return@launch
            }
            mutableState.value = latest.copy(mutating = false, messageKey = successKey)
            if (identityChanged) identityChangedListener?.onChanged()
            loadPage(generation, reset = true, preserveMessage = true)
        }
    }

    private fun loadPage(
        generation: Long,
        reset: Boolean,
        preserveMessage: Boolean = false,
        preserveError: Boolean = false
    ) {
        val before = mutableState.value
        val trackId = before.localTrackId
        val sourceOffset = if (reset) 0 else before.snapshot?.sources?.size ?: 0
        val candidateOffset = if (reset) 0 else before.snapshot?.pendingCandidates?.size ?: 0
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                requireDataSource().snapshotForLocalTrack(
                    localTrackId = trackId,
                    sourceOffset = sourceOffset,
                    candidateOffset = candidateOffset,
                    pageSize = PAGE_SIZE
                )
            }
            val latest = mutableState.value
            if (generation != loadGeneration || !latest.visible || latest.localTrackId != trackId) {
                return@launch
            }
            val page = result.getOrNull()
            val merged = if (reset || latest.snapshot == null || page == null) {
                page
            } else {
                page.copy(
                    sources = (latest.snapshot.sources + page.sources).distinctBy { it.sourceId },
                    pendingCandidates = (latest.snapshot.pendingCandidates + page.pendingCandidates)
                        .distinctBy { it.candidateId }
                )
            }
            mutableState.value = latest.copy(
                loading = false,
                loadingMore = false,
                snapshot = merged,
                messageKey = if (preserveMessage) latest.messageKey else "",
                errorMessage = when {
                    result.isFailure -> sanitizeUiError(result.exceptionOrNull()?.message.orEmpty())
                    page == null -> "recording.match.not.found"
                    preserveError -> latest.errorMessage
                    else -> ""
                }
            )
        }
    }

    private fun requireDataSource(): RecordingMatchDataSource =
        requireNotNull(dataSource) { "Recording match data source is not bound" }

    private fun sanitizeUiError(value: String): String = value
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
        .replace(
            Regex("(?i)(authorization|cookie|token|password|secret)\\s*[:=]\\s*\\S+"),
            "$1=[redacted]"
        )
        .trim()
        .take(240)

    private companion object {
        const val PAGE_SIZE = 50
        const val MERGE_RESULT_LIMIT = 20
    }
}
