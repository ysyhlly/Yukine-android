package app.yukine

import app.yukine.model.Track
import app.yukine.model.PlaybackTrackSourceOverlay
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

/** Owns streaming playback URL resolution, recovery, pre-resolution and track-match persistence. */
class StreamingPlaybackResolutionStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val stateOwner: StreamingFeatureStateOwner,
    private val repository: () -> StreamingRepository,
    private val ioDispatcher: () -> CoroutineDispatcher
) {
    private var playbackPlanner: StreamingPlaybackResolvePlanner? = null
    private var playbackTaskQueue: StreamingPlaybackTaskQueue? = null
    private var trackMatchStore: StreamingTrackMatchStore? = null
    private val queueWindowPreResolveInFlight = Collections.newSetFromMap(
        ConcurrentHashMap<StreamingQueuePreResolveKey, Boolean>()
    )
    private val playbackResolveInFlight =
        ConcurrentHashMap<StreamingPlaybackResolveKey, CompletableDeferred<StreamingResolvedPlayback>>()
    @Volatile
    private var currentPlaybackResolveJob: Job? = null

    fun bindPlaybackCoordinator(
        planner: StreamingPlaybackResolvePlanner?,
        taskQueue: StreamingPlaybackTaskQueue?
    ) {
        playbackPlanner = planner
        playbackTaskQueue = taskQueue
    }

    fun bindTrackMatchStore(store: StreamingTrackMatchStore?) {
        trackMatchStore = store
    }

    fun resolveStreamingPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                repository().resolvePlayback(provider, providerTrackId, quality)
            }.onSuccess { source ->
                updatePlaybackSource(source)
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun resolveStreamingPlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: StreamingTrack? = null
    ): Job {
        beginRequest()
        return scope.launch {
            runCatching {
                resolvePlaybackTrackWithFallback(provider, providerTrackId, quality, metadata)
            }.onSuccess { result ->
                updatePlaybackTrack(result.source, result.track)
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun resolveStreamingTrackForPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack? = null,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<Track?>
    ): Job = resolveStreamingTrackForPlaybackInternal(
        provider,
        providerTrackId,
        metadata,
        quality,
        forceRefresh = false,
        onResolved
    )

    private fun resolveStreamingTrackForPlaybackInternal(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?,
        quality: StreamingAudioQuality,
        forceRefresh: Boolean,
        onResolved: StreamingCallback<Track?>
    ): Job = resolveStreamingPlaybackResult(
        provider,
        providerTrackId,
        metadata,
        quality,
        forceRefresh
    ) { result ->
        onResolved.onResult(result?.track)
    }

    private fun resolveStreamingPlaybackResult(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?,
        quality: StreamingAudioQuality,
        forceRefresh: Boolean,
        onResolved: StreamingCallback<StreamingResolvedPlayback?>
    ): Job {
        beginRequest()
        return scope.launch {
            try {
                val result = resolvePlaybackTrackWithFallback(
                    provider,
                    providerTrackId,
                    quality,
                    metadata,
                    forceRefresh
                )
                updatePlaybackTrack(result.source, result.track)
                updateDiagnostics(repository().diagnostics())
                onResolved.onResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
                onResolved.onResult(null)
            }
        }
    }

    fun preResolveNextStreamingTrack(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingBiCallback<Long, Track?>
    ): Boolean {
        val planner = playbackPlanner ?: return false
        val taskQueue = playbackTaskQueue ?: return false
        val request = planner.prepareNextPreResolve(snapshot, queue) ?: return false
        taskQueue.scheduleNextUrlResolve(
            StreamingPlaybackTask { onComplete ->
                val job = preResolveStreamingTrackForPlayback(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    quality
                ) { resolved ->
                    onResolved.onResult(
                        request.oldTrackId,
                        resolved?.let {
                            PlaybackTrackSourceOverlay.merge(request.logicalTrack, it)
                        }
                    )
                }
                job.invokeOnCompletion {
                    planner.clearPreResolve(request.key)
                    onComplete.run()
                }
            }
        )
        return true
    }

    /**
     * Resolves a queued track without mutating the foreground streaming request state. A failed
     * prefetch must not clear the current source, show a loading indicator, or surface a playback
     * error while the current physical source keeps playing.
     */
    private fun preResolveStreamingTrackForPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?,
        quality: StreamingAudioQuality,
        onResolved: StreamingCallback<Track?>
    ): Job = scope.launch {
        try {
            val result = resolvePlaybackTrackWithFallback(
                provider,
                providerTrackId,
                quality,
                metadata
            )
            updateDiagnostics(repository().diagnostics())
            onResolved.onResult(result.track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            updateDiagnostics(repository().diagnostics())
            onResolved.onResult(null)
        }
    }

    fun preResolveStreamingQueueWindow(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        maxCount: Int = STREAMING_QUEUE_PRE_RESOLVE_LIMIT,
        onResolved: StreamingBiCallback<Long, Track?>
    ): Job? = preResolveStreamingQueueWindowBatch(
        snapshot,
        queue,
        quality,
        maxCount
    ) { resolvedTracks ->
        resolvedTracks.forEach { (oldTrackId, resolved) ->
            onResolved.onResult(oldTrackId, resolved)
        }
    }

    /**
     * Foreground maintenance for local NetEase/QQ sessions. The repository/store throttle actual
     * network work, and this method deliberately avoids the global loading/error UI so reopening
     * the app never feels blocked by a background cookie check.
     */
    fun preResolveStreamingQueueWindowBatch(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        maxCount: Int = STREAMING_QUEUE_PRE_RESOLVE_LIMIT,
        onResolved: (Map<Long, Track>) -> Unit
    ): Job? {
        if (snapshot == null || !snapshot.playing || queue.isNullOrEmpty() || maxCount <= 0) {
            return null
        }
        val targets = streamingQueuePreResolveTargets(snapshot, queue, maxCount)
        if (targets.isEmpty()) {
            return null
        }
        val eligibleTargets = targets.filter { target ->
            queueWindowPreResolveInFlight.add(target.inFlightKey(quality))
        }
        if (eligibleTargets.isEmpty()) {
            return null
        }
        return scope.launch {
            try {
                val resolvedTracks = linkedMapOf<Long, Track>()
                eligibleTargets.map { target ->
                    async(ioDispatcher()) {
                        target to runCatching {
                            resolvePlaybackTrackWithFallback(
                                target.provider,
                                target.providerTrackId,
                                quality,
                                target.metadata
                            )
                        }.getOrNull()
                    }
                }.awaitAll().forEach { (target, result) ->
                    result?.let {
                        updatePlaybackTrack(it.source, it.track)
                        resolvedTracks[target.oldTrackId] = PlaybackTrackSourceOverlay.merge(
                            target.logicalTrack,
                            it.track
                        )
                    }
                }
                if (resolvedTracks.isNotEmpty()) {
                    onResolved(resolvedTracks)
                }
                updateDiagnostics(repository().diagnostics())
            } finally {
                eligibleTargets.forEach { target ->
                    queueWindowPreResolveInFlight.remove(target.inFlightKey(quality))
                }
            }
        }
    }

    fun resolveStreamingTrackListForPlayback(
        tracks: List<Track>?,
        index: Int,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<ResolvedStreamingTrackList?>
    ): Boolean {
        val planner = playbackPlanner ?: return false
        val taskQueue = playbackTaskQueue ?: return false
        val request = planner.prepare(tracks, index) ?: return false
        currentPlaybackResolveJob?.cancel()
        taskQueue.scheduleCurrentUrlResolve(
            StreamingPlaybackTask { onComplete ->
                val job = resolveStreamingPlaybackResult(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    quality,
                    forceRefresh = false
                ) { result ->
                    if (result == null) {
                        onResolved.onResult(null)
                        return@resolveStreamingPlaybackResult
                    }
                    onResolved.onResult(
                        ResolvedStreamingTrackList(
                            tracks = planner.replaceResolvedTrack(request, result.track),
                            index = request.index,
                            resolutionPath = result.resolutionPath
                        )
                    )
                }
                trackCurrentPlaybackResolve(job, onComplete)
            }
        )
        return true
    }

    fun prepareCurrentStreamingQueueResolveTarget(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?
    ): StreamingQueueResolveTarget? {
        if (snapshot?.currentTrack == null) {
            return null
        }
        // A paused stream with a concrete playback URI is already loaded by ExoPlayer and must
        // resume directly. Re-resolving it here turns a simple play command into an asynchronous
        // source replacement and can leave the player paused when the resolver declines/retries.
        if (!StreamingPlaybackAdapter.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
            return null
        }
        if (queue.isNullOrEmpty()) {
            return StreamingQueueResolveTarget(
                tracks = listOf(snapshot.currentTrack),
                index = 0
            )
        }
        return StreamingQueueResolveTarget(
            tracks = queue,
            index = snapshot.currentIndex.coerceIn(0, queue.size - 1)
        )
    }

    private data class StreamingQueuePreResolveTarget(
        val oldTrackId: Long,
        val logicalTrack: Track,
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val metadata: StreamingTrack?
    ) {
        fun inFlightKey(quality: StreamingAudioQuality): StreamingQueuePreResolveKey =
            StreamingQueuePreResolveKey(oldTrackId, provider, providerTrackId, quality)
    }

    private data class StreamingQueuePreResolveKey(
        val oldTrackId: Long,
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality
    )

    private data class StreamingPlaybackResolveKey(
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality,
        val metadata: StreamingTrack?,
        val forceRefresh: Boolean
    )

    private fun streamingQueuePreResolveTargets(
        snapshot: PlaybackStateSnapshot,
        queue: List<Track>,
        maxCount: Int
    ): List<StreamingQueuePreResolveTarget> {
        if (queue.size <= 2) {
            return emptyList()
        }
        val startIndex = (snapshot.currentIndex + 2).floorMod(queue.size)
        return (0 until queue.size)
            .asSequence()
            .map { offset -> (startIndex + offset).floorMod(queue.size) }
            .filter { index -> index != snapshot.currentIndex }
            .map { queue[it] }
            .filter { StreamingPlaybackAdapter.isUnresolvedStreamingTrack(it) }
            .distinctBy { it.dataPath }
            .mapNotNull { track ->
                val provider = StreamingPlaybackAdapter.streamingProviderName(track.dataPath) ?: return@mapNotNull null
                val providerTrackId = StreamingPlaybackAdapter.providerTrackId(track.dataPath)
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                StreamingQueuePreResolveTarget(
                    oldTrackId = track.id,
                    logicalTrack = track,
                    provider = provider,
                    providerTrackId = providerTrackId,
                    metadata = ResolveStreamingPlaybackUseCase().metadataFor(track, provider, providerTrackId)
                )
            }
            .take(maxCount)
            .toList()
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    fun recoverStreamingBuffering(
        snapshot: PlaybackStateSnapshot?,
        selectedQuality: StreamingAudioQuality,
        adaptiveQuality: StreamingAudioQuality,
        refuseAutomaticQualityDowngrade: Boolean,
        onResolved: StreamingCallback<StreamingRecoveryResolution?>
    ): StreamingAudioQuality? {
        val planner = playbackPlanner ?: return null
        val taskQueue = playbackTaskQueue ?: return null
        val request = planner.prepareRecovery(
            snapshot,
            selectedQuality,
            adaptiveQuality,
            refuseAutomaticQualityDowngrade
        ) ?: return null
        currentPlaybackResolveJob?.cancel()
        taskQueue.scheduleCurrentPlaybackRecovery(
            StreamingPlaybackTask { onComplete ->
                val job = resolveStreamingTrackForPlaybackInternal(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    request.quality,
                    forceRefresh = true
                ) { resolved ->
                    onResolved.onResult(
                        resolved?.let {
                            StreamingRecoveryResolution(
                                expectedTrackId = request.expectedTrackId,
                                track = PlaybackTrackSourceOverlay.merge(request.logicalTrack, it),
                                quality = request.quality,
                                positionMs = snapshot?.positionMs ?: 0L
                            )
                        }
                    )
                }
                currentPlaybackResolveJob = job
                job.invokeOnCompletion {
                    if (currentPlaybackResolveJob === job) {
                        currentPlaybackResolveJob = null
                    }
                    planner.clearRecovery(request.key)
                    onComplete.run()
                }
            }
        )
        return request.quality
    }

    fun loadStreamingProviderTrackId(
        track: Track,
        provider: StreamingProviderName,
        onResolved: StreamingCallback<String>
    ): Job {
        return scope.launch {
            val providerTrackId = withContext(ioDispatcher()) {
                trackMatchStore?.providerTrackIdFor(track, provider).orEmpty()
            }
            onResolved.onResult(providerTrackId)
        }
    }

    fun streamingProviderTrackIdFor(track: Track?, provider: StreamingProviderName?): String {
        if (track == null || provider == null) {
            return ""
        }
        return trackMatchStore?.providerTrackIdFor(track, provider).orEmpty()
    }

    fun saveStreamingProviderTrackId(
        track: Track?,
        provider: StreamingProviderName?,
        providerTrackId: String?
    ): Job {
        return scope.launch {
            val cleanTrackId = providerTrackId?.trim().orEmpty()
            if (track == null || provider == null || cleanTrackId.isEmpty()) {
                return@launch
            }
            withContext(ioDispatcher()) {
                trackMatchStore?.saveProviderTrackId(track, provider, cleanTrackId)
            }
        }
    }

    private suspend fun resolvePlaybackTrackWithFallback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality,
        metadata: StreamingTrack?,
        forceRefresh: Boolean = false
    ): StreamingResolvedPlayback {
        val key = StreamingPlaybackResolveKey(
            provider,
            providerTrackId.trim(),
            quality,
            metadata,
            forceRefresh
        )
        val pending = CompletableDeferred<StreamingResolvedPlayback>()
        val existing = playbackResolveInFlight.putIfAbsent(key, pending)
        if (existing != null) {
            return existing.await()
        }
        try {
            val result = repository().resolvePlaybackTrack(
                provider,
                providerTrackId,
                quality,
                metadata,
                forceRefresh = forceRefresh
            )
            pending.complete(result)
            return result
        } catch (cancelled: CancellationException) {
            pending.cancel(cancelled)
            throw cancelled
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            playbackResolveInFlight.remove(key, pending)
        }
    }

    private fun trackCurrentPlaybackResolve(job: Job, onComplete: Runnable) {
        currentPlaybackResolveJob = job
        job.invokeOnCompletion {
            if (currentPlaybackResolveJob === job) {
                currentPlaybackResolveJob = null
            }
            onComplete.run()
        }
    }

    fun updatePlaybackSource(source: StreamingPlaybackSource) {
        stateOwner.value = stateOwner.value.copy(
            resolvedPlaybackSource = source,
            resolvedPlaybackTrack = null,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updatePlaybackTrack(source: StreamingPlaybackSource, track: Track) {
        stateOwner.value = stateOwner.value.copy(
            resolvedPlaybackSource = source,
            resolvedPlaybackTrack = track,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }


    private fun beginRequest() {
        stateOwner.value = stateOwner.value.copy(
            loading = true,
            loadingMore = false,
            errorMessage = null
        )
    }

    private fun failRequest(message: String?) {
        stateOwner.value = stateOwner.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message ?: "Streaming request failed"
        )
    }

    private fun updateDiagnostics(diagnostics: StreamingGatewayDiagnostics) {
        stateOwner.value = stateOwner.value.copy(diagnostics = diagnostics)
    }
}
