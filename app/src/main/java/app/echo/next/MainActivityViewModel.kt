package app.echo.next

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.echo.next.model.Playlist
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.model.TrackPlayRecord
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.streaming.StreamingAudioQuality
import app.echo.next.streaming.StreamingAuthKind
import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingGatewayDiagnostics
import app.echo.next.streaming.StreamingMediaType
import app.echo.next.streaming.StreamingPlaybackSource
import app.echo.next.streaming.StreamingProviderCapability
import app.echo.next.streaming.StreamingProviderDescriptor
import app.echo.next.streaming.StreamingProviderHealth
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingRepository
import app.echo.next.streaming.StreamingSearchResult
import app.echo.next.streaming.StreamingTrack
import app.echo.next.streaming.StreamingTrackMatchPolicy
import app.echo.next.ui.HomeDashboardUiState
import app.echo.next.ui.LibraryGroupUiState
import app.echo.next.ui.NetworkSourceUiState
import app.echo.next.ui.PlaylistRowUiState
import app.echo.next.ui.PlaylistTrackUiState
import app.echo.next.ui.QueueTrackUiState
import app.echo.next.ui.TrackRowUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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
    val playlistImportSummary: app.echo.next.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary? = null,
    val playlistImporting: Boolean = false,
    val userPlaylists: List<app.echo.next.streaming.StreamingPlaylist> = emptyList(),
    val userPlaylistsLoading: Boolean = false
)

data class MainActivityStreamingAuthLaunch(
    val provider: StreamingProviderName,
    val launchUrl: String,
    val kind: StreamingAuthKind
)

/** Java-friendly single-arg callback (avoids java.util.function.Consumer which needs API 24). */
fun interface StreamingCallback<T> {
    fun onResult(value: T)
}

/** Java-friendly two-arg callback (avoids java.util.function.BiConsumer which needs API 24). */
fun interface StreamingBiCallback<A, B> {
    fun onResult(first: A, second: B)
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val streamingRepositorySource: StreamingRepositorySource = EmptyStreamingRepositorySource,
    private val dashboardRepository: app.echo.next.dashboard.DashboardRepository? = null
) : ViewModel() {
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
            errorMessage = message?.takeIf { it.isNotBlank() } ?: "流媒体请求失败"
        )
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
        metadata: app.echo.next.streaming.StreamingTrack? = null
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

    fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>,
        onComplete: ((app.echo.next.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary) -> Unit)? = null
    ): Job {
        streamingState.value = streamingState.value.copy(
            playlistImporting = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            runCatching {
                val importer = app.echo.next.streaming.StreamingPlaylistImporter(streamingRepository)
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
                    errorMessage = error.message ?: "姝屽崟瀵煎叆澶辫触"
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
        onComplete: StreamingCallback<app.echo.next.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary>?
    ): Job {
        return importPlaylistToStreaming(provider, playlistName, localTracks) { summary ->
            onComplete?.onResult(summary)
        }
    }

    fun clearStreamingPlaylistImport() {
        if (streamingState.value.playlistImportSummary != null) {
            streamingState.value = streamingState.value.copy(playlistImportSummary = null)
        }
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
                    errorMessage = error.message ?: "鏃犳硶鍔犺浇璐︽埛姝屽崟"
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
        onResolved: StreamingCallback<List<app.echo.next.streaming.StreamingTrack>>
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
        onResolved: StreamingCallback<List<app.echo.next.streaming.StreamingTrack>>
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
        onResolved: StreamingCallback<List<app.echo.next.streaming.StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.heartbeatRecommendations(
                    app.echo.next.streaming.StreamingHeartbeatRequest(
                        provider = provider,
                        providerTrackId = providerTrackId,
                        providerPlaylistId = providerPlaylistId
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
        onResolved: StreamingBiCallback<String, List<app.echo.next.streaming.StreamingTrack>>
    ): Job {
        val pageSize = 2000
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                val tracks = ArrayList<app.echo.next.streaming.StreamingTrack>()
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
                playlistName to tracks
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null
                )
                updateStreamingDiagnostics()
                val name = result.first?.takeIf { it.isNotBlank() }
                    ?: "娴佸獟浣撴瓕鍗?$providerPlaylistId"
                onResolved.onResult(name, result.second)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics()
                onResolved.onResult("", emptyList())
            }
        }
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
