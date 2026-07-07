package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingHeartbeatRequest
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StreamingRecommendationState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val diagnostics: StreamingGatewayDiagnostics = StreamingGatewayDiagnostics()
)

internal interface HeartbeatRecommendationPlayer {
    fun stopHeartbeatRecommendationMode()

    fun canContinueHeartbeatRecommendationLoading(provider: StreamingProviderName): Boolean

    fun markHeartbeatRecommendationLoadingFinished()

    fun prepareHeartbeatRecommendationRefill(snapshot: PlaybackStateSnapshot?): HeartbeatRefillRequest?

    fun acceptsHeartbeatRecommendationRefill(provider: StreamingProviderName): Boolean

    fun markHeartbeatRecommendationRefillFinished(provider: StreamingProviderName)

    fun prepareStreamingHeartbeatRecommendationRequest(
        requestedProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingHeartbeatRecommendationRequest?

    fun streamingHeartbeatRecommendationEmptyStatus(languageMode: String): String

    fun fetchHeartbeatRecommendations(
        provider: StreamingProviderName,
        providerTrackId: String?,
        providerPlaylistId: String?,
        onResolved: StreamingCallback<List<StreamingTrack>>
    ): Job

    fun resolveHeartbeatRecommendationSeed(
        provider: StreamingProviderName,
        candidates: List<Track>?,
        onResolved: StreamingCallback<String>
    ): Job

    fun prepareHeartbeatRecommendationPresentation(
        tracks: List<StreamingTrack>?,
        emptyStatus: String,
        playingStatus: String
    ): StreamingRecommendationPresentation

    fun prepareHeartbeatRecommendationAppendPresentation(
        tracks: List<StreamingTrack>?,
        languageMode: String
    ): StreamingRecommendationPresentation
}

@HiltViewModel
class StreamingRecommendationViewModel @Inject constructor(
    private val streamingRepositorySource: StreamingRepositorySource
) : ViewModel(), HeartbeatRecommendationPlayer {
    constructor() : this(EmptyStreamingRepositorySource)

    private val dailyRecommendationUseCase = StreamingDailyRecommendationUseCase(streamingRepositorySource)
    private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()
    private val recommendationState = MutableStateFlow(StreamingRecommendationState())
    private var providers: List<StreamingProviderDescriptor> = emptyList()
    private var streamingTrackMatchStore: StreamingTrackMatchStore? = null

    val state: StateFlow<StreamingRecommendationState> = recommendationState.asStateFlow()

    fun updateProviders(nextProviders: List<StreamingProviderDescriptor>) {
        providers = nextProviders
    }

    fun bindStreamingTrackMatchStore(store: StreamingTrackMatchStore?) {
        streamingTrackMatchStore = store
    }

    fun onAction(
        action: RecommendationAction,
        languageMode: String,
        callbacks: RecommendationActionCallbacks
    ): Job {
        return when (action) {
            is RecommendationAction.PlayDaily -> playDailyRecommendations(
                action.provider,
                languageMode,
                callbacks::setStatus,
                callbacks::playDailyRecommendation
            )
            is RecommendationAction.PlayHeartbeat -> playHeartbeatRecommendations(
                action.provider,
                languageMode,
                callbacks
            )
        }
    }

    fun playDailyRecommendations(
        provider: StreamingProviderName?,
        languageMode: String,
        onStatus: (String) -> Unit,
        onPresentation: (StreamingRecommendationPresentation) -> Unit
    ): Job {
        val request = prepareDailyRecommendationRequest(provider, languageMode)
        if (request == null) {
            onStatus(dailyRecommendationEmptyStatus(languageMode))
            return viewModelScope.launch { }
        }
        onStatus(request.loadingStatus)
        recommendationState.value = recommendationState.value.copy(
            loading = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            runCatching {
                dailyRecommendationUseCase.fetch(request.provider)
            }.onSuccess { result ->
                recommendationState.value = recommendationState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = result.diagnostics
                )
                onPresentation(prepareDailyRecommendationPresentation(result.tracks, request.emptyStatus, request.title))
            }.onFailure { error ->
                recommendationState.value = recommendationState.value.copy(
                    loading = false,
                    errorMessage = error.message,
                    diagnostics = recommendationState.value.diagnostics
                )
                onPresentation(StreamingRecommendationPresentation(emptyStatus = request.emptyStatus))
            }
        }
    }

