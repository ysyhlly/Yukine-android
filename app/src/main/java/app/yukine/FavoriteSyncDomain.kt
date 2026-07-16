package app.yukine

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.streaming.StreamingErrorCode
import app.yukine.streaming.StreamingGatewayException
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object FavoriteRecordSyncState {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val RETRY = "RETRY"
    const val NEEDS_CONFIRMATION = "NEEDS_CONFIRMATION"
}

internal enum class FavoriteSyncStatus {
    PENDING,
    MATCHING,
    SYNCED,
    NO_SOURCE,
    AUTH_REQUIRED,
    READ_ONLY,
    RETRYABLE_ERROR,
    NEEDS_CONFIRMATION
}

internal enum class FavoriteSyncAction { ADD, REMOVE }

internal data class UnifiedFavorite(
    val unifiedId: String,
    val localTrackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null,
    val active: Boolean = true,
    val sourceProvider: StreamingProviderName? = null,
    val sourceProviderTrackId: String? = null,
    val lastBatchId: String = "",
    val updatedAtMs: Long = 0L,
    val recordingId: Long = 0L,
    val canonicalUuid: String = ""
)

internal data class ProviderFavoriteMapping(
    val unifiedId: String,
    val provider: StreamingProviderName,
    val providerTrackId: String = "",
    val status: FavoriteSyncStatus = FavoriteSyncStatus.PENDING,
    val confidence: Float = 0f,
    val lastBatchId: String = "",
    val errorMessage: String = "",
    val updatedAtMs: Long = 0L,
    val active: Boolean = true,
    val recordingId: Long = 0L
)

internal data class FavoriteSyncCursor(
    val provider: StreamingProviderName,
    val cursor: String = "",
    val seenProviderTrackIds: Set<String> = emptySet(),
    val lastSyncAtMs: Long = 0L
)

internal data class FavoriteSyncOperation(
    val operationId: String,
    val unifiedId: String,
    val action: FavoriteSyncAction,
    val sourceProvider: StreamingProviderName? = null,
    val targetProvider: StreamingProviderName,
    val batchId: String,
    val status: FavoriteSyncStatus = FavoriteSyncStatus.PENDING,
    val attemptCount: Int = 0,
    val nextAttemptAtMs: Long = 0L,
    val errorMessage: String = "",
    val updatedAtMs: Long = 0L,
    val recordingId: Long = 0L
)

internal data class FavoriteSyncConflict(
    val conflictId: String,
    val unifiedId: String,
    val provider: StreamingProviderName,
    val candidateProviderTrackId: String,
    val confidence: Float,
    val status: FavoriteSyncStatus = FavoriteSyncStatus.NEEDS_CONFIRMATION,
    val createdAtMs: Long = 0L,
    val recordingId: Long = 0L
)

internal data class PendingProviderFavorite(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isrc: String? = null,
    val attemptCount: Int = 1,
    val errorMessage: String = "",
    val updatedAtMs: Long = 0L
)

internal data class FavoriteSyncTaskLog(
    val batchId: String,
    val provider: StreamingProviderName? = null,
    val message: String,
    val status: FavoriteSyncStatus,
    val createdAtMs: Long
)

internal data class ProviderCapability(
    val provider: StreamingProviderName,
    val displayName: String,
    val enabled: Boolean,
    val loggedIn: Boolean,
    val authorized: Boolean = loggedIn,
    val canPullFavorites: Boolean,
    val canAddFavorite: Boolean,
    val canRemoveFavorite: Boolean
)

internal data class FavoriteSyncStoreState(
    val favorites: List<UnifiedFavorite> = emptyList(),
    val mappings: List<ProviderFavoriteMapping> = emptyList(),
    val cursors: List<FavoriteSyncCursor> = emptyList(),
    val operations: List<FavoriteSyncOperation> = emptyList(),
    val conflicts: List<FavoriteSyncConflict> = emptyList(),
    val pendingImports: List<PendingProviderFavorite> = emptyList(),
    val logs: List<FavoriteSyncTaskLog> = emptyList(),
    val preferences: FavoriteSyncPreferences = FavoriteSyncPreferences(),
    val lastSyncAtMs: Long = 0L
)

internal interface FavoriteSyncRepository {
    val state: StateFlow<FavoriteSyncStoreState>
    fun update(transform: (FavoriteSyncStoreState) -> FavoriteSyncStoreState): FavoriteSyncStoreState
    fun beginBatch() = Unit
    fun endBatch() = Unit
}

internal class InMemoryFavoriteSyncRepository(
    initial: FavoriteSyncStoreState = FavoriteSyncStoreState()
) : FavoriteSyncRepository {
    private val lock = Any()
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<FavoriteSyncStoreState> = mutableState.asStateFlow()

    override fun update(transform: (FavoriteSyncStoreState) -> FavoriteSyncStoreState): FavoriteSyncStoreState =
        synchronized(lock) {
            transform(mutableState.value).also { mutableState.value = it }
        }
}

internal data class FavoritePullDelta(
    val added: List<StreamingTrack>,
    val cursor: String = "",
    val observedProviderTrackIds: Set<String> = emptySet(),
    val removedProviderTrackIds: Set<String> = emptySet()
)

