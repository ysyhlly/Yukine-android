package app.yukine.streaming

import android.content.Context
import android.util.Log
import app.yukine.model.Track
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.Locale

data class StreamingResolvedPlayback(
    val source: StreamingPlaybackSource,
    val track: Track,
    val resolutionPath: StreamingPlaybackResolutionPath = StreamingPlaybackResolutionPath.KNOWN_PROVIDER_ID
)

enum class StreamingPlaybackResolutionPath(val wireName: String) {
    URL_CACHE("url_cache"),
    KNOWN_PROVIDER_ID("known_provider_id"),
    TITLE_SEARCH("title_search")
}

class StreamingRepository(
    private val gateway: StreamingGateway,
    private val cache: StreamingCacheRepository? = null,
    private val playbackTrackAdapter: StreamingPlaybackTrackAdapter = HeaderBackedStreamingPlaybackTrackAdapter(),
    private val cachePolicy: StreamingCachePolicy = StreamingCachePolicy(),
    private val playbackSourcePolicy: PlaybackSourcePolicy = DefaultPlaybackSourcePolicy,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val playbackAttemptTimeoutMs: Long = DEFAULT_PLAYBACK_ATTEMPT_TIMEOUT_MS,
    private val titleSearchTimeoutMs: Long = DEFAULT_TITLE_SEARCH_TIMEOUT_MS,
    private val matchScoreV2ShadowEnabled: Boolean = true,
    private val playbackTop1V2Enabled: Boolean = true,
    private val playbackTelemetry: StreamingPlaybackTelemetry = NoOpStreamingPlaybackTelemetry
) {
    private val diagnosticsLock = Any()
    private val recentLogs = ArrayDeque<StreamingGatewayLogEntry>()
    private var totalRequests = 0
    private var cacheHits = 0

    fun diagnostics(): StreamingGatewayDiagnostics {
        return synchronized(diagnosticsLock) {
            StreamingGatewayDiagnostics(
                totalRequests = totalRequests,
                cacheHits = cacheHits,
                recentLogs = recentLogs.toList()
            )
        }
    }

    suspend fun providers(): List<StreamingProviderDescriptor> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("providers") {
                gateway.providers()
            }
        }
    }

    suspend fun providerCapabilities(): List<StreamingProviderCapability> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("capabilities") {
                gateway.providerCapabilities().map(::effectiveCapability)
            }
        }
    }

    fun playbackSourcePolicy(): PlaybackSourcePolicySnapshot = playbackSourcePolicy.snapshot()

    fun setPlaybackProviderEnabled(provider: StreamingProviderName, enabled: Boolean) {
        (playbackSourcePolicy as? MutablePlaybackSourcePolicy)?.setEnabled(provider, enabled)
    }

    fun setPlaybackProviderPriority(providers: List<StreamingProviderName>) {
        (playbackSourcePolicy as? MutablePlaybackSourcePolicy)?.setPriority(providers)
    }

    suspend fun providersHealth(): List<StreamingProviderHealth> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("health") {
                gateway.providersHealth()
            }
        }
    }

    suspend fun clearExpiredCache(): Int {
        return withContext(Dispatchers.IO) {
            cache?.clearExpired() ?: 0
        }
    }

    suspend fun search(
        provider: StreamingProviderName,
        query: String,
        mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
        page: Int = 1,
        pageSize: Int = 20,
        useCache: Boolean = true
    ): StreamingSearchResult {
        return withContext(Dispatchers.IO) {
            val request = StreamingSearchRequest(
                provider = provider,
                query = query,
                mediaTypes = mediaTypes,
                page = page,
                pageSize = pageSize
            )
            if (useCache) {
                cache?.cachedSearch(request)?.let { cached ->
                    recordCacheHit("search", provider)
                    return@withContext StreamingGatewayJson.searchResult(cached).copy(cached = true)
                }
            }
            recordGatewayCall("search", provider) {
                gateway.search(request).also { result ->
                    if (useCache || result.tracks.isNotEmpty() || result.unifiedItems.isNotEmpty()) {
                        cache?.saveSearch(
                            request,
                            StreamingGatewayJson.searchResultJson(result),
                            cachePolicy.ttlForSearch(request, result)
                        )
                    }
                }
            }
        }
    }

    suspend fun playlist(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        page: Int = 1,
        pageSize: Int = 500,
        useCache: Boolean = page == 1
    ): StreamingPlaylistDetail {
        return withContext(Dispatchers.IO) {
            if (useCache && page == 1) {
                cache?.cachedPlaylist(provider, providerPlaylistId)?.let { cached ->
                    recordCacheHit("playlist", provider)
                    return@withContext StreamingGatewayJson.playlistDetail(cached).copy(cached = true)
                }
            }
            val request = StreamingPlaylistRequest(
                provider = provider,
                providerPlaylistId = providerPlaylistId,
                page = page,
                pageSize = pageSize
            )
            recordGatewayCall("playlist", provider) {
                gateway.playlist(request).also { detail ->
                    if (useCache && request.page == 1) {
                        cache?.savePlaylist(
                            provider,
                            providerPlaylistId,
                            StreamingGatewayJson.playlistDetailJson(detail),
                            cachePolicy.ttlForPlaylist(provider, providerPlaylistId)
                        )
                    }
                }
            }
        }
    }

    suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("user_playlists", provider) {
                gateway.userPlaylists(provider)
            }
        }
    }

    suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("user_liked_tracks", provider) {
                gateway.userLikedTracks(provider)
            }
        }
    }

    suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("daily_recommendations", provider) {
                gateway.dailyRecommendations(provider)
            }
        }
    }

    suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("heartbeat_recommendations", request.provider) {
                gateway.heartbeatRecommendations(request)
            }
        }
    }

    suspend fun resolvePlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        luoxueMusicInfoJson: String? = null,
        forceRefresh: Boolean = false
    ): StreamingPlaybackSource {
        return resolvePlaybackSource(
            provider,
            providerTrackId,
            quality,
            luoxueMusicInfoJson,
            forceRefresh
        ).source
    }

    private suspend fun resolvePlaybackSource(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality,
        luoxueMusicInfoJson: String?,
        forceRefresh: Boolean
    ): ResolvedPlaybackSource {
        return withContext(Dispatchers.IO) {
            requireAudioResolveAllowed(provider)
            if (!forceRefresh) cache
                ?.takeIf { audioCacheAllowed(provider) }
                ?.cachedPlayback(provider, providerTrackId, quality, luoxueMusicInfoJson)
                ?.let { cached ->
                val source = runCatching { StreamingGatewayJson.playbackSource(cached) }.getOrNull()
                if (
                    source != null &&
                    StreamingAudioCapabilityPolicy.canUsePlaybackUrl(source.provider) &&
                    isSupportedPlaybackSourceUrl(source.url)
                ) {
                    recordCacheHit("playback", provider)
                    return@withContext ResolvedPlaybackSource(source, cacheHit = true)
                }
                logWarning("Ignoring invalid cached playback source for ${provider.wireName}:$providerTrackId")
            }
            val request = StreamingPlaybackRequest(
                provider = provider,
                providerTrackId = providerTrackId,
                quality = quality,
                luoxueMusicInfoJson = luoxueMusicInfoJson
            )
            val source = recordGatewayCall("playback", provider) {
                gateway.resolvePlayback(request).also { source ->
                    validatePlaybackSource(source)
                    val ttlMs = cachePolicy.ttlForPlayback(source, clockMs())
                    cache?.takeIf { audioCacheAllowed(provider) }?.savePlayback(
                        provider,
                        providerTrackId,
                        quality,
                        StreamingGatewayJson.playbackSourceJson(source),
                        ttlMs,
                        luoxueMusicInfoJson
                    )
                }
            }
            ResolvedPlaybackSource(source, cacheHit = false)
        }
    }

    suspend fun resolvePlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: StreamingTrack? = null,
        forceRefresh: Boolean = false
    ): StreamingResolvedPlayback {
        return withContext(Dispatchers.IO) {
            // 热路径只使用已经绑定的来源 ID。只有这些来源全部失效后，才按音源
            // 优先级逐个搜索，并使用统一录音匹配策略选择相似度最高的候选。
            val attempts = playbackResolveAttempts(provider, providerTrackId, metadata).toMutableList()
            var lastError: Throwable? = null
            val searchedProviders = linkedSetOf<StreamingProviderName>()
            var index = 0
            if (attempts.size >= 2) {
                val raced = raceKnownPlaybackAttempts(
                    attempts[0],
                    attempts[1],
                    quality,
                    forceRefresh
                )
                raced.source?.let {
                    return@withContext resolvedPlayback(raced, metadata)
                }
                lastError = raced.error
                index = 2
            }
            while (true) {
                if (index >= attempts.size) {
                    val fallback = playbackNextSearchFallbackAttempt(
                        metadata = metadata,
                        existingAttempts = attempts,
                        searchedProviders = searchedProviders
                    ) ?: break
                    attempts += fallback
                }
                val attempt = attempts[index]
                val outcome = resolvePlaybackAttempt(attempt, quality, forceRefresh)
                outcome.source?.let {
                    return@withContext resolvedPlayback(outcome, metadata)
                }
                lastError = outcome.error
                if (lastError != null) {
                    logWarning(
                        "Playback resolve failed for ${attempt.provider.wireName}:${attempt.providerTrackId}, " +
                            "trying next source",
                        lastError
                    )
                }
                index += 1
            }
            throw lastError ?: StreamingGatewayException(
                "No playable source is associated with ${provider.wireName}:$providerTrackId",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
    }

    private suspend fun raceKnownPlaybackAttempts(
        primary: PlaybackResolveAttempt,
        secondary: PlaybackResolveAttempt,
        quality: StreamingAudioQuality,
        forceRefresh: Boolean
    ): PlaybackAttemptOutcome = coroutineScope {
        val primaryResult = async {
            resolvePlaybackAttempt(primary, quality, forceRefresh)
        }
        val secondaryResult = async {
            delay(SECONDARY_SOURCE_STAGGER_MS)
            resolvePlaybackAttempt(secondary, quality, forceRefresh)
        }
        val first = select<Pair<Int, PlaybackAttemptOutcome>> {
            primaryResult.onAwait { 0 to it }
            secondaryResult.onAwait { 1 to it }
        }
        if (first.second.source != null) {
            if (first.first == 0) secondaryResult.cancel() else primaryResult.cancel()
            return@coroutineScope first.second
        }
        val second = if (first.first == 0) secondaryResult.await() else primaryResult.await()
        if (second.source != null) {
            if (first.first == 0) primaryResult.cancel() else secondaryResult.cancel()
        }
        second
    }

    private suspend fun resolvePlaybackAttempt(
        attempt: PlaybackResolveAttempt,
        quality: StreamingAudioQuality,
        forceRefresh: Boolean
    ): PlaybackAttemptOutcome {
        val started = clockMs()
        return try {
            val outcome = withTimeout(playbackAttemptTimeoutMs.coerceAtLeast(1L)) {
                resolvePlaybackAttemptWithinTimeout(attempt, quality, forceRefresh)
            }
            recordPlaybackTelemetry(
                StreamingPlaybackTelemetryEvent(
                    stage = StreamingPlaybackTelemetryStage.URL_RESOLVE,
                    provider = attempt.provider,
                    providerTrackId = attempt.providerTrackId,
                    resolutionPath = outcome.resolutionPath,
                    durationMs = elapsedSince(started),
                    success = outcome.source != null,
                    cacheHit = outcome.resolutionPath == StreamingPlaybackResolutionPath.URL_CACHE,
                    errorCode = streamingErrorCode(outcome.error)
                )
            )
            outcome
        } catch (timeout: TimeoutCancellationException) {
            val outcome = PlaybackAttemptOutcome(
                attempt = attempt,
                resolutionPath = attempt.resolutionPath(),
                error = StreamingGatewayException(
                    "Playback resolve timed out for ${attempt.provider.wireName}:${attempt.providerTrackId}",
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                    cause = timeout
                )
            )
            recordPlaybackTelemetry(
                StreamingPlaybackTelemetryEvent(
                    stage = StreamingPlaybackTelemetryStage.URL_RESOLVE,
                    provider = attempt.provider,
                    providerTrackId = attempt.providerTrackId,
                    resolutionPath = outcome.resolutionPath,
                    durationMs = elapsedSince(started),
                    success = false,
                    timedOut = true,
                    errorCode = StreamingErrorCode.SOURCE_UNAVAILABLE
                )
            )
            outcome
        } catch (cancelled: CancellationException) {
            recordPlaybackTelemetry(
                StreamingPlaybackTelemetryEvent(
                    stage = StreamingPlaybackTelemetryStage.URL_RESOLVE,
                    provider = attempt.provider,
                    providerTrackId = attempt.providerTrackId,
                    resolutionPath = attempt.resolutionPath(),
                    durationMs = elapsedSince(started),
                    success = false,
                    cancelled = true
                )
            )
            throw cancelled
        }
    }

    private suspend fun resolvePlaybackAttemptWithinTimeout(
        attempt: PlaybackResolveAttempt,
        quality: StreamingAudioQuality,
        forceRefresh: Boolean
    ): PlaybackAttemptOutcome {
        val initialResult = capturePlaybackResult {
            resolvePlaybackSource(
                attempt.provider,
                attempt.providerTrackId,
                quality,
                attempt.luoxueMusicInfoJson,
                forceRefresh
            ).also { validatePlaybackSource(it.source) }
        }
        val attemptResult = if (
            (initialResult.exceptionOrNull() as? StreamingGatewayException)?.code ==
                StreamingErrorCode.AUTH_REQUIRED
        ) {
            val refreshed = capturePlaybackResult {
                refreshAuthSession(attempt.provider, force = true)
            }.getOrNull()
            if (refreshed?.connected == true) {
                capturePlaybackResult {
                    resolvePlaybackSource(
                        attempt.provider,
                        attempt.providerTrackId,
                        quality,
                        attempt.luoxueMusicInfoJson,
                        true
                    ).also { validatePlaybackSource(it.source) }
                }
            } else {
                initialResult
            }
        } else {
            initialResult
        }
        val resolved = attemptResult.getOrNull()
        return PlaybackAttemptOutcome(
            attempt = attempt,
            source = resolved?.source,
            resolutionPath = when {
                resolved?.cacheHit == true -> StreamingPlaybackResolutionPath.URL_CACHE
                else -> attempt.resolutionPath()
            },
            error = attemptResult.exceptionOrNull()
        )
    }

    private suspend fun <T> capturePlaybackResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun resolvedPlayback(
        outcome: PlaybackAttemptOutcome,
        metadata: StreamingTrack?
    ): StreamingResolvedPlayback {
        val attempt = outcome.attempt
        val source = checkNotNull(outcome.source)
        val resolvedMetadata = metadata?.let { original ->
            if (original.provider == attempt.provider &&
                original.providerTrackId == attempt.providerTrackId) {
                original
            } else {
                original.copy(
                    provider = attempt.provider,
                    providerTrackId = attempt.providerTrackId,
                    luoxueMusicInfoJson = attempt.luoxueMusicInfoJson
                )
            }
        }
        return StreamingResolvedPlayback(
            source = source,
            track = playbackTrackAdapter.toTrack(source, resolvedMetadata),
            resolutionPath = outcome.resolutionPath
        )
    }

    /** Builds the single policy-controlled candidate chain used by every streaming entry point. */
    private fun playbackResolveAttempts(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?
    ): List<PlaybackResolveAttempt> {
        val primaryPayload = metadata
            ?.takeIf { it.provider == provider && it.providerTrackId == providerTrackId }
            ?.luoxueMusicInfoJson
        val attempts = mutableListOf<PlaybackResolveAttempt>()
        val seen = linkedSetOf<String>()
        if (provider == StreamingProviderName.LUOXUE && playbackSourcePolicy.isEnabled(provider)) {
            attempts += PlaybackResolveAttempt(provider, providerTrackId, primaryPayload)
            seen += "${provider.wireName}:$providerTrackId"
        }
        val priority = playbackSourcePolicy.orderedEnabledProviders().withIndex().associate { it.value to it.index }
        val candidates = metadata?.playbackCandidates.orEmpty()
            .filterNot { StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(it.provider) }
            .filter { playbackSourcePolicy.isEnabled(it.provider) }
            .sortedBy { candidate -> priority[candidate.provider] ?: Int.MAX_VALUE }
        candidates.forEach { candidate ->
            val candidateTrackId = candidate.providerTrackId?.takeIf { it.isNotBlank() }
                ?: providerTrackId.takeIf { candidate.provider == provider }
                ?: return@forEach
            if (!candidate.available) {
                return@forEach
            }
            if (seen.add("${candidate.provider.wireName}:$candidateTrackId")) {
                val candidatePayload = candidate.luoxueMusicInfoJson ?: metadata
                    ?.takeIf {
                        it.provider == candidate.provider &&
                            it.providerTrackId == candidateTrackId
                    }
                    ?.luoxueMusicInfoJson
                attempts += PlaybackResolveAttempt(candidate.provider, candidateTrackId, candidatePayload)
            }
        }
        if (playbackSourcePolicy.isEnabled(provider) &&
            !StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(provider) &&
            seen.add("${provider.wireName}:$providerTrackId")) {
            attempts += PlaybackResolveAttempt(provider, providerTrackId, primaryPayload)
        }
        return attempts
    }

    /**
     * 已知来源全部失败后才搜索。每次只搜索一个平台，并复用曲库合并使用的录音匹配
     * 标准，按相似度降序直接选择第一名。
     */
    private suspend fun playbackNextSearchFallbackAttempt(
        metadata: StreamingTrack?,
        existingAttempts: List<PlaybackResolveAttempt>,
        searchedProviders: MutableSet<StreamingProviderName>
    ): PlaybackResolveAttempt? {
        val track = metadata ?: return null
        val query = track.title.trim()
        if (query.isBlank()) {
            return null
        }
        val seen = existingAttempts
            .mapTo(linkedSetOf()) { "${it.provider.wireName}:${it.providerTrackId}" }
        val providers = playbackSourcePolicy.orderedEnabledProviders().filter { target ->
            target !in searchedProviders &&
                !StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(target)
        }
        providers.forEach { target ->
            searchedProviders += target
            val searchStarted = clockMs()
            val searchResult = try {
                val result = withTimeout(titleSearchTimeoutMs.coerceAtLeast(1L)) {
                    search(target, query, setOf(StreamingMediaType.TRACK), pageSize = 5)
                }
                val candidates = result.tracks
                    .filter { candidate ->
                        candidate.provider == target && candidate.providerTrackId.isNotBlank()
                    }
                recordPlaybackTelemetry(
                    StreamingPlaybackTelemetryEvent(
                        stage = StreamingPlaybackTelemetryStage.TITLE_SEARCH,
                        provider = target,
                        durationMs = elapsedSince(searchStarted),
                        success = true,
                        cacheHit = result.cached,
                        candidateCount = candidates.size
                    )
                )
                candidates
            } catch (timeout: TimeoutCancellationException) {
                recordPlaybackTelemetry(
                    StreamingPlaybackTelemetryEvent(
                        stage = StreamingPlaybackTelemetryStage.TITLE_SEARCH,
                        provider = target,
                        durationMs = elapsedSince(searchStarted),
                        success = false,
                        timedOut = true,
                        errorCode = StreamingErrorCode.SOURCE_UNAVAILABLE
                    )
                )
                logWarning(
                    "Title search timed out for ${target.wireName} (queryLength=${query.length})",
                    timeout
                )
                null
            } catch (cancelled: CancellationException) {
                recordPlaybackTelemetry(
                    StreamingPlaybackTelemetryEvent(
                        stage = StreamingPlaybackTelemetryStage.TITLE_SEARCH,
                        provider = target,
                        durationMs = elapsedSince(searchStarted),
                        success = false,
                        cancelled = true
                    )
                )
                throw cancelled
            } catch (error: Throwable) {
                recordPlaybackTelemetry(
                    StreamingPlaybackTelemetryEvent(
                        stage = StreamingPlaybackTelemetryStage.TITLE_SEARCH,
                        provider = target,
                        durationMs = elapsedSince(searchStarted),
                        success = false,
                        errorCode = streamingErrorCode(error)
                    )
                )
                logWarning(
                    "Title search failed for ${target.wireName} (queryLength=${query.length})",
                    error
                )
                null
            }
            val candidates = searchResult ?: return@forEach
            val rankStarted = clockMs()
            val reference = StreamingTrackMatchPolicy.reference(track)
            val rankedV2 = StreamingTrackMatchPolicy.rankCandidates(reference, candidates)
            val match = if (playbackTop1V2Enabled) {
                rankedV2.firstOrNull()?.track
            } else {
                StreamingTrackMatchPolicy.pickBestCandidateV1(reference, candidates)
            }
            recordPlaybackTelemetry(
                StreamingPlaybackTelemetryEvent(
                    stage = StreamingPlaybackTelemetryStage.CANDIDATE_RANK,
                    provider = target,
                    providerTrackId = match?.providerTrackId.orEmpty(),
                    durationMs = elapsedSince(rankStarted),
                    success = match != null,
                    candidateCount = candidates.size
                )
            )
            if (matchScoreV2ShadowEnabled) {
                recordMatchShadow(
                    target,
                    rankStarted,
                    candidates,
                    reference,
                    rankedV2
                )
            }
            if (match != null && seen.add("${match.provider.wireName}:${match.providerTrackId}")) {
                return PlaybackResolveAttempt(
                    match.provider,
                    match.providerTrackId,
                    match.luoxueMusicInfoJson,
                    fromTitleSearch = true
                )
            }
        }
        return null
    }

    private fun recordMatchShadow(
        provider: StreamingProviderName,
        started: Long,
        candidates: List<StreamingTrack>,
        reference: StreamingTrackMatchPolicy.Reference,
        rankedV2: List<StreamingTrackMatchPolicy.CandidateMatch>
    ) {
        val v1 = StreamingTrackMatchPolicy.pickBestCandidateV1(reference, candidates)
        val top = rankedV2.firstOrNull()
        val ended = clockMs()
        val message = buildString {
            append("scoreVersion=").append(RecordingMatchEvaluatorV2.SCORE_VERSION)
            append(",candidates=").append(candidates.size)
            append(",v1Top1=").append(v1?.providerTrackId.orEmpty())
            append(",v2Top1=").append(top?.track?.providerTrackId.orEmpty())
            append(",changed=").append(v1?.providerTrackId != top?.track?.providerTrackId)
            append(",topScore=").append(
                String.format(Locale.ROOT, "%.3f", top?.evaluation?.sameRecordingProbability ?: 0.0)
            )
            append(",sameWork=").append(
                String.format(Locale.ROOT, "%.3f", top?.evaluation?.sameWorkProbability ?: 0.0)
            )
            append(",relationship=").append(top?.evaluation?.relationship?.name.orEmpty())
            append(",hardConflicts=").append(
                top?.evaluation?.hardConflicts?.joinToString("|") { it.name }.orEmpty()
            )
        }
        synchronized(diagnosticsLock) {
            addLogLocked(
                StreamingGatewayLogEntry(
                    operation = "playback_match_shadow_v2",
                    provider = provider,
                    durationMs = (ended - started).coerceAtLeast(0L),
                    message = message,
                    timestampMs = ended
                )
            )
        }
        logDebug("Playback match shadow ${provider.wireName}: $message")
    }

    suspend fun setUserTrackFavorite(provider: StreamingProviderName, providerTrackId: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            recordGatewayCall("favorite_write", provider) {
                gateway.setUserTrackFavorite(provider, providerTrackId, favorite)
            }
        }
    }

    suspend fun createUserPlaylist(provider: StreamingProviderName, title: String): StreamingPlaylist =
        withContext(Dispatchers.IO) {
            recordGatewayCall("playlist_create", provider) { gateway.createUserPlaylist(provider, title) }
        }

    suspend fun renameUserPlaylist(provider: StreamingProviderName, id: String, title: String) {
        withContext(Dispatchers.IO) {
            recordGatewayCall("playlist_rename", provider) { gateway.renameUserPlaylist(provider, id, title) }
        }
    }

    suspend fun deleteUserPlaylist(provider: StreamingProviderName, id: String) {
        withContext(Dispatchers.IO) {
            recordGatewayCall("playlist_delete", provider) { gateway.deleteUserPlaylist(provider, id) }
        }
    }

    suspend fun mutateUserPlaylistTracks(
        provider: StreamingProviderName,
        id: String,
        trackIds: List<String>,
        add: Boolean
    ) {
        withContext(Dispatchers.IO) {
            recordGatewayCall("playlist_tracks", provider) {
                gateway.mutateUserPlaylistTracks(provider, id, trackIds, add)
            }
        }
    }

    suspend fun reorderUserPlaylistTracks(provider: StreamingProviderName, id: String, trackIds: List<String>) {
        withContext(Dispatchers.IO) {
            recordGatewayCall("playlist_reorder", provider) {
                gateway.reorderUserPlaylistTracks(provider, id, trackIds)
            }
        }
    }

    private suspend fun audioCacheAllowed(provider: StreamingProviderName): Boolean {
        if (!playbackSourcePolicy.isEnabled(provider) ||
            StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(provider)) return false
        return runCatching {
            providerCapabilities().firstOrNull { it.provider == provider }?.supportsAudioCache
        }.getOrNull() != false
    }

    private fun requireAudioResolveAllowed(provider: StreamingProviderName) {
        if (StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(provider) ||
            !playbackSourcePolicy.isEnabled(provider)) {
            throw StreamingGatewayException(
                "${provider.wireName} is metadata-only; audio resolve and playback are disabled",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
    }

    private fun effectiveCapability(value: StreamingProviderCapability): StreamingProviderCapability {
        val enabledForPlayback = playbackSourcePolicy.isEnabled(value.provider) &&
            !StreamingAudioCapabilityPolicy.isPermanentlyMetadataOnly(value.provider)
        return value.copy(
            supportsPlayback = value.supportsPlayback && enabledForPlayback,
            supportsAudioResolve = value.supportsAudioResolve && enabledForPlayback,
            supportsAudioFallback = value.supportsAudioFallback && enabledForPlayback,
            supportsAudioDownload = value.supportsAudioDownload && enabledForPlayback,
            supportsAudioCache = value.supportsAudioCache && enabledForPlayback
        )
    }

    private data class PlaybackResolveAttempt(
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val luoxueMusicInfoJson: String?,
        val fromTitleSearch: Boolean = false
    ) {
        fun resolutionPath(): StreamingPlaybackResolutionPath = if (fromTitleSearch) {
            StreamingPlaybackResolutionPath.TITLE_SEARCH
        } else {
            StreamingPlaybackResolutionPath.KNOWN_PROVIDER_ID
        }
    }

    private data class ResolvedPlaybackSource(
        val source: StreamingPlaybackSource,
        val cacheHit: Boolean
    )

    private data class PlaybackAttemptOutcome(
        val attempt: PlaybackResolveAttempt,
        val source: StreamingPlaybackSource? = null,
        val resolutionPath: StreamingPlaybackResolutionPath = StreamingPlaybackResolutionPath.KNOWN_PROVIDER_ID,
        val error: Throwable? = null
    )

    suspend fun authState(provider: StreamingProviderName): StreamingAuthState {
        return withContext(Dispatchers.IO) {
            cache?.cachedAuth(provider)?.let { cached ->
                val cachedState = StreamingGatewayJson.authState(cached, provider)
                if (cachedState.connected) {
                    recordCacheHit("auth", provider)
                    return@withContext cachedState
                }
            }
            recordGatewayCall("auth", provider) {
                gateway.authState(provider).also { authState ->
                    cache?.saveAuth(
                        provider,
                        StreamingGatewayJson.authStateJson(provider, authState),
                        cachePolicy.ttlForAuth(provider, authState)
                    )
                }
            }
        }
    }

    /** Explicitly bypasses the 24-hour auth metadata cache for verification/automatic renewal. */
    suspend fun refreshAuthSession(
        provider: StreamingProviderName,
        force: Boolean = false
    ): StreamingAuthState {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("auth_refresh", provider) {
                gateway.refreshAuthSession(provider, force).also { authState ->
                    val ttl = if (authState.credentialState == StreamingCredentialState.INVALID || !authState.connected) {
                        cachePolicy.ttlForSignedOutAuth()
                    } else {
                        cachePolicy.ttlForAuth(provider, authState)
                    }
                    cache?.saveAuth(
                        provider,
                        StreamingGatewayJson.authStateJson(provider, authState),
                        ttl
                    )
                }
            }
        }
    }

    suspend fun startAuth(
        provider: StreamingProviderName,
        redirectUri: String? = null,
        forceRefresh: Boolean = false
    ): StreamingAuthResult {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("auth_start", provider) {
                gateway.startAuth(
                    StreamingAuthRequest(
                        provider = provider,
                        redirectUri = redirectUri,
                        forceRefresh = forceRefresh
                    )
                ).also { result ->
                    cache?.saveAuth(
                        provider,
                        StreamingGatewayJson.authStateJson(provider, result.state),
                        cachePolicy.ttlForAuth(provider, result.state)
                    )
                }
            }
        }
    }

    suspend fun completeAuth(provider: StreamingProviderName, callbackUri: String, cookieHeader: String? = null): StreamingAuthResult {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("auth_complete", provider) {
                gateway.completeAuth(provider, callbackUri, cookieHeader).also { result ->
                    cache?.saveAuth(
                        provider,
                        StreamingGatewayJson.authStateJson(provider, result.state),
                        cachePolicy.ttlForAuth(provider, result.state)
                    )
                }
            }
        }
    }

    suspend fun signOut(provider: StreamingProviderName): StreamingAuthState {
        return withContext(Dispatchers.IO) {
            recordGatewayCall("auth_sign_out", provider) {
                gateway.signOut(provider).also { authState ->
                    cache?.saveAuth(
                        provider,
                        StreamingGatewayJson.authStateJson(provider, authState),
                        cachePolicy.ttlForSignedOutAuth()
                    )
                }
            }
        }
    }

    private fun recordCacheHit(operation: String, provider: StreamingProviderName?) {
        val now = clockMs()
        synchronized(diagnosticsLock) {
            totalRequests += 1
            cacheHits += 1
            addLogLocked(
                StreamingGatewayLogEntry(
                    operation = operation,
                    provider = provider,
                    durationMs = 0L,
                    cacheHit = true,
                    timestampMs = now
                )
            )
        }
    }

    private suspend fun <T> recordGatewayCall(
        operation: String,
        provider: StreamingProviderName? = null,
        block: suspend () -> T
    ): T {
        val started = clockMs()
        return try {
            block().also {
                recordGatewayResult(
                    operation = operation,
                    provider = provider,
                    started = started,
                    error = null
                )
            }
        } catch (error: Exception) {
            recordGatewayResult(
                operation = operation,
                provider = provider,
                started = started,
                error = error
            )
            throw error
        }
    }

    private fun recordGatewayResult(
        operation: String,
        provider: StreamingProviderName?,
        started: Long,
        error: Exception?
    ) {
        val ended = clockMs()
        val gatewayError = error as? StreamingGatewayException
        synchronized(diagnosticsLock) {
            totalRequests += 1
            addLogLocked(
                StreamingGatewayLogEntry(
                    operation = operation,
                    provider = provider,
                    durationMs = (ended - started).coerceAtLeast(0L),
                    cacheHit = false,
                    errorCode = gatewayError?.code ?: error?.let { StreamingErrorCode.UNKNOWN },
                    message = error?.message,
                    timestampMs = ended
                )
            )
        }
    }

    private fun addLogLocked(entry: StreamingGatewayLogEntry) {
        recentLogs.addFirst(entry)
        while (recentLogs.size > MAX_RECENT_LOGS) {
            recentLogs.removeLast()
        }
    }

    private fun validatePlaybackSource(source: StreamingPlaybackSource) {
        val cleanUrl = source.url.trim()
        if (cleanUrl.isBlank()) {
            throw StreamingGatewayException(
                "Playback source is empty for ${source.provider.wireName}:${source.providerTrackId}",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
        val uri = runCatching { URI(cleanUrl) }.getOrElse {
            throw StreamingGatewayException(
                "Playback source URL is invalid for ${source.provider.wireName}:${source.providerTrackId}",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                cause = it
            )
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        val supported = when (scheme) {
            "http", "https" -> !uri.host.isNullOrBlank()
            "content", "file", "android.resource" -> true
            else -> false
        }
        if (!supported) {
            throw StreamingGatewayException(
                "Playback source scheme is unsupported for ${source.provider.wireName}:${source.providerTrackId}: $scheme",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarning(message: String, error: Throwable? = null) {
        runCatching {
            if (error == null) {
                Log.w(TAG, message)
            } else {
                Log.w(TAG, message, error)
            }
        }
    }

    private fun recordPlaybackTelemetry(event: StreamingPlaybackTelemetryEvent) {
        runCatching { playbackTelemetry.record(event) }
    }

    private fun elapsedSince(started: Long): Long = (clockMs() - started).coerceAtLeast(0L)

    private fun streamingErrorCode(error: Throwable?): StreamingErrorCode? = when (error) {
        null -> null
        is StreamingGatewayException -> error.code
        else -> StreamingErrorCode.UNKNOWN
    }

    private companion object {
        const val MAX_RECENT_LOGS = 20
        const val SECONDARY_SOURCE_STAGGER_MS = 400L
        const val DEFAULT_PLAYBACK_ATTEMPT_TIMEOUT_MS = 2_500L
        const val DEFAULT_TITLE_SEARCH_TIMEOUT_MS = 2_000L
        const val TAG = "StreamingRepository"
    }
}

object StreamingRepositoryFactory {
    fun empty(): StreamingRepository {
        return StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(MockStreamingProvider()))),
            playbackTrackAdapter = HeaderBackedStreamingPlaybackTrackAdapter(),
            cachePolicy = StreamingCachePolicy()
        )
    }

    fun remote(endpointBaseUrl: String): StreamingRepository {
        return StreamingRepository(
            RemoteStreamingGateway(endpointBaseUrl),
            playbackTrackAdapter = HeaderBackedStreamingPlaybackTrackAdapter(),
            cachePolicy = StreamingCachePolicy()
        )
    }

    fun remote(context: Context, endpointBaseUrl: String): StreamingRepository {
        return StreamingRepository(
            RemoteStreamingGateway(
                endpointBaseUrl = endpointBaseUrl,
                localAuthStore = LocalStreamingAuthStore(context)
            ),
            StreamingCacheRepository.create(context),
            HeaderBackedStreamingPlaybackTrackAdapter(),
            StreamingCachePolicy()
        )
    }
}