    fun prepareDailyRecommendationPresentation(
        tracks: List<StreamingTrack>?,
        emptyStatus: String,
        title: String
    ): StreamingRecommendationPresentation {
        val placeholders = tracks.orEmpty()
            .take(DAILY_RECOMMENDATION_PLAYBACK_LIMIT)
            .map { StreamingPlaybackAdapter.placeholderTrack(it) }
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

    override fun stopHeartbeatRecommendationMode() {
        heartbeatRecommendationUseCase.stop()
    }

    override fun canContinueHeartbeatRecommendationLoading(provider: StreamingProviderName): Boolean =
        heartbeatRecommendationUseCase.canContinueLoading(provider)

    override fun markHeartbeatRecommendationLoadingFinished() {
        heartbeatRecommendationUseCase.markLoadingFinished()
    }

    override fun prepareHeartbeatRecommendationRefill(snapshot: PlaybackStateSnapshot?): HeartbeatRefillRequest? =
        heartbeatRecommendationUseCase.prepareRefill(snapshot)

    override fun acceptsHeartbeatRecommendationRefill(provider: StreamingProviderName): Boolean =
        heartbeatRecommendationUseCase.accepts(provider)

    override fun markHeartbeatRecommendationRefillFinished(provider: StreamingProviderName) {
        heartbeatRecommendationUseCase.markLoadingFinished(provider)
    }

    override fun prepareStreamingHeartbeatRecommendationRequest(
        requestedProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingHeartbeatRecommendationRequest? {
        val provider = dailyRecommendationProvider(requestedProvider) ?: return null
        heartbeatRecommendationUseCase.startLoading(provider)
        return StreamingHeartbeatRecommendationRequest(
            provider = provider,
            loadingStatus = text(languageMode, "streaming.recommend.heartbeat.loading"),
            emptyStatus = text(languageMode, "streaming.recommend.heartbeat.empty"),
            playingStatus = text(languageMode, "streaming.recommend.heartbeat.playing")
        )
    }

    override fun streamingHeartbeatRecommendationEmptyStatus(languageMode: String): String =
        text(languageMode, "streaming.recommend.heartbeat.empty")

    private fun playHeartbeatRecommendations(
        provider: StreamingProviderName?,
        languageMode: String,
        callbacks: RecommendationActionCallbacks
    ): Job {
        val request = prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)
        if (request == null) {
            callbacks.setStatus(streamingHeartbeatRecommendationEmptyStatus(languageMode))
            return viewModelScope.launch { }
        }
        callbacks.setStatus(request.loadingStatus)
        val seedRequest = callbacks.seedRequest(request.provider)
        if (seedRequest.hasSeed) {
            return fetchHeartbeatRecommendations(request.provider, seedRequest.seedTrackId, seedRequest.playlistId) { streamingTracks ->
                val presentation = prepareHeartbeatRecommendationPresentation(
                    streamingTracks,
                    request.emptyStatus,
                    request.playingStatus
                )
                callbacks.playHeartbeatRecommendation(presentation)
            }
        }
        return resolveHeartbeatSeedFromQueue(request, seedRequest, callbacks)
    }

    private fun resolveHeartbeatSeedFromQueue(
        recommendationRequest: StreamingHeartbeatRecommendationRequest,
        seedRequest: HeartbeatRecommendationSeedRequest,
        callbacks: RecommendationActionCallbacks
    ): Job {
        if (!seedRequest.hasCandidates) {
            callbacks.logSeedMiss(seedRequest)
            markHeartbeatRecommendationLoadingFinished()
            callbacks.setStatus(recommendationRequest.emptyStatus)
            return viewModelScope.launch { }
        }
        return resolveHeartbeatRecommendationSeed(
            recommendationRequest.provider,
            seedRequest.candidates
        ) { resolvedTrackId ->
            if (!canContinueHeartbeatRecommendationLoading(recommendationRequest.provider)) {
                return@resolveHeartbeatRecommendationSeed
            }
            if (!resolvedTrackId.isNullOrEmpty()) {
                fetchHeartbeatRecommendations(
                    recommendationRequest.provider,
                    resolvedTrackId,
                    resolvedTrackId
                ) { streamingTracks ->
                    val presentation = prepareHeartbeatRecommendationPresentation(
                        streamingTracks,
                        recommendationRequest.emptyStatus,
                        recommendationRequest.playingStatus
                    )
                    callbacks.playHeartbeatRecommendation(presentation)
                }
                return@resolveHeartbeatRecommendationSeed
            }
            callbacks.logSeedMiss(seedRequest)
            markHeartbeatRecommendationLoadingFinished()
            callbacks.setStatus(recommendationRequest.emptyStatus)
        }
    }

    override fun fetchHeartbeatRecommendations(
        provider: StreamingProviderName,
        providerTrackId: String?,
        providerPlaylistId: String?,
        onResolved: StreamingCallback<List<StreamingTrack>>
    ): Job {
        recommendationState.value = recommendationState.value.copy(
            loading = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            val repository = streamingRepositorySource.current()
            runCatching {
                repository.heartbeatRecommendations(
                    StreamingHeartbeatRequest(
                        provider = provider,
                        providerTrackId = providerTrackId,
                        providerPlaylistId = providerPlaylistId,
                        count = 60
                    )
                )
            }.onSuccess { tracks ->
                recommendationState.value = recommendationState.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository.diagnostics()
                )
                onResolved.onResult(tracks)
            }.onFailure { error ->
                recommendationState.value = recommendationState.value.copy(
                    loading = false,
                    errorMessage = error.message,
                    diagnostics = repository.diagnostics()
                )
                onResolved.onResult(emptyList())
            }
        }
    }