internal interface FavoriteProviderAdapter {
    suspend fun capabilities(): List<ProviderCapability>
    suspend fun pullFavoriteDelta(provider: StreamingProviderName, cursor: FavoriteSyncCursor?): FavoritePullDelta
    suspend fun addFavorite(provider: StreamingProviderName, providerTrackId: String)
    suspend fun removeFavorite(provider: StreamingProviderName, providerTrackId: String)
    suspend fun search(provider: StreamingProviderName, track: UnifiedFavorite): List<StreamingTrack>
}

internal interface UnifiedFavoriteLibrary {
    fun importExternalFavorite(track: StreamingTrack): Track
    fun localTrack(localTrackId: Long): Track?
    fun setFavorite(track: Track, favorite: Boolean)
    fun isFavorite(localTrackId: Long): Boolean
    fun canonicalId(localTrackId: Long): String? = null
    fun recordingId(localTrackId: Long): Long = 0L
    fun confirmedProviderTrackId(recordingId: Long, provider: StreamingProviderName): String = ""
    fun confirmDirectProviderSource(
        localTrackId: Long,
        provider: StreamingProviderName,
        providerTrackId: String
    ): Boolean = false
    fun favoriteTracks(): List<Track> = emptyList()
    fun updateFavoriteSyncState(recordingId: Long, syncState: String) = Unit
}

internal fun interface FavoriteSyncNetworkPolicy {
    fun canSync(wifiOnly: Boolean): Boolean
}

internal object AllowFavoriteSyncNetwork : FavoriteSyncNetworkPolicy {
    override fun canSync(wifiOnly: Boolean): Boolean = true
}

@Singleton
internal class FavoriteSyncEventBus @Inject constructor() {
    private val pending = CopyOnWriteArrayList<Pair<Track, Boolean>>()
    @Volatile
    private var listener: ((Track, Boolean) -> Unit)? = null

    fun publish(track: Track, favorite: Boolean) {
        val target = listener
        if (target == null) {
            pending += track to favorite
        } else {
            target(track, favorite)
        }
    }

    fun bind(listener: (Track, Boolean) -> Unit) {
        this.listener = listener
        val queued = pending.toList()
        pending.clear()
        queued.forEach { (track, favorite) -> listener(track, favorite) }
    }

    fun unbind() {
        listener = null
    }
}

