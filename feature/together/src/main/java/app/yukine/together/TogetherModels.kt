package app.yukine.together

import app.yukine.model.Track
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

sealed interface TogetherQueueSource {
    data class Local(
        val uri: String,
        val sizeBytes: Long = 0L,
        val contentRoot: String = ""
    ) : TogetherQueueSource

    data class Streaming(
        val provider: String,
        val providerTrackId: String,
        val quality: String,
        val durationMs: Long = 0L,
        val resolvedUrl: String = "",
        val headers: Map<String, String> = emptyMap(),
        val expiresAtEpochMs: Long? = null,
        val mimeType: String = "",
        val supportsRange: Boolean = true,
        val sizeBytes: Long = 0L,
        /** Host-private LX metadata needed to refresh expiring URLs; never serialized to peers. */
        val luoxueMusicInfoJson: String? = null
    ) : TogetherQueueSource
}

data class TogetherQueueItem(
    val stableId: String,
    val title: String,
    val artist: String,
    val sourceUri: String,
    val sizeBytes: Long = 0L,
    val contentRoot: String = "",
    val shareable: Boolean = true,
    val receivedFileId: String? = null,
    val album: String = "",
    val artworkUri: String = "",
    val durationMs: Long = 0L,
    val source: TogetherQueueSource = TogetherQueueSource.Local(
        uri = sourceUri,
        sizeBytes = sizeBytes,
        contentRoot = contentRoot
    )
) {
    val dedupeKey: String
        get() = when (val value = source) {
            is TogetherQueueSource.Local -> "local:${value.uri}"
            is TogetherQueueSource.Streaming ->
                "streaming:${value.provider}:${value.providerTrackId}"
        }
}

sealed interface TogetherPlaylistRef {
    data class Local(val playlistId: Long) : TogetherPlaylistRef
    data class Streaming(
        val provider: String,
        val providerPlaylistId: String
    ) : TogetherPlaylistRef
}

data class TogetherPlaylistSummary(
    val ref: TogetherPlaylistRef,
    val title: String,
    val subtitle: String = "",
    val trackCount: Int = 0
)

data class TogetherSkippedItem(
    val title: String,
    val reason: String
)

data class TogetherPlaylistCatalogResult(
    val playlists: List<TogetherPlaylistSummary>,
    val warnings: List<String> = emptyList()
)

data class TogetherPlaylistLoadResult(
    val title: String,
    val items: List<TogetherQueueItem>,
    val skipped: List<TogetherSkippedItem> = emptyList()
)

interface TogetherPlaylistCatalogPort {
    suspend fun listPlaylists(): TogetherPlaylistCatalogResult
    suspend fun loadPlaylist(ref: TogetherPlaylistRef): TogetherPlaylistLoadResult
}

object EmptyTogetherPlaylistCatalog : TogetherPlaylistCatalogPort {
    override suspend fun listPlaylists(): TogetherPlaylistCatalogResult =
        TogetherPlaylistCatalogResult(emptyList())

    override suspend fun loadPlaylist(ref: TogetherPlaylistRef): TogetherPlaylistLoadResult =
        TogetherPlaylistLoadResult("", emptyList())
}

fun interface TogetherStreamingResolverPort {
    fun resolve(source: TogetherQueueSource.Streaming): TogetherQueueSource.Streaming?
}

object EmptyTogetherStreamingResolver : TogetherStreamingResolverPort {
    override fun resolve(source: TogetherQueueSource.Streaming): TogetherQueueSource.Streaming? = null
}

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
    fun canEditQueue(): Boolean = false

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
    fun onLocalQueueChanged(tracks: List<Track>)
    override fun canEditQueue(): Boolean
    fun onSystemInterruption(buffering: Boolean)
    fun setTransferForegroundActive(active: Boolean)
}

interface TogetherQueueEditPort {
    fun remove(stableId: String)
    /** Move the row [fromStableId] to the current position of [toStableId] in the full Media3 queue. */
    fun move(fromStableId: String, toStableId: String)
}

object EmptyTogetherQueueEditPort : TogetherQueueEditPort {
    override fun remove(stableId: String) = Unit
    override fun move(fromStableId: String, toStableId: String) = Unit
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
    fun currentQueueTracks(): List<Track> = emptyList()
    fun setRoomPlaybackConstraints(enabled: Boolean)
    fun replaceQueueWithTracks(tracks: List<Track>) = Unit
    fun replaceQueueWithItems(queue: List<TogetherQueueItem>) = Unit
    fun replaceQueueWithStreamUrls(queue: List<TogetherQueueItem>, urls: List<String>)
}
