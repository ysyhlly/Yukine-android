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
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingPlaylistLinkParser
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import app.yukine.ui.HomeDashboardUiState
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.NetworkSourceUiState
import app.yukine.ui.PlaylistRowUiState
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.QueueTrackUiState
import app.yukine.ui.TrackRowUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder
import kotlin.random.Random
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val STREAMING_AUTH_REDIRECT_URI = "echo-next://streaming-auth"
private const val HEARTBEAT_SEED_SAMPLE_SIZE = 12

data class MainActivityRouteState(
    val selectedTab: String = MainRoutes.TAB_HOME,
    val libraryMode: String = LibraryGrouping.SONGS,
    val selectedLibraryGroupKey: String = "",
    val selectedLibraryGroupTitle: String = "",
    val selectedPlaylistId: Long = -1L,
    val searchQuery: String = "",
    val networkPage: String = MainRoutes.NETWORK_HOME,
    val settingsPage: String = MainRoutes.SETTINGS_HOME,
    val selectedRemoteSourceId: Long = -1L
)

data class MainActivityLibraryState(
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

data class MainActivityPlaybackState(
    val snapshot: PlaybackStateSnapshot = PlaybackStateSnapshot.empty(),
    val queue: List<Track> = emptyList()
)

data class MainActivityTrackListUiState(
    val title: String = "",
    val rows: List<TrackRowUiState> = emptyList()
)

data class MainActivityHomeDashboardUiState(
    val content: HomeDashboardUiState = HomeDashboardUiState()
)

data class MainActivityLibraryGroupsUiState(
    val title: String = "",
    val rows: List<LibraryGroupUiState> = emptyList()
)

data class MainActivityPlaylistTracksUiState(
    val title: String = "",
    val rows: List<PlaylistTrackUiState> = emptyList()
)

data class MainActivityQueueUiState(
    val rows: List<QueueTrackUiState> = emptyList()
)

data class MainActivityPlaylistListUiState(
    val title: String = "",
    val rows: List<PlaylistRowUiState> = emptyList()
)

data class MainActivityNetworkSourcesUiState(
    val title: String = "",
    val rows: List<NetworkSourceUiState> = emptyList()
)

data class MainActivityStreamingState(
    val providers: List<StreamingProviderDescriptor> = emptyList(),
    val providerCapabilities: List<StreamingProviderCapability> = emptyList(),
    val providerHealth: List<StreamingProviderHealth> = emptyList(),
    val diagnostics: StreamingGatewayDiagnostics = StreamingGatewayDiagnostics(),
    val selectedProvider: StreamingProviderName = StreamingProviderName.MOCK,
    val searchQuery: String = "",
    val searchMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val searchResult: StreamingSearchResult? = null,
    val resolvedPlaybackSource: StreamingPlaybackSource? = null,
    val resolvedPlaybackTrack: Track? = null,
    val authStates: Map<StreamingProviderName, StreamingAuthState> = emptyMap(),
    val pendingAuthLaunch: MainActivityStreamingAuthLaunch? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
    val playlistImportSummary: app.yukine.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary? = null,
    val playlistImporting: Boolean = false,
    val userPlaylists: List<app.yukine.streaming.StreamingPlaylist> = emptyList(),
    val userPlaylistsLoading: Boolean = false
)

data class MainActivityStreamingAuthLaunch(
    val provider: StreamingProviderName,
    val launchUrl: String,
    val kind: StreamingAuthKind
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

interface StreamingLocalPlaylistOperations {
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

data class RecommendationScreenState(
    val title: String = "",
    val tracks: List<app.yukine.model.Track> = emptyList(),
    val loading: Boolean = false
)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val streamingRepositorySource: StreamingRepositorySource = EmptyStreamingRepositorySource,
    private val dashboardRepository: app.yukine.dashboard.DashboardRepository? = null
) : ViewModel(), StreamingSearchActionHandler, StreamingAuthCallbackHandler {
    private var streamingRepository: StreamingRepository = streamingRepositorySource.current()
    private val routeState = MutableStateFlow(MainActivityRouteStateStore.restore(savedStateHandle))
    private val libraryState = MutableStateFlow(MainActivityLibraryState())
    private val playbackState = MutableStateFlow(MainActivityPlaybackState())
    private val homeDashboardState = MutableStateFlow(MainActivityHomeDashboardUiState())
    private val _dashboardLoading = MutableStateFlow(false)
    val dashboardLoading: StateFlow<Boolean> = _dashboardLoading.asStateFlow()
    private val trackListState = MutableStateFlow(MainActivityTrackListUiState())
    private val libraryGroupsState = MutableStateFlow(MainActivityLibraryGroupsUiState())
    private val playlistTracksState = MutableStateFlow(MainActivityPlaylistTracksUiState())
    private val queueState = MutableStateFlow(MainActivityQueueUiState())
    private val playlistListState = MutableStateFlow(MainActivityPlaylistListUiState())
    private val networkSourcesState = MutableStateFlow(MainActivityNetworkSourcesUiState())
    private val streamingState = MutableStateFlow(MainActivityStreamingState())
    private val _recommendationState = MutableStateFlow(RecommendationScreenState())
    val recommendationScreen: StateFlow<RecommendationScreenState> = _recommendationState.asStateFlow()
    private var streamingLocalPlaylistOperations: StreamingLocalPlaylistOperations? = null
    private var streamingTrackMatchStore: StreamingTrackMatchStore? = null
    private var streamingActionGateway: MainActivityStreamingActionGateway? = null
    private var streamingPlaybackPlanner: StreamingPlaybackResolvePlanner? = null
    private var streamingPlaybackTaskQueue: StreamingPlaybackTaskQueue? = null
    private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()
    private var heartbeatSeedCursor = 0
    private var lastHistoryRefreshTrackId = -1L

    val state: StateFlow<MainActivityRouteState> = routeState.asStateFlow()
    val library: StateFlow<MainActivityLibraryState> = libraryState.asStateFlow()
    val playback: StateFlow<MainActivityPlaybackState> = playbackState.asStateFlow()
    val homeDashboard: StateFlow<MainActivityHomeDashboardUiState> = homeDashboardState.asStateFlow()
    val trackList: StateFlow<MainActivityTrackListUiState> = trackListState.asStateFlow()
    val libraryGroups: StateFlow<MainActivityLibraryGroupsUiState> = libraryGroupsState.asStateFlow()
    val playlistTracks: StateFlow<MainActivityPlaylistTracksUiState> = playlistTracksState.asStateFlow()
    val queue: StateFlow<MainActivityQueueUiState> = queueState.asStateFlow()
    val playlistList: StateFlow<MainActivityPlaylistListUiState> = playlistListState.asStateFlow()
    val networkSources: StateFlow<MainActivityNetworkSourcesUiState> = networkSourcesState.asStateFlow()
    val streaming: StateFlow<MainActivityStreamingState> = streamingState.asStateFlow()

    fun configureStreamingRepository(): Job {
        streamingRepository = streamingRepositorySource.current()
        return clearExpiredStreamingCache()
    }

    fun bindStreamingActionGateway(gateway: MainActivityStreamingActionGateway?) {
        streamingActionGateway = gateway
    }

    fun bindStreamingPlaybackCoordinator(
        planner: StreamingPlaybackResolvePlanner?,
        taskQueue: StreamingPlaybackTaskQueue?
    ) {
        streamingPlaybackPlanner = planner
        streamingPlaybackTaskQueue = taskQueue
    }

    fun clearExpiredStreamingCache(): Job {
        return viewModelScope.launch {
            runCatching {
                streamingRepository.clearExpiredCache()
            }.onFailure { error ->
                failStreamingRequest(error.message)
            }
        }
    }

    fun update(transform: (MainActivityRouteState) -> MainActivityRouteState) {
        val next = transform(routeState.value)
        routeState.value = next
        MainActivityRouteStateStore.save(savedStateHandle, next)
    }

    fun updateRoute(snapshot: MainActivityRouteState) {
        routeState.value = snapshot
        MainActivityRouteStateStore.save(savedStateHandle, snapshot)
    }

    fun updateLibrary(snapshot: MainActivityLibraryState) {
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
        libraryState.value = current.copy(favoriteTrackIds = nextFavoriteIds)
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
        libraryState.value = current.copy(favoriteTrackIds = nextFavoriteIds)
    }

    fun updatePlayback(snapshot: MainActivityPlaybackState) {
        playbackState.value = snapshot
    }

    fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot?): PlaybackStateSnapshot {
        val previous = playbackState.value.snapshot
        playbackState.value = playbackState.value.copy(
            snapshot = snapshot ?: PlaybackStateSnapshot.empty()
        )
        return previous
    }

    fun resetPlayback() {
        playbackState.value = MainActivityPlaybackState()
        lastHistoryRefreshTrackId = -1L
    }

    fun updatePlaybackQueue(queue: List<Track>) {
        playbackState.value = playbackState.value.copy(queue = queue.toList())
    }

    fun updateHomeDashboard(content: HomeDashboardUiState) {
        homeDashboardState.value = MainActivityHomeDashboardUiState(content)
    }

    fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot?) {
        val playback = snapshot ?: PlaybackStateSnapshot.empty()
        val track = playback.currentTrack ?: return
        val durationMs = when {
            playback.durationMs > 0L -> playback.durationMs
            track.durationMs > 0L -> track.durationMs
            else -> 0L
        }
        val progress = if (durationMs <= 0L) {
            0f
        } else {
            (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
        val current = homeDashboardState.value.content
        homeDashboardState.value = MainActivityHomeDashboardUiState(
            current.copy(
                heroSubtitle = "接上 ${track.artist} 的「${track.title}」，或者从最近入库里挑一张封面开始。",
                continueTitle = track.title,
                continueSubtitle = track.subtitle(),
                continueDetail = if (playback.playing) "正在播放" else "继续播放",
                continueAlbumArtUri = track.albumArtUri,
                continueProgress = progress,
                continuePlaying = playback.playing
            )
        )
    }

    /**
     * Fetch homepage data from backend, falling back to local data on failure.
     * Call this instead of direct HomeDashboardStateFactory when backend is configured.
     */
    fun fetchHomeDashboard(
        localTracks: List<Track>,
        localRecords: List<TrackPlayRecord>,
        localPlayback: PlaybackStateSnapshot?,
        onComplete: Runnable? = null
    ): Job {
        _dashboardLoading.value = true
        return viewModelScope.launch {
            val repo = dashboardRepository
            val state = if (repo != null) {
                repo.fetchHome(localTracks, localRecords, localPlayback)
            } else {
                // No repository injected, use local factory
                HomeDashboardStateFactory.create(
                    "zh",
                    localTracks,
                    if (localTracks.isNotEmpty()) localTracks else emptyList(),
                    localRecords,
                    localPlayback
                )
            }
            homeDashboardState.value = MainActivityHomeDashboardUiState(state)
            _dashboardLoading.value = false
            onComplete?.run()
        }
    }

    /**
     * Toggle playback via backend API (if configured), returns true if handled by backend.
     */
    fun togglePlaybackRemote(onResult: (Boolean) -> Unit): Job? {
        val repo = dashboardRepository ?: return null
        return viewModelScope.launch {
            val result = repo.toggle()
            onResult(result?.ok == true)
        }
    }

    /**
     * Seek via backend API.
     */
    fun seekRemote(positionMs: Long, onResult: (Boolean) -> Unit): Job? {
        val repo = dashboardRepository ?: return null
        return viewModelScope.launch {
            val result = repo.seek(positionMs)
            onResult(result?.ok == true)
        }
    }

    /**
     * Skip to next via backend API.
     */
    fun nextRemote(onResult: (Boolean) -> Unit): Job? {
        val repo = dashboardRepository ?: return null
        return viewModelScope.launch {
            val result = repo.next()
            onResult(result?.ok == true)
        }
    }

    /**
     * Skip to previous via backend API.
     */
    fun previousRemote(onResult: (Boolean) -> Unit): Job? {
        val repo = dashboardRepository ?: return null
        return viewModelScope.launch {
            val result = repo.previous()
            onResult(result?.ok == true)
        }
    }

    fun updatePlayback(snapshot: PlaybackStateSnapshot?, queue: List<Track>) {
        playbackState.value = MainActivityPlaybackState(
            snapshot = snapshot ?: PlaybackStateSnapshot.empty(),
            queue = queue.toList()
        )
    }

    fun lastHistoryRefreshTrackId(): Long {
        return lastHistoryRefreshTrackId
    }

    fun setLastHistoryRefreshTrackId(trackId: Long) {
        lastHistoryRefreshTrackId = trackId
    }

    fun updateTrackList(title: String, rows: List<TrackRowUiState>) {
        trackListState.value = MainActivityTrackListUiState(title, rows.toList())
    }

    fun updateLibraryGroups(title: String, rows: List<LibraryGroupUiState>) {
        libraryGroupsState.value = MainActivityLibraryGroupsUiState(title, rows.toList())
    }

    fun updatePlaylistTracks(title: String, rows: List<PlaylistTrackUiState>) {
        playlistTracksState.value = MainActivityPlaylistTracksUiState(title, rows.toList())
    }

    fun updateQueue(rows: List<QueueTrackUiState>) {
        queueState.value = MainActivityQueueUiState(rows.toList())
    }

    fun updatePlaylistList(title: String, rows: List<PlaylistRowUiState>) {
        playlistListState.value = MainActivityPlaylistListUiState(title, rows.toList())
    }

    fun updateNetworkSources(title: String, rows: List<NetworkSourceUiState>) {
        networkSourcesState.value = MainActivityNetworkSourcesUiState(title, rows.toList())
    }

    fun updateStreamingProviders(
        providers: List<StreamingProviderDescriptor>,
        capabilities: List<StreamingProviderCapability> = streamingState.value.providerCapabilities,
        health: List<StreamingProviderHealth> = streamingState.value.providerHealth
    ) {
        val current = streamingState.value
        val preferredProvider = providers.firstOrNull { provider ->
            provider.name != StreamingProviderName.MOCK &&
                ((current.authStates[provider.name] ?: provider.auth).connected)
        }
            ?: providers.firstOrNull { provider ->
                provider.name != StreamingProviderName.MOCK && provider.enabled
            }
            ?: providers.firstOrNull { provider -> provider.name != StreamingProviderName.MOCK }
        val selected = providers.firstOrNull {
            it.name == current.selectedProvider && current.selectedProvider != StreamingProviderName.MOCK
        }?.name
            ?: preferredProvider?.name
            ?: providers.firstOrNull()?.name
            ?: current.selectedProvider
        streamingState.value = current.copy(
            providers = providers.toList(),
            providerCapabilities = capabilities.toList(),
            providerHealth = health.toList(),
            selectedProvider = selected
        )
    }

    fun selectStreamingProvider(provider: StreamingProviderName) {
        val current = streamingState.value
        if (current.selectedProvider == provider) {
            return
        }
        streamingState.value = current.copy(
            selectedProvider = provider,
            searchResult = null,
            resolvedPlaybackSource = null,
            resolvedPlaybackTrack = null,
            pendingAuthLaunch = null,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateStreamingSearchQuery(query: String) {
        streamingState.value = streamingState.value.copy(searchQuery = query)
    }

    fun beginStreamingRequest() {
        streamingState.value = streamingState.value.copy(
            loading = true,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun beginStreamingNextPageRequest() {
        streamingState.value = streamingState.value.copy(
            loadingMore = true,
            errorMessage = null
        )
    }

    fun updateStreamingSearchResult(result: StreamingSearchResult) {
        streamingState.value = streamingState.value.copy(
            selectedProvider = result.provider,
            searchQuery = result.query,
            searchResult = result,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun appendStreamingSearchResult(result: StreamingSearchResult) {
        val current = streamingState.value
        val previous = current.searchResult
        val merged = if (
            previous != null &&
            previous.provider == result.provider &&
            previous.query == result.query &&
            result.page > previous.page
        ) {
            result.copy(
                tracks = previous.tracks + result.tracks,
                albums = previous.albums + result.albums,
                artists = previous.artists + result.artists,
                playlists = previous.playlists + result.playlists,
                mvs = previous.mvs + result.mvs,
                items = previous.unifiedItems + result.unifiedItems
            )
        } else {
            result
        }
        streamingState.value = current.copy(
            selectedProvider = merged.provider,
            searchQuery = merged.query,
            searchResult = merged,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateStreamingPlaybackSource(source: StreamingPlaybackSource) {
        streamingState.value = streamingState.value.copy(
            resolvedPlaybackSource = source,
            resolvedPlaybackTrack = null,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateStreamingPlaybackTrack(source: StreamingPlaybackSource, track: Track) {
        streamingState.value = streamingState.value.copy(
            resolvedPlaybackSource = source,
            resolvedPlaybackTrack = track,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateStreamingAuthState(provider: StreamingProviderName, authState: StreamingAuthState) {
        streamingState.value = streamingState.value.copy(
            authStates = streamingState.value.authStates + (provider to authState),
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun updateStreamingAuthLaunch(
        provider: StreamingProviderName,
        authState: StreamingAuthState,
        launchUrl: String?
    ) {
        val cleanLaunchUrl = launchUrl?.takeIf { it.isNotBlank() }
        streamingState.value = streamingState.value.copy(
            authStates = streamingState.value.authStates + (provider to authState),
            pendingAuthLaunch = cleanLaunchUrl?.let {
                MainActivityStreamingAuthLaunch(provider, it, authState.kind)
            },
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun clearStreamingAuthLaunch() {
        streamingState.value = streamingState.value.copy(pendingAuthLaunch = null)
    }

    fun updateStreamingDiagnostics() {
        streamingState.value = streamingState.value.copy(
            diagnostics = streamingRepository.diagnostics()
        )
    }

    fun failStreamingRequest(message: String?) {
        streamingState.value = streamingState.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message?.takeIf { it.isNotBlank() } ?: "Streaming request failed"
        )
    }

    override fun selectProvider(provider: StreamingProviderName) {
        selectStreamingProvider(provider)
    }

    override fun search(query: String) {
        val provider = streamingState.value.selectedProvider
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsSearch ?: StreamingCapabilityResolver.canSearch(descriptor))) {
            failStreamingRequest(sourceMessage(descriptor, "streaming.search.unavailable"))
            return
        }
        val mediaTypes = capability?.supportedSearchMediaTypes ?: StreamingCapabilityResolver.supportedSearchMediaTypes(descriptor)
        if (descriptor != null && mediaTypes.isEmpty()) {
            failStreamingRequest(sourceMessage(descriptor, "streaming.search.types.unavailable"))
            return
        }
        searchStreaming(
            provider = provider,
            query = query,
            mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
            page = 1,
            pageSize = 20
        )
    }

    override fun login(provider: StreamingProviderName) {
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsAuth ?: StreamingCapabilityResolver.canAuth(descriptor))) {
            failStreamingRequest(sourceMessage(descriptor, "streaming.auth.unsupported"))
            return
        }
        startStreamingAuth(
            provider = provider,
            redirectUri = STREAMING_AUTH_REDIRECT_URI + "?provider=${provider.wireName}",
            onLaunchReady = {
                openAuthLaunch()
            }
        )
    }

    override fun signOut(provider: StreamingProviderName) {
        val descriptor = descriptorFor(provider)
        if (descriptor != null && !descriptor.capabilities.supportsAuth) {
            return
        }
        signOutStreaming(provider)
    }

    override fun openAuthLaunch() {
        val gateway = streamingActionGateway ?: return
        if (gateway.openAuthLaunch(streamingState.value.pendingAuthLaunch)) {
            clearStreamingAuthLaunch()
        }
    }

    override fun playStreamingTrack(track: StreamingTrack) {
        val descriptor = descriptorFor(track.provider)
        val capability = capabilityFor(track.provider)
        if (descriptor != null && !(capability?.supportsPlayback ?: StreamingCapabilityResolver.canPlayback(descriptor))) {
            failStreamingRequest(sourceMessage(descriptor, "streaming.playback.unsupported"))
            return
        }
        if (!track.playable) {
            val reason = track.unavailableReason
            failStreamingRequest(reason?.takeIf { it.isNotBlank() } ?: text("streaming.track.unavailable"))
            return
        }
        resolveStreamingPlaybackTrack(
            provider = track.provider,
            providerTrackId = track.providerTrackId,
            quality = streamingActionGateway?.streamingPlaybackQuality() ?: StreamingAudioQuality.LOSSLESS,
            metadata = track
        )
    }

    override fun playResolvedTrack(track: Track) {
        streamingActionGateway?.playResolvedTrack(track)
    }

    override fun loadNextPage() {
        val provider = streamingState.value.selectedProvider
        val descriptor = descriptorFor(provider)
        val capability = capabilityFor(provider)
        if (descriptor != null && !(capability?.supportsSearch ?: StreamingCapabilityResolver.canSearch(descriptor))) {
            failStreamingRequest(sourceMessage(descriptor, "streaming.search.unavailable"))
            return
        }
        searchNextStreamingPage()
    }

    override fun handleAuthCallback(callbackUri: String?, cookieHeader: String?): Boolean {
        val uri = callbackUri
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it) }.getOrNull() }
            ?: return false
        if (uri.scheme != "echo-next" || uri.host != "streaming-auth") {
            return false
        }
        val providerValue = queryParameter(uri.rawQuery, "provider")
        val selectedProvider = streamingState.value.selectedProvider
        val parsedProvider = providerValue
            ?.takeIf { it.isNotBlank() }
            ?.let(StreamingProviderName::fromWireName)
        val provider = parsedProvider ?: selectedProvider
        completeStreamingAuth(provider, uri.toString(), cookieHeader) { loggedInProvider ->
            streamingActionGateway?.onStreamingLoginSuccess(loggedInProvider)
        }
        clearStreamingAuthLaunch()
        return true
    }

    private fun queryParameter(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) {
            return null
        }
        return rawQuery
            .split("&")
            .asSequence()
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                val key = if (separator >= 0) part.substring(0, separator) else part
                if (decodeQueryValue(key) != name) {
                    null
                } else {
                    val value = if (separator >= 0) part.substring(separator + 1) else ""
                    decodeQueryValue(value)
                }
            }
            .firstOrNull()
    }

    private fun decodeQueryValue(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    private fun descriptorFor(provider: StreamingProviderName): StreamingProviderDescriptor? {
        return streamingState.value.providers.firstOrNull { it.name == provider }
    }

    private fun capabilityFor(provider: StreamingProviderName): StreamingProviderCapability? {
        return streamingState.value.providerCapabilities.firstOrNull { it.provider == provider }
    }

    private fun sourceMessage(descriptor: StreamingProviderDescriptor, suffixKey: String): String {
        return descriptor.displayName + text(suffixKey)
    }

    private fun text(key: String): String {
        return AppLanguage.text(streamingActionGateway?.languageMode() ?: AppLanguage.MODE_SYSTEM, key)
    }

    fun refreshStreamingProviders(): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val providers = streamingRepository.providers()
                val capabilities = runCatching { streamingRepository.providerCapabilities() }.getOrElse { emptyList() }
                val health = runCatching { streamingRepository.providersHealth() }.getOrElse { emptyList() }
                val authStates = providers.associate { provider ->
                    provider.name to runCatching {
                        streamingRepository.authState(provider.name)
                    }.getOrElse {
                        provider.auth
                    }
                }
                StreamingProviderRefresh(providers, capabilities, health, authStates)
            }.onSuccess { refresh ->
                val (providers, capabilities, health, authStates) = refresh
                updateStreamingProviders(providers, capabilities, health)
                streamingState.value = streamingState.value.copy(
                    authStates = authStates,
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    private data class StreamingProviderRefresh(
        val providers: List<StreamingProviderDescriptor>,
        val capabilities: List<StreamingProviderCapability>,
        val health: List<StreamingProviderHealth>,
        val authStates: Map<StreamingProviderName, StreamingAuthState>
    )

    fun searchStreaming(
        provider: StreamingProviderName = streamingState.value.selectedProvider,
        query: String = streamingState.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        page: Int = 1,
        pageSize: Int = 20
    ): Job {
        val normalizedMediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) }
        streamingState.value = streamingState.value.copy(
            searchQuery = query,
            searchMediaTypes = normalizedMediaTypes
        )
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.search(provider, query, normalizedMediaTypes, page, pageSize)
            }.onSuccess { result ->
                updateStreamingSearchResult(result)
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun searchNextStreamingPage() {
        val current = streamingState.value
        val result = current.searchResult ?: return
        if (!result.hasMore || current.loading || current.loadingMore) {
            return
        }
        beginStreamingNextPageRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.search(
                    result.provider,
                    result.query,
                    current.searchMediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
                    result.page + 1,
                    result.pageSize
                )
            }.onSuccess { nextResult ->
                appendStreamingSearchResult(nextResult)
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun resolveStreamingTrackMatch(
        provider: StreamingProviderName,
        localTrack: Track,
        onResolved: StreamingCallback<StreamingTrack?>
    ): Job {
        val query = StreamingTrackMatchPolicy.searchQuery(localTrack)
        if (query.isBlank()) {
            streamingState.value = streamingState.value.copy(
                loading = false,
                errorMessage = null
            )
            return viewModelScope.launch {
                onResolved.onResult(null)
            }
        }
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val result = streamingRepository.search(
                    provider = provider,
                    query = query,
                    mediaTypes = setOf(StreamingMediaType.TRACK),
                    page = 1,
                    pageSize = 5,
                    useCache = false
                )
                StreamingTrackMatchPolicy.pickBestCandidate(localTrack, result.tracks)
            }.onSuccess { track ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onResolved.onResult(track)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult(null)
            }
        }
    }

    fun resolveHeartbeatRecommendationSeed(
        provider: StreamingProviderName,
        candidates: List<Track>?,
        onResolved: StreamingCallback<String>
    ): Job {
        val seedCandidates = candidates.orEmpty()
        if (seedCandidates.isEmpty()) {
            streamingState.value = streamingState.value.copy(
                loading = false,
                errorMessage = null
            )
            return viewModelScope.launch {
                onResolved.onResult("")
            }
        }
        beginStreamingRequest()
        return viewModelScope.launch {
            val resolvedTrackId = resolveHeartbeatRecommendationSeedId(provider, seedCandidates)
            streamingState.value = streamingState.value.copy(
                loading = false,
                errorMessage = null
            )
            updateStreamingDiagnostics()
            onResolved.onResult(resolvedTrackId)
        }
    }

    private suspend fun resolveHeartbeatRecommendationSeedId(
        provider: StreamingProviderName,
        candidates: List<Track>
    ): String {
        val store = streamingTrackMatchStore
        for (track in candidates) {
            val directTrackId = store?.directProviderTrackId(track, provider).orEmpty().trim()
            if (directTrackId.isNotEmpty()) {
                saveHeartbeatSeedMatch(store, track, provider, directTrackId)
                return directTrackId
            }
            val cachedTrackId = withContext(Dispatchers.IO) {
                store?.providerTrackIdFor(track, provider).orEmpty().trim()
            }
            if (cachedTrackId.isNotEmpty()) {
                return cachedTrackId
            }
            val resolvedTrackId = searchHeartbeatSeedMatch(provider, track)
            if (resolvedTrackId.isNotEmpty()) {
                saveHeartbeatSeedMatch(store, track, provider, resolvedTrackId)
                return resolvedTrackId
            }
        }
        return ""
    }

    private suspend fun searchHeartbeatSeedMatch(
        provider: StreamingProviderName,
        track: Track
    ): String {
        val query = StreamingTrackMatchPolicy.searchQuery(track)
        if (query.isBlank()) {
            return ""
        }
        return runCatching {
            val result = streamingRepository.search(
                provider = provider,
                query = query,
                mediaTypes = setOf(StreamingMediaType.TRACK),
                page = 1,
                pageSize = 5,
                useCache = false
            )
            StreamingTrackMatchPolicy.pickBestCandidate(track, result.tracks)
                ?.providerTrackId
                .orEmpty()
                .trim()
        }.getOrElse { error ->
            failStreamingRequest(error.message)
            updateStreamingDiagnostics()
            ""
        }
    }

    private suspend fun saveHeartbeatSeedMatch(
        store: StreamingTrackMatchStore?,
        track: Track,
        provider: StreamingProviderName,
        providerTrackId: String
    ) {
        val cleanTrackId = providerTrackId.trim()
        if (cleanTrackId.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            store?.saveProviderTrackId(track, provider, cleanTrackId)
        }
    }

    fun resolveStreamingPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.resolvePlayback(provider, providerTrackId, quality)
            }.onSuccess { source ->
                updateStreamingPlaybackSource(source)
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun resolveStreamingPlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: app.yukine.streaming.StreamingTrack? = null
    ) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.resolvePlaybackTrack(provider, providerTrackId, quality, metadata)
            }.onSuccess { result ->
                updateStreamingPlaybackTrack(result.source, result.track)
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    /**
     * Resolves a streaming placeholder track (provider + providerTrackId) into a playable [Track]
     * with a real URL, then delivers it via [onResolved]. On failure, delivers null and surfaces
     * an error message. Used when playing a track imported from a remote playlist.
     */
    fun resolveStreamingTrackForPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack? = null,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<Track?>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.resolvePlaybackTrack(
                    provider,
                    providerTrackId,
                    quality,
                    metadata
                )
            }.onSuccess { result ->
                updateStreamingPlaybackTrack(result.source, result.track)
                updateStreamingDiagnostics()
                onResolved.onResult(result.track)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
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
        val planner = streamingPlaybackPlanner ?: return false
        val taskQueue = streamingPlaybackTaskQueue ?: return false
        val request = planner.prepareNextPreResolve(snapshot, queue) ?: return false
        taskQueue.scheduleNextUrlResolve(
            StreamingPlaybackTask { onComplete ->
                resolveStreamingTrackForPlayback(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    quality
                ) { resolved ->
                    planner.clearPreResolve(request.key)
                    onResolved.onResult(request.oldTrackId, resolved)
                    onComplete.run()
                }
            }
        )
        return true
    }

    fun resolveStreamingTrackListForPlayback(
        tracks: List<Track>?,
        index: Int,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<ResolvedStreamingTrackList?>
    ): Boolean {
        val planner = streamingPlaybackPlanner ?: return false
        val taskQueue = streamingPlaybackTaskQueue ?: return false
        val request = planner.prepare(tracks, index) ?: return false
        taskQueue.scheduleCurrentUrlResolve(
            StreamingPlaybackTask { onComplete ->
                resolveStreamingTrackForPlayback(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    quality
                ) { resolved ->
                    if (resolved == null) {
                        onResolved.onResult(null)
                        onComplete.run()
                        return@resolveStreamingTrackForPlayback
                    }
                    onResolved.onResult(
                        ResolvedStreamingTrackList(
                            tracks = planner.replaceResolvedTrack(request, resolved),
                            index = request.index
                        )
                    )
                    onComplete.run()
                }
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

    fun stopHeartbeatRecommendationMode() {
        heartbeatRecommendationUseCase.stop()
    }

    fun startHeartbeatRecommendationLoading(provider: StreamingProviderName) {
        heartbeatRecommendationUseCase.startLoading(provider)
    }

    fun canContinueHeartbeatRecommendationLoading(provider: StreamingProviderName): Boolean =
        heartbeatRecommendationUseCase.canContinueLoading(provider)

    fun markHeartbeatRecommendationLoadingFinished() {
        heartbeatRecommendationUseCase.markLoadingFinished()
    }

    fun prepareHeartbeatRecommendationRefill(snapshot: PlaybackStateSnapshot?): HeartbeatRefillRequest? =
        heartbeatRecommendationUseCase.prepareRefill(snapshot)

    fun acceptsHeartbeatRecommendationRefill(provider: StreamingProviderName): Boolean =
        heartbeatRecommendationUseCase.accepts(provider)

    fun markHeartbeatRecommendationRefillFinished(provider: StreamingProviderName) {
        heartbeatRecommendationUseCase.markLoadingFinished(provider)
    }

    fun prepareRecommendationTrackList(
        tracks: List<StreamingTrack>?
    ): StreamingRecommendationTrackList {
        return StreamingRecommendationTrackList(
            tracks = tracks.orEmpty()
                .filterNotNull()
                .map { StreamingPlaybackAdapter.placeholderTrack(it) }
        )
    }

    fun prepareStreamingDailyRecommendationRequest(
        requestedProvider: StreamingProviderName?
    ): StreamingDailyRecommendationRequest? {
        val provider = recommendationProvider(requestedProvider) ?: return null
        stopHeartbeatRecommendationMode()
        return StreamingDailyRecommendationRequest(
            provider = provider,
            loadingStatus = text("streaming.recommend.daily.loading"),
            emptyStatus = text("streaming.recommend.daily.empty"),
            title = text("streaming.recommend.daily")
        )
    }

    fun prepareStreamingHeartbeatRecommendationRequest(
        requestedProvider: StreamingProviderName?
    ): StreamingHeartbeatRecommendationRequest? {
        val provider = recommendationProvider(requestedProvider) ?: return null
        startHeartbeatRecommendationLoading(provider)
        return StreamingHeartbeatRecommendationRequest(
            provider = provider,
            loadingStatus = text("streaming.recommend.heartbeat.loading"),
            emptyStatus = text("streaming.recommend.heartbeat.empty"),
            playingStatus = text("streaming.recommend.heartbeat.playing")
        )
    }

    fun streamingDailyRecommendationEmptyStatus(): String =
        text("streaming.recommend.daily.empty")

    fun streamingHeartbeatRecommendationEmptyStatus(): String =
        text("streaming.recommend.heartbeat.empty")

    fun prepareStreamingRecommendationPresentation(
        tracks: List<StreamingTrack>?,
        emptyStatus: String,
        title: String
    ): StreamingRecommendationPresentation {
        val placeholders = prepareRecommendationTrackList(tracks).tracks
        if (placeholders.isEmpty()) {
            return StreamingRecommendationPresentation(emptyStatus = emptyStatus)
        }
        return StreamingRecommendationPresentation(
            tracks = placeholders,
            emptyStatus = emptyStatus,
            readyStatus = "$title (${placeholders.size})",
            title = title
        )
    }

    fun prepareHeartbeatRecommendationPlaylist(
        tracks: List<StreamingTrack>?
    ): StreamingRecommendationTrackList {
        return StreamingRecommendationTrackList(
            tracks = heartbeatRecommendationUseCase.playlistPlaceholders(tracks)
        )
    }

    fun prepareHeartbeatRecommendationPresentation(
        tracks: List<StreamingTrack>?,
        emptyStatus: String,
        playingStatus: String
    ): StreamingRecommendationPresentation {
        val placeholders = prepareHeartbeatRecommendationPlaylist(tracks).tracks
        if (placeholders.isEmpty()) {
            return StreamingRecommendationPresentation(emptyStatus = emptyStatus)
        }
        return StreamingRecommendationPresentation(
            tracks = placeholders,
            emptyStatus = emptyStatus,
            readyStatus = "$playingStatus (${placeholders.size})",
            title = playingStatus
        )
    }

    fun prepareHeartbeatRecommendationAppend(
        tracks: List<StreamingTrack>?
    ): StreamingRecommendationTrackList {
        return StreamingRecommendationTrackList(
            tracks = heartbeatRecommendationUseCase.appendPlaceholders(tracks)
        )
    }

    fun prepareHeartbeatRecommendationAppendPresentation(
        tracks: List<StreamingTrack>?
    ): StreamingRecommendationPresentation {
        val playingStatus = text("streaming.recommend.heartbeat.playing")
        val emptyStatus = text("streaming.recommend.heartbeat.empty")
        val placeholders = prepareHeartbeatRecommendationAppend(tracks).tracks
        if (placeholders.isEmpty()) {
            return StreamingRecommendationPresentation(emptyStatus = emptyStatus)
        }
        return StreamingRecommendationPresentation(
            tracks = placeholders,
            emptyStatus = emptyStatus,
            readyStatus = "$playingStatus (+${placeholders.size})",
            title = playingStatus
        )
    }

    fun prepareStreamingPlaybackStatusText(
        quality: StreamingAudioQuality? = null
    ): StreamingPlaybackStatusText {
        val languageMode = streamingActionGateway?.languageMode() ?: AppLanguage.MODE_SYSTEM
        val qualityLabel = quality?.let {
            SettingsPageRenderController.streamingQualityLabel(
                StreamingQualityPreference.valueFor(it),
                languageMode
            )
        }.orEmpty()
        return StreamingPlaybackStatusText(
            resolving = text("streaming.resolving"),
            resolveFailed = text("streaming.resolve.failed"),
            qualityDowngrading = text("streaming.quality.downgrading") + qualityLabel,
            qualityDowngraded = text("streaming.quality.downgraded") + qualityLabel
        )
    }

    fun prepareStreamingStatusText(
        qualityPreference: String? = null
    ): StreamingStatusText {
        val languageMode = streamingActionGateway?.languageMode() ?: AppLanguage.MODE_SYSTEM
        val qualityLabel = qualityPreference?.let {
            SettingsPageRenderController.streamingQualityLabel(it, languageMode)
        }.orEmpty()
        return StreamingStatusText(
            streamingQualityApplied = text("streaming.quality.applied") + qualityLabel
        )
    }

    fun streamingPlaylistLoadedDialogTitle(): String =
        text("streaming.playlist.load.success.title")

    fun prepareHeartbeatRecommendationSeedRequest(
        provider: StreamingProviderName,
        serviceSnapshot: PlaybackStateSnapshot?,
        serviceQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?,
        viewModelQueue: List<Track?>?,
        libraryContextTracks: List<Track?>? = null
    ): HeartbeatRecommendationSeedRequest {
        val store = streamingTrackMatchStore
        val candidates = randomHeartbeatSeedCandidates(store?.heartbeatSeedCandidates(
            serviceSnapshot,
            mergeHeartbeatSeedQueues(serviceQueue, libraryContextTracks),
            storeSnapshot,
            viewModelQueue
        ).orEmpty())
        val seedTrackId = store?.providerTrackIdFromCandidates(candidates, provider).orEmpty().trim()
        val playlistId = seedTrackId
        val queue = store?.snapshotQueueForHeartbeat(
            mergeHeartbeatSeedQueues(serviceQueue, libraryContextTracks),
            viewModelQueue,
            storeSnapshot
        ).orEmpty()
        val diagnosticSnapshot = serviceSnapshot ?: storeSnapshot
        val seedMissingMessage = store?.heartbeatSeedMissMessage(
            provider,
            diagnosticSnapshot,
            storeSnapshot,
            queue
        ).orEmpty()
        return HeartbeatRecommendationSeedRequest(
            candidates = candidates,
            seedTrackId = seedTrackId,
            playlistId = playlistId,
            seedMissingMessage = seedMissingMessage
        )
    }

    private fun mergeHeartbeatSeedQueues(
        primaryQueue: List<Track?>?,
        contextTracks: List<Track?>?
    ): List<Track?>? {
        if (contextTracks.isNullOrEmpty()) {
            return primaryQueue
        }
        if (primaryQueue.isNullOrEmpty()) {
            return contextTracks
        }
        return contextTracks + primaryQueue
    }

    private fun randomHeartbeatSeedCandidates(candidates: List<Track>): List<Track> {
        if (candidates.size <= 1) {
            return candidates
        }
        val cursor = heartbeatSeedCursor
        heartbeatSeedCursor = if (heartbeatSeedCursor == Int.MAX_VALUE) 0 else heartbeatSeedCursor + 1
        val anchorIndex = Math.floorMod(cursor, candidates.size)
        val anchor = candidates[anchorIndex]
        val sampleSeed = candidates.fold(cursor * 31 + candidates.size) { seed, track ->
            seed * 31 + track.id.hashCode()
        }
        val rest = candidates
            .filterIndexed { index, _ -> index != anchorIndex }
            .shuffled(Random(sampleSeed))
            .take((HEARTBEAT_SEED_SAMPLE_SIZE - 1).coerceAtLeast(0))
        return listOf(anchor) + rest
    }

    fun recoverStreamingBuffering(
        snapshot: PlaybackStateSnapshot?,
        selectedQuality: StreamingAudioQuality,
        adaptiveQuality: StreamingAudioQuality,
        onResolved: StreamingCallback<StreamingRecoveryResolution?>
    ): StreamingAudioQuality? {
        val planner = streamingPlaybackPlanner ?: return null
        val taskQueue = streamingPlaybackTaskQueue ?: return null
        val request = planner.prepareRecovery(snapshot, selectedQuality, adaptiveQuality) ?: return null
        taskQueue.scheduleCurrentPlaybackRecovery(
            StreamingPlaybackTask { onComplete ->
                resolveStreamingTrackForPlayback(
                    request.provider,
                    request.providerTrackId,
                    request.metadata,
                    request.quality
                ) { resolved ->
                    planner.clearRecovery(request.key)
                    onResolved.onResult(
                        resolved?.let {
                            StreamingRecoveryResolution(
                                track = it,
                                quality = request.quality,
                                positionMs = snapshot?.positionMs ?: 0L
                            )
                        }
                    )
                    onComplete.run()
                }
            }
        )
        return request.quality
    }

    fun refreshStreamingAuthState(provider: StreamingProviderName) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.authState(provider)
            }.onSuccess { authState ->
                updateStreamingAuthState(provider, authState)
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun startStreamingAuth(
        provider: StreamingProviderName,
        redirectUri: String? = null,
        onLaunchReady: (() -> Unit)? = null
    ) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.startAuth(provider, redirectUri)
            }.onSuccess { result ->
                updateStreamingAuthLaunch(provider, result.state, result.launchUrl)
                updateStreamingDiagnostics()
                if (!result.launchUrl.isNullOrBlank()) {
                    onLaunchReady?.invoke()
                }
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun signOutStreaming(provider: StreamingProviderName): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.signOut(provider)
            }.onSuccess { authState ->
                updateStreamingAuthState(provider, authState)
                refreshStreamingProviders()
                updateStreamingDiagnostics()
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun completeStreamingAuth(
        provider: StreamingProviderName,
        callbackUri: String,
        cookieHeader: String? = null,
        onAuthSuccess: StreamingCallback<StreamingProviderName>? = null
    ) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.completeAuth(provider, callbackUri, cookieHeader)
            }.onSuccess { result ->
                updateStreamingAuthState(provider, result.state)
                refreshStreamingProviders()
                updateStreamingDiagnostics()
                if (result.state.connected) {
                    onAuthSuccess?.onResult(provider)
                }
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
            }
        }
    }

    fun prepareManualCookieDialogState(provider: StreamingProviderName?): StreamingManualCookieDialogState {
        val unavailable = provider == null || provider in setOf(
            StreamingProviderName.MOCK,
            StreamingProviderName.M3U8,
            StreamingProviderName.PLUGIN
        )
        return StreamingManualCookieDialogState(
            provider = provider,
            unavailable = unavailable,
            title = text("streaming.manual.cookie"),
            unavailableStatus = text("streaming.choose.login.provider")
        )
    }

    fun prepareManualCookieAuthRequest(
        provider: StreamingProviderName?,
        cookieHeader: String?
    ): StreamingManualCookieAuthRequest? {
        val dialogState = prepareManualCookieDialogState(provider)
        val cleanCookie = cookieHeader?.trim().orEmpty()
        val cleanProvider = dialogState.provider
        if (dialogState.unavailable || cleanProvider == null || cleanCookie.isEmpty()) {
            return null
        }
        return StreamingManualCookieAuthRequest(
            provider = cleanProvider,
            callbackUri = "$STREAMING_AUTH_REDIRECT_URI?provider=${cleanProvider.wireName}&manualCookie=1",
            cookieHeader = cleanCookie,
            emptyStatus = text("streaming.cookie.empty"),
            savedStatus = text("streaming.cookie.saved")
        )
    }

    fun manualCookieEmptyStatus(): String = text("streaming.cookie.empty")

    fun prepareStreamingPlaylistImportDialogState(): StreamingPlaylistImportDialogState {
        return StreamingPlaylistImportDialogState(
            title = text("streaming.import.playlist.from"),
            hint = text("streaming.paste.playlist.link")
        )
    }

    fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>,
        onComplete: ((app.yukine.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary) -> Unit)? = null
    ): Job {
        streamingState.value = streamingState.value.copy(
            playlistImporting = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            runCatching {
                val importer = app.yukine.streaming.StreamingPlaylistImporter(streamingRepository)
                importer.importToStreaming(provider, playlistName, localTracks)
            }.onSuccess { summary ->
                streamingState.value = streamingState.value.copy(
                    playlistImporting = false,
                    playlistImportSummary = summary,
                    selectedProvider = provider,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onComplete?.invoke(summary)
            }.onFailure { error ->
                streamingState.value = streamingState.value.copy(
                    playlistImporting = false,
                    errorMessage = error.message ?: "Playlist import failed"
                )
                updateStreamingDiagnostics()
            }
        }
    }

    /**
     * Java-friendly overload that takes a [Runnable] called with the resulting summary kept on the
     * shared [streamingState].
     */
    fun importPlaylistToStreamingJava(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>,
        onComplete: StreamingCallback<StreamingPlaylistImportStatus>?
    ): Job {
        return importPlaylistToStreaming(provider, playlistName, localTracks) { summary ->
            onComplete?.onResult(streamingPlaylistImportStatus(summary))
        }
    }

    fun streamingImportProviderPickerState(
        providers: List<StreamingProviderDescriptor>?,
        requireSearch: Boolean = true
    ): StreamingProviderPickerState {
        val selectable = providers.orEmpty()
            .filterNotNull()
            .filter { !requireSearch || it.capabilities.supportsSearch }
            .filter { it.name != StreamingProviderName.MOCK }
        return StreamingProviderPickerState(
            providers = selectable,
            labels = selectable.map { it.displayName }.toTypedArray()
        )
    }

    fun prepareStreamingImportProviderPickerRequest(
        providers: List<StreamingProviderDescriptor>?,
        requireSearch: Boolean = true
    ): StreamingProviderPickerRequest {
        val pickerState = streamingImportProviderPickerState(providers, requireSearch)
        return StreamingProviderPickerRequest(
            pickerState = pickerState,
            title = text("choose.streaming.provider"),
            emptyStatus = text("streaming.no.providers"),
            valid = pickerState.providers.isNotEmpty()
        )
    }

    fun streamingPlaylistImportStatus(
        summary: app.yukine.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary?
    ): StreamingPlaylistImportStatus {
        if (summary == null) {
            return StreamingPlaylistImportStatus()
        }
        return StreamingPlaylistImportStatus(
            matchedCount = summary.matchedTracks.size,
            totalRequested = summary.totalRequested,
            unresolvedCount = summary.unresolvedTracks.size
        )
    }

    fun prepareStreamingPlaylistExportPresentation(
        importStatus: StreamingPlaylistImportStatus?
    ): StreamingPlaylistExportPresentation {
        if (importStatus == null) {
            return StreamingPlaylistExportPresentation()
        }
        var status = text("streaming.import.matched.prefix") +
            importStatus.matchedCount +
            " / " +
            importStatus.totalRequested
        if (importStatus.unresolvedCount > 0) {
            status += " (" +
                importStatus.unresolvedCount +
                text("streaming.import.unresolved.suffix") +
                ")"
        }
        return StreamingPlaylistExportPresentation(status = status)
    }

    fun prepareStreamingPlaylistExportRequest(
        playlistName: String?,
        tracks: List<Track>?
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (playlistName.isNullOrBlank() || normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text("streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = playlistName,
            tracks = normalizedTracks,
            status = text("streaming.import.matched.prefix") + "...",
            valid = true
        )
    }

    fun prepareStreamingFavoritesExportRequest(
        tracks: List<Track>?
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text("streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = text("favorites"),
            tracks = normalizedTracks,
            status = text("streaming.import.matched.prefix") + "...",
            valid = true
        )
    }

    fun prepareStreamingPlaylistImportTarget(
        linkOrId: String?,
        fallbackProvider: StreamingProviderName?
    ): StreamingPlaylistImportTarget {
        val parsed = StreamingPlaylistLinkParser.parse(
            linkOrId,
            fallbackProvider ?: streamingState.value.selectedProvider
        )
        return if (parsed == null) {
            StreamingPlaylistImportTarget(invalid = true)
        } else {
            StreamingPlaylistImportTarget(
                provider = parsed.provider,
                providerPlaylistId = parsed.providerPlaylistId
            )
        }
    }

    fun prepareStreamingPlaylistImportStartRequest(
        linkOrId: String?,
        fallbackProvider: StreamingProviderName?
    ): StreamingPlaylistImportStartRequest {
        val target = prepareStreamingPlaylistImportTarget(linkOrId, fallbackProvider)
        val provider = target.provider
        val invalid = target.invalid || provider == null || target.providerPlaylistId.isEmpty()
        if (invalid) {
            return StreamingPlaylistImportStartRequest(
                invalidStatus = text("streaming.playlist.link.invalid")
            )
        }
        return StreamingPlaylistImportStartRequest(
            provider = provider,
            providerPlaylistId = target.providerPlaylistId,
            invalidStatus = text("streaming.playlist.link.invalid"),
            resolvingStatus = text("streaming.resolving"),
            valid = true
        )
    }

    fun prepareStreamingLoginPlaylistRequest(
        provider: StreamingProviderName
    ): StreamingLoginPlaylistRequest {
        val displayName = streamingProviderDisplayName(provider)
        val playlistName =
            text("streaming.my.playlist.prefix") +
                displayName +
                text("streaming.my.playlist.suffix")
        return StreamingLoginPlaylistRequest(
            provider = provider,
            playlistName = playlistName
        )
    }

    fun prepareStreamingLikedPlaylistName(provider: StreamingProviderName): String {
        return text("streaming.liked.playlist.prefix") +
            streamingProviderDisplayName(provider) +
            text("streaming.liked.playlist.suffix")
    }

    fun prepareStreamingPlaylistImportPresentation(
        result: StreamingLocalPlaylistImportResult?
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text("streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text("streaming.playlist.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingLikedImportPresentation(
        result: StreamingLocalPlaylistImportResult?
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text("streaming.liked.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text("streaming.liked.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingPlaylistSyncPresentation(
        result: StreamingLocalPlaylistSyncResult?
    ): StreamingLocalPlaylistSyncPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistSyncPresentation(
                empty = true,
                status = text("streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistSyncPresentation(
            status = text("streaming.sync.complete") + " (${result.syncedCount})"
        )
    }

    fun prepareStreamingLoginPlaylistPresentation(
        request: StreamingLoginPlaylistRequest,
        result: StreamingLoginPlaylistResult?
    ): StreamingLoginPlaylistPresentation {
        return StreamingLoginPlaylistPresentation(
            status = text("streaming.playlist.created") + ": " + request.playlistName,
            playlistId = result?.playlistId ?: -1L
        )
    }

    private fun streamingProviderDisplayName(provider: StreamingProviderName): String =
        descriptorFor(provider)?.displayName ?: provider.wireName

    private fun recommendationProvider(
        requested: StreamingProviderName?
    ): StreamingProviderName? {
        if (requested == StreamingProviderName.NETEASE) {
            return requested
        }
        return if (descriptorFor(StreamingProviderName.NETEASE) != null) {
            StreamingProviderName.NETEASE
        } else {
            null
        }
    }

    fun clearStreamingPlaylistImport() {
        if (streamingState.value.playlistImportSummary != null) {
            streamingState.value = streamingState.value.copy(playlistImportSummary = null)
        }
    }

    fun bindStreamingLocalPlaylistOperations(operations: StreamingLocalPlaylistOperations?) {
        streamingLocalPlaylistOperations = operations
    }

    fun bindStreamingTrackMatchStore(store: StreamingTrackMatchStore?) {
        streamingTrackMatchStore = store
    }

    fun loadStreamingProviderTrackId(
        track: Track,
        provider: StreamingProviderName,
        onResolved: StreamingCallback<String>
    ): Job {
        return viewModelScope.launch {
            val providerTrackId = withContext(Dispatchers.IO) {
                streamingTrackMatchStore?.providerTrackIdFor(track, provider).orEmpty()
            }
            onResolved.onResult(providerTrackId)
        }
    }

    fun streamingProviderTrackIdFor(track: Track?, provider: StreamingProviderName?): String {
        if (track == null || provider == null) {
            return ""
        }
        return streamingTrackMatchStore?.providerTrackIdFor(track, provider).orEmpty()
    }

    fun saveStreamingProviderTrackId(
        track: Track?,
        provider: StreamingProviderName?,
        providerTrackId: String?
    ): Job {
        return viewModelScope.launch {
            val cleanTrackId = providerTrackId?.trim().orEmpty()
            if (track == null || provider == null || cleanTrackId.isEmpty()) {
                return@launch
            }
            withContext(Dispatchers.IO) {
                streamingTrackMatchStore?.saveProviderTrackId(track, provider, cleanTrackId)
            }
        }
    }

    fun prepareStreamingPlaylistSyncTarget(localPlaylistId: Long): StreamingPlaylistSyncTarget? {
        if (localPlaylistId < 0L) {
            return null
        }
        val link = streamingLocalPlaylistOperations?.linkedPlaylist(localPlaylistId)
        return if (link == null) {
            StreamingPlaylistSyncTarget(missingLink = true)
        } else {
            StreamingPlaylistSyncTarget(link = link)
        }
    }

    fun prepareStreamingPlaylistSyncStartRequest(localPlaylistId: Long): StreamingPlaylistSyncStartRequest? {
        val target = prepareStreamingPlaylistSyncTarget(localPlaylistId) ?: return null
        if (target.missingLink || target.link == null) {
            return StreamingPlaylistSyncStartRequest(
                status = text("streaming.not.linked")
            )
        }
        return StreamingPlaylistSyncStartRequest(
            link = target.link,
            status = text("streaming.sync.started"),
            valid = true
        )
    }

    /**
     * Loads the playlists saved on the user's account for the given provider (requires the
     * gateway's userPlaylists endpoint). Results are kept on [MainActivityStreamingState.userPlaylists].
     */
    fun loadUserPlaylists(provider: StreamingProviderName): Job {
        streamingState.value = streamingState.value.copy(
            userPlaylistsLoading = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            runCatching {
                streamingRepository.userPlaylists(provider)
            }.onSuccess { playlists ->
                streamingState.value = streamingState.value.copy(
                    userPlaylists = playlists,
                    userPlaylistsLoading = false,
                    selectedProvider = provider
                )
                updateStreamingDiagnostics()
            }.onFailure { error ->
                streamingState.value = streamingState.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    errorMessage = error.message ?: "Could not load account playlists"
                )
                updateStreamingDiagnostics()
            }
        }
    }

    /**
     * Fetches the user's liked/saved tracks from the provider and delivers them via callback.
     */
    fun fetchUserLikedTracks(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<app.yukine.streaming.StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.userLikedTracks(provider)
            }.onSuccess { tracks ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult(emptyList())
            }
        }
    }

    /**
     * Fetches the provider's daily recommendation tracks (e.g. NetEase 每日推荐) and delivers them
     * via callback. Delivers an empty list on failure.
     */
    fun fetchDailyRecommendations(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<app.yukine.streaming.StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.dailyRecommendations(provider)
            }.onSuccess { tracks ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult(emptyList())
            }
        }
    }

    /**
     * Fetches the provider's heartbeat / intelligence recommendation tracks (e.g. NetEase 心动推荐)
     * and delivers them via callback. Delivers an empty list on failure.
     */
    fun fetchHeartbeatRecommendations(
        provider: StreamingProviderName,
        providerTrackId: String?,
        providerPlaylistId: String?,
        onResolved: StreamingCallback<List<app.yukine.streaming.StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.heartbeatRecommendations(
                    app.yukine.streaming.StreamingHeartbeatRequest(
                        provider = provider,
                        providerTrackId = providerTrackId,
                        providerPlaylistId = providerPlaylistId,
                        count = 60
                    )
                )
            }.onSuccess { tracks ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult(emptyList())
            }
        }
    }

    /**
     * Fetches a streaming playlist's full track list and delivers the contained streaming tracks
     * via [onResolved] so the caller (MainActivity) can persist them as a local playlist. Delivers
     * an empty list on failure.
     */
    fun fetchStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        onResolved: StreamingBiCallback<String, List<app.yukine.streaming.StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                loadStreamingPlaylistTracks(provider, providerPlaylistId)
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onResolved.onResult(result.first, result.second)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult("", emptyList())
            }
        }
    }

    fun importStreamingPlaylistToLocal(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        onImported: StreamingCallback<StreamingLocalPlaylistImportResult>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val (playlistName, tracks) = loadStreamingPlaylistTracks(provider, providerPlaylistId)
                if (tracks.isEmpty()) {
                    return@runCatching StreamingLocalPlaylistImportResult(
                        playlistName = playlistName,
                        empty = true
                    )
                }
                importStreamingTracksToLocal(
                    playlistName = playlistName,
                    provider = provider,
                    providerPlaylistId = providerPlaylistId,
                    tracks = tracks,
                    linkWhenProviderPlaylistIdBlank = false
                )
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onImported.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onImported.onResult(StreamingLocalPlaylistImportResult(empty = true))
            }
        }
    }

    fun importStreamingLikedTracksToLocal(
        provider: StreamingProviderName,
        playlistName: String,
        onImported: StreamingCallback<StreamingLocalPlaylistImportResult>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val tracks = streamingRepository.userLikedTracks(provider)
                if (tracks.isEmpty()) {
                    return@runCatching StreamingLocalPlaylistImportResult(
                        playlistName = playlistName,
                        empty = true
                    )
                }
                importStreamingTracksToLocal(
                    playlistName = playlistName,
                    provider = provider,
                    providerPlaylistId = "",
                    tracks = tracks,
                    linkWhenProviderPlaylistIdBlank = true
                )
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onImported.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onImported.onResult(StreamingLocalPlaylistImportResult(empty = true))
            }
        }
    }

    fun syncStreamingPlaylistToLocal(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        onSynced: StreamingCallback<StreamingLocalPlaylistSyncResult>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val tracks = if (link.providerPlaylistId.isNullOrBlank()) {
                    streamingRepository.userLikedTracks(link.provider)
                } else {
                    loadStreamingPlaylistTracks(link.provider, link.providerPlaylistId).second
                }
                val operations = streamingLocalPlaylistOperations
                    ?: error("Streaming local playlist operations are not bound")
                withContext(Dispatchers.IO) {
                    operations.syncStreamingPlaylist(link, tracks)
                }
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                onSynced.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onSynced.onResult(StreamingLocalPlaylistSyncResult(empty = true))
            }
        }
    }

    fun ensureStreamingLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName,
        onEnsured: StreamingCallback<StreamingLoginPlaylistResult>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val operations = streamingLocalPlaylistOperations
                    ?: error("Streaming local playlist operations are not bound")
                withContext(Dispatchers.IO) {
                    operations.ensureStreamingLoginPlaylist(playlistName, provider)
                }
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    selectedProvider = provider
                )
                updateStreamingDiagnostics()
                onEnsured.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onEnsured.onResult(StreamingLoginPlaylistResult(playlistName = playlistName))
            }
        }
    }

    private suspend fun loadStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): Pair<String, List<StreamingTrack>> {
        val pageSize = 2000
        val tracks = ArrayList<StreamingTrack>()
        var playlistName: String? = null
        var page = 1
        var total: Int? = null
        while (true) {
            val detail = streamingRepository.playlist(
                provider = provider,
                providerPlaylistId = providerPlaylistId,
                page = page,
                pageSize = pageSize,
                useCache = false
            )
            if (playlistName.isNullOrBlank()) {
                playlistName = detail.playlist?.title?.takeIf { it.isNotBlank() }
            }
            total = detail.total ?: total
            tracks.addAll(detail.tracks)

            val reachedTotal = total?.let { expected -> tracks.size >= expected } == true
            if (!detail.hasMore || detail.tracks.isEmpty() || reachedTotal) {
                break
            }
            page += 1
        }
        val name = playlistName?.takeIf { it.isNotBlank() }
            ?: "Streaming playlist $providerPlaylistId"
        return name to tracks
    }

    private suspend fun importStreamingTracksToLocal(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        tracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): StreamingLocalPlaylistImportResult {
        val operations = streamingLocalPlaylistOperations
            ?: error("Streaming local playlist operations are not bound")
        val result = withContext(Dispatchers.IO) {
            operations.importStreamingPlaylist(
                playlistName,
                provider,
                providerPlaylistId,
                tracks,
                linkWhenProviderPlaylistIdBlank
            )
        }
        return StreamingLocalPlaylistImportResult(
            playlistName = result.playlistName,
            playlistAddedCount = result.playlistAddedCount,
            empty = result.isEmpty
        )
    }
}

object MainActivityRouteStateStore {
    private const val SELECTED_TAB = "selectedTab"
    private const val LIBRARY_MODE = "libraryMode"
    private const val SELECTED_LIBRARY_GROUP_KEY = "selectedLibraryGroupKey"
    private const val SELECTED_LIBRARY_GROUP_TITLE = "selectedLibraryGroupTitle"
    private const val SELECTED_PLAYLIST_ID = "selectedPlaylistId"
    private const val SEARCH_QUERY = "searchQuery"
    private const val NETWORK_PAGE = "networkPage"
    private const val SETTINGS_PAGE = "settingsPage"
    private const val SELECTED_REMOTE_SOURCE_ID = "selectedRemoteSourceId"

    fun restore(savedStateHandle: SavedStateHandle): MainActivityRouteState {
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
        return MainActivityRouteState(
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

    fun save(savedStateHandle: SavedStateHandle, state: MainActivityRouteState) {
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
