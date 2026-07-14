package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.*
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.max

private const val CROSS_SOURCE_DURATION_TOLERANCE_MS = 3_000L
private val CROSS_SOURCE_ARTIST_JOINERS = setOf("and", "feat", "featuring", "ft", "with", "和", "与")

/** Owns streaming search requests, pagination, aggregation and match ranking. */
class StreamingSearchStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val stateOwner: StreamingFeatureStateOwner,
    private val repository: () -> StreamingRepository
) {
    fun updateStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) {
        stateOwner.value = stateOwner.value.copy(
            searchChromeLabels = labels,
            searchChromeActions = actions
        )
    }

    fun updateStreamingSearchQuery(query: String) {
        stateOwner.value = stateOwner.value.copy(searchQuery = query)
    }

    fun clearStreamingSearchSession() {
        stateOwner.value = stateOwner.value.copy(
            searchQuery = "",
            searchResult = null,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun beginStreamingRequest() {
        stateOwner.value = stateOwner.value.copy(
            loading = true,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun beginStreamingNextPageRequest() {
        stateOwner.value = stateOwner.value.copy(
            loadingMore = true,
            errorMessage = null
        )
    }

    fun updateStreamingSearchResult(result: StreamingSearchResult) {
        val trackOnlyResult = result.trackOnlySearchResult()
        stateOwner.value = stateOwner.value.copy(
            selectedProvider = trackOnlyResult.provider,
            searchQuery = trackOnlyResult.query,
            searchResult = trackOnlyResult,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun appendStreamingSearchResult(result: StreamingSearchResult) {
        val current = stateOwner.value
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
        stateOwner.value = current.copy(
            selectedProvider = merged.provider,
            searchQuery = merged.query,
            searchResult = merged,
            loading = false,
            loadingMore = false,
            errorMessage = null
        )
    }

    fun searchStreaming(
        provider: StreamingProviderName = stateOwner.value.selectedProvider,
        query: String = stateOwner.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        page: Int = 1,
        pageSize: Int = 20
    ): Job {
        val normalizedMediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) }
        stateOwner.value = stateOwner.value.copy(
            searchQuery = query,
            searchMediaTypes = normalizedMediaTypes
        )
        beginStreamingRequest()
        return scope.launch {
            runCatching {
                repository().search(provider, query, normalizedMediaTypes, page, pageSize)
            }.onSuccess { result ->
                updateStreamingSearchResult(result)
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
            }
        }
    }

    fun searchAllStreaming(
        query: String = stateOwner.value.searchQuery,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        pageSize: Int = 12
    ): Job {
        val normalizedMediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) }
        stateOwner.value = stateOwner.value.copy(
            searchQuery = query,
            searchMediaTypes = normalizedMediaTypes
        )
        beginStreamingRequest()
        return scope.launch {
            val current = stateOwner.value
            val searchableProviders = current.providers
                .filter { provider ->
                    provider.name != StreamingProviderName.MOCK &&
                        (current.providerCapabilities.firstOrNull { it.provider == provider.name }?.supportsSearch
                            ?: app.yukine.streaming.StreamingCapabilityResolver.canSearch(provider))
                }
                .map { it.name }
                .distinct()
            if (searchableProviders.isEmpty()) {
                failRequest("当前没有可搜索的在线音源")
                updateDiagnostics(repository().diagnostics())
                return@launch
            }
            val results = searchableProviders.map { provider ->
                async {
                    runCatching {
                        repository().search(
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
                failRequest(message)
                updateDiagnostics(repository().diagnostics())
                return@launch
            }
            val merged = mergeStreamingSearchResults(query, pageSize, successes)
                .trackOnlySearchResult()
                .mergeCrossSourceDuplicates()
            stateOwner.value = stateOwner.value.copy(
                selectedProvider = successes.firstOrNull { it.tracks.isNotEmpty() }?.provider
                    ?: current.selectedProvider,
                searchQuery = query,
                searchResult = merged,
                loading = false,
                loadingMore = false,
                errorMessage = null,
                diagnostics = repository().diagnostics()
            )
        }
    }

    fun searchNextStreamingPage() {
        val current = stateOwner.value
        val result = current.searchResult ?: return
        if (!result.hasMore || current.loading || current.loadingMore) {
            return
        }
        beginStreamingNextPageRequest()
        scope.launch {
            runCatching {
                repository().search(
                    result.provider,
                    result.query,
                    current.searchMediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
                    result.page + 1,
                    result.pageSize
                )
            }.onSuccess { nextResult ->
                appendStreamingSearchResult(nextResult)
                updateDiagnostics(repository().diagnostics())
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
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
            stateOwner.value = stateOwner.value.copy(
                loading = false,
                errorMessage = null
            )
            return scope.launch {
                onResolved.onResult(null)
            }
        }
        beginStreamingRequest()
        return scope.launch {
            runCatching {
                val result = repository().search(
                    provider = provider,
                    query = query,
                    mediaTypes = setOf(StreamingMediaType.TRACK),
                    page = 1,
                    pageSize = 5,
                    useCache = false
                )
                StreamingTrackMatchPolicy.pickBestCandidate(localTrack, result.tracks)
            }.onSuccess { track ->
                stateOwner.value = stateOwner.value.copy(
                    loading = false,
                    errorMessage = null,
                    diagnostics = repository().diagnostics()
                )
                onResolved.onResult(track)
            }.onFailure { error ->
                failRequest(error.message)
                updateDiagnostics(repository().diagnostics())
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
        // 保持首次出现顺序，并允许同名歌曲按时长形成多个独立版本簇。
        val clusters = mutableListOf<MutableList<StreamingTrack>>()
        tracks.forEach { track ->
            val sourceFamily = track.crossSourceFamilyKey()
            val cluster = clusters.firstOrNull { group ->
                group.first().isSameSongAcrossSource(track) &&
                    group.none { existing -> existing.crossSourceFamilyKey() == sourceFamily }
            }
            if (cluster != null) {
                cluster += track
            } else {
                clusters += mutableListOf(track)
            }
        }
        val mergedTracks = clusters
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
        val artistValues = artists
            .map { it.name }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(artist) }
        val normalizedArtist = artistValues
            .flatMap { value -> value.rankText().split(' ') }
            .filter { it.isNotBlank() }
            .filterNot { it in CROSS_SOURCE_ARTIST_JOINERS }
            .distinct()
            .sorted()
            .joinToString(" ")
        return "$normalizedArtist\u0001$normalizedTitle"
    }

    /** 普通 provider 本身就是音源族；LX/插件型 provider 再按曲目 ID 前缀区分子音源。 */
    private fun StreamingTrack.crossSourceFamilyKey(): String {
        if (provider != StreamingProviderName.LUOXUE && provider != StreamingProviderName.PLUGIN) {
            return provider.wireName
        }
        val sourcePrefix = providerTrackId.substringBefore(':', "").trim().lowercase()
        return if (sourcePrefix.isBlank()) provider.wireName else "${provider.wireName}:$sourcePrefix"
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

    /** 优先选真正支持播放的代表项，并完整保留每个合并项已有的音质/子音源候选。 */
    private fun List<StreamingTrack>.mergeSourcesIntoRepresentative(): StreamingTrack {
        if (size == 1) {
            return first()
        }
        val representative = firstOrNull { track ->
            track.playable && providerSupportsPlayback(track.provider)
        } ?: firstOrNull { it.playable } ?: first()
        val candidates = mutableListOf<StreamingPlaybackCandidate>()
        val seenCandidates = linkedSetOf<String>()

        fun addCandidate(candidate: StreamingPlaybackCandidate, sourceTrack: StreamingTrack) {
            val providerTrackId = candidate.providerTrackId?.trim()?.takeIf { it.isNotBlank() }
                ?: sourceTrack.providerTrackId.takeIf { candidate.provider == sourceTrack.provider }
                ?: return
            val normalized = candidate.copy(
                providerTrackId = providerTrackId,
                available = candidate.available && providerSupportsPlayback(candidate.provider),
                luoxueMusicInfoJson = candidate.luoxueMusicInfoJson
                    ?: sourceTrack.luoxueMusicInfoJson.takeIf {
                        candidate.provider == sourceTrack.provider &&
                            providerTrackId == sourceTrack.providerTrackId
                    }
            )
            val identity = "${normalized.provider.wireName}:$providerTrackId:${normalized.quality?.wireName.orEmpty()}"
            if (seenCandidates.add(identity)) {
                candidates += normalized
            }
        }

        val orderedTracks = listOf(representative) + filterNot { it === representative }
        orderedTracks.forEach { track ->
            if (track !== representative) {
                addCandidate(
                    StreamingPlaybackCandidate(
                        provider = track.provider,
                        quality = null,
                        label = track.provider.wireName,
                        providerTrackId = track.providerTrackId,
                        available = track.playable,
                        luoxueMusicInfoJson = track.luoxueMusicInfoJson
                    ),
                    track
                )
            }
            track.playbackCandidates.forEach { candidate -> addCandidate(candidate, track) }
        }
        return representative.copy(
            qualities = flatMap { it.qualities }.toSet(),
            lyricSources = flatMap { it.lyricSources }.distinctBy { source ->
                "${source.provider.wireName}:${source.providerTrackId.orEmpty()}:${source.name}"
            },
            playbackCandidates = candidates
        )
    }

    private fun providerSupportsPlayback(provider: StreamingProviderName): Boolean {
        val state = stateOwner.value
        state.providerCapabilities.firstOrNull { it.provider == provider }?.let { capability ->
            return capability.supportsPlayback
        }
        val descriptor = state.providers.firstOrNull { it.name == provider }
        return descriptor?.let(app.yukine.streaming.StreamingCapabilityResolver::canPlayback) ?: true
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


    fun failRequest(message: String?) {
        stateOwner.value = stateOwner.value.copy(
            loading = false,
            loadingMore = false,
            errorMessage = message ?: "Streaming request failed"
        )
    }

    private fun updateDiagnostics(diagnostics: StreamingGatewayDiagnostics) {
        stateOwner.value = stateOwner.value.copy(diagnostics = diagnostics)
    }
}
