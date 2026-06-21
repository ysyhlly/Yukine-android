package app.yukine.streaming

import android.content.Context
import android.util.Log
import app.yukine.model.Track
import app.yukine.streaming.cache.StreamingCachePolicy
import app.yukine.streaming.cache.StreamingCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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
                recordCacheHit("playback", provider)
                return@withContext StreamingGatewayJson.playbackSource(cached)
            }
            val request = StreamingPlaybackRequest(
                provider = provider,
                providerTrackId = providerTrackId,
                quality = quality
            )
            recordGatewayCall("playback", provider) {
                gateway.resolvePlayback(request).also { source ->
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
            val source = resolvePlayback(provider, providerTrackId, quality)
            preflightPlaybackSource(source)
            StreamingResolvedPlayback(
                source = source,
                track = playbackTrackAdapter.toTrack(source, metadata)
            )
        }
    }

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

    private fun preflightPlaybackSource(source: StreamingPlaybackSource) {
        validatePlaybackSource(source)
        if (!source.url.startsWith("http://", ignoreCase = true)
            && !source.url.startsWith("https://", ignoreCase = true)
        ) {
            return
        }
        val started = clockMs()
        runCatching {
            val headCode = preflightConnection(source, "HEAD", false)
            if (headCode == HttpURLConnection.HTTP_BAD_METHOD || headCode == HttpURLConnection.HTTP_FORBIDDEN) {
                preflightConnection(source, "GET", true)
            } else {
                headCode
            }
        }.onSuccess { code ->
            recordPreflightResult(source, started, code, null)
            if (code < 200 || code >= 400) {
                logWarning("CDN preflight returned HTTP $code for ${source.provider.wireName}:${source.providerTrackId}")
            } else {
                logDebug("CDN preflight OK HTTP $code for ${source.provider.wireName}:${source.providerTrackId}")
            }
        }.onFailure { error ->
            recordPreflightResult(source, started, null, error)
            logWarning("CDN preflight failed for ${source.provider.wireName}:${source.providerTrackId}", error)
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

    private fun recordPreflightResult(
        source: StreamingPlaybackSource,
        started: Long,
        statusCode: Int?,
        error: Throwable?
    ) {
        val ended = clockMs()
        val host = runCatching { URL(source.url).host }.getOrDefault("")
        synchronized(diagnosticsLock) {
            totalRequests += 1
            addLogLocked(
                StreamingGatewayLogEntry(
                    operation = "playback_preflight",
                    provider = source.provider,
                    durationMs = (ended - started).coerceAtLeast(0L),
                    cacheHit = false,
                    errorCode = when {
                        error != null -> StreamingErrorCode.UNKNOWN
                        statusCode != null && (statusCode < 200 || statusCode >= 400) -> StreamingErrorCode.SOURCE_UNAVAILABLE
                        else -> null
                    },
                    message = listOfNotNull(
                        statusCode?.let { "http=$it" },
                        host.takeIf { it.isNotBlank() }?.let { "host=$it" },
                        error?.message
                    ).joinToString(" ").ifBlank { null },
                    timestampMs = ended
                )
            )
        }
    }

    private fun preflightConnection(
        source: StreamingPlaybackSource,
        method: String,
        rangeProbe: Boolean
    ): Int {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = method
            connectTimeout = PREFLIGHT_CONNECT_TIMEOUT_MS
            readTimeout = PREFLIGHT_READ_TIMEOUT_MS
            source.headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (rangeProbe) {
                setRequestProperty("Range", "bytes=0-0")
            }
        }
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
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
        const val PREFLIGHT_CONNECT_TIMEOUT_MS = 1500
        const val PREFLIGHT_READ_TIMEOUT_MS = 2000
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
