package app.yukine
import app.yukine.streaming.StreamingQualityPreference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingGatewayDiagnostics
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingCookieHeaderParser
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingPlaylistLinkParser
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderCapability
import app.yukine.streaming.StreamingProviderDescriptor
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchItem
import app.yukine.streaming.StreamingSearchResult
import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingTrackMatchPolicy
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import javax.inject.Inject

private const val STREAMING_QUEUE_PRE_RESOLVE_LIMIT = 3
private const val CROSS_SOURCE_DURATION_TOLERANCE_MS = 3_000L

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
    val userPlaylistsLoading: Boolean = false,
    val searchChromeLabels: StreamingSearchLabels = StreamingSearchLabels.empty(),
    val searchChromeActions: StreamingSearchActions = StreamingSearchActions.empty()
)

data class MainActivityStreamingAuthLaunch(
    val provider: StreamingProviderName,
    val launchUrl: String,
    val kind: StreamingAuthKind
)

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamingRepositorySource: StreamingRepositorySource
) : ViewModel() {
    constructor() : this(EmptyStreamingRepositorySource)

    private val streamingState = MutableStateFlow(MainActivityStreamingState())
    private var streamingRepository: StreamingRepository = streamingRepositorySource.current()
    private var streamingPlaybackPlanner: StreamingPlaybackResolvePlanner? = null
    private var streamingPlaybackTaskQueue: StreamingPlaybackTaskQueue? = null
    private var streamingLocalPlaylistOperations: StreamingLocalPlaylistOperations? = null
    private var streamingTrackMatchStore: StreamingTrackMatchStore? = null
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    val streaming: StateFlow<MainActivityStreamingState> = streamingState.asStateFlow()
    var state: MainActivityStreamingState
        get() = streamingState.value
        set(value) {
            streamingState.value = value
        }

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
        streamingPlaybackPlanner = planner
        streamingPlaybackTaskQueue = taskQueue
    }

    fun bindStreamingLocalPlaylistOperations(operations: StreamingLocalPlaylistOperations?) {
        streamingLocalPlaylistOperations = operations
    }

    fun bindStreamingTrackMatchStore(store: StreamingTrackMatchStore?) {
        streamingTrackMatchStore = store
    }

    fun updateStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) {
        streamingState.value = streamingState.value.copy(
            searchChromeLabels = labels,
            searchChromeActions = actions
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
                updateStreamingProviders(refresh.providers, refresh.capabilities, refresh.health)
                streamingState.value = streamingState.value.copy(
                    authStates = refresh.authStates,
                    loading = false,
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }
        }
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

    fun clearStreamingSearchSession() {
        streamingState.value = streamingState.value.copy(
            searchQuery = "",
            searchResult = null,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
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
        val trackOnlyResult = result.trackOnlySearchResult()
        streamingState.value = streamingState.value.copy(
            selectedProvider = trackOnlyResult.provider,
            searchQuery = trackOnlyResult.query,
            searchResult = trackOnlyResult,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun appendStreamingSearchResult(result: StreamingSearchResult) {
        val current = streamingState.value
        val previous = current.searchResult
        val trackOnlyResult = result.trackOnlySearchResult()
        val merged = if (
            previous != null &&
            previous.provider == trackOnlyResult.provider &&
            previous.query == trackOnlyResult.query &&
            trackOnlyResult.page > previous.page
        ) {
            val mergedTracks = (previous.tracks + trackOnlyResult.tracks)
                .distinctBy { "${it.provider.wireName}:${it.providerTrackId}" }
            trackOnlyResult.copy(
                tracks = mergedTracks,
                total = mergedTracks.size,
                items = (previous.unifiedItems + trackOnlyResult.unifiedItems)
                    .filter { it.type == StreamingMediaType.TRACK && it.track != null }
                    .distinctBy { "${it.provider.wireName}:${it.id}" }
            )
        } else {
            trackOnlyResult
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
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }
        }
    }

    fun searchAllStreaming(
        query: String = streamingState.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        pageSize: Int = 12
    ): Job {
        val normalizedMediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) }
        streamingState.value = streamingState.value.copy(
            searchQuery = query,
            searchMediaTypes = normalizedMediaTypes
        )
        beginStreamingRequest()
        return viewModelScope.launch {
            val current = streamingState.value
            val searchableProviders = current.providers
                .filter { provider ->
                    provider.name != StreamingProviderName.MOCK &&
                        (current.providerCapabilities.firstOrNull { it.provider == provider.name }?.supportsSearch
                            ?: app.yukine.streaming.StreamingCapabilityResolver.canSearch(provider))
                }
                .map { it.name }
                .distinct()
            if (searchableProviders.isEmpty()) {
                failStreamingRequest("当前没有可搜索的在线音源")
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                return@launch
            }
            val results = searchableProviders.map { provider ->
                async {
                    runCatching {
                        streamingRepository.search(
                            provider = provider,
                            query = query,
                            mediaTypes = normalizedMediaTypes,
                            page = 1,
                            pageSize = pageSize
                        )
                    }
                }
            }.awaitAll()
            val successes = results.mapNotNull { it.getOrNull() }
            if (successes.isEmpty()) {
                val message = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                    ?: "在线聚合搜索失败"
                failStreamingRequest(message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                return@launch
            }
            val merged = mergeStreamingSearchResults(query, pageSize, successes)
                .trackOnlySearchResult()
                .mergeCrossSourceDuplicates()
            streamingState.value = streamingState.value.copy(
                selectedProvider = successes.firstOrNull { it.tracks.isNotEmpty() }?.provider
                    ?: current.selectedProvider,
                searchQuery = query,
                searchResult = merged,
                loading = false,
                loadingMore = false,
                errorMessage = null,
                diagnostics = streamingRepository.diagnostics()
            )
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
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                    errorMessage = null,
                    diagnostics = streamingRepository.diagnostics()
                )
                onResolved.onResult(track)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onResolved.onResult(null)
            }
        }
    }

    private fun mergeStreamingSearchResults(
        query: String,
        pageSize: Int,
        results: List<StreamingSearchResult>
    ): StreamingSearchResult {
        val tracks = results
            .flatMap { it.tracks }
            .distinctBy { "${it.provider.wireName}:${it.providerTrackId}" }
            .rankBySearchSimilarity(query)
        val albums = results
            .flatMap { it.albums }
            .distinctBy { "${it.provider.wireName}:${it.providerAlbumId}" }
        val artists = results
            .flatMap { it.artists }
            .distinctBy { "${it.provider.wireName}:${it.providerArtistId}" }
        val playlists = results
            .flatMap { it.playlists }
            .distinctBy { "${it.provider.wireName}:${it.providerPlaylistId}" }
        val mvs = results
            .flatMap { it.mvs }
            .distinctBy { "${it.provider.wireName}:${it.providerMvId}" }
        return StreamingSearchResult(
            provider = results.firstOrNull { it.tracks.isNotEmpty() }?.provider
                ?: results.first().provider,
            query = query,
            page = 1,
            pageSize = pageSize,
            total = results.mapNotNull { it.total }.takeIf { it.isNotEmpty() }?.sum(),
            hasMore = false,
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists,
            mvs = mvs,
            cached = results.all { it.cached },
            items = results.flatMap { it.unifiedItems }
                .distinctBy { "${it.provider.wireName}:${it.type.wireName}:${it.id}" }
                .rankItemsBySearchSimilarity(query)
        )
    }

    /**
     * 把不同音源里「同作者 + 同曲名」（时长在容差内）的曲目合并为一条，列表只保留一条代表项，
     * 其余音源折叠进代表项的 [StreamingTrack.playbackCandidates]，供播放解析失败时自动回退、
     * 以及在播放页手动切换音源使用。仅在多音源聚合搜索结果上调用。
     */
    private fun StreamingSearchResult.mergeCrossSourceDuplicates(): StreamingSearchResult {
        if (tracks.size <= 1) {
            return this
        }
        val itemsByKey = unifiedItems
            .filter { it.type == StreamingMediaType.TRACK && it.track != null }
            .associateBy { "${it.provider.wireName}:${it.id}" }
        // 保持原有排序：按首次出现的代表项顺序聚类。
        val clusters = LinkedHashMap<String, MutableList<StreamingTrack>>()
        tracks.forEach { track ->
            val key = track.crossSourceMergeKey()
            val bucket = clusters.getOrPut(key) { mutableListOf() }
            val sibling = bucket.firstOrNull { it.isSameSongAcrossSource(track) }
            if (sibling != null || bucket.isEmpty()) {
                bucket.add(track)
            } else {
                // 同作者+同曲名但时长差异过大（疑似同名不同曲），归入带序号的独立簇避免误合并。
                clusters.getOrPut("$key#${clusters.size}") { mutableListOf() }.add(track)
            }
        }
        val mergedTracks = clusters.values
            .filter { it.isNotEmpty() }
            .map { group -> group.mergeSourcesIntoRepresentative() }
        if (mergedTracks.size == tracks.size) {
            return this
        }
        val mergedItems = mergedTracks.map { track ->
            itemsByKey["${track.provider.wireName}:${track.providerTrackId}"]
                ?.copy(track = track)
                ?: StreamingSearchItem.fromTrack(track)
        }
        return copy(
            tracks = mergedTracks,
            total = mergedTracks.size,
            items = mergedItems
        )
    }

    /** 归一化「作者 + 曲名」作为合并主键；作者多人时排序 token 以兼容不同音源的拼接顺序。 */
    private fun StreamingTrack.crossSourceMergeKey(): String {
        val normalizedTitle = title.rankText()
        val normalizedArtist = artist.rankText()
            .split(' ')
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(" ")
        return "$normalizedArtist$normalizedTitle"
    }

    /** 作者名与曲名归一化相同，且时长缺失或落在 ±3 秒容差内时，判定为同一首歌。 */
    private fun StreamingTrack.isSameSongAcrossSource(other: StreamingTrack): Boolean {
        if (crossSourceMergeKey() != other.crossSourceMergeKey()) {
            return false
        }
        val left = durationMs
        val right = other.durationMs
        if (left == null || right == null || left <= 0L || right <= 0L) {
            return true
        }
        return kotlin.math.abs(left - right) <= CROSS_SOURCE_DURATION_TOLERANCE_MS
    }

    /** 选可播放的首项作代表，其余音源折叠为备用候选；代表项已有候选保留在前。 */
    private fun List<StreamingTrack>.mergeSourcesIntoRepresentative(): StreamingTrack {
        if (size == 1) {
            return first()
        }
        val representative = firstOrNull { it.playable } ?: first()
        val seen = linkedSetOf("${representative.provider.wireName}:${representative.providerTrackId}")
        val candidates = representative.playbackCandidates.toMutableList()
        forEach { track ->
            val identity = "${track.provider.wireName}:${track.providerTrackId}"
            if (!seen.add(identity)) {
                return@forEach
            }
            candidates += StreamingPlaybackCandidate(
                provider = track.provider,
                quality = null,
                label = track.provider.wireName,
                providerTrackId = track.providerTrackId,
                available = track.playable
            )
        }
        return representative.copy(playbackCandidates = candidates)
    }

    private fun StreamingSearchResult.trackOnlySearchResult(): StreamingSearchResult {
        val trackItems = unifiedItems
            .filter { it.type == StreamingMediaType.TRACK && it.track != null }
            .distinctBy { "${it.provider.wireName}:${it.id}" }
        val trackItemsByKey = trackItems.associateBy { "${it.provider.wireName}:${it.id}" }
        val normalizedTracks = (tracks + trackItems.mapNotNull { it.track })
            .distinctBy { "${it.provider.wireName}:${it.providerTrackId}" }
        val normalizedItems = normalizedTracks.map { track ->
            trackItemsByKey["${track.provider.wireName}:${track.providerTrackId}"]
                ?: StreamingSearchItem.fromTrack(track)
        }
        return copy(
            tracks = normalizedTracks,
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
            mvs = emptyList(),
            total = normalizedTracks.size,
            items = normalizedItems
        )
    }

    private fun List<StreamingTrack>.rankBySearchSimilarity(query: String): List<StreamingTrack> {
        val normalizedQuery = query.rankText()
        if (normalizedQuery.isBlank() || size <= 1) {
            return this
        }
        return withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<StreamingTrack>> { (_, track) ->
                    track.searchSimilarityScore(normalizedQuery)
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun List<StreamingSearchItem>.rankItemsBySearchSimilarity(query: String): List<StreamingSearchItem> {
        val normalizedQuery = query.rankText()
        if (normalizedQuery.isBlank() || size <= 1) {
            return this
        }
        return withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<StreamingSearchItem>> { (_, item) ->
                    item.track?.searchSimilarityScore(normalizedQuery) ?: 0
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun StreamingTrack.searchSimilarityScore(normalizedQuery: String): Int {
        val title = title.rankText()
        val artist = artist.rankText()
        val album = album.orEmpty().rankText()
        return similarityScore(normalizedQuery, title) * 6 +
            similarityScore(normalizedQuery, "$title $artist") * 3 +
            similarityScore(normalizedQuery, artist) * 2 +
            similarityScore(normalizedQuery, album)
    }

    private fun similarityScore(query: String, candidate: String): Int {
        if (query.isBlank() || candidate.isBlank()) {
            return 0
        }
        if (query == candidate) {
            return 1_000
        }
        if (candidate.startsWith(query)) {
            return 850
        }
        if (candidate.contains(query)) {
            return 720
        }
        val queryTokens = query.split(' ').filter { it.isNotBlank() }
        val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }
        val tokenHits = queryTokens.count { token ->
            candidateTokens.any { it == token || it.startsWith(token) || it.contains(token) }
        }
        val tokenScore = if (queryTokens.isNotEmpty()) tokenHits * 500 / queryTokens.size else 0
        val distance = levenshteinDistance(query, candidate)
        val maxLength = max(query.length, candidate.length).coerceAtLeast(1)
        val fuzzyScore = ((maxLength - distance).coerceAtLeast(0) * 360) / maxLength
        return max(tokenScore, fuzzyScore)
    }

    private fun levenshteinDistance(first: String, second: String): Int {
        if (first == second) {
            return 0
        }
        if (first.isEmpty()) {
            return second.length
        }
        if (second.isEmpty()) {
            return first.length
        }
        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)
        for (i in first.indices) {
            current[0] = i + 1
            for (j in second.indices) {
                val cost = if (first[i] == second[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[second.length]
    }

    private fun String.rankText(): String {
        return lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun refreshStreamingAuthState(provider: StreamingProviderName) {
        beginStreamingRequest()
        viewModelScope.launch {
            runCatching {
                streamingRepository.authState(provider)
            }.onSuccess { authState ->
                updateStreamingAuthState(provider, authState)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                if (!result.launchUrl.isNullOrBlank()) {
                    onLaunchReady?.invoke()
                }
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                refreshStreamingProviders().join()
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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
                refreshStreamingProviders().join()
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                if (result.state.connected) {
                    onAuthSuccess?.onResult(provider)
                }
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }
        }
    }

    fun resolveStreamingPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.resolvePlayback(provider, providerTrackId, quality)
            }.onSuccess { source ->
                updateStreamingPlaybackSource(source)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }
        }
    }

    fun resolveStreamingPlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: StreamingTrack? = null
    ): Job {
        beginStreamingRequest()
        return viewModelScope.launch {
            runCatching {
                streamingRepository.resolvePlaybackTrack(provider, providerTrackId, quality, metadata)
            }.onSuccess { result ->
                updateStreamingPlaybackTrack(result.source, result.track)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
            }
        }
    }

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
                updateStreamingDiagnostics(streamingRepository.diagnostics())
                onResolved.onResult(result.track)
            }.onFailure { error ->
                failStreamingRequest(error.message)
                updateStreamingDiagnostics(streamingRepository.diagnostics())
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

    fun preResolveStreamingQueueWindow(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        maxCount: Int = STREAMING_QUEUE_PRE_RESOLVE_LIMIT,
        onResolved: StreamingBiCallback<Long, Track?>
    ): Job? {
        if (snapshot == null || !snapshot.playing || queue.isNullOrEmpty() || maxCount <= 0) {
            return null
        }
        val targets = streamingQueuePreResolveTargets(snapshot, queue, maxCount)
        if (targets.isEmpty()) {
            return null
        }
        return viewModelScope.launch {
            targets.map { target ->
                async(ioDispatcher) {
                    val resolved = runCatching {
                        streamingRepository.resolvePlaybackTrack(
                            target.provider,
                            target.providerTrackId,
                            quality,
                            target.metadata
                        )
                    }.getOrNull()
                    target.oldTrackId to resolved
                }
            }.awaitAll().forEach { (oldTrackId, result) ->
                result?.let {
                    updateStreamingPlaybackTrack(it.source, it.track)
                    onResolved.onResult(oldTrackId, it.track)
                }
            }
            updateStreamingDiagnostics(streamingRepository.diagnostics())
        }
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

    private data class StreamingQueuePreResolveTarget(
        val oldTrackId: Long,
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val metadata: StreamingTrack?
    )

    private fun streamingQueuePreResolveTargets(
        snapshot: PlaybackStateSnapshot,
        queue: List<Track>,
        maxCount: Int
    ): List<StreamingQueuePreResolveTarget> {
        if (queue.size <= 2) {
            return emptyList()
        }
        val startIndex = (snapshot.currentIndex + 2).floorMod(queue.size)
        return (0 until queue.size)
            .asSequence()
            .map { offset -> (startIndex + offset).floorMod(queue.size) }
            .filter { index -> index != snapshot.currentIndex }
            .map { queue[it] }
            .filter { StreamingPlaybackAdapter.isUnresolvedStreamingTrack(it) }
            .distinctBy { it.dataPath }
            .mapNotNull { track ->
                val provider = StreamingPlaybackAdapter.streamingProviderName(track.dataPath) ?: return@mapNotNull null
                val providerTrackId = StreamingPlaybackAdapter.providerTrackId(track.dataPath)
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                StreamingQueuePreResolveTarget(
                    oldTrackId = track.id,
                    provider = provider,
                    providerTrackId = providerTrackId,
                    metadata = ResolveStreamingPlaybackUseCase().metadataFor(track, provider, providerTrackId)
                )
            }
            .take(maxCount)
            .toList()
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

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

    fun loadStreamingProviderTrackId(
        track: Track,
        provider: StreamingProviderName,
        onResolved: StreamingCallback<String>
    ): Job {
        return viewModelScope.launch {
            val providerTrackId = withContext(ioDispatcher) {
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
            withContext(ioDispatcher) {
                streamingTrackMatchStore?.saveProviderTrackId(track, provider, cleanTrackId)
            }
        }
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
            qualityDowngraded = text(languageMode, "streaming.quality.downgraded") + qualityLabel
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
    ): StreamingManualCookieDialogState {
        val unavailable = provider == null || provider in setOf(
            StreamingProviderName.MOCK,
            StreamingProviderName.M3U8,
            StreamingProviderName.PLUGIN
        )
        return StreamingManualCookieDialogState(
            provider = provider,
            unavailable = unavailable,
            title = text(languageMode, "streaming.manual.cookie"),
            hint = if (provider == StreamingProviderName.QQ_MUSIC) {
                text(languageMode, "streaming.cookie.hint.qq")
            } else {
                text(languageMode, "streaming.cookie.hint.default")
            },
            unavailableStatus = text(languageMode, "streaming.choose.login.provider")
        )
    }

    fun prepareManualCookieAuthRequest(
        provider: StreamingProviderName?,
        cookieHeader: String?,
        languageMode: String
    ): StreamingManualCookieAuthRequest? {
        val dialogState = prepareManualCookieDialogState(provider, languageMode)
        val cleanCookie = StreamingCookieHeaderParser.normalize(cookieHeader)
        val cleanProvider = dialogState.provider
        if (dialogState.unavailable || cleanProvider == null || cleanCookie.isEmpty()) {
            return null
        }
        return StreamingManualCookieAuthRequest(
            provider = cleanProvider,
            callbackUri = "$STREAMING_AUTH_REDIRECT_URI?provider=${cleanProvider.wireName}&manualCookie=1",
            cookieHeader = cleanCookie,
            emptyStatus = text(languageMode, "streaming.cookie.empty"),
            savedStatus = text(languageMode, "streaming.cookie.saved")
        )
    }

    fun manualCookieEmptyStatus(languageMode: String): String =
        text(languageMode, "streaming.cookie.empty")

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
                val tracks = if (link.providerPlaylistId.isNullOrBlank()) {
                    streamingRepository.userLikedTracks(link.provider)
                } else {
                    loadStreamingPlaylistTracks(link.provider, link.providerPlaylistId).second
                }
                val operations = streamingLocalPlaylistOperations
                    ?: error("Streaming local playlist operations are not bound")
                withContext(ioDispatcher) {
                    operations.syncStreamingPlaylist(link, tracks)
                }
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
                val operations = streamingLocalPlaylistOperations
                    ?: error("Streaming local playlist operations are not bound")
                withContext(ioDispatcher) {
                    operations.ensureStreamingLoginPlaylist(playlistName, provider)
                }
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
        val operations = streamingLocalPlaylistOperations
            ?: error("Streaming local playlist operations are not bound")
        val result = withContext(ioDispatcher) {
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
        val current = streamingState.value
        streamingState.value = current.copy(
            authStates = current.authStates + (provider to authState),
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

    private data class StreamingProviderRefresh(
        val providers: List<StreamingProviderDescriptor>,
        val capabilities: List<StreamingProviderCapability>,
        val health: List<StreamingProviderHealth>,
        val authStates: Map<StreamingProviderName, StreamingAuthState>
    )
}
