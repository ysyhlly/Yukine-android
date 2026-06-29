package app.yukine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Playlist
import app.yukine.model.PlaylistImportResult
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

internal const val STREAMING_AUTH_REDIRECT_URI = "echo-next://streaming-auth"

data class LibraryStoreState(
    val allTracks: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val favoriteTrackIds: Set<Long> = emptySet(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentRecords: List<TrackPlayRecord> = emptyList(),
    val mostPlayedRecords: List<TrackPlayRecord> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val remoteSources: List<RemoteSource> = emptyList()
)

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
    val index: Int = 0
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
    val track: Track,
    val quality: StreamingAudioQuality,
    val positionMs: Long
)

data class StreamingPlaybackStatusText(
    val resolving: String = "",
    val resolveFailed: String = "",
    val qualityDowngrading: String = "",
    val qualityDowngraded: String = ""
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
}

data class StreamingLocalPlaylistSyncResult(
    val playlistId: Long = -1L,
    val syncedCount: Int = 0,
    val empty: Boolean = false
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

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val libraryState = MutableStateFlow(LibraryStoreState())

    val library: StateFlow<LibraryStoreState> = libraryState.asStateFlow()

    fun updateLibrary(snapshot: LibraryStoreState) {
        libraryState.value = snapshot
    }

    fun replaceLibrary(
        allTracks: List<Track>,
        visibleTracks: List<Track>,
        favoriteTrackIds: Set<Long>
    ) {
        val current = libraryState.value
        libraryState.value = current.copy(
            allTracks = allTracks.toList(),
            visibleTracks = visibleTracks.toList(),
            favoriteTrackIds = favoriteTrackIds.toSet()
        )
    }

    fun updateVisibleTracks(visibleTracks: List<Track>) {
        libraryState.value = libraryState.value.copy(
            visibleTracks = visibleTracks.toList()
        )
    }

    fun applyCollections(
        favoriteTrackIds: Set<Long>,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        mostPlayedRecords: List<TrackPlayRecord>,
        playlists: List<Playlist>,
        selectedPlaylistTracks: List<Track>,
        remoteSources: List<RemoteSource>
    ) {
        val current = libraryState.value
        libraryState.value = current.copy(
            favoriteTrackIds = favoriteTrackIds.toSet(),
            favoriteTracks = favoriteTracks.toList(),
            recentRecords = recentRecords.toList(),
            mostPlayedRecords = mostPlayedRecords.toList(),
            playlists = playlists.toList(),
            selectedPlaylistTracks = selectedPlaylistTracks.toList(),
            remoteSources = remoteSources.toList()
        )
    }

    fun clearPlayHistory() {
        libraryState.value = libraryState.value.copy(
            recentRecords = emptyList(),
            mostPlayedRecords = emptyList()
        )
    }

    fun toggleFavorite(trackId: Long): Boolean {
        val current = libraryState.value
        val nextFavoriteIds = current.favoriteTrackIds.toMutableSet()
        val nextValue = if (nextFavoriteIds.contains(trackId)) {
            nextFavoriteIds.remove(trackId)
            false
        } else {
            nextFavoriteIds.add(trackId)
            true
        }
        libraryState.value = current.copy(
            favoriteTrackIds = nextFavoriteIds,
            favoriteTracks = updateFavoriteTracks(current, trackId, nextValue)
        )
        return nextValue
    }

    fun setFavorite(trackId: Long, favorite: Boolean) {
        val current = libraryState.value
        val nextFavoriteIds = current.favoriteTrackIds.toMutableSet()
        if (favorite) {
            nextFavoriteIds.add(trackId)
        } else {
            nextFavoriteIds.remove(trackId)
        }
        libraryState.value = current.copy(
            favoriteTrackIds = nextFavoriteIds,
            favoriteTracks = updateFavoriteTracks(current, trackId, favorite)
        )
    }

    private fun updateFavoriteTracks(
        current: LibraryStoreState,
        trackId: Long,
        favorite: Boolean
    ): List<Track> {
        if (!favorite) {
            return current.favoriteTracks.filterNot { it.id == trackId }
        }
        if (current.favoriteTracks.any { it.id == trackId }) {
            return current.favoriteTracks
        }
        val track = current.allTracks.firstOrNull { it.id == trackId }
            ?: current.visibleTracks.firstOrNull { it.id == trackId }
            ?: current.selectedPlaylistTracks.firstOrNull { it.id == trackId }
            ?: return current.favoriteTracks
        return current.favoriteTracks + track
    }

    /**
     * Loads the playlists saved on the user's account for the given provider (requires the
     * gateway's userPlaylists endpoint). Results are kept on [StreamingSearchState.userPlaylists].
     */
    /**
     * Fetches the user's liked/saved tracks from the provider and delivers them via callback.
     */
    /**
     * Fetches the provider's daily recommendation tracks (e.g. NetEase 每日推荐) and delivers them
     * via callback. Delivers an empty list on failure.
     */
    /**
     * Fetches the provider's heartbeat / intelligence recommendation tracks (e.g. NetEase 心动推荐)
     * and delivers them via callback. Delivers an empty list on failure.
     */
    /**
     * Fetches a streaming playlist's full track list and delivers the contained streaming tracks
     * via [onResolved] so the caller (MainActivity) can persist them as a local playlist. Delivers
     * an empty list on failure.
     */
}

