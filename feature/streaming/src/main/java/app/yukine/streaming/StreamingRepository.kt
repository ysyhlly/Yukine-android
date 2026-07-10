package app.yukine.streaming

import android.content.Context
import android.util.Log
import app.yukine.model.Track
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

data class StreamingResolvedPlayback(
    val source: StreamingPlaybackSource,
    val track: Track
)

class StreamingRepository(
    private val gateway: StreamingGateway,
    private val cache: StreamingCacheRepository? = null,
    private val playbackTrackAdapter: StreamingPlaybackTrackAdapter = HeaderBackedStreamingPlaybackTrackAdapter(),
    private val cachePolicy: StreamingCachePolicy = StreamingCachePolicy(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
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
                gateway.providerCapabilities()
            }
        }
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
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ): StreamingPlaybackSource {
        return withContext(Dispatchers.IO) {
            cache?.cachedPlayback(provider, providerTrackId, quality)?.let { cached ->
                val source = runCatching { StreamingGatewayJson.playbackSource(cached) }.getOrNull()
                if (source != null && isSupportedPlaybackSourceUrl(source.url)) {
                    recordCacheHit("playback", provider)
                    return@withContext source
                }
                logWarning("Ignoring invalid cached playback source for ${provider.wireName}:$providerTrackId")
            }
            val request = StreamingPlaybackRequest(
                provider = provider,
                providerTrackId = providerTrackId,
                quality = quality
            )
            recordGatewayCall("playback", provider) {
                gateway.resolvePlayback(request).also { source ->
                    validatePlaybackSource(source)
                    val ttlMs = cachePolicy.ttlForPlayback(source, clockMs())
                    cache?.savePlayback(
                        provider,
                        providerTrackId,
                        quality,
                        StreamingGatewayJson.playbackSourceJson(source),
                        ttlMs
                    )
                }
            }
        }
    }

    suspend fun resolvePlaybackTrack(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS,
        metadata: StreamingTrack? = null
    ): StreamingResolvedPlayback {
        return withContext(Dispatchers.IO) {
            val attempts = playbackResolveAttempts(provider, providerTrackId, metadata)
            var lastError: Throwable? = null
            attempts.forEachIndexed { index, attempt ->
                val initialResult = runCatching {
                    val source = resolvePlayback(attempt.provider, attempt.providerTrackId, quality)
                    validatePlaybackSource(source)
                    source
                }
                val attemptResult = if (
                    (initialResult.exceptionOrNull() as? StreamingGatewayException)?.code ==
                        StreamingErrorCode.AUTH_REQUIRED
                ) {
                    // An expired local Cookie can be repaired once without moving to a different
                    // provider. Never retry region/membership/source errors: they are not auth
                    // refresh problems and would only create duplicate network traffic.
                    val refreshed = runCatching {
                        refreshAuthSession(attempt.provider, force = true)
                    }.getOrNull()
                    if (refreshed?.connected == true) {
                        runCatching {
                            val source = resolvePlayback(attempt.provider, attempt.providerTrackId, quality)
                            validatePlaybackSource(source)
                            source
                        }
                    } else {
                        initialResult
                    }
                } else {
                    initialResult
                }
                attemptResult.getOrNull()?.let { source ->
                    // 命中备用音源时，元数据沿用代表项但替换为实际播放的音源标识，
                    // 让生成的 Track.dataPath 指向真正解析成功的音源。
                    val resolvedMetadata = if (index == 0 || metadata == null) {
                        metadata
                    } else {
                        metadata.copy(provider = attempt.provider, providerTrackId = attempt.providerTrackId)
                    }
                    return@withContext StreamingResolvedPlayback(
                        source = source,
                        track = playbackTrackAdapter.toTrack(source, resolvedMetadata)
                    )
                }
                lastError = attemptResult.exceptionOrNull()
                if (lastError != null && index < attempts.lastIndex) {
                    logWarning(
                        "Playback resolve failed for ${attempt.provider.wireName}:${attempt.providerTrackId}, " +
                            "trying next source",
                        lastError
                    )
                }
            }
            throw lastError ?: IllegalStateException("No playback source resolved")
        }
    }

    /**
     * 主音源排在第一位，其余来自 [StreamingTrack.playbackCandidates] 的跨音源候选按原顺序追加，
     * 用于主音源解析失败时自动回退。按「音源 + 曲目 ID」去重。
     */
    private fun playbackResolveAttempts(
        provider: StreamingProviderName,
        providerTrackId: String,
        metadata: StreamingTrack?
    ): List<PlaybackResolveAttempt> {
        val attempts = mutableListOf(PlaybackResolveAttempt(provider, providerTrackId))
        val seen = linkedSetOf("${provider.wireName}:$providerTrackId")
        metadata?.playbackCandidates?.forEach { candidate ->
            val candidateTrackId = candidate.providerTrackId?.takeIf { it.isNotBlank() }
                ?: providerTrackId.takeIf { candidate.provider == provider }
                ?: return@forEach
            if (!candidate.available) {
                return@forEach
            }
            if (seen.add("${candidate.provider.wireName}:$candidateTrackId")) {
                attempts += PlaybackResolveAttempt(candidate.provider, candidateTrackId)
            }
        }
        return attempts
    }

    private data class PlaybackResolveAttempt(
        val provider: StreamingProviderName,
        val providerTrackId: String
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

    private companion object {
        const val MAX_RECENT_LOGS = 20
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
