package app.yukine.together

import android.content.Context
import app.yukine.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface TogetherForegroundController {
    fun setDataSyncActive(active: Boolean)
}

class TogetherSessionOwner internal constructor(
    private val context: Context,
    private val player: TogetherPlayerPort,
    private val foregroundController: TogetherForegroundController,
    private val bridge: TogetherNativeBridge,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val idleTimeoutMs: Long = 10L * 60L * 1000L,
    private val streamingResolver: TogetherStreamingResolverPort = EmptyTogetherStreamingResolver
) : TogetherSessionHostPort {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mutableState = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
    private val echoSuppressor = TogetherEchoSuppressor()
    private val transferActive = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0L)
    private val queueUpdateGeneration = AtomicLong(0L)
    private var nativeSession: TogetherNativeBridge.NativeSession? = null
    private var roomQueue: List<TogetherQueueItem> = emptyList()
    private var legacyStreamQueue: List<TogetherQueueItem>? = null
    @Volatile
    private var privateStreamingSources: Map<String, TogetherQueueItem> = emptyMap()
    private val streamingSourcesLock = Any()
    private var roomCode = ""
    private var idleLeaveJob: Job? = null
    private var interrupted = false
    private var hosting = false
    private var lastQueueSignature = ""
    /** Id-only signature so queue tick paths can skip File.isFile work when identity is unchanged. */
    private var lastLightQueueSignature = ""
    /** Last host queue submitted to native (create/updateQueue); indices for queue_item_skipped. */
    private var hostSubmitQueue: List<TogetherQueueItem> = emptyList()
    private var hostSkippedOriginalIndices: Set<Int> = emptySet()

    override val state: StateFlow<TogetherSessionState> = mutableState.asStateFlow()

    override suspend fun testConnection(options: TogetherConnectOptions): Result<String> =
        runCatching {
            withContext(ioDispatcher) {
                bridge.testConnection(TogetherJson.options(options))
            }
        }

    override suspend fun previewJoin(
        roomCode: String,
        options: TogetherConnectOptions
    ): Result<TogetherJoinPreview> = runCatching {
        check(nativeSession == null) { "Already in a room" }
        val normalized = TogetherRoomCode.normalize(roomCode)
        require(TogetherRoomCode.isValid(normalized)) { "Invalid junto room code" }
        val policy = TogetherCachePolicy(cacheRoot())
        withContext(ioDispatcher) { policy.trim(emptySet()) }
        val raw = withContext(ioDispatcher) {
            bridge.preview(TogetherJson.options(options), normalized)
        }
        TogetherJoinPreview(
            queue = parseQueue(JSONObject(raw).optJSONArray("items")),
            freeBytes = cacheRoot().usableSpace
        )
    }

    override suspend fun create(
        editableQueue: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit> {
        if (nativeSession != null) {
            return Result.failure(IllegalStateException("Already in a room"))
        }
        if (editableQueue.isEmpty()) {
            return Result.failure(IllegalArgumentException("Choose at least one local audio file"))
        }
        val generation = sessionGeneration.incrementAndGet()
        val callbackGate = CompletableDeferred<Unit>()
        return runCatching {
            val cache = sessionCache("preparing")
            val policy = TogetherCachePolicy(cacheRoot())
            policy.trim(emptySet())
            policy.requireCapacity()
            mutableState.value = TogetherSessionState.Preparing(editableQueue)
            val prepared = withContext(ioDispatcher) {
                TogetherQueueMaterializer(context, cache, streamingResolver).prepare(editableQueue)
            }
            ensureCurrentGeneration(generation, "Together create cancelled")
            // Capture before any room-side filtering so we only mutate the player after create
            // succeeds — a failed bridge.create must not leave a permanently reduced queue.
            val originalTracks = player.currentQueueTracks()
            val needsPlaybackReplace =
                togetherQueueSignature(prepared) != togetherQueueSignature(editableQueue)
            // Hosting/constraints only after native session is assigned so leave-during-create
            // cannot leave room-active with nativeSession == null.
            mutableState.value = TogetherSessionState.Connecting(joining = false)
            val createdSession = withContext(ioDispatcher) {
                bridge.create(
                    TogetherJson.options(options.copy(cacheDirectory = cache.absolutePath)),
                    TogetherJson.queue(prepared),
                    callbackFor(generation, callbackGate)
                )
            }
            if (!isCurrentGeneration(generation)) {
                callbackGate.complete(Unit)
                withContext(ioDispatcher) { runCatching { createdSession.leave() } }
                detachSessionState()
                error("Together create cancelled")
            }
            nativeSession = createdSession
            roomCode = createdSession.roomCode()
            roomQueue = prepared
            hostSubmitQueue = prepared
            hostSkippedOriginalIndices = emptySet()
            hosting = true
            lastQueueSignature = togetherQueueSignature(prepared)
            // Match Media3 track.id strings used by onLocalQueueChanged light short-circuit.
            lastLightQueueSignature = TogetherStableIds.lightQueueSignatureFromStableIds(
                prepared.map(TogetherQueueItem::stableId)
            )
            synchronized(streamingSourcesLock) {
                privateStreamingSources = prepared
                    .filter { it.source is TogetherQueueSource.Streaming }
                    .associateBy { it.dedupeKey }
            }
            player.setRoomPlaybackConstraints(true)
            if (needsPlaybackReplace) {
                replacePlaybackQueue(prepared, originalTracks)
            }
            mutableState.value = TogetherSessionState.WaitingReady(roomCode, roomQueue, emptyList())
            callbackGate.complete(Unit)
            Unit
        }.onFailure { error ->
            callbackGate.complete(Unit)
            if (isCurrentGeneration(generation)) {
                fail(error)
            } else {
                // leave() already advanced generation; still clear any orphan hosting/constraints.
                clearOrphanRoomSideEffects()
            }
        }
    }

    override suspend fun join(
        roomCode: String,
        localMatches: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit> {
        if (nativeSession != null) {
            return Result.failure(IllegalStateException("Already in a room"))
        }
        val normalized = TogetherRoomCode.normalize(roomCode)
        if (!TogetherRoomCode.isValid(normalized)) {
            return Result.failure(IllegalArgumentException("Invalid junto room code"))
        }
        val generation = sessionGeneration.incrementAndGet()
        val callbackGate = CompletableDeferred<Unit>()
        return runCatching {
            val hash = roomHash(normalized)
            val cache = sessionCache(hash)
            TogetherCachePolicy(cacheRoot()).apply {
                trim(emptySet())
                requireCapacity()
            }
            val preparedMatches = withContext(ioDispatcher) {
                TogetherQueueMaterializer(context, cache, streamingResolver).prepare(localMatches)
            }
            ensureCurrentGeneration(generation, "Together join cancelled")
            this.roomCode = normalized
            roomQueue = preparedMatches
            hosting = false
            lastQueueSignature = ""
            mutableState.value = TogetherSessionState.Connecting(joining = true)
            val joinedSession = withContext(ioDispatcher) {
                bridge.join(
                    TogetherJson.options(options.copy(cacheDirectory = cache.absolutePath)),
                    normalized,
                    TogetherJson.queue(preparedMatches),
                    callbackFor(generation, callbackGate)
                )
            }
            if (!isCurrentGeneration(generation)) {
                callbackGate.complete(Unit)
                withContext(ioDispatcher) { runCatching { joinedSession.leave() } }
                detachSessionState()
                error("Together join cancelled")
            }
            nativeSession = joinedSession
            player.setRoomPlaybackConstraints(true)
            callbackGate.complete(Unit)
            Unit
        }.onFailure { error ->
            callbackGate.complete(Unit)
            if (isCurrentGeneration(generation)) {
                fail(error)
            } else {
                clearOrphanRoomSideEffects()
            }
        }
    }

    override suspend fun leave(reason: String) {
        if (nativeSession == null && mutableState.value is TogetherSessionState.Idle) return
        val leaveGeneration = sessionGeneration.incrementAndGet()
        mutableState.value = TogetherSessionState.Leaving(reason)
        val session = detachSessionState()
        withContext(ioDispatcher) {
            runCatching { session?.leave() }
        }
        if (isCurrentGeneration(leaveGeneration)) {
            mutableState.value = TogetherSessionState.Idle
        }
        withContext(ioDispatcher) {
            TogetherCachePolicy(cacheRoot()).trim(emptySet())
        }
    }

    override suspend fun saveReceived(fileId: String): Result<String> = runCatching {
        val session = requireNotNull(nativeSession) { "Not in a room" }
        val path = session.receivedFilePath(fileId)
        require(path.isNotBlank()) { "File is not completely verified" }
        withContext(ioDispatcher) {
            TogetherReceivedFileSaver(context).save(path, session.receivedFileRoot(fileId)).getOrThrow()
        }
    }

    override fun onLocalPlayback(event: TogetherPlaybackEvent) {
        if (nativeSession == null) return
        if (interrupted && event is TogetherPlaybackEvent.PauseChanged) return
        if (echoSuppressor.consumeIfExpected(event)) return
        nativeSession?.notifyPlayback(TogetherJson.playback(event))
        scheduleIdleLeave(event)
    }

    override fun onLocalQueueChanged(tracks: List<Track>) {
        val session = nativeSession
        if (!hosting || session == null) return
        // Id-only short-circuit before any File.isFile work on the main thread.
        val lightSignature = TogetherStableIds.lightQueueSignatureFromTracks(tracks.map { it.id })
        if (lightSignature == lastLightQueueSignature && lastQueueSignature.isNotEmpty()) {
            return
        }
        val mapped = TogetherQueueItemMapper.fromTracks(tracks)
            .filter(TogetherQueueItem::shareable)
        if (mapped.isEmpty()) {
            requestLeave("queue_empty")
            return
        }
        val signature = togetherQueueSignature(mapped)
        if (signature == lastQueueSignature) {
            lastLightQueueSignature = lightSignature
            return
        }
        lastLightQueueSignature = lightSignature
        lastQueueSignature = signature
        roomQueue = mapped
        updateStateQueue(mapped)
        val generation = sessionGeneration.get()
        val updateGeneration = queueUpdateGeneration.incrementAndGet()
        scope.launch {
            runCatching {
                val prepared = withContext(ioDispatcher) {
                    TogetherQueueMaterializer(
                        context,
                        sessionCache(roomCode.ifBlank { "active" }),
                        streamingResolver
                    ).prepare(mapped)
                }
                if (
                    !isCurrentGeneration(generation) ||
                    updateGeneration != queueUpdateGeneration.get() ||
                    !hosting
                ) {
                    return@runCatching
                }
                synchronized(streamingSourcesLock) {
                    privateStreamingSources = prepared
                        .filter { it.source is TogetherQueueSource.Streaming }
                        .associateBy(TogetherQueueItem::dedupeKey)
                }
                val preparedSignature = togetherQueueSignature(prepared)
                lastQueueSignature = preparedSignature
                lastLightQueueSignature = TogetherStableIds.lightQueueSignatureFromStableIds(
                    prepared.map(TogetherQueueItem::stableId)
                )
                roomQueue = prepared
                hostSubmitQueue = prepared
                hostSkippedOriginalIndices = emptySet()
                updateStateQueue(prepared)
                if (preparedSignature != signature) {
                    replacePlaybackQueue(prepared, tracks)
                }
                session.updateQueue(TogetherJson.queue(prepared))
            }.onFailure { error ->
                if (isCurrentGeneration(generation) && updateGeneration == queueUpdateGeneration.get()) {
                    lastQueueSignature = ""
                    lastLightQueueSignature = ""
                    fail(error, recoverable = true)
                }
            }
        }
    }

    override fun canEditQueue(): Boolean = hosting && nativeSession != null

    override fun onSystemInterruption(buffering: Boolean) {
        interrupted = buffering
        onLocalPlayback(TogetherPlaybackEvent.BufferingChanged(buffering))
    }

    override fun setTransferForegroundActive(active: Boolean) {
        if (transferActive.getAndSet(active) == active) return
        foregroundController.setDataSyncActive(active)
        if (active) idleLeaveJob?.cancel() else scheduleIdleLeave(null)
    }

    fun close() {
        sessionGeneration.incrementAndGet()
        val session = detachSessionState()
        runCatching { session?.leave() }
        scope.cancel()
    }

    fun requestLeave(reason: String) {
        scope.launch { leave(reason) }
    }

    private fun callbackFor(
        generation: Long,
        ready: CompletableDeferred<Unit>
    ): TogetherNativeBridge.Callback = object : TogetherNativeBridge.Callback {
        override fun onEvent(eventJson: String) {
            scope.launch {
                ready.await()
                if (isCurrentGeneration(generation)) handleNativeEvent(eventJson)
            }
        }

        override fun onCommand(commandJson: String) {
            scope.launch {
                ready.await()
                if (isCurrentGeneration(generation)) applyRemoteCommand(commandJson)
            }
        }

        override fun resolveSource(requestJson: String): String {
            if (!ready.isCompleted || !isCurrentGeneration(generation)) return ""
            val request = runCatching { JSONObject(requestJson) }.getOrNull() ?: return ""
            val provider = request.optString("provider")
            val trackId = request.optString("track_id")
            if (provider.isBlank() || trackId.isBlank()) return ""
            val key = "streaming:$provider:$trackId"
            val item = synchronized(streamingSourcesLock) { privateStreamingSources[key] }
            val source = item?.source as? TogetherQueueSource.Streaming
                ?: TogetherQueueSource.Streaming(
                    provider = provider,
                    providerTrackId = trackId,
                    quality = request.optString("quality"),
                    durationMs = request.optLong("duration_ms")
                )
            val refreshed = runCatching { streamingResolver.resolve(source) }
                .getOrNull()
                ?.takeIf {
                    it.provider == source.provider &&
                        it.providerTrackId == source.providerTrackId &&
                        it.supportsRange &&
                        it.resolvedUrl.isNotBlank()
                }
                ?: source
            if (refreshed.resolvedUrl.isBlank() || !refreshed.supportsRange) return ""
            // Prefer freshly resolved size (Go rejects size<=0). Fall back to private entry / request.
            val knownSize = item?.sizeBytes?.takeIf { it > 0L }
                ?: (item?.source as? TogetherQueueSource.Streaming)?.sizeBytes?.takeIf { it > 0L }
                ?: 0L
            val sizeBytes = when {
                refreshed.sizeBytes > 0L -> refreshed.sizeBytes
                knownSize > 0L -> knownSize
                else -> request.optLong("size")
            }
            if (sizeBytes <= 0L) return ""
            val sizedSource = refreshed.copy(sizeBytes = sizeBytes, supportsRange = true)
            val refreshedItem = item?.copy(
                sourceUri = refreshed.resolvedUrl,
                sizeBytes = sizeBytes,
                source = sizedSource
            ) ?: TogetherQueueItem(
                stableId = key,
                title = "",
                artist = "",
                sourceUri = refreshed.resolvedUrl,
                sizeBytes = sizeBytes,
                source = sizedSource
            )
            synchronized(streamingSourcesLock) {
                privateStreamingSources = privateStreamingSources + (key to refreshedItem)
            }
            return JSONObject()
                .put("kind", "streaming")
                .put("provider", refreshed.provider)
                .put("track_id", refreshed.providerTrackId)
                .put("quality", refreshed.quality)
                .put("duration_ms", refreshed.durationMs)
                .put("url", refreshed.resolvedUrl)
                .put("headers", JSONObject(refreshed.headers))
                .put("expires_at_ms", refreshed.expiresAtEpochMs)
                .put("mime", refreshed.mimeType)
                .put("supports_range", true)
                .put("size", sizeBytes)
                .toString()
        }
    }

    private fun handleNativeEvent(raw: String) {
        runCatching {
            val event = JSONObject(raw)
            when (event.optString("type")) {
                "snapshot" -> applySnapshot(event)
                "queue_item_skipped" -> applyQueueItemSkipped(event)
                "queue" -> {
                    val items = event.optJSONArray("items")
                    val nextQueue = restoreHostPrivateQueueSources(
                        parseQueue(items),
                        roomQueue,
                        synchronized(streamingSourcesLock) { privateStreamingSources }
                    )
                    val nextSignature = togetherQueueSignature(nextQueue)
                    if (!hosting || nextSignature == lastQueueSignature) {
                        roomQueue = nextQueue
                        updateStateQueue(nextQueue, parseMembers(event))
                        val embeddedUrls = parseStreamUrls(items)
                        if (
                            embeddedUrls.size == nextQueue.size &&
                            embeddedUrls.all(String::isNotBlank)
                        ) {
                            legacyStreamQueue = null
                            player.replaceQueueWithStreamUrls(nextQueue, embeddedUrls)
                        } else {
                            legacyStreamQueue = nextQueue.takeUnless { hosting }
                        }
                    }
                }
                "stream_urls" -> {
                    val queueSnapshot = legacyStreamQueue
                    val urls = parseStringArray(event.optJSONArray("urls"))
                    if (
                        queueSnapshot != null &&
                        urls.size == queueSnapshot.size &&
                        urls.all(String::isNotBlank)
                    ) {
                        legacyStreamQueue = null
                        player.replaceQueueWithStreamUrls(queueSnapshot, urls)
                    }
                }
                "transfer" -> applyTransfer(event)
                "reconnecting" -> mutableState.value = TogetherSessionState.Reconnecting(
                    roomHash(roomCode),
                    event.optInt("attempt", 1),
                    (mutableState.value as? TogetherSessionState.Active)?.transfer
                )
                "failed" -> fail(
                    IllegalStateException(event.optString("message", "Together session failed")),
                    event.optBoolean("recoverable", true)
                )
                "terminal" -> fail(
                    IllegalStateException(
                        event.optString("message").ifBlank {
                            "Together session ended: ${event.optString("reason", "unknown")}"
                        }
                    ),
                    event.optBoolean("recoverable", true)
                )
            }
        }.onFailure {
            fail(IllegalStateException("Invalid junto mobile event", it), recoverable = true)
        }
    }

    /**
     * Native host drops unrelayable streaming rows and continues with a shorter file list.
     * Indices refer to the last queue submitted via create/updateQueue (not the live room queue
     * after prior skips). Align Media3 + room UI so peers and host share one order.
     */
    private fun applyQueueItemSkipped(event: JSONObject) {
        if (!hosting) return
        val originalIndex = event.optInt("index", -1)
        val skippedId = event.optString("id").takeIf(String::isNotBlank)
        if (originalIndex < 0 && skippedId == null) return
        val submit = hostSubmitQueue.ifEmpty { roomQueue }
        if (submit.isEmpty()) return
        val nextSkipped = hostSkippedOriginalIndices.toMutableSet()
        when {
            skippedId != null -> {
                val byId = submit.indexOfFirst { it.stableId == skippedId }
                if (byId >= 0) nextSkipped += byId
            }
            originalIndex in submit.indices -> nextSkipped += originalIndex
            else -> return
        }
        hostSkippedOriginalIndices = nextSkipped
        val nextQueue = submit.filterIndexed { index, _ -> index !in nextSkipped }
        if (nextQueue.isEmpty()) {
            requestLeave("queue_empty")
            return
        }
        if (nextQueue.size == roomQueue.size &&
            togetherQueueSignature(nextQueue) == lastQueueSignature
        ) {
            return
        }
        val previousTracks = player.currentQueueTracks()
        roomQueue = nextQueue
        lastQueueSignature = togetherQueueSignature(nextQueue)
        lastLightQueueSignature = TogetherStableIds.lightQueueSignatureFromStableIds(
            nextQueue.map(TogetherQueueItem::stableId)
        )
        updateStateQueue(nextQueue)
        replacePlaybackQueue(nextQueue, previousTracks)
    }

    private fun applySnapshot(event: JSONObject) {
        val previous = mutableState.value as? TogetherSessionState.Active
        val next = TogetherSessionState.Active(
            roomCode = roomCode,
            queue = roomQueue,
            members = parseMembers(event),
            currentIndex = event.optInt("current_index", player.currentQueueIndex()),
            driftMs = event.optLong("drift_ms").takeIf { event.has("drift_ms") },
            paused = event.optBoolean("paused", true),
            buffering = event.optBoolean("buffering"),
            transfer = previous?.transfer,
            connectionKind = when (event.optString("connection")) {
                "direct" -> TogetherConnectionKind.Direct
                "turn" -> TogetherConnectionKind.Turn
                else -> TogetherConnectionKind.Unknown
            }
        )
        mutableState.value = next
        scheduleIdleLeave(TogetherPlaybackEvent.PauseChanged(next.paused))
    }

    private fun applyTransfer(event: JSONObject) {
        val transfer = TogetherTransfer(
            fileId = event.optString("file_id"),
            fileName = event.optString("file_name"),
            bytesVerified = event.optLong("verified"),
            totalBytes = event.optLong("total"),
            bytesPerSecond = event.optLong("bytes_per_second"),
            complete = event.optBoolean("complete")
        )
        setTransferForegroundActive(!transfer.complete && transfer.totalBytes > 0L)
        val active = mutableState.value as? TogetherSessionState.Active
        if (active != null) mutableState.value = active.copy(transfer = transfer)
    }

    private fun parseMembers(event: JSONObject): List<TogetherMember> {
        val members = event.optJSONArray("members") ?: return emptyList()
        return buildList {
            for (i in 0 until members.length()) {
                val member = members.optJSONObject(i) ?: continue
                add(TogetherMember(
                    idHash = member.optString("id_hash"),
                    nickname = member.optString("nickname"),
                    ready = member.optBoolean("ready"),
                    buffering = member.optBoolean("buffering"),
                    downloadPercent = member.optInt("download_percent"),
                    driftMs = member.optLong("drift_ms").takeIf { member.has("drift_ms") }
                ))
            }
        }
    }

    private fun parseQueue(array: org.json.JSONArray?): List<TogetherQueueItem> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val streamUrl = item.optString("stream_url")
            val source = if (item.optString("kind") == "streaming") {
                TogetherQueueSource.Streaming(
                    provider = item.optString("provider"),
                    providerTrackId = item.optString("track_id"),
                    quality = item.optString("quality"),
                    durationMs = item.optLong("duration_ms"),
                    resolvedUrl = streamUrl,
                    sizeBytes = item.optLong("size")
                )
            } else TogetherQueueSource.Local(streamUrl, item.optLong("size"), item.optString("root"))
            add(TogetherQueueItem(
                stableId = item.optString("id", i.toString()),
                title = item.optString("title", item.optString("name")),
                artist = item.optString("artist"),
                sourceUri = streamUrl,
                sizeBytes = item.optLong("size"),
                contentRoot = item.optString("root"),
                receivedFileId = item.optString("file_id").takeIf(String::isNotBlank),
                album = item.optString("album"),
                artworkUri = item.optString("artwork_uri"),
                durationMs = item.optLong("duration_ms"),
                source = source
            ))
        }
    }

    private fun replacePlaybackQueue(
        queue: List<TogetherQueueItem>,
        originalTracks: List<Track>
    ) {
        val retainedTracks = retainTracksForTogetherQueue(originalTracks, queue)
        if (retainedTracks.size == queue.size) {
            player.replaceQueueWithTracks(retainedTracks)
        } else {
            player.replaceQueueWithItems(queue)
        }
    }

    private fun parseStreamUrls(array: org.json.JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            add(array.optJSONObject(i)?.optString("stream_url").orEmpty())
        }
    }

    private fun parseStringArray(array: org.json.JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) add(array.optString(i))
    }

    private fun applyRemoteCommand(raw: String) {
        val command = JSONObject(raw)
        when (command.optString("type")) {
            "pause" -> {
                val paused = command.optBoolean("paused")
                echoSuppressor.expect(TogetherPlaybackEvent.PauseChanged(paused))
                if (paused) player.pause() else player.play()
            }
            "seek" -> {
                val position = command.optLong("position_ms").coerceAtLeast(0L)
                echoSuppressor.expect(TogetherPlaybackEvent.Seeked(position))
                player.seekTo(position)
            }
            "speed" -> {
                val speed = command.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f)
                echoSuppressor.expect(TogetherPlaybackEvent.SpeedChanged(speed))
                player.setSpeed(speed)
            }
            "index" -> {
                val index = command.optInt("index").coerceAtLeast(0)
                echoSuppressor.expect(TogetherPlaybackEvent.QueueIndexChanged(index))
                player.skipToQueueIndex(index)
            }
        }
    }

    private fun updateStateQueue(
        queue: List<TogetherQueueItem>,
        members: List<TogetherMember>? = null
    ) {
        mutableState.value = when (val current = mutableState.value) {
            is TogetherSessionState.Active -> current.copy(
                queue = queue,
                members = members ?: current.members,
                currentIndex = current.currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
            )
            is TogetherSessionState.WaitingReady -> current.copy(
                queue = queue,
                members = members ?: current.members
            )
            else -> TogetherSessionState.WaitingReady(
                roomCode = roomCode,
                queue = queue,
                members = members.orEmpty()
            )
        }
    }

    private fun scheduleIdleLeave(event: TogetherPlaybackEvent?) {
        if (event != null && event !is TogetherPlaybackEvent.PauseChanged) {
            idleLeaveJob?.cancel()
            return
        }
        val active = mutableState.value as? TogetherSessionState.Active ?: return
        val paused = event?.paused ?: active.paused
        if (!paused || transferActive.get()) {
            idleLeaveJob?.cancel()
            return
        }
        idleLeaveJob?.cancel()
        idleLeaveJob = scope.launch {
            delay(idleTimeoutMs)
            if (!transferActive.get()) leave("idle_timeout")
        }
    }

    private fun fail(error: Throwable, recoverable: Boolean = true) {
        sessionGeneration.incrementAndGet()
        val session = detachSessionState()
        mutableState.value = TogetherSessionState.Failed(
            error.message ?: "Together session failed",
            recoverable
        )
        if (session != null) {
            scope.launch(ioDispatcher) {
                runCatching { session.leave() }
            }
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        generation == sessionGeneration.get()

    private fun ensureCurrentGeneration(generation: Long, message: String) {
        if (!isCurrentGeneration(generation)) {
            error(message)
        }
    }

    /**
     * When leave advanced the generation mid create/join, [fail] must not run (it would
     * stomp Idle with Failed). Still clear room-side player constraints if orphaned.
     */
    private fun clearOrphanRoomSideEffects() {
        if (nativeSession != null || hosting) {
            detachSessionState()
            return
        }
        player.setRoomPlaybackConstraints(false)
    }

    private fun detachSessionState(): TogetherNativeBridge.NativeSession? {
        val session = nativeSession
        nativeSession = null
        roomCode = ""
        roomQueue = emptyList()
        hostSubmitQueue = emptyList()
        hostSkippedOriginalIndices = emptySet()
        legacyStreamQueue = null
        idleLeaveJob?.cancel()
        synchronized(streamingSourcesLock) {
            privateStreamingSources = emptyMap()
        }
        hosting = false
        lastQueueSignature = ""
        lastLightQueueSignature = ""
        queueUpdateGeneration.incrementAndGet()
        idleLeaveJob = null
        echoSuppressor.clear()
        transferActive.set(false)
        interrupted = false
        foregroundController.setDataSyncActive(false)
        player.setRoomPlaybackConstraints(false)
        return session
    }

    private fun cacheRoot(): File = File(context.cacheDir, "together")

    private fun sessionCache(key: String): File = File(cacheRoot(), roomHash(key))

    private fun roomHash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(
            context: Context,
            player: TogetherPlayerPort,
            foregroundController: TogetherForegroundController,
            streamingResolver: TogetherStreamingResolverPort = EmptyTogetherStreamingResolver
        ): TogetherSessionOwner = TogetherSessionOwner(
            context.applicationContext,
            player,
            foregroundController,
            GomobileTogetherNativeBridge(),
            Dispatchers.Main.immediate,
            Dispatchers.IO,
            streamingResolver = streamingResolver
        )
    }
}