    override fun resolveHeartbeatRecommendationSeed(
        provider: StreamingProviderName,
        candidates: List<Track>?,
        onResolved: StreamingCallback<String>
    ): Job {
        val seedCandidates = candidates.orEmpty()
        if (seedCandidates.isEmpty()) {
            recommendationState.value = recommendationState.value.copy(
                loading = false,
                errorMessage = null
            )
            return viewModelScope.launch {
                onResolved.onResult("")
            }
        }
        recommendationState.value = recommendationState.value.copy(
            loading = true,
            errorMessage = null
        )
        return viewModelScope.launch {
            val repository = streamingRepositorySource.current()
            val resolvedTrackId = resolveHeartbeatRecommendationSeedId(repository, provider, seedCandidates)
            recommendationState.value = recommendationState.value.copy(
                loading = false,
                errorMessage = null,
                diagnostics = repository.diagnostics()
            )
            onResolved.onResult(resolvedTrackId)
        }
    }

    private suspend fun resolveHeartbeatRecommendationSeedId(
        repository: app.yukine.streaming.StreamingRepository,
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
            val resolvedTrackId = searchHeartbeatSeedMatch(repository, provider, track)
            if (resolvedTrackId.isNotEmpty()) {
                saveHeartbeatSeedMatch(store, track, provider, resolvedTrackId)
                return resolvedTrackId
            }
        }
        return ""
    }

    private suspend fun searchHeartbeatSeedMatch(
        repository: app.yukine.streaming.StreamingRepository,
        provider: StreamingProviderName,
        track: Track
    ): String {
        val query = StreamingTrackMatchPolicy.searchQuery(track)
        if (query.isBlank()) {
            return ""
        }
        return runCatching {
            val result = repository.search(
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
            recommendationState.value = recommendationState.value.copy(
                loading = false,
                errorMessage = error.message,
                diagnostics = repository.diagnostics()
            )
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

    fun prepareHeartbeatRecommendationPlaylist(
        tracks: List<StreamingTrack>?
    ): StreamingRecommendationTrackList {
        return StreamingRecommendationTrackList(
            tracks = heartbeatRecommendationUseCase.playlistPlaceholders(tracks)
        )
    }

    override fun prepareHeartbeatRecommendationPresentation(
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

    override fun prepareHeartbeatRecommendationAppendPresentation(
        tracks: List<StreamingTrack>?,
        languageMode: String
    ): StreamingRecommendationPresentation {
        val playingStatus = text(languageMode, "streaming.recommend.heartbeat.playing")
        val emptyStatus = text(languageMode, "streaming.recommend.heartbeat.empty")
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

    private fun prepareDailyRecommendationRequest(
        requestedProvider: StreamingProviderName?,
        languageMode: String
    ): StreamingDailyRecommendationRequest? {
        val provider = dailyRecommendationProvider(requestedProvider) ?: return null
        return StreamingDailyRecommendationRequest(
            provider = provider,
            loadingStatus = text(languageMode, "streaming.recommend.daily.loading"),
            emptyStatus = text(languageMode, "streaming.recommend.daily.empty"),
            title = text(languageMode, "streaming.recommend.daily")
        )
    }

    private fun dailyRecommendationEmptyStatus(languageMode: String): String =
        text(languageMode, "streaming.recommend.daily.empty")

    private fun dailyRecommendationProvider(requested: StreamingProviderName?): StreamingProviderName? {
        if (requested == StreamingProviderName.NETEASE) {
            return requested
        }
        return if (providers.any { it.name == StreamingProviderName.NETEASE }) {
            StreamingProviderName.NETEASE
        } else {
            null
        }
    }

    private fun text(languageMode: String, key: String): String {
        return AppLanguage.text(languageMode, key)
    }

    private companion object {
        const val DAILY_RECOMMENDATION_PLAYBACK_LIMIT = 30
    }
}