object NavigationRouteStateStore {
    private const val SELECTED_TAB = "selectedTab"
    private const val LIBRARY_MODE = "libraryMode"
    private const val SELECTED_LIBRARY_GROUP_KEY = "selectedLibraryGroupKey"
    private const val SELECTED_LIBRARY_GROUP_TITLE = "selectedLibraryGroupTitle"
    private const val SELECTED_PLAYLIST_ID = "selectedPlaylistId"
    private const val SEARCH_QUERY = "searchQuery"
    private const val NETWORK_PAGE = "networkPage"
    private const val SETTINGS_PAGE = "settingsPage"
    private const val SELECTED_REMOTE_SOURCE_ID = "selectedRemoteSourceId"

    fun restore(savedStateHandle: SavedStateHandle): NavigationRouteState {
        val restoredTab = savedStateHandle[SELECTED_TAB] ?: MainRoutes.TAB_HOME
        val restoredLibraryMode = savedStateHandle[LIBRARY_MODE] ?: LibraryGrouping.SONGS
        val selectedTab = when {
            restoredTab == MainRoutes.TAB_NOW -> MainRoutes.TAB_HOME
            restoredTab == MainRoutes.TAB_LIBRARY && restoredLibraryMode == LibraryGrouping.HOME -> MainRoutes.TAB_HOME
            else -> restoredTab
        }
        val libraryMode = if (restoredLibraryMode == LibraryGrouping.HOME) {
            LibraryGrouping.SONGS
        } else {
            restoredLibraryMode
        }
        return NavigationRouteState(
            selectedTab = selectedTab,
            libraryMode = libraryMode,
            selectedLibraryGroupKey = savedStateHandle[SELECTED_LIBRARY_GROUP_KEY] ?: "",
            selectedLibraryGroupTitle = savedStateHandle[SELECTED_LIBRARY_GROUP_TITLE] ?: "",
            selectedPlaylistId = savedStateHandle[SELECTED_PLAYLIST_ID] ?: -1L,
            searchQuery = savedStateHandle[SEARCH_QUERY] ?: "",
            networkPage = savedStateHandle[NETWORK_PAGE] ?: MainRoutes.NETWORK_HOME,
            settingsPage = savedStateHandle[SETTINGS_PAGE] ?: MainRoutes.SETTINGS_HOME,
            selectedRemoteSourceId = savedStateHandle[SELECTED_REMOTE_SOURCE_ID] ?: -1L
        )
    }

    fun save(savedStateHandle: SavedStateHandle, state: NavigationRouteState) {
        savedStateHandle[SELECTED_TAB] = state.selectedTab
        savedStateHandle[LIBRARY_MODE] = state.libraryMode
        savedStateHandle[SELECTED_LIBRARY_GROUP_KEY] = state.selectedLibraryGroupKey
        savedStateHandle[SELECTED_LIBRARY_GROUP_TITLE] = state.selectedLibraryGroupTitle
        savedStateHandle[SELECTED_PLAYLIST_ID] = state.selectedPlaylistId
        savedStateHandle[SEARCH_QUERY] = state.searchQuery
        savedStateHandle[NETWORK_PAGE] = state.networkPage
        savedStateHandle[SETTINGS_PAGE] = state.settingsPage
        savedStateHandle[SELECTED_REMOTE_SOURCE_ID] = state.selectedRemoteSourceId
    }
}

