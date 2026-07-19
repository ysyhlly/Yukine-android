package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingPlaybackResolutionPath
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylistSyncSnapshot
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.Job

const val STREAMING_AUTH_REDIRECT_URI = "echo-next://streaming-auth"

data class StreamingManualCookieDialogState(
    val provider: StreamingProviderName? = null,
    val unavailable: Boolean = false,
    val title: String = "",
    val hint: String = "MUSIC_U=...; os=pc; appver=...",
    val unavailableStatus: String = ""
)

data class StreamingManualCookieAuthRequest(
    val provider: StreamingProviderName,
    val callbackUri: String,
    val cookieHeader: String,
    val emptyStatus: String = "",
    val savedStatus: String = ""
)

data class StreamingPlaylistImportDialogState(
    val title: String = "",
    val hint: String = ""
)

data class StreamingPlaylistImportStartRequest(
    val provider: StreamingProviderName? = null,
    val providerPlaylistId: String = "",
    val invalidStatus: String = "",
    val resolvingStatus: String = "",
    val valid: Boolean = false
)

data class ResolvedStreamingTrackList(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0,
    val resolutionPath: StreamingPlaybackResolutionPath? = null
)

data class StreamingQueueResolveTarget(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0
)

data class StreamingRecommendationTrackList(
    val tracks: List<Track> = emptyList()
)

data class StreamingDailyRecommendationRequest(
    val provider: StreamingProviderName,
    val loadingStatus: String,
    val emptyStatus: String,
    val title: String
)

data class StreamingHeartbeatRecommendationRequest(
    val provider: StreamingProviderName,
    val loadingStatus: String,
    val emptyStatus: String,
    val playingStatus: String
)

data class StreamingRecommendationPresentation(
    val tracks: List<Track> = emptyList(),
    val emptyStatus: String = "",
    val readyStatus: String = "",
    val title: String = ""
) {
    val empty: Boolean
        get() = tracks.isEmpty()
}

data class HeartbeatRecommendationSeedRequest(
    val candidates: List<Track> = emptyList(),
    val seedTrackId: String = "",
    val playlistId: String = "",
    val seedMissingMessage: String = ""
) {
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    val hasSeed: Boolean
        get() = seedTrackId.isNotEmpty()
}

data class StreamingProviderPickerState(
    val providers: List<StreamingProviderDescriptor> = emptyList(),
    val labels: Array<String> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamingProviderPickerState) return false
        return providers == other.providers && labels.contentEquals(other.labels)
    }

    override fun hashCode(): Int = 31 * providers.hashCode() + labels.contentHashCode()
}

data class StreamingProviderPickerRequest(
    val pickerState: StreamingProviderPickerState = StreamingProviderPickerState(),
    val title: String = "",
    val emptyStatus: String = "",
    val valid: Boolean = false
)

data class StreamingPlaylistImportStatus(
    val matchedCount: Int = 0,
    val totalRequested: Int = 0,
    val unresolvedCount: Int = 0
)

data class StreamingPlaylistExportPresentation(
    val status: String = ""
)

data class StreamingPlaylistExportRequest(
    val playlistName: String = "",
    val tracks: List<Track> = emptyList(),
    val status: String = "",
    val valid: Boolean = false
)

data class StreamingPlaylistImportTarget(
    val provider: StreamingProviderName? = null,
    val providerPlaylistId: String = "",
    val invalid: Boolean = false
)

data class StreamingRecoveryResolution(
    val expectedTrackId: Long,
    val track: Track,
    val quality: StreamingAudioQuality,
    val positionMs: Long
)

data class StreamingPlaybackStatusText(
    val resolving: String = "",
    val resolveFailed: String = "",
    val qualityDowngrading: String = "",
    val qualityDowngraded: String = "",
    val qualityRefreshing: String = "",
    val qualityRefreshed: String = ""
)

data class StreamingStatusText(
    val streamingQualityApplied: String = ""
)

/** Java-friendly single-arg callback (avoids java.util.function.Consumer which needs API 24). */
fun interface StreamingCallback<T> {
    fun onResult(value: T)
}

fun interface StreamingPlaybackTask {
    fun run(onComplete: Runnable)
}