internal class FavoriteSyncCoordinator(
    private val repository: FavoriteSyncRepository,
    private val providers: FavoriteProviderAdapter,
    private val library: UnifiedFavoriteLibrary,
    private val trackMatches: StreamingTrackMatchUseCase,
    private val eventBus: FavoriteSyncEventBus,
    private val networkPolicy: FavoriteSyncNetworkPolicy = AllowFavoriteSyncNetwork,
    private val clockMs: () -> Long = System::currentTimeMillis,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    syncParallelism: Int = 4
) : FavoriteSyncController {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val syncMutex = Mutex()
    private val mutableDashboard = MutableStateFlow(dashboard(repository.state.value, false))
    override val dashboard: StateFlow<FavoriteSyncDashboard> = mutableDashboard.asStateFlow()
    private var refreshLibrary: Runnable? = null
    private var activeJob: Job? = null
    private val boundedSyncParallelism = syncParallelism.coerceIn(1, 8)

    fun start(refreshLibrary: Runnable? = null) {
        this.refreshLibrary = refreshLibrary
        eventBus.bind { track, favorite ->
            scope.launch { onLocalFavoriteChanged(track, favorite) }
        }
        refreshDashboard(false)
        scope.launch {
            syncMutex.withLock {
                library.favoriteTracks().forEach { track ->
                    upsertLocalFavorite(track, true, "bootstrap")
                }
                refreshDashboard(false)
            }
        }
    }

    override fun requestIncrementalSync() {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch { syncIncremental() }
    }

    suspend fun syncIncremental(): FavoriteSyncDashboard = syncMutex.withLock {
        repository.beginBatch()
        try {
            val preferences = repository.state.value.preferences
            if (!networkPolicy.canSync(preferences.wifiOnly)) {
                appendLog("network", null, "Waiting for an allowed network", FavoriteSyncStatus.PENDING)
                return@withLock refreshDashboard(false)
            }
            refreshDashboard(true)
            val batchId = batchId()
            val capabilities = runCatching { providers.capabilities() }.getOrElse { error ->
                appendLog(batchId, null, safeSyncMessage(error.message.orEmpty()), FavoriteSyncStatus.RETRYABLE_ERROR)
                return@withLock finishSync()
            }
            for (capability in capabilities.filter { it.enabled && it.canPullFavorites }) {
                if (!capability.loggedIn || !capability.authorized) {
                    appendLog(batchId, capability.provider, "Authentication required", FavoriteSyncStatus.AUTH_REQUIRED)
                    continue
                }
                runCatching { pullProviderIncrement(capability, capabilities, batchId) }
                    .onFailure { error ->
                        appendLog(
                            batchId,
                            capability.provider,
                            safeSyncMessage(error.message.orEmpty()),
                            error.syncFailureStatus()
                        )
                    }
            }
            retryPendingOperations(capabilities, batchId)
            finishSync()
        } finally {
            repository.endBatch()
        }
    }

    suspend fun onLocalFavoriteChanged(track: Track, favorite: Boolean) = syncMutex.withLock {
        val batchId = batchId()
        val unified = upsertLocalFavorite(track, favorite, batchId)
        if (favorite) {
            library.updateFavoriteSyncState(unified.recordingId, FavoriteRecordSyncState.PENDING)
        }
        if (!favorite && !repository.state.value.preferences.propagateRemovals) {
            appendLog(batchId, null, "Local removal retained without remote propagation", FavoriteSyncStatus.SYNCED)
            refreshLibrary?.run()
            refreshDashboard(false)
            return@withLock
        }
        val capabilities = runCatching { providers.capabilities() }.getOrElse { emptyList() }
        propagate(
            unified,
            if (favorite) FavoriteSyncAction.ADD else FavoriteSyncAction.REMOVE,
            unified.sourceProvider,
            capabilities,
            batchId
        )
        refreshFavoriteSyncState(unified, if (favorite) FavoriteSyncAction.ADD else FavoriteSyncAction.REMOVE)
        refreshLibrary?.run()
        refreshDashboard(false)
    }

    override fun updatePreferences(transform: (FavoriteSyncPreferences) -> FavoriteSyncPreferences) {
        repository.update { state ->
            state.copy(preferences = transform(state.preferences).copy(confirmLowConfidence = true))
        }
        refreshDashboard(activeJob?.isActive == true)
    }

    fun close() {
        eventBus.unbind()
        activeJob?.cancel()
        scope.cancel()
    }

    private suspend fun pullProviderIncrement(
        capability: ProviderCapability,
        allCapabilities: List<ProviderCapability>,
        batchId: String
    ) {
        val cursor = repository.state.value.cursors.firstOrNull { it.provider == capability.provider }
        val delta = providers.pullFavoriteDelta(capability.provider, cursor)
        val removals = delta.removedProviderTrackIds
        val additions = delta.added
        forEachConcurrent(additions) { track ->
            ingestExternalFavorite(track, allCapabilities, batchId)
        }
        if (repository.state.value.preferences.propagateRemovals) {
            forEachConcurrent(removals.toList()) { providerTrackId ->
                ingestExternalRemoval(capability.provider, providerTrackId, allCapabilities, batchId)
            }
        }
        upsertCursor(
            FavoriteSyncCursor(
                provider = capability.provider,
                cursor = delta.cursor,
                seenProviderTrackIds = delta.observedProviderTrackIds,
                lastSyncAtMs = clockMs()
            )
        )
        appendLog(
            batchId,
            capability.provider,
            "Merged ${additions.size} new favorites and ${removals.size} removals",
            FavoriteSyncStatus.SYNCED
        )
    }

    /**
     * Completes a bounded batch before advancing to the next one. Successful siblings are retained
     * when one item fails, while the provider cursor remains unchanged so the failed delta retries.
     */
    private suspend fun <T> forEachConcurrent(values: List<T>, block: suspend (T) -> Unit) {
        values.chunked(boundedSyncParallelism).forEach { chunk ->
            val failures = coroutineScope {
                chunk.map { value ->
                    async {
                        try {
                            block(value)
                            null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            error
                        }
                    }
                }.awaitAll()
            }
            failures.firstOrNull { it != null }?.let { throw it }
        }
    }

    private suspend fun ingestExternalFavorite(
        source: StreamingTrack,
        capabilities: List<ProviderCapability>,
        batchId: String
    ) {
        val existingMapping = repository.state.value.mappings.firstOrNull {
            it.provider == source.provider && it.providerTrackId == source.providerTrackId
        }
        if (existingMapping?.active == true) {
            acknowledge(source.provider, source.providerTrackId)
            return
        }
        if (existingMapping != null) {
            val existingUnified = repository.state.value.favorites.firstOrNull {
                sameFavoriteIdentity(it.recordingId, it.unifiedId, existingMapping.recordingId, existingMapping.unifiedId)
            }
            val localTrack = existingUnified?.let { library.localTrack(it.localTrackId) }
            if (existingUnified != null && localTrack != null) {
                library.setFavorite(localTrack, true)
                val reactivated = canonicalized(existingUnified, localTrack, source.isrc).copy(
                    active = true,
                    sourceProvider = source.provider,
                    sourceProviderTrackId = source.providerTrackId,
                    lastBatchId = batchId,
                    updatedAtMs = clockMs()
                )
                upsertUnified(reactivated)
                upsertMapping(existingMapping.copy(
                    unifiedId = reactivated.unifiedId,
                    recordingId = reactivated.recordingId,
                    active = true,
                    lastBatchId = batchId,
                    updatedAtMs = clockMs()
                ))
                acknowledge(source.provider, source.providerTrackId)
                propagate(reactivated, FavoriteSyncAction.ADD, source.provider, capabilities, batchId)
                refreshFavoriteSyncState(reactivated, FavoriteSyncAction.ADD)
                return
            }
        }
        val localTrack = try {
            library.importExternalFavorite(source)
        } catch (error: Throwable) {
            upsertPendingImport(source, error.message.orEmpty())
            throw error
        }
        if (!library.confirmDirectProviderSource(localTrack.id, source.provider, source.providerTrackId)) {
            upsertPendingImport(source, "Canonical provider source is not available")
            error("Canonical provider source is not available")
        }
        val existing = findUnified(localTrack, source.isrc)
        val unified = canonicalized(existing, localTrack, source.isrc).copy(
            active = true,
            sourceProvider = source.provider,
            sourceProviderTrackId = source.providerTrackId,
            lastBatchId = batchId,
            updatedAtMs = clockMs()
        )
        if (unified.recordingId <= 0L) {
            upsertPendingImport(source, "Canonical recording is not available")
            error("Canonical recording is not available")
        }
        removePendingImport(source.provider, source.providerTrackId)
        upsertUnified(unified)
        upsertMapping(
            ProviderFavoriteMapping(
                unifiedId = unified.unifiedId,
                provider = source.provider,
                providerTrackId = source.providerTrackId,
                status = FavoriteSyncStatus.SYNCED,
                confidence = 1f,
                lastBatchId = batchId,
                updatedAtMs = clockMs(),
                recordingId = unified.recordingId
            )
        )
        trackMatches.saveProviderTrackId(localTrack, source.provider, source.providerTrackId)
        acknowledge(source.provider, source.providerTrackId)
        propagate(unified, FavoriteSyncAction.ADD, source.provider, capabilities, batchId)
        refreshFavoriteSyncState(unified, FavoriteSyncAction.ADD)
    }

    private suspend fun ingestExternalRemoval(
        sourceProvider: StreamingProviderName,
        providerTrackId: String,
        capabilities: List<ProviderCapability>,
        batchId: String
    ) {
        val sourceMapping = repository.state.value.mappings.firstOrNull {
            it.provider == sourceProvider && it.providerTrackId == providerTrackId && it.active
        } ?: return
        val unified = repository.state.value.favorites.firstOrNull {
            sameFavoriteIdentity(it.recordingId, it.unifiedId, sourceMapping.recordingId, sourceMapping.unifiedId)
        } ?: return
        val inactive = unified.copy(
            active = false,
            sourceProvider = sourceProvider,
            sourceProviderTrackId = providerTrackId,
            lastBatchId = batchId,
            updatedAtMs = clockMs()
        )
        upsertUnified(inactive)
        upsertMapping(sourceMapping.copy(active = false, lastBatchId = batchId, updatedAtMs = clockMs()))
        library.localTrack(unified.localTrackId)?.let { localTrack ->
            if (library.isFavorite(localTrack.id)) {
                library.setFavorite(localTrack, false)
            }
        }
        propagate(inactive, FavoriteSyncAction.REMOVE, sourceProvider, capabilities, batchId)
    }

    private suspend fun retryPendingOperations(capabilities: List<ProviderCapability>, batchId: String) {
        val retryable = repository.state.value.operations.filter {
            it.batchId != batchId && when (it.status) {
                FavoriteSyncStatus.PENDING, FavoriteSyncStatus.AUTH_REQUIRED -> true
                FavoriteSyncStatus.RETRYABLE_ERROR -> it.nextAttemptAtMs <= clockMs()
                else -> false
            }
        }
        retryable.forEach { operation ->
                val unified = repository.state.value.favorites.firstOrNull {
                    sameFavoriteIdentity(it.recordingId, it.unifiedId, operation.recordingId, operation.unifiedId)
                }
                    ?: return@forEach
                syncToTarget(unified, operation.action, operation.sourceProvider, operation.targetProvider, capabilities, batchId)
            }
    }

    private suspend fun propagate(
        unified: UnifiedFavorite,
        action: FavoriteSyncAction,
        sourceProvider: StreamingProviderName?,
        capabilities: List<ProviderCapability>,
        batchId: String
    ) {
        if (unified.recordingId <= 0L) {
            library.updateFavoriteSyncState(unified.recordingId, FavoriteRecordSyncState.PENDING)
            return
        }
        capabilities.filter {
            it.enabled && it.provider != sourceProvider && (it.canAddFavorite || it.canRemoveFavorite)
        }.forEach { capability ->
            syncToTarget(unified, action, sourceProvider, capability.provider, capabilities, batchId)
        }
    }

    private suspend fun syncToTarget(
        unified: UnifiedFavorite,
        action: FavoriteSyncAction,
        sourceProvider: StreamingProviderName?,
        targetProvider: StreamingProviderName,
        capabilities: List<ProviderCapability>,
        batchId: String
    ) {
        val capability = capabilities.firstOrNull { it.provider == targetProvider } ?: return
        val existing = mapping(unified, targetProvider)
        val confirmedTargetProviderTrackId = library.confirmedProviderTrackId(
            unified.recordingId,
            targetProvider
        ).trim()
        if (action == FavoriteSyncAction.ADD &&
            existing?.status == FavoriteSyncStatus.SYNCED &&
            existing.active &&
            confirmedTargetProviderTrackId.isNotBlank() &&
            existing.providerTrackId == confirmedTargetProviderTrackId
        ) {
            return
        }
        if (action == FavoriteSyncAction.REMOVE && existing?.active == false) return
        val operation = operation(unified, action, sourceProvider, targetProvider, batchId, FavoriteSyncStatus.PENDING)
        upsertOperation(operation)
        if (!capability.loggedIn || !capability.authorized) {
            updateTargetFailure(unified, operation, FavoriteSyncStatus.AUTH_REQUIRED, "Authentication required")
            return
        }
        val writable = if (action == FavoriteSyncAction.ADD) capability.canAddFavorite else capability.canRemoveFavorite
        if (!writable) {
            updateTargetFailure(unified, operation, FavoriteSyncStatus.READ_ONLY, "Provider is read-only")
            return
        }
        val localTrack = library.localTrack(unified.localTrackId) ?: unified.toTrack()
        val match = try {
            matchTarget(
                unified,
                localTrack,
                targetProvider,
                confirmedTargetProviderTrackId
            )
        } catch (error: Throwable) {
            updateTargetFailure(unified, operation, error.syncFailureStatus(), error.message.orEmpty())
            return
        }
        if (match == null) {
            updateTargetFailure(unified, operation, FavoriteSyncStatus.NO_SOURCE, "No confirmed canonical source")
            return
        }
        if (match.status == FavoriteSyncStatus.NEEDS_CONFIRMATION) {
            upsertConflict(
                FavoriteSyncConflict(
                    conflictId = favoriteConflictId(unified.recordingId, unified.unifiedId, targetProvider),
                    unifiedId = unified.unifiedId,
                    provider = targetProvider,
                    candidateProviderTrackId = match.providerTrackId,
                    confidence = match.confidence,
                    createdAtMs = clockMs(),
                    recordingId = unified.recordingId
                )
            )
            updateTargetFailure(unified, operation, FavoriteSyncStatus.NEEDS_CONFIRMATION, "Low confidence match")
            return
        }
        try {
            if (action == FavoriteSyncAction.ADD) {
                providers.addFavorite(targetProvider, match.providerTrackId)
            } else {
                providers.removeFavorite(targetProvider, match.providerTrackId)
            }
            trackMatches.saveProviderTrackId(localTrack, targetProvider, match.providerTrackId)
            if (action == FavoriteSyncAction.ADD) {
                acknowledge(targetProvider, match.providerTrackId)
            } else {
                forgetAcknowledgement(targetProvider, match.providerTrackId)
            }
            upsertMapping(
                ProviderFavoriteMapping(
                    unifiedId = unified.unifiedId,
                    provider = targetProvider,
                    providerTrackId = match.providerTrackId,
                    status = FavoriteSyncStatus.SYNCED,
                    confidence = match.confidence,
                    lastBatchId = batchId,
                    updatedAtMs = clockMs(),
                    active = action == FavoriteSyncAction.ADD,
                    recordingId = unified.recordingId
                )
            )
            upsertOperation(operation.copy(status = FavoriteSyncStatus.SYNCED, attemptCount = operation.attemptCount + 1, updatedAtMs = clockMs()))
            refreshFavoriteSyncState(unified, action)
        } catch (error: Throwable) {
            updateTargetFailure(unified, operation, error.syncFailureStatus(), error.message.orEmpty())
        }
    }

    private suspend fun matchTarget(
        unified: UnifiedFavorite,
        localTrack: Track,
        provider: StreamingProviderName,
        confirmedProviderTrackId: String
    ): FavoriteTargetMatch? {
        confirmedProviderTrackId.takeIf { it.isNotBlank() }?.let { providerTrackId ->
            return FavoriteTargetMatch(providerTrackId, FavoriteSyncStatus.SYNCED, 1f)
        }
        val savedMapping = mapping(unified, provider)
        val candidateProviderTrackId = savedMapping?.providerTrackId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: trackMatches.providerTrackIdFor(localTrack, provider)
                .trim()
                .takeIf { it.isNotBlank() && !isStreamingNoSourceMatch(it) }
            ?: return null
        return FavoriteTargetMatch(
            candidateProviderTrackId,
            FavoriteSyncStatus.NEEDS_CONFIRMATION,
            savedMapping?.confidence?.coerceIn(0f, 1f) ?: 0f
        )
    }

    private fun upsertLocalFavorite(track: Track, favorite: Boolean, batchId: String): UnifiedFavorite {
        val existing = findUnified(track, null)
        val provider = StreamingDataPathMetadata.provider(track.dataPath)
        val providerTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
        val unified = canonicalized(existing, track, null).copy(
            active = favorite,
            sourceProvider = provider ?: existing?.sourceProvider,
            sourceProviderTrackId = providerTrackId.takeIf { it.isNotBlank() }
                ?: existing?.sourceProviderTrackId,
            lastBatchId = batchId,
            updatedAtMs = clockMs()
        )
        upsertUnified(unified)
        if (provider != null && providerTrackId.isNotBlank() && unified.recordingId > 0L) {
            val directSourceConfirmed = library.confirmDirectProviderSource(
                track.id,
                provider,
                providerTrackId
            )
            upsertMapping(
                ProviderFavoriteMapping(
                    unifiedId = unified.unifiedId,
                    provider = provider,
                    providerTrackId = providerTrackId,
                    status = if (directSourceConfirmed) {
                        FavoriteSyncStatus.SYNCED
                    } else {
                        FavoriteSyncStatus.NEEDS_CONFIRMATION
                    },
                    confidence = if (directSourceConfirmed) 1f else 0f,
                    lastBatchId = batchId,
                    updatedAtMs = clockMs(),
                    active = favorite,
                    recordingId = unified.recordingId
                )
            )
        }
        return unified
    }

    private fun findUnified(track: Track, isrc: String?): UnifiedFavorite? {
        val recordingId = library.recordingId(track.id)
        if (recordingId > 0L) {
            repository.state.value.favorites.firstOrNull { it.recordingId == recordingId }?.let { return it }
        }
        val canonicalId = library.canonicalId(track.id)?.trim().orEmpty()
        if (canonicalId.isNotBlank()) {
            repository.state.value.favorites.firstOrNull {
                it.unifiedId == "recording:$canonicalId"
            }?.let { return it }
        }
        val normalizedIsrc = StreamingTrackMatchPolicy.normalizeIsrc(isrc)
        val trackReference = StreamingTrackMatchPolicy.reference(track)
        return repository.state.value.favorites.firstOrNull { favorite ->
            (normalizedIsrc.isNotBlank() &&
                StreamingTrackMatchPolicy.normalizeIsrc(favorite.isrc) == normalizedIsrc) ||
                favorite.localTrackId == track.id ||
                StreamingTrackMatchPolicy.isSameRecording(
                    StreamingTrackMatchPolicy.Reference(
                        title = favorite.title,
                        artist = favorite.artist,
                        album = favorite.album,
                        durationMs = favorite.durationMs.takeIf { it > 0L },
                        isrc = favorite.isrc
                    ),
                    trackReference
                )
        }
    }

    private fun unifiedFavorite(track: Track, isrc: String?): UnifiedFavorite = UnifiedFavorite(
        unifiedId = unifiedId(track, isrc),
        localTrackId = track.id,
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = track.durationMs,
        isrc = isrc?.takeIf { it.isNotBlank() },
        updatedAtMs = clockMs(),
        recordingId = library.recordingId(track.id),
        canonicalUuid = library.canonicalId(track.id)?.trim().orEmpty()
    )

    private fun unifiedId(track: Track, isrc: String?): String {
        library.canonicalId(track.id)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return "recording:$it"
        }
        val normalizedIsrc = StreamingTrackMatchPolicy.normalizeIsrc(isrc)
        if (normalizedIsrc.isNotBlank()) return "isrc:$normalizedIsrc"
        return "meta:${StreamingTrackMatchPolicy.canonicalTitle(track.title)}|" +
            "${StreamingTrackMatchPolicy.canonicalArtistKey(listOf(track.artist))}|" +
            "${StreamingTrackMatchPolicy.canonicalAlbum(track.album)}|${track.durationMs / 1_000L}"
    }

    private fun canonicalized(existing: UnifiedFavorite?, track: Track, isrc: String?): UnifiedFavorite {
        val discoveredRecordingId = library.recordingId(track.id)
        val discoveredCanonicalUuid = library.canonicalId(track.id)?.trim().orEmpty()
        val desiredRecordingId = existing?.recordingId?.takeIf { it > 0L } ?: discoveredRecordingId
        val desiredCanonicalUuid = existing?.canonicalUuid?.takeIf { it.isNotBlank() } ?: discoveredCanonicalUuid
        val desiredId = desiredCanonicalUuid.takeIf { it.isNotBlank() }?.let { "recording:$it" }
            ?: unifiedId(track, isrc)
        val value = existing ?: return unifiedFavorite(track, isrc)
        if (value.unifiedId == desiredId && value.recordingId == desiredRecordingId) return value
        val rekeyed = value.copy(
            unifiedId = desiredId,
            localTrackId = if (value.recordingId > 0L) value.localTrackId else track.id,
            recordingId = desiredRecordingId,
            canonicalUuid = desiredCanonicalUuid
        )
        repository.update { state ->
            state.copy(
                favorites = state.favorites.filterNot {
                    sameFavoriteIdentity(it.recordingId, it.unifiedId, value.recordingId, value.unifiedId)
                }.upsertBy({ favoriteIdentityKey(it.recordingId, it.unifiedId) }, rekeyed),
                mappings = state.mappings.map {
                    if (sameFavoriteIdentity(it.recordingId, it.unifiedId, value.recordingId, value.unifiedId)) {
                        it.copy(unifiedId = desiredId, recordingId = desiredRecordingId)
                    } else it
                },
                operations = state.operations.map {
                    if (sameFavoriteIdentity(it.recordingId, it.unifiedId, value.recordingId, value.unifiedId)) {
                        it.copy(
                            operationId = favoriteOperationId(
                                it.action,
                                desiredRecordingId,
                                desiredId,
                                it.targetProvider
                            ),
                            unifiedId = desiredId,
                            recordingId = desiredRecordingId
                        )
                    } else it
                }.groupBy { it.operationId }.values.map { duplicates ->
                    duplicates.maxBy { it.updatedAtMs }
                },
                conflicts = state.conflicts.map {
                    if (sameFavoriteIdentity(it.recordingId, it.unifiedId, value.recordingId, value.unifiedId)) {
                        it.copy(
                            conflictId = favoriteConflictId(desiredRecordingId, desiredId, it.provider),
                            unifiedId = desiredId,
                            recordingId = desiredRecordingId
                        )
                    } else it
                }.groupBy { it.conflictId }.values.map { duplicates ->
                    duplicates.maxBy { it.createdAtMs }
                }
            )
        }
        return rekeyed
    }

    private fun mapping(unified: UnifiedFavorite, provider: StreamingProviderName): ProviderFavoriteMapping? =
        repository.state.value.mappings.firstOrNull {
            it.provider == provider &&
                sameFavoriteIdentity(it.recordingId, it.unifiedId, unified.recordingId, unified.unifiedId)
        }

    private fun upsertUnified(value: UnifiedFavorite) {
        repository.update { state ->
            state.copy(favorites = state.favorites.upsertBy({ favoriteIdentityKey(it.recordingId, it.unifiedId) }, value))
        }
    }

    private fun upsertMapping(value: ProviderFavoriteMapping) {
        repository.update { state ->
            state.copy(mappings = state.mappings.upsertBy({
                "${favoriteIdentityKey(it.recordingId, it.unifiedId)}:${it.provider.wireName}"
            }, value))
        }
    }

    private fun upsertCursor(value: FavoriteSyncCursor) {
        repository.update { state -> state.copy(cursors = state.cursors.upsertBy({ it.provider.wireName }, value)) }
    }

    private fun upsertOperation(value: FavoriteSyncOperation) {
        repository.update { state -> state.copy(operations = state.operations.upsertBy({ it.operationId }, value)) }
    }

    private fun upsertConflict(value: FavoriteSyncConflict) {
        repository.update { state -> state.copy(conflicts = state.conflicts.upsertBy({ it.conflictId }, value)) }
    }

    private fun upsertPendingImport(source: StreamingTrack, message: String) {
        repository.update { state ->
            val previous = state.pendingImports.firstOrNull {
                it.provider == source.provider && it.providerTrackId == source.providerTrackId
            }
            val pending = PendingProviderFavorite(
                provider = source.provider,
                providerTrackId = source.providerTrackId,
                title = source.title,
                artist = source.artist,
                album = source.album.orEmpty(),
                durationMs = source.durationMs ?: 0L,
                isrc = source.isrc,
                attemptCount = (previous?.attemptCount ?: 0) + 1,
                errorMessage = safeSyncMessage(message),
                updatedAtMs = clockMs()
            )
            state.copy(pendingImports = state.pendingImports.upsertBy({
                "${it.provider.wireName}:${it.providerTrackId}"
            }, pending))
        }
    }

    private fun removePendingImport(provider: StreamingProviderName, providerTrackId: String) {
        repository.update { state ->
            state.copy(pendingImports = state.pendingImports.filterNot {
                it.provider == provider && it.providerTrackId == providerTrackId
            })
        }
    }

    private fun acknowledge(provider: StreamingProviderName, providerTrackId: String) {
        val current = repository.state.value.cursors.firstOrNull { it.provider == provider }
            ?: FavoriteSyncCursor(provider)
        upsertCursor(current.copy(seenProviderTrackIds = current.seenProviderTrackIds + providerTrackId))
    }

    private fun forgetAcknowledgement(provider: StreamingProviderName, providerTrackId: String) {
        val current = repository.state.value.cursors.firstOrNull { it.provider == provider } ?: return
        upsertCursor(current.copy(seenProviderTrackIds = current.seenProviderTrackIds - providerTrackId))
    }

    private fun updateTargetFailure(
        unified: UnifiedFavorite,
        operation: FavoriteSyncOperation,
        status: FavoriteSyncStatus,
        message: String
    ) {
        val safeMessage = safeSyncMessage(message)
        val existing = mapping(unified, operation.targetProvider)
        upsertMapping(
            ProviderFavoriteMapping(
                unifiedId = unified.unifiedId,
                provider = operation.targetProvider,
                providerTrackId = existing?.providerTrackId.orEmpty(),
                status = status,
                lastBatchId = operation.batchId,
                errorMessage = safeMessage,
                updatedAtMs = clockMs(),
                active = existing?.active ?: (operation.action == FavoriteSyncAction.REMOVE),
                recordingId = unified.recordingId
            )
        )
        upsertOperation(
            operation.copy(
                status = status,
                attemptCount = operation.attemptCount + 1,
                nextAttemptAtMs = if (status == FavoriteSyncStatus.RETRYABLE_ERROR) clockMs() + 60_000L else 0L,
                errorMessage = safeMessage,
                updatedAtMs = clockMs()
            )
        )
        appendLog(operation.batchId, operation.targetProvider, safeMessage, status)
        refreshFavoriteSyncState(unified, operation.action)
    }

    private fun operation(
        unified: UnifiedFavorite,
        action: FavoriteSyncAction,
        sourceProvider: StreamingProviderName?,
        targetProvider: StreamingProviderName,
        batchId: String,
        status: FavoriteSyncStatus
    ): FavoriteSyncOperation {
        val id = favoriteOperationId(action, unified.recordingId, unified.unifiedId, targetProvider)
        val previous = repository.state.value.operations.firstOrNull { it.operationId == id }
        return FavoriteSyncOperation(
            operationId = id,
            unifiedId = unified.unifiedId,
            action = action,
            sourceProvider = sourceProvider,
            targetProvider = targetProvider,
            batchId = batchId,
            status = status,
            attemptCount = previous?.attemptCount ?: 0,
            updatedAtMs = clockMs(),
            recordingId = unified.recordingId
        )
    }

    private fun refreshFavoriteSyncState(unified: UnifiedFavorite, action: FavoriteSyncAction) {
        if (action == FavoriteSyncAction.REMOVE || unified.recordingId <= 0L) return
        val operations = repository.state.value.operations.filter {
            it.action == FavoriteSyncAction.ADD &&
                sameFavoriteIdentity(it.recordingId, it.unifiedId, unified.recordingId, unified.unifiedId)
        }
        val hasSyncedProviderMapping = repository.state.value.mappings.any {
            it.status == FavoriteSyncStatus.SYNCED && it.active &&
                sameFavoriteIdentity(it.recordingId, it.unifiedId, unified.recordingId, unified.unifiedId) &&
                library.confirmedProviderTrackId(unified.recordingId, it.provider) == it.providerTrackId
        }
        val syncState = when {
            operations.any { it.status == FavoriteSyncStatus.NEEDS_CONFIRMATION || it.status == FavoriteSyncStatus.NO_SOURCE } ->
                FavoriteRecordSyncState.NEEDS_CONFIRMATION
            operations.any {
                it.status == FavoriteSyncStatus.RETRYABLE_ERROR || it.status == FavoriteSyncStatus.AUTH_REQUIRED ||
                    it.status == FavoriteSyncStatus.READ_ONLY
            } -> FavoriteRecordSyncState.RETRY
            operations.any { it.status == FavoriteSyncStatus.PENDING || it.status == FavoriteSyncStatus.MATCHING } ->
                FavoriteRecordSyncState.PENDING
            operations.isNotEmpty() && operations.all { it.status == FavoriteSyncStatus.SYNCED } ->
                FavoriteRecordSyncState.SYNCED
            hasSyncedProviderMapping -> FavoriteRecordSyncState.SYNCED
            else -> FavoriteRecordSyncState.LOCAL_ONLY
        }
        library.updateFavoriteSyncState(unified.recordingId, syncState)
    }

    private fun appendLog(batchId: String, provider: StreamingProviderName?, message: String, status: FavoriteSyncStatus) {
        repository.update { state ->
            state.copy(
                logs = (state.logs + FavoriteSyncTaskLog(batchId, provider, message, status, clockMs())).takeLast(100)
            )
        }
    }

    private fun finishSync(): FavoriteSyncDashboard {
        repository.update { it.copy(lastSyncAtMs = clockMs()) }
        refreshLibrary?.run()
        return refreshDashboard(false)
    }

    private fun refreshDashboard(running: Boolean): FavoriteSyncDashboard {
        val value = dashboard(repository.state.value, running)
        mutableDashboard.value = value
        return value
    }

    private fun batchId(): String = "${clockMs()}-${UUID.randomUUID()}"

    private fun dashboard(state: FavoriteSyncStoreState, running: Boolean): FavoriteSyncDashboard {
        val pending = state.pendingImports.size + state.operations.count {
            it.status == FavoriteSyncStatus.PENDING || it.status == FavoriteSyncStatus.MATCHING ||
                it.status == FavoriteSyncStatus.AUTH_REQUIRED || it.status == FavoriteSyncStatus.NEEDS_CONFIRMATION
        }
        val failures = state.operations.count { it.status == FavoriteSyncStatus.RETRYABLE_ERROR }
        return FavoriteSyncDashboard(state.lastSyncAtMs, pending, failures, running, state.preferences)
    }

    private fun Throwable.syncFailureStatus(): FavoriteSyncStatus {
        val gateway = this as? StreamingGatewayException
        return when {
            gateway?.code == StreamingErrorCode.AUTH_REQUIRED -> FavoriteSyncStatus.AUTH_REQUIRED
            gateway?.code == StreamingErrorCode.UNSUPPORTED_OPERATION -> FavoriteSyncStatus.READ_ONLY
            gateway?.retryable == true -> FavoriteSyncStatus.RETRYABLE_ERROR
            this is java.io.IOException -> FavoriteSyncStatus.RETRYABLE_ERROR
            else -> FavoriteSyncStatus.RETRYABLE_ERROR
        }
    }

    private fun safeSyncMessage(value: String): String = value
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
        .replace(Regex("(?i)(authorization|cookie|token|password|secret)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
        .trim()
        .take(240)
        .ifBlank { "Favorite synchronization failed" }

    private fun UnifiedFavorite.toTrack(): Track = Track(
        localTrackId,
        title,
        artist,
        album,
        durationMs,
        null,
        "favorite-sync:$unifiedId"
    )

    private data class FavoriteTargetMatch(
        val providerTrackId: String,
        val status: FavoriteSyncStatus,
        val confidence: Float
    )
}

private fun <T, K> List<T>.upsertBy(key: (T) -> K, value: T): List<T> {
    val valueKey = key(value)
    val index = indexOfFirst { key(it) == valueKey }
    if (index < 0) return this + value
    return toMutableList().apply { this[index] = value }
}

private fun favoriteIdentityKey(recordingId: Long, unifiedId: String): String =
    if (recordingId > 0L) "recording-id:$recordingId" else unifiedId

private fun sameFavoriteIdentity(
    leftRecordingId: Long,
    leftUnifiedId: String,
    rightRecordingId: Long,
    rightUnifiedId: String
): Boolean = if (leftRecordingId > 0L && rightRecordingId > 0L) {
    leftRecordingId == rightRecordingId
} else {
    leftUnifiedId == rightUnifiedId
}

private fun favoriteOperationId(
    action: FavoriteSyncAction,
    recordingId: Long,
    unifiedId: String,
    provider: StreamingProviderName
): String = "${action.name}:${favoriteIdentityKey(recordingId, unifiedId)}:${provider.wireName}"

private fun favoriteConflictId(
    recordingId: Long,
    unifiedId: String,
    provider: StreamingProviderName
): String = "${favoriteIdentityKey(recordingId, unifiedId)}:${provider.wireName}"
