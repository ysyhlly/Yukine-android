package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistImportSummary
import app.yukine.streaming.StreamingPlaylistLinkParser
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal const val STREAMING_QUEUE_PRE_RESOLVE_LIMIT = 3
// Kept as source-compatible aliases for existing callers/tests; the coordinator owns the limits.
internal const val STREAMING_PLAYLIST_PAGE_SIZE =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_PAGE_SIZE
internal const val STREAMING_PLAYLIST_MAX_PAGES =
    StreamingPlaylistDataCoordinator.STREAMING_PLAYLIST_MAX_PAGES

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamingRepositorySource: StreamingRepositorySource
) : ViewModel() {
    constructor() : this(EmptyStreamingRepositorySource)

    private val streamingState = StreamingFeatureStateOwner()
    private var streamingRepository: StreamingRepository = streamingRepositorySource.current()
    private var streamingLocalPlaylistOperations: StreamingLocalPlaylistOperations? = null
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val streamingPlaylistDataCoordinator = StreamingPlaylistDataCoordinator(
        repositoryProvider = { streamingRepository },
        localOperationsProvider = { streamingLocalPlaylistOperations },
        ioDispatcherProvider = { ioDispatcher }
    )
    internal val auth = StreamingAuthStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    internal val search = StreamingSearchStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository }
    )
    internal val playbackResolution = StreamingPlaybackResolutionStateOwner(
        scope = viewModelScope,
        stateOwner = streamingState,
        repository = { streamingRepository },
        ioDispatcher = { ioDispatcher }
    )
    @JvmName("playbackResolutionOwner")
    internal fun playbackResolutionOwner(): StreamingPlaybackResolutionStateOwner = playbackResolution
    @JvmName("searchOwner")
    internal fun searchOwner(): StreamingSearchStateOwner = search
    val streaming: StateFlow<StreamingSearchState> = streamingState.state
    val state: StreamingSearchState
        get() = streamingState.value

    fun bindStreamingRepository(repository: StreamingRepository) {
        streamingRepository = repository
    }

    /**
     * Test-only seam: lets unit tests inject a deterministic dispatcher so background resolves run
     * on the test scheduler instead of the real IO thread pool. Production keeps [Dispatchers.IO].
     */
    internal fun bindIoDispatcherForTest(dispatcher: CoroutineDispatcher) {
        ioDispatcher = dispatcher
    }

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

    fun bindStreamingPlaybackCoordinator(
        planner: StreamingPlaybackResolvePlanner?,
        taskQueue: StreamingPlaybackTaskQueue?
    ) {
        playbackResolution.bindPlaybackCoordinator(planner, taskQueue)
    }

    fun bindStreamingLocalPlaylistOperations(operations: StreamingLocalPlaylistOperations?) {
        streamingLocalPlaylistOperations = operations
    }

    fun bindStreamingTrackMatchStore(store: StreamingTrackMatchStore?) {
        playbackResolution.bindTrackMatchStore(store)
    }
    fun updateStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) =
        search.updateStreamingSearchChrome(labels, actions)

    fun refreshStreamingProviders(): Job = auth.refreshProviders()

    fun updateStreamingProviders(
        providers: List<StreamingProviderDescriptor>,
        capabilities: List<StreamingProviderCapability> = streamingState.value.providerCapabilities,
        health: List<StreamingProviderHealth> = streamingState.value.providerHealth
    ) = auth.updateProviders(providers, capabilities, health)

    fun selectStreamingProvider(provider: StreamingProviderName) = auth.selectProvider(provider)

    fun updateStreamingSearchQuery(query: String) = search.updateStreamingSearchQuery(query)

    fun clearStreamingSearchSession() = search.clearStreamingSearchSession()

    fun beginStreamingRequest() = search.beginStreamingRequest()

    fun beginStreamingNextPageRequest() = search.beginStreamingNextPageRequest()

    fun updateStreamingSearchResult(result: StreamingSearchResult) =
        search.updateStreamingSearchResult(result)

    fun appendStreamingSearchResult(result: StreamingSearchResult) =
        search.appendStreamingSearchResult(result)

    fun searchStreaming(
        provider: StreamingProviderName = streamingState.value.selectedProvider,
        query: String = streamingState.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        page: Int = 1,
        pageSize: Int = 20
    ): Job = search.searchStreaming(provider, query, mediaTypes, page, pageSize)

    fun searchAllStreaming(
        query: String = streamingState.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        pageSize: Int = 12
    ): Job = search.searchAllStreaming(query, mediaTypes, pageSize)

    fun searchNextStreamingPage() = search.searchNextStreamingPage()

    fun resolveStreamingTrackMatch(
        provider: StreamingProviderName,
        localTrack: Track,
        onResolved: StreamingCallback<StreamingTrack?>
    ): Job = search.resolveStreamingTrackMatch(provider, localTrack, onResolved)

    fun refreshStreamingAuthState(provider: StreamingProviderName) = auth.refreshAuthState(provider)

    fun startStreamingAuth(
        provider: StreamingProviderName,
        redirectUri: String? = null,
        onLaunchReady: (() -> Unit)? = null
    ) = auth.startAuth(provider, redirectUri, onLaunchReady)

    fun signOutStreaming(provider: StreamingProviderName): Job = auth.signOut(provider)

    fun completeStreamingAuth(
        provider: StreamingProviderName,
        callbackUri: String,
        cookieHeader: String? = null,
        onAuthSuccess: StreamingCallback<StreamingProviderName>? = null
    ) = auth.completeAuth(provider, callbackUri, cookieHeader, onAuthSuccess)
    fun resolveStreamingPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ): Job = playbackResolution.resolveStreamingPlayback(provider, providerTrackId, quality)

    fun resolveStreamingPlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: StreamingTrack? = null
    ): Job = playbackResolution.resolveStreamingPlaybackTrack(provider, providerTrackId, quality, metadata)

    fun resolveStreamingTrackForPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack? = null,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<Track?>
    ): Job = playbackResolution.resolveStreamingTrackForPlayback(
        provider, providerTrackId, metadata, quality, onResolved
    )

    fun preResolveNextStreamingTrack(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingBiCallback<Long, Track?>
    ): Boolean = playbackResolution.preResolveNextStreamingTrack(snapshot, queue, quality, onResolved)

    fun preResolveStreamingQueueWindow(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        maxCount: Int = STREAMING_QUEUE_PRE_RESOLVE_LIMIT,
        onResolved: StreamingBiCallback<Long, Track?>
    ): Job? = playbackResolution.preResolveStreamingQueueWindow(
        snapshot, queue, quality, maxCount, onResolved
    )

    /**
     * Foreground maintenance for local NetEase/QQ sessions. The repository/store throttle actual
     * network work, and this method deliberately avoids the global loading/error UI so reopening
     * the app never feels blocked by a background cookie check.
     */
    fun maintainStreamingAuthSessions(): Job = auth.maintainSessions()
    fun preResolveStreamingQueueWindowBatch(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        maxCount: Int = STREAMING_QUEUE_PRE_RESOLVE_LIMIT,
        onResolved: (Map<Long, Track>) -> Unit
    ): Job? = playbackResolution.preResolveStreamingQueueWindowBatch(
        snapshot, queue, quality, maxCount, onResolved
    )

    fun resolveStreamingTrackListForPlayback(
        tracks: List<Track>?,
        index: Int,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        onResolved: StreamingCallback<ResolvedStreamingTrackList?>
    ): Boolean = playbackResolution.resolveStreamingTrackListForPlayback(
        tracks, index, quality, onResolved
    )

    fun prepareCurrentStreamingQueueResolveTarget(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?
    ): StreamingQueueResolveTarget? =
        playbackResolution.prepareCurrentStreamingQueueResolveTarget(snapshot, queue)

    fun recoverStreamingBuffering(
        snapshot: PlaybackStateSnapshot?,
        selectedQuality: StreamingAudioQuality,
        adaptiveQuality: StreamingAudioQuality,
        refuseAutomaticQualityDowngrade: Boolean,
        onResolved: StreamingCallback<StreamingRecoveryResolution?>
    ): StreamingAudioQuality? = playbackResolution.recoverStreamingBuffering(
        snapshot,
        selectedQuality,
        adaptiveQuality,
        refuseAutomaticQualityDowngrade,
        onResolved
    )

    fun loadStreamingProviderTrackId(
        track: Track,
        provider: StreamingProviderName,
        onResolved: StreamingCallback<String>
    ): Job = playbackResolution.loadStreamingProviderTrackId(track, provider, onResolved)

    fun streamingProviderTrackIdFor(track: Track?, provider: StreamingProviderName?): String =
        playbackResolution.streamingProviderTrackIdFor(track, provider)

    fun saveStreamingProviderTrackId(
        track: Track?,
        provider: StreamingProviderName?,
        providerTrackId: String?
    ): Job = playbackResolution.saveStreamingProviderTrackId(track, provider, providerTrackId)
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
        requestedProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingDailyRecommendationRequest? {
        val provider = recommendationProvider(requestedProvider) ?: return null
        return StreamingDailyRecommendationRequest(
            provider = provider,
            loadingStatus = text(languageMode, "streaming.recommend.daily.loading"),
            emptyStatus = text(languageMode, "streaming.recommend.daily.empty"),
            title = text(languageMode, "streaming.recommend.daily")
        )
    }

    fun streamingDailyRecommendationEmptyStatus(languageMode: String): String =
        text(languageMode, "streaming.recommend.daily.empty")

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

    fun prepareStreamingPlaybackStatusText(
        languageMode: String,
        quality: StreamingAudioQuality? = null
    ): StreamingPlaybackStatusText {
        val qualityLabel = quality?.let {
            SettingsLabelFormatter.streamingQualityLabel(
                StreamingQualityPreference.valueFor(it),
                languageMode
            )
        }.orEmpty()
        return StreamingPlaybackStatusText(
            resolving = text(languageMode, "streaming.resolving"),
            resolveFailed = text(languageMode, "streaming.resolve.failed"),
            qualityDowngrading = text(languageMode, "streaming.quality.downgrading") + qualityLabel,
            qualityDowngraded = text(languageMode, "streaming.quality.downgraded") + qualityLabel,
            qualityRefreshing = text(languageMode, "streaming.quality.refreshing") + qualityLabel,
            qualityRefreshed = text(languageMode, "streaming.quality.refreshed") + qualityLabel
        )
    }

    fun prepareStreamingStatusText(
        languageMode: String,
        qualityPreference: String? = null
    ): StreamingStatusText {
        val qualityLabel = qualityPreference?.let {
            SettingsLabelFormatter.streamingQualityLabel(it, languageMode)
        }.orEmpty()
        return StreamingStatusText(
            streamingQualityApplied = text(languageMode, "streaming.quality.applied") + qualityLabel
        )
    }

    fun streamingPlaylistLoadedDialogTitle(languageMode: String): String =
        text(languageMode, "streaming.playlist.load.success.title")

    fun prepareManualCookieDialogState(
        provider: StreamingProviderName?,
        languageMode: String
    ): StreamingManualCookieDialogState = auth.prepareManualCookieDialogState(provider, languageMode)

    fun prepareManualCookieAuthRequest(
        provider: StreamingProviderName?,
        cookieHeader: String?,
        languageMode: String
    ): StreamingManualCookieAuthRequest? =
        auth.prepareManualCookieAuthRequest(provider, cookieHeader, languageMode)

    fun manualCookieEmptyStatus(languageMode: String): String = auth.manualCookieEmptyStatus(languageMode)

    fun prepareStreamingPlaylistImportDialogState(languageMode: String): StreamingPlaylistImportDialogState =
        prepareStreamingPlaylistImportDialogState(languageMode, streamingState.value.selectedProvider)

    fun prepareStreamingPlaylistImportDialogState(
        languageMode: String,
        provider: StreamingProviderName?
    ): StreamingPlaylistImportDialogState {
        val luoxueSelected = provider == StreamingProviderName.LUOXUE
        return StreamingPlaylistImportDialogState(
            title = if (luoxueSelected) {
                text(languageMode, "streaming.lx.import.source")
            } else {
                text(languageMode, "streaming.import.playlist.from")
            },
            hint = if (luoxueSelected) {
                text(languageMode, "streaming.lx.import.hint")
            } else {
                text(languageMode, "streaming.paste.playlist.link")
            }
        )
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
        requireSearch: Boolean = true,
        languageMode: String
    ): StreamingProviderPickerRequest {
        val pickerState = streamingImportProviderPickerState(providers, requireSearch)
        return StreamingProviderPickerRequest(
            pickerState = pickerState,
            title = text(languageMode, "choose.streaming.provider"),
            emptyStatus = text(languageMode, "streaming.no.providers"),
            valid = pickerState.providers.isNotEmpty()
        )
    }

    fun streamingPlaylistImportStatus(
        summary: StreamingPlaylistImportSummary?
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
        importStatus: StreamingPlaylistImportStatus?,
        languageMode: String
    ): StreamingPlaylistExportPresentation {
        if (importStatus == null) {
            return StreamingPlaylistExportPresentation()
        }
        var status = text(languageMode, "streaming.import.matched.prefix") +
            importStatus.matchedCount +
            " / " +
            importStatus.totalRequested
        if (importStatus.unresolvedCount > 0) {
            status += " (" +
                importStatus.unresolvedCount +
                text(languageMode, "streaming.import.unresolved.suffix") +
                ")"
        }
        return StreamingPlaylistExportPresentation(status = status)
    }

    fun prepareStreamingPlaylistExportRequest(
        playlistName: String?,
        tracks: List<Track>?,
        languageMode: String
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (playlistName.isNullOrBlank() || normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text(languageMode, "streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = playlistName,
            tracks = normalizedTracks,
            status = text(languageMode, "streaming.import.matched.prefix") + "...",
            valid = true
        )
    }

    fun prepareStreamingFavoritesExportRequest(
        tracks: List<Track>?,
        languageMode: String
    ): StreamingPlaylistExportRequest {
        val normalizedTracks = tracks.orEmpty().filterNotNull()
        if (normalizedTracks.isEmpty()) {
            return StreamingPlaylistExportRequest(
                status = text(languageMode, "streaming.no.tracks.to.import")
            )
        }
        return StreamingPlaylistExportRequest(
            playlistName = text(languageMode, "favorites"),
            tracks = normalizedTracks,
            status = text(languageMode, "streaming.import.matched.prefix") + "...",
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
        fallbackProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingPlaylistImportStartRequest {
        val target = prepareStreamingPlaylistImportTarget(linkOrId, fallbackProvider)
        val provider = target.provider
        val invalid = target.invalid || provider == null || target.providerPlaylistId.isEmpty()
        if (invalid) {
            return StreamingPlaylistImportStartRequest(
                invalidStatus = text(languageMode, "streaming.playlist.link.invalid")
            )
        }
        return StreamingPlaylistImportStartRequest(
            provider = provider,
            providerPlaylistId = target.providerPlaylistId,
            invalidStatus = text(languageMode, "streaming.playlist.link.invalid"),
            resolvingStatus = text(languageMode, "streaming.resolving"),
            valid = true
        )
    }

    fun prepareStreamingLoginPlaylistRequest(
        provider: StreamingProviderName,
        languageMode: String
    ): StreamingLoginPlaylistRequest {
        val displayName = streamingProviderDisplayName(provider)
        val playlistName =
            text(languageMode, "streaming.my.playlist.prefix") +
                displayName +
                text(languageMode, "streaming.my.playlist.suffix")
        return StreamingLoginPlaylistRequest(
            provider = provider,
            playlistName = playlistName
        )
    }

    fun prepareStreamingLikedPlaylistName(
        provider: StreamingProviderName,
        languageMode: String
    ): String {
        return text(languageMode, "streaming.liked.playlist.prefix") +
            streamingProviderDisplayName(provider) +
            text(languageMode, "streaming.liked.playlist.suffix")
    }

    fun prepareStreamingPlaylistImportPresentation(
        result: StreamingLocalPlaylistImportResult?,
        languageMode: String
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text(languageMode, "streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text(languageMode, "streaming.playlist.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingLikedImportPresentation(
        result: StreamingLocalPlaylistImportResult?,
        languageMode: String
    ): StreamingLocalPlaylistImportPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistImportPresentation(
                empty = true,
                status = text(languageMode, "streaming.liked.empty")
            )
        }
        return StreamingLocalPlaylistImportPresentation(
            status = text(languageMode, "streaming.liked.imported.prefix") +
                result.playlistName +
                " (${result.playlistAddedCount})",
            showLoadedDialog = true
        )
    }

    fun prepareStreamingPlaylistSyncPresentation(
        result: StreamingLocalPlaylistSyncResult?,
        languageMode: String
    ): StreamingLocalPlaylistSyncPresentation {
        if (result == null || result.empty) {
            return StreamingLocalPlaylistSyncPresentation(
                empty = true,
                status = text(languageMode, "streaming.playlist.empty")
            )
        }
        return StreamingLocalPlaylistSyncPresentation(
            status = text(languageMode, "streaming.sync.complete") + " (${result.syncedCount})"
        )
    }

    fun prepareStreamingLoginPlaylistPresentation(
        request: StreamingLoginPlaylistRequest,
        result: StreamingLoginPlaylistResult?,
        languageMode: String
    ): StreamingLoginPlaylistPresentation {
        return StreamingLoginPlaylistPresentation(
            status = text(languageMode, "streaming.playlist.created") + ": " + request.playlistName,
            playlistId = result?.playlistId ?: -1L
        )
    }

    fun prepareStreamingPlaylistSyncTarget(localPlaylistId: Long): StreamingPlaylistSyncTarget? {
        if (localPlaylistId < 0L) {
            return null
        }
        val operations = streamingLocalPlaylistOperations
        if (operations != null && !operations.playlistExists(localPlaylistId)) {
            return StreamingPlaylistSyncTarget(missingLink = true)
        }
        val link = operations?.linkedPlaylist(localPlaylistId)
        return if (link == null) {
            StreamingPlaylistSyncTarget(missingLink = true)
        } else {
            StreamingPlaylistSyncTarget(link = link)
        }
    }

    fun prepareStreamingPlaylistSyncStartRequest(
        localPlaylistId: Long,
        languageMode: String
    ): StreamingPlaylistSyncStartRequest? {
        val target = prepareStreamingPlaylistSyncTarget(localPlaylistId) ?: return null
        if (target.missingLink || target.link == null) {
            return StreamingPlaylistSyncStartRequest(
                status = text(languageMode, "streaming.not.linked")
            )
        }
        return StreamingPlaylistSyncStartRequest(
            link = target.link,
            status = text(languageMode, "streaming.sync.started"),
            valid = true
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

    private fun descriptorFor(provider: StreamingProviderName): StreamingProviderDescriptor? {
        return streamingState.value.providers.firstOrNull { it.name == provider }
    }

    private fun text(languageMode: String, key: String): String {
        return AppLanguage.text(languageMode, key)
    }

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
                    selectedProvider = provider,
                    diagnostics = streamingRepository.diagnostics()
                )
            }.onFailure { error ->
                streamingState.value = streamingState.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = streamingRepository.diagnostics()
                )
            }
        }
    }

    fun importAllAccountPlaylistsToLocal(
        provider: StreamingProviderName,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ): Job {
        streamingState.value = streamingState.value.copy(
            userPlaylistsLoading = true,
            playlistImporting = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            val playlists = runCatching {
                streamingRepository.userPlaylists(provider)
            }.getOrElse { error ->
                streamingState.value = streamingState.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    playlistImporting = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = streamingRepository.diagnostics()
                )
                onImported.onResult(StreamingAccountPlaylistImportResult(failedCount = 1))
                return@launch
            }
            importAccountPlaylistsToLocalInternal(provider, playlists, onImported)
        }
    }

    fun fetchAccountPlaylistsForImport(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<StreamingPlaylist>>
    ): Job {
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
                    selectedProvider = provider,
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(playlists)
            }.onFailure { error ->
                streamingState.value = streamingState.value.copy(
                    userPlaylists = emptyList(),
                    userPlaylistsLoading = false,
                    errorMessage = error.message ?: "Could not load account playlists",
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(emptyList())
            }
        }
    }

    fun importAccountPlaylistsToLocal(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ): Job {
        streamingState.value = streamingState.value.copy(
            userPlaylistsLoading = true,
            playlistImporting = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            importAccountPlaylistsToLocalInternal(provider, playlists, onImported)
        }
    }

    private suspend fun importAccountPlaylistsToLocalInternal(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>,
        onImported: StreamingCallback<StreamingAccountPlaylistImportResult>
    ) {
        var importedPlaylists = 0
        var importedTracks = 0
        var failed = 0
        for (playlist in playlists) {
            if (playlist.providerPlaylistId.isBlank()) {
                failed += 1
                continue
            }
            val result = runCatching {
                val (playlistName, tracks) = loadStreamingPlaylistTracks(
                    playlist.provider,
                    playlist.providerPlaylistId
                )
                if (tracks.isEmpty()) {
                    StreamingLocalPlaylistImportResult(
                        playlistName = playlist.title.ifBlank { playlistName },
                        empty = true
                    )
                } else {
                    val linkedPlaylist = streamingLocalPlaylistOperations?.linkedPlaylist(
                        playlist.provider,
                        playlist.providerPlaylistId
                    )
                    if (linkedPlaylist != null) {
                        val syncResult = streamingLocalPlaylistOperations?.syncStreamingPlaylist(
                            linkedPlaylist,
                            tracks
                        ) ?: StreamingLocalPlaylistSyncResult(empty = true)
                        StreamingLocalPlaylistImportResult(
                            playlistName = playlistName.ifBlank { playlist.title },
                            playlistAddedCount = syncResult.syncedCount,
                            empty = syncResult.empty
                        )
                    } else {
                        importStreamingTracksToLocal(
                            playlistName = playlistName.ifBlank { playlist.title },
                            provider = playlist.provider,
                            providerPlaylistId = playlist.providerPlaylistId,
                            tracks = tracks,
                            linkWhenProviderPlaylistIdBlank = false
                        )
                    }
                }
            }.getOrElse {
                failed += 1
                null
            }
            if (result != null && !result.empty) {
                importedPlaylists += 1
                importedTracks += result.playlistAddedCount
            }
        }
        streamingState.value = streamingState.value.copy(
            userPlaylists = playlists,
            userPlaylistsLoading = false,
            playlistImporting = false,
            selectedProvider = provider,
            errorMessage = null,
            diagnostics = streamingRepository.diagnostics()
        )
        onImported.onResult(
            StreamingAccountPlaylistImportResult(
                playlistCount = playlists.size,
                importedPlaylistCount = importedPlaylists,
                importedTrackCount = importedTracks,
                failedCount = failed
            )
        )
    }

    fun fetchUserLikedTracks(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.userLikedTracks(provider)
            }.onSuccess { tracks ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onResolved.onResult(emptyList())
            }
        }
    }

    fun fetchDailyRecommendations(
        provider: StreamingProviderName,
        onResolved: StreamingCallback<List<StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.dailyRecommendations(provider)
            }.onSuccess { tracks ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(tracks)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onResolved.onResult(emptyList())
            }
        }
    }

    fun fetchStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        onResolved: StreamingBiCallback<String, List<StreamingTrack>>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                loadStreamingPlaylistTracks(provider, providerPlaylistId)
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(result.first, result.second)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onResolved.onResult("", emptyList())
            }
        }
    }

    suspend fun loadStreamingPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): Pair<String, List<StreamingTrack>> {
        return streamingPlaylistDataCoordinator.loadPlaylistTracks(provider, providerPlaylistId)
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
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onImported.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onImported.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onImported.onResult(StreamingLocalPlaylistImportResult(empty = true))
            }
        }
    }

    fun syncStreamingPlaylistToLocal(
        link: app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist,
        onSynced: StreamingCallback<StreamingLocalPlaylistSyncResult>
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingPlaylistDataCoordinator.syncPlaylistToLocal(link)
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onSynced.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                streamingPlaylistDataCoordinator.ensureLoginPlaylist(playlistName, provider)
            }.onSuccess { result ->
                streamingState.value = streamingState.value.copy(
                    loading = false,
                    errorMessage = null,
                    selectedProvider = provider,
                    diagnostics = streamingRepository.diagnostics()
                )
                onEnsured.onResult(result)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onEnsured.onResult(StreamingLoginPlaylistResult(playlistName = playlistName))
            }
        }
    }

    fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>,
        onComplete: ((StreamingPlaylistImportSummary) -> Unit)? = null
    ): Job {
        streamingState.value = streamingState.value.copy(
            playlistImporting = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            runCatching {
                streamingPlaylistDataCoordinator.importPlaylistToStreaming(
                    provider,
                    playlistName,
                    localTracks
                )
            }.onSuccess { summary ->
                streamingState.value = streamingState.value.copy(
                    playlistImporting = false,
                    playlistImportSummary = summary,
                    selectedProvider = provider,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onComplete?.invoke(summary)
            }.onFailure { error ->
                streamingState.value = streamingState.value.copy(
                    playlistImporting = false,
                    errorMessage = error.message ?: "Playlist import failed",
                    diagnostics = streamingRepository.diagnostics()
                )
            }
        }
    }

    private suspend fun importStreamingTracksToLocal(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        tracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): StreamingLocalPlaylistImportResult {
        return streamingPlaylistDataCoordinator.importTracksToLocal(
            playlistName,
            provider,
            providerPlaylistId,
            tracks,
            linkWhenProviderPlaylistIdBlank
        )
    }

    fun updateStreamingPlaybackSource(source: StreamingPlaybackSource) =
        playbackResolution.updatePlaybackSource(source)

    fun updateStreamingPlaybackTrack(source: StreamingPlaybackSource, track: Track) =
        playbackResolution.updatePlaybackTrack(source, track)

    fun updateStreamingAuthState(provider: StreamingProviderName, authState: StreamingAuthState) =
        auth.updateAuthState(provider, authState)

    fun updateStreamingAuthLaunch(
        provider: StreamingProviderName,
        authState: StreamingAuthState,
        launchUrl: String?
    ) = auth.updateAuthLaunch(provider, authState, launchUrl)

    fun clearStreamingAuthLaunch() = auth.clearAuthLaunch()

    fun updateStreamingDiagnostics(diagnostics: StreamingGatewayDiagnostics) {
        streamingState.value = streamingState.value.copy(diagnostics = diagnostics)
    }

    fun refreshStreamingDiagnostics() {
        updateStreamingDiagnostics(streamingRepository.diagnostics())
    }

    fun failStreamingRequest(message: String?) {
        streamingState.value = streamingState.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message ?: "Streaming request failed"
        )
    }

}
