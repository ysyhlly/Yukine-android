package app.yukine.together

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TogetherSessionClient : TogetherSessionPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
    private var host: TogetherSessionHostPort? = null
    private var collection: Job? = null

    override val state: StateFlow<TogetherSessionState> = mutableState.asStateFlow()

    fun attach(next: TogetherSessionHostPort?) {
        if (host === next) return
        collection?.cancel()
        host = next
        if (next == null) {
            mutableState.value = TogetherSessionState.Idle
            return
        }
        collection = scope.launch {
            next.state.collect { mutableState.value = it }
        }
    }

    override suspend fun testConnection(options: TogetherConnectOptions): Result<String> =
        host?.testConnection(options)
            ?: Result.failure(IllegalStateException("Playback service is not connected"))

    override suspend fun previewJoin(
        roomCode: String,
        options: TogetherConnectOptions
    ): Result<TogetherJoinPreview> = host?.previewJoin(roomCode, options)
        ?: Result.failure(IllegalStateException("Playback service is not connected"))

    override suspend fun create(
        editableQueue: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit> = host?.create(editableQueue, options)
        ?: Result.failure(IllegalStateException("Playback service is not connected"))

    override suspend fun join(
        roomCode: String,
        localMatches: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit> = host?.join(roomCode, localMatches, options)
        ?: Result.failure(IllegalStateException("Playback service is not connected"))

    override suspend fun leave(reason: String) {
        host?.leave(reason)
    }

    override suspend fun saveReceived(fileId: String): Result<String> =
        host?.saveReceived(fileId)
            ?: Result.failure(IllegalStateException("Playback service is not connected"))

    fun close() {
        collection?.cancel()
        host = null
        scope.cancel()
    }
}
