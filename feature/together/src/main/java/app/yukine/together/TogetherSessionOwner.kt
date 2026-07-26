package app.yukine.together

import android.content.Context
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
    private val idleTimeoutMs: Long = 10L * 60L * 1000L
) : TogetherSessionHostPort {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mutableState = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
    private val echoSuppressor = TogetherEchoSuppressor()
    private val transferActive = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0L)
    private var nativeSession: TogetherNativeBridge.NativeSession? = null
    private var roomQueue: List<TogetherQueueItem> = emptyList()
    private var roomCode = ""
    private var idleLeaveJob: Job? = null
    private var interrupted = false

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
                TogetherQueueMaterializer(context, cache).prepare(editableQueue)
            }
            if (!isCurrentGeneration(generation)) return@runCatching
            roomQueue = prepared
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
                return@runCatching
            }
            nativeSession = createdSession
            roomCode = createdSession.roomCode()
            player.setRoomPlaybackConstraints(true)
            mutableState.value = TogetherSessionState.WaitingReady(roomCode, roomQueue, emptyList())
            callbackGate.complete(Unit)
        }.onFailure { error ->
            callbackGate.complete(Unit)
            if (isCurrentGeneration(generation)) fail(error)
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
                TogetherQueueMaterializer(context, cache).prepare(localMatches)
            }
            if (!isCurrentGeneration(generation)) return@runCatching
            this.roomCode = normalized
            roomQueue = preparedMatches
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
                return@runCatching
            }
            nativeSession = joinedSession
            player.setRoomPlaybackConstraints(true)
            callbackGate.complete(Unit)
        }.onFailure { error ->
            callbackGate.complete(Unit)
            if (isCurrentGeneration(generation)) fail(error)
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
    }

    private fun handleNativeEvent(raw: String) {
        runCatching {
            val event = JSONObject(raw)
            when (event.optString("type")) {
                "snapshot" -> applySnapshot(event)
                "queue" -> {
                    roomQueue = parseQueue(event.optJSONArray("items"))
                    mutableState.value = TogetherSessionState.WaitingReady(
                        roomCode,
                        roomQueue,
                        parseMembers(event)
                    )
                }
                "stream_urls" -> {
                    val array = event.optJSONArray("urls")
                    val urls = buildList {
                        if (array != null) for (i in 0 until array.length()) add(array.optString(i))
                    }
                    if (urls.isNotEmpty()) player.replaceQueueWithStreamUrls(urls)
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
            add(TogetherQueueItem(
                stableId = item.optString("id", i.toString()),
                title = item.optString("title", item.optString("name")),
                artist = item.optString("artist"),
                sourceUri = item.optString("stream_url"),
                sizeBytes = item.optLong("size"),
                contentRoot = item.optString("root"),
                receivedFileId = item.optString("file_id").takeIf(String::isNotBlank)
            ))
        }
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

    private fun detachSessionState(): TogetherNativeBridge.NativeSession? {
        val session = nativeSession
        nativeSession = null
        roomCode = ""
        roomQueue = emptyList()
        idleLeaveJob?.cancel()
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
        fun create(
            context: Context,
            player: TogetherPlayerPort,
            foregroundController: TogetherForegroundController
        ): TogetherSessionOwner = TogetherSessionOwner(
            context.applicationContext,
            player,
            foregroundController,
            GomobileTogetherNativeBridge(),
            Dispatchers.Main.immediate,
            Dispatchers.IO
        )
    }
}