internal fun togetherQueueSignature(queue: List<TogetherQueueItem>): String =
    queue.joinToString("\u001f", transform = TogetherQueueItem::dedupeKey)

internal fun retainTracksForTogetherQueue(
    tracks: List<Track>,
    queue: List<TogetherQueueItem>
): List<Track> {
    if (tracks.isEmpty() || queue.isEmpty()) return emptyList()
    val tracksById = tracks
        .groupBy { it.id }
        .mapValues { (_, values) -> ArrayDeque(values) }
    return queue.mapNotNull { item ->
        tracksById[TogetherStableIds.media3TrackId(item.stableId)]?.removeFirstOrNull()
    }
}

internal fun restoreHostPrivateQueueSources(
    queue: List<TogetherQueueItem>,
    previousQueue: List<TogetherQueueItem>,
    privateSources: Map<String, TogetherQueueItem>
): List<TogetherQueueItem> {
    val previousByStableId = previousQueue.associateBy(TogetherQueueItem::stableId)
    return queue.map { item ->
        val previousItem = previousByStableId[item.stableId]
            ?: previousQueue.firstOrNull { it.dedupeKey == item.dedupeKey }
        val restoredMetadata = if (previousItem == null) {
            item
        } else {
            item.copy(
                title = item.title.ifBlank { previousItem.title },
                artist = item.artist.ifBlank { previousItem.artist },
                album = item.album.ifBlank { previousItem.album },
                artworkUri = item.artworkUri.ifBlank { previousItem.artworkUri },
                durationMs = item.durationMs.takeIf { it > 0L } ?: previousItem.durationMs
            )
        }
        when (val parsedSource = restoredMetadata.source) {
            is TogetherQueueSource.Local -> {
                val previousSource = previousItem
                    ?.source as? TogetherQueueSource.Local
                if (previousSource == null) {
                    restoredMetadata
                } else {
                    restoredMetadata.copy(source = previousSource)
                }
            }
            is TogetherQueueSource.Streaming -> {
                val privateSource = (
                    privateSources[restoredMetadata.dedupeKey]
                        ?: previousItem
                    )?.source as? TogetherQueueSource.Streaming
                    ?: return@map restoredMetadata
                if (
                    privateSource.provider != parsedSource.provider ||
                    privateSource.providerTrackId != parsedSource.providerTrackId
                ) {
                    restoredMetadata
                } else {
                    restoredMetadata.copy(source = privateSource)
                }
            }
        }
    }
}