interface StreamingPlaybackTaskQueue {
    fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask)

    fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask)

    fun scheduleNextUrlResolve(task: StreamingPlaybackTask)
}

/** Java-friendly two-arg callback (avoids java.util.function.BiConsumer which needs API 24). */
fun interface StreamingBiCallback<A, B> {
    fun onResult(first: A, second: B)
}

data class StreamingLocalPlaylistImportResult(
    val playlistName: String = "",
    val playlistAddedCount: Int = 0,
    val empty: Boolean = false
)

data class StreamingAccountPlaylistImportResult(
    val playlistCount: Int = 0,
    val importedPlaylistCount: Int = 0,
    val importedTrackCount: Int = 0,
    val failedCount: Int = 0
)

interface StreamingLocalPlaylistOperations {
    fun playlistExists(localPlaylistId: Long): Boolean

    fun importStreamingPlaylist(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        streamingTracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): PlaylistImportResult

    fun syncStreamingPlaylist(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        streamingTracks: List<StreamingTrack>
    ): StreamingLocalPlaylistSyncResult

    fun ensureStreamingLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName
    ): StreamingLoginPlaylistResult

    fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist?

    fun linkedPlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): StreamingPlaylistSyncStore.LinkedPlaylist?

    fun linkPlaylist(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        direction: app.yukine.streaming.StreamingPlaylistSyncDirection
    ) = Unit

    fun localPlaylistSnapshot(localPlaylistId: Long): StreamingLocalPlaylistSnapshot? = null

    fun markPlaylistSynced(localPlaylistId: Long) = Unit

    fun updatePlaylistSyncBaseline(
        localPlaylistId: Long,
        snapshot: StreamingPlaylistSyncSnapshot,
        localUpdatedAtMs: Long?,
        remoteUpdatedAtMs: Long?,
        remoteObservedChangeAtMs: Long?
    ) = Unit
}

data class StreamingLocalPlaylistSnapshot(
    val playlistId: Long,
    val playlistName: String,
    val tracks: List<Track>
)

data class StreamingLocalPlaylistSyncResult(
    val playlistId: Long = -1L,
    val syncedCount: Int = 0,
    val empty: Boolean = false,
    val errorMessage: String = ""
)

data class StreamingPlaylistSyncTarget(
    val link: StreamingPlaylistSyncStore.LinkedPlaylist? = null,
    val missingLink: Boolean = false
)

data class StreamingPlaylistSyncStartRequest(
    val link: StreamingPlaylistSyncStore.LinkedPlaylist? = null,
    val status: String = "",
    val valid: Boolean = false
)

data class StreamingLoginPlaylistResult(
    val playlistId: Long = -1L,
    val playlistName: String = ""
)

data class StreamingLoginPlaylistRequest(
    val provider: StreamingProviderName,
    val playlistName: String
)

data class StreamingLocalPlaylistImportPresentation(
    val empty: Boolean = false,
    val status: String = "",
    val showLoadedDialog: Boolean = false
)

data class StreamingLocalPlaylistSyncPresentation(
    val empty: Boolean = false,
    val status: String = ""
)

data class StreamingLoginPlaylistPresentation(
    val status: String = "",
    val playlistId: Long = -1L
)

interface StreamingTrackMatchStore {
    fun directProviderTrackId(track: Track, provider: StreamingProviderName): String = ""

    fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String

    fun saveProviderTrackId(track: Track, provider: StreamingProviderName, providerTrackId: String)

    fun providerTrackIdFromCandidates(
        candidates: List<Track?>?,
        provider: StreamingProviderName?
    ): String = ""

    fun heartbeatSeedCandidates(
        serviceSnapshot: PlaybackStateSnapshot?,
        serviceQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?,
        viewModelQueue: List<Track?>?
    ): List<Track> = emptyList()

    fun snapshotQueueForHeartbeat(
        serviceQueue: List<Track?>?,
        viewModelQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?
    ): List<Track> = emptyList()

    fun heartbeatSeedMissMessage(
        provider: StreamingProviderName?,
        snapshot: PlaybackStateSnapshot?,
        storeSnapshot: PlaybackStateSnapshot?,
        queue: List<Track?>?
    ): String = ""
}

