package app.yukine.together

import kotlinx.coroutines.flow.StateFlow

enum class TogetherConnectionKind {
    Unknown,
    Direct,
    Turn
}

data class TogetherMember(
    val idHash: String,
    val nickname: String,
    val ready: Boolean,
    val buffering: Boolean,
    val downloadPercent: Int,
    val driftMs: Long?
)

data class TogetherQueueItem(
    val stableId: String,
    val title: String,
    val artist: String,
    val sourceUri: String,
    val sizeBytes: Long = 0L,
    val contentRoot: String = "",
    val shareable: Boolean = true,
    val receivedFileId: String? = null
)

data class TogetherTransfer(
    val fileId: String,
    val fileName: String,
    val bytesVerified: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val complete: Boolean
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f
        else (bytesVerified.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

data class TogetherJoinPreview(
    val queue: List<TogetherQueueItem>,
    val freeBytes: Long
)

data class TogetherConnectOptions(
    val nickname: String,
    val relays: List<String> = DEFAULT_RELAYS,
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    val cacheDirectory: String = ""
) {
    companion object {
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom"
        )
    }
}

sealed interface TogetherSessionState {
    data object Idle : TogetherSessionState
    data class Preparing(val queue: List<TogetherQueueItem>) : TogetherSessionState
    data class Connecting(val joining: Boolean) : TogetherSessionState
    data class WaitingReady(
        val roomCode: String,
        val queue: List<TogetherQueueItem>,
        val members: List<TogetherMember>
    ) : TogetherSessionState

    data class Active(
        val roomCode: String,
        val queue: List<TogetherQueueItem>,
        val members: List<TogetherMember>,
        val currentIndex: Int,
        val driftMs: Long?,
        val paused: Boolean,
        val buffering: Boolean,
        val transfer: TogetherTransfer?,
        val connectionKind: TogetherConnectionKind
    ) : TogetherSessionState

    data class Reconnecting(
        val roomCodeHash: String,
        val attempt: Int,
        val transfer: TogetherTransfer?
    ) : TogetherSessionState

    data class Leaving(val reason: String) : TogetherSessionState
    data class Failed(val message: String, val recoverable: Boolean) : TogetherSessionState
}

interface TogetherSessionPort {
    val state: StateFlow<TogetherSessionState>

    suspend fun testConnection(options: TogetherConnectOptions): Result<String>

    suspend fun previewJoin(
        roomCode: String,
        options: TogetherConnectOptions
    ): Result<TogetherJoinPreview>

    suspend fun create(
        editableQueue: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit>

    suspend fun join(
        roomCode: String,
        localMatches: List<TogetherQueueItem>,
        options: TogetherConnectOptions
    ): Result<Unit>

    suspend fun leave(reason: String)

    suspend fun saveReceived(fileId: String): Result<String>
}

interface TogetherSessionHostPort : TogetherSessionPort {
    fun onLocalPlayback(event: TogetherPlaybackEvent)
    fun onSystemInterruption(buffering: Boolean)
    fun setTransferForegroundActive(active: Boolean)
}

sealed interface TogetherPlaybackEvent {
    data class PauseChanged(val paused: Boolean) : TogetherPlaybackEvent
    data class Seeked(val positionMs: Long) : TogetherPlaybackEvent
    data class SpeedChanged(val speed: Float) : TogetherPlaybackEvent
    data class QueueIndexChanged(val index: Int) : TogetherPlaybackEvent
    data class BufferingChanged(val buffering: Boolean) : TogetherPlaybackEvent
    data object PlaybackRestarted : TogetherPlaybackEvent
}

interface TogetherPlayerPort {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun skipToQueueIndex(index: Int)
    fun currentPositionMs(): Long
    fun currentQueueIndex(): Int
    fun setRoomPlaybackConstraints(enabled: Boolean)
    fun replaceQueueWithStreamUrls(urls: List<String>)
}
