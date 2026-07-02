package app.yukine.streaming

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

interface StreamingGateway {
    suspend fun providers(): List<StreamingProviderDescriptor>

    suspend fun providerCapabilities(): List<StreamingProviderCapability>

    suspend fun providersHealth(): List<StreamingProviderHealth>

    suspend fun search(request: StreamingSearchRequest): StreamingSearchResult

    suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail

    /**
     * Lists the playlists the user has saved / created on the given provider's account.
     * Returns an empty list when the provider or gateway does not expose this capability.
     */
    suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist>

    /**
     * Returns the user's liked/saved tracks (favorites) on the given provider's account.
     * Returns an empty list when the provider or gateway does not expose this capability.
     */
    suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack>

    /**
     * Returns the provider's personalized "daily recommendation" track list (e.g. NetEase 每日推荐).
     * Returns an empty list when the provider or gateway does not expose this capability.
     */
    suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack>

    /**
     * Returns the provider's "heartbeat / intelligence" recommendation list (e.g. NetEase 心动推荐),
     * an endless personalized stream seeded from the user's tastes. Returns an empty list when the
     * provider or gateway does not expose this capability.
     */
    suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack>

    suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource

    suspend fun authState(provider: StreamingProviderName): StreamingAuthState

    suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult

    suspend fun completeAuth(provider: StreamingProviderName, callbackUri: String, cookieHeader: String? = null): StreamingAuthResult

    suspend fun signOut(provider: StreamingProviderName): StreamingAuthState
}

class StreamingProviderRegistry(
    providers: List<StreamingProvider> = emptyList()
) {
    private val providersByName = providers.associateBy { it.descriptor.name }

    fun descriptors(): List<StreamingProviderDescriptor> {
        return providersByName.values.map { it.descriptor }
    }

    fun providers(): List<StreamingProvider> {
        return providersByName.values.toList()
    }

    fun provider(name: StreamingProviderName): StreamingProvider? {
        return providersByName[name]
    }
}

class RegistryStreamingGateway(
    private val registry: StreamingProviderRegistry
) : StreamingGateway {
    override suspend fun providers(): List<StreamingProviderDescriptor> {
        return registry.descriptors()
    }

    override suspend fun providerCapabilities(): List<StreamingProviderCapability> {
        return StreamingCapabilityResolver.providerCapabilities(registry.descriptors())
    }

    override suspend fun providersHealth(): List<StreamingProviderHealth> {
        return registry.providers().map { provider ->
            try {
                provider.health()
            } catch (error: Exception) {
                StreamingProviderHealth(
                    provider = provider.descriptor.name,
                    available = false,
                    authenticated = false,
                    errorCode = StreamingErrorCode.SOURCE_UNAVAILABLE,
                    errorMessage = error.message
                )
            }
        }
    }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val provider = requireProvider(request.provider)
        if (!provider.descriptor.capabilities.supportsSearch) {
            throw StreamingGatewayException(
                "Provider ${request.provider} does not support search",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
        return provider.search(request.normalized())
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        val provider = requireProvider(request.provider)
        if (!provider.descriptor.capabilities.supportsPlaylists) {
            throw StreamingGatewayException(
                "Provider ${request.provider} does not support playlists",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
        return provider.playlist(request.normalized())
    }

    override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> {
        // The registry-backed (offline) gateway has no concept of remote user accounts.
        return emptyList()
    }

    override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> {
        // The registry-backed (offline) gateway has no concept of remote user accounts.
        return emptyList()
    }

    override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
        // The registry-backed (offline) gateway has no personalized recommendations.
        return emptyList()
    }

    override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
        // The registry-backed (offline) gateway has no personalized recommendations.
        return emptyList()
    }

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        val provider = requireProvider(request.provider)
        if (!provider.descriptor.capabilities.supportsPlayback) {
            throw StreamingGatewayException(
                "Provider ${request.provider} does not support playback",
                code = StreamingErrorCode.UNSUPPORTED_OPERATION
            )
        }
        return provider.resolvePlayback(request)
    }

    override suspend fun authState(provider: StreamingProviderName): StreamingAuthState {
        return requireProvider(provider).authState()
    }

    override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
        return requireProvider(request.provider).startAuth(request)
    }

    override suspend fun completeAuth(provider: StreamingProviderName, callbackUri: String, cookieHeader: String?): StreamingAuthResult {
        return requireProvider(provider).completeAuth(callbackUri)
    }

    override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState {
        return requireProvider(provider).signOut()
    }

    private fun requireProvider(name: StreamingProviderName): StreamingProvider {
        return registry.provider(name) ?: throw StreamingGatewayException(
            "Provider $name is not registered",
            code = StreamingErrorCode.SOURCE_UNAVAILABLE
        )
    }
}

class RemoteStreamingGateway(
    private val endpointBaseUrl: String,
    private val offlineProvider: StreamingProvider = MockStreamingProvider(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleepMs: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
    private val maxRetries: Int = 1,
    private val retryDelayMs: Long = 250L,
    private val circuitBreakerThreshold: Int = 3,
    private val circuitOpenMs: Long = 30_000L,
    private val localAuthStore: StreamingLocalAuthStore? = null,
    private val localNeteaseClient: LocalNeteaseStreamingClient = LocalNeteaseStreamingClient(localAuthStore),
    private val localQqMusicClient: LocalQqMusicStreamingClient = LocalQqMusicStreamingClient(localAuthStore),
    private val localLuoxueClient: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient(
        neteaseClient = localNeteaseClient,
        qqMusicClient = localQqMusicClient
    ),
    private val luoxueSourceStore: LuoxueSourceStore? = null
) : StreamingGateway {
    private var consecutiveGatewayFailures = 0
    private var circuitOpenUntilMs = 0L
    private var rateLimitedUntilMs = 0L
    private val localProviders = LocalStreamingProviderRegistry(
        localAuthStore,
        localNeteaseClient,
        localQqMusicClient,
        localLuoxueClient,
        luoxueSourceStore
    )

    override suspend fun providers(): List<StreamingProviderDescriptor> {
        if (!configured()) {
            return localProviderDescriptors()
        }
        return try {
            val remoteProviders = StreamingGatewayJson.providerDescriptors(get("providers"))
            val catalogProviders = StreamingProviderCatalog.gatewayBackedDescriptors()
            val baseList = if (remoteProviders.isEmpty()) {
                catalogProviders
            } else {
                (remoteProviders + catalogProviders)
                    .distinctBy { it.name }
            }
            baseList.map(::applyLocalAuth)
        } catch (_: StreamingGatewayException) {
            localProviderDescriptors()
        }
    }

    override suspend fun providerCapabilities(): List<StreamingProviderCapability> {
        if (!configured()) {
            return StreamingCapabilityResolver.providerCapabilities(providers())
        }
        return try {
            val remoteCapabilities = StreamingGatewayJson.providerCapabilities(get("providers/capabilities"))
            remoteCapabilities.ifEmpty {
                StreamingCapabilityResolver.providerCapabilities(providers())
            }
        } catch (error: StreamingGatewayException) {
            if (error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE) {
                StreamingCapabilityResolver.providerCapabilities(localProviderDescriptors())
            } else {
                StreamingCapabilityResolver.providerCapabilities(providers())
            }
        }
    }

    override suspend fun providersHealth(): List<StreamingProviderHealth> {
        if (!configured()) {
            return localProviderHealth()
        }
        return try {
            StreamingGatewayJson.providerHealth(get("providers/health"))
        } catch (_: StreamingGatewayException) {
            localProviderHealth()
        }
    }

    private fun applyLocalAuth(descriptor: StreamingProviderDescriptor): StreamingProviderDescriptor {
        localProviders.provider(descriptor.name)?.descriptor?.takeIf { it.enabled }?.let { localDescriptor ->
            return localDescriptor
        }
        val store = localAuthStore ?: return descriptor
        val localState = store.authState(descriptor.name)
        if (!localState.connected) {
            return descriptor
        }
        return descriptor.copy(
            auth = localState,
            status = StreamingProviderStatus.READY,
            statusMessage = localState.statusMessage ?: descriptor.statusMessage
        )
    }

    private fun localProviderDescriptors(): List<StreamingProviderDescriptor> {
        return localProviders.descriptors().map(::applyLocalAuth)
    }

    /**
     * In local/unconfigured mode only providers with a direct local client can actually serve data.
     * Mark the rest as disabled so the UI greys them out instead of offering a dead entry point.
     */
    private fun applyOfflineAvailability(descriptor: StreamingProviderDescriptor): StreamingProviderDescriptor {
        if (localProviders.canHandle(descriptor.name)) {
            return descriptor
        }
        return descriptor.copy(
            enabled = false,
            status = StreamingProviderStatus.DISABLED,
            statusMessage = gatewayRequiredMessage(descriptor)
        )
    }

    private fun localProviderHealth(): List<StreamingProviderHealth> {
        return localProviderDescriptors().map { descriptor ->
            val authState = if (descriptor.capabilities.supportsAuth) {
                localAuthStore?.authState(descriptor.name) ?: descriptor.auth
            } else {
                descriptor.auth
            }
            StreamingProviderHealth(
                provider = descriptor.name,
                available = descriptor.enabled,
                authenticated = authState.connected,
                errorCode = if (descriptor.enabled) null else StreamingErrorCode.UNSUPPORTED_OPERATION,
                errorMessage = descriptor.statusMessage ?: authState.statusMessage
            )
        }
    }

    private fun localUnavailableMessage(
        descriptor: StreamingProviderDescriptor,
        locallyConnected: Boolean
    ): String {
        if (locallyConnected) {
            return "本地登录已保存"
        }
        return when {
            descriptor.name == StreamingProviderName.LUOXUE -> "需要配置支持 LX/洛雪的流媒体网关"
            descriptor.auth.kind == StreamingAuthKind.NONE -> gatewayRequiredMessage(descriptor)
            else -> "未连接，点击登录"
        }
    }

    private fun gatewayRequiredMessage(descriptor: StreamingProviderDescriptor): String {
        return if (descriptor.name == StreamingProviderName.LUOXUE) {
            "需要配置支持 LX/洛雪的流媒体网关后才能使用"
        } else {
            "需要配置流媒体网关后才能使用"
        }
    }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        val normalized = request.normalized()
        if (!configured() && normalized.provider == offlineProvider.descriptor.name) {
            return offlineProvider.search(normalized)
        }
        val localSearchError = localProviders.provider(normalized.provider)
            ?.takeIf { it.descriptor.enabled }
            ?.let { provider ->
                try {
                    val result = provider.search(normalized)
                    return if (configured()) result.withProxiedArtwork() else result
                } catch (error: StreamingGatewayException) {
                    error
                }
            }
        if (!configured() && localSearchError != null) {
            throw localSearchError
        }
        requireConfigured()
        return try {
            StreamingGatewayJson.searchResult(
                post("search", StreamingGatewayJson.searchRequest(normalized).toString())
            ).withProxiedArtwork()
        } catch (error: StreamingGatewayException) {
            if (error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE && localSearchError != null) {
                throw localSearchError
            }
            throw error
        }
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        if (!configured() && request.provider == offlineProvider.descriptor.name) {
            return offlineProvider.playlist(request.normalized())
        }
        if (!configured()) {
            localProviders.provider(request.provider)?.takeIf { it.descriptor.enabled }?.let { provider ->
                return provider.playlist(request.normalized())
            }
        }
        val normalized = request.normalized()
        val localPlaylistError = localProviders.provider(normalized.provider)
            ?.takeIf { it.descriptor.enabled }
            ?.let { provider ->
                try {
                    return provider.playlist(normalized)
                } catch (error: StreamingGatewayException) {
                    error
                }
            }
        if (!configured() && localPlaylistError != null) {
            throw localPlaylistError
        }
        requireConfigured()
        return try {
            val detail = StreamingGatewayJson.playlistDetail(
                get(
                    "playlist?provider=${encode(normalized.provider.wireName)}" +
                        "&providerPlaylistId=${encode(normalized.providerPlaylistId)}" +
                        "&page=${normalized.page}&pageSize=${normalized.pageSize}"
                )
            ).withProxiedArtwork()
            if (shouldUseLocalNeteasePlaylistFallback(normalized, detail)) {
                localNeteaseClient.playlist(normalized)
            } else {
                detail
            }
        } catch (error: StreamingGatewayException) {
            when {
                error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE && localPlaylistError != null ->
                    throw localPlaylistError
                error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE && localNeteaseClient.canHandle(normalized.provider) ->
                    localNeteaseClient.playlist(normalized)
                else -> throw error
            }
        }
    }

    private fun shouldUseLocalNeteasePlaylistFallback(
        request: StreamingPlaylistRequest,
        detail: StreamingPlaylistDetail
    ): Boolean {
        if (!localNeteaseClient.canHandle(request.provider)) {
            return false
        }
        if (request.provider != StreamingProviderName.NETEASE) {
            return false
        }
        val total = detail.total ?: detail.playlist?.trackCount ?: return false
        return detail.tracks.size < total
    }

    override suspend fun userPlaylists(provider: StreamingProviderName): List<StreamingPlaylist> {
        if (!configured() && provider == offlineProvider.descriptor.name) {
            return emptyList()
        }
        if (!configured() && provider == StreamingProviderName.NETEASE && localNeteaseClient.canHandle(provider)) {
            return localNeteaseClient.userPlaylists()
        }
        if (!configured() && provider == StreamingProviderName.QQ_MUSIC) {
            return localQqMusicClient.userPlaylists()
        }
        requireConfigured()
        return try {
            StreamingGatewayJson.userPlaylists(
                get("userPlaylists?provider=${encode(provider.wireName)}"),
                provider
            ).map { it.withProxiedArtwork() }
        } catch (error: StreamingGatewayException) {
            when {
                error.code == StreamingErrorCode.UNSUPPORTED_OPERATION -> emptyList()
                error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE &&
                    provider == StreamingProviderName.NETEASE &&
                    localNeteaseClient.canHandle(provider) ->
                    localNeteaseClient.userPlaylists()
                error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE &&
                    provider == StreamingProviderName.QQ_MUSIC ->
                    localQqMusicClient.userPlaylists()
                else -> throw error
            }
        }
    }

    override suspend fun userLikedTracks(provider: StreamingProviderName): List<StreamingTrack> {
        if (!configured() && provider == offlineProvider.descriptor.name) {
            return emptyList()
        }
        if (!configured() && provider == StreamingProviderName.NETEASE && localNeteaseClient.canHandle(provider)) {
            return localNeteaseClient.userLikedTracks()
        }
        requireConfigured()
        return try {
            StreamingGatewayJson.tracks(
                get("userLikedTracks?provider=${encode(provider.wireName)}"),
                provider
            ).map { it.withProxiedArtwork() }
        } catch (error: StreamingGatewayException) {
            when {
                error.code == StreamingErrorCode.UNSUPPORTED_OPERATION -> emptyList()
                error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE &&
                    provider == StreamingProviderName.NETEASE &&
                    localNeteaseClient.canHandle(provider) ->
                    localNeteaseClient.userLikedTracks()
                else -> throw error
            }
        }
    }

    override suspend fun dailyRecommendations(provider: StreamingProviderName): List<StreamingTrack> {
        // No remote gateway endpoint exists for personalized recommendations; serve NetEase via the
        // local client and return empty for everything else. In local mode artwork is used as-is
        // (matching userLikedTracks' local branch) so we don't proxy through an unconfigured gateway.
        if (provider == StreamingProviderName.NETEASE && localNeteaseClient.canHandle(provider)) {
            val tracks = localNeteaseClient.dailyRecommendedTracks()
            return if (configured()) tracks.map { it.withProxiedArtwork() } else tracks
        }
        return emptyList()
    }

    override suspend fun heartbeatRecommendations(request: StreamingHeartbeatRequest): List<StreamingTrack> {
        val provider = request.provider
        if (localProviders.canHandle(provider) && provider == StreamingProviderName.NETEASE) {
            val tracks = localNeteaseClient.heartbeatRecommendedTracks(
                seedTrackId = request.providerTrackId,
                playlistId = request.providerPlaylistId,
                count = request.count
            )
            return if (configured()) tracks.map { it.withProxiedArtwork() } else tracks
        }
        return emptyList()
    }

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        if (!configured() && request.provider == offlineProvider.descriptor.name) {
            return offlineProvider.resolvePlayback(request)
        }
        if (!configured()) {
            localProviders.provider(request.provider)?.takeIf { it.descriptor.enabled }?.let { provider ->
                return provider.resolvePlayback(request)
            }
        }
        val localPlaybackError = localProviders.provider(request.provider)
            ?.takeIf { it.descriptor.enabled }
            ?.let { provider ->
                try {
                    return provider.resolvePlayback(request)
                } catch (error: StreamingGatewayException) {
                    error
                }
            }
        requireConfigured()
        return try {
            StreamingGatewayJson.playbackSource(
                post("resolvePlayback", StreamingGatewayJson.playbackRequest(request).toString())
            )
        } catch (error: StreamingGatewayException) {
            if (error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE && localNeteaseClient.canHandle(request.provider)) {
                localNeteaseClient.resolvePlayback(request)
            } else if (error.code == StreamingErrorCode.GATEWAY_UNAVAILABLE && localPlaybackError != null) {
                throw localPlaybackError
            } else {
                throw error
            }
        }
    }

    override suspend fun authState(provider: StreamingProviderName): StreamingAuthState {
        if (provider == offlineProvider.descriptor.name) {
            return offlineProvider.authState()
        }
        localProviders.provider(provider)
            ?.takeIf { it.descriptor.enabled && it.descriptor.capabilities.supportsAuth.not() }
            ?.let { localProvider ->
            return localProvider.authState()
        }
        localAuthStore?.let { store ->
            val local = store.authState(provider)
            if (local.connected) {
                return local
            }
        }
        if (!configured()) {
            return localAuthStore?.authState(provider) ?: disconnectedAuthState(provider)
        }
        return try {
            StreamingGatewayJson.authState(get("auth/state?provider=${encode(provider.wireName)}"), provider)
        } catch (_: StreamingGatewayException) {
            localAuthStore?.authState(provider) ?: disconnectedAuthState(provider)
        }
    }

    override suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
        val localUrl = LocalStreamingLoginEndpoints.loginUrl(request.provider, request.redirectUri)
        if (!configured()) {
            return localFallbackStart(request, localUrl)
        }
        return try {
            val remote = StreamingGatewayJson.authResult(
                post("auth/start", StreamingGatewayJson.authRequest(request).toString()),
                request.provider
            )
            // If the gateway responded but did not provide a launchUrl and we have a local URL,
            // surface the local URL so the user still has a way to log in.
            if (remote.launchUrl.isNullOrBlank() && !localUrl.isNullOrBlank()) {
                remote.copy(launchUrl = localUrl)
            } else {
                remote
            }
        } catch (_: StreamingGatewayException) {
            localFallbackStart(request, localUrl)
        }
    }

    override suspend fun completeAuth(provider: StreamingProviderName, callbackUri: String, cookieHeader: String?): StreamingAuthResult {
        val store = localAuthStore
        val hasCapturedCookie = !cookieHeader.isNullOrBlank()
        val localState = store?.saveLogin(provider, cookieHeader)
        if (!configured()) {
            val state = localState ?: StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = hasCapturedCookie,
                statusMessage = if (hasCapturedCookie) "本地登录成功" else "未捕获到登录凭据，请重试"
            )
            return StreamingAuthResult(
                provider = provider,
                state = state,
                statusMessage = state.statusMessage
            )
        }
        return try {
            val remote = StreamingGatewayJson.authResult(
                post("auth/complete", StreamingGatewayJson.completeAuthRequest(provider, callbackUri, cookieHeader).toString()),
                provider
            )
            val state = if (hasCapturedCookie || localState?.connected == true) {
                mergeLocalAuthState(localState, remote.state)
            } else {
                localState ?: remote.state.copy(
                    connected = false,
                    statusMessage = "未捕获到登录凭据，请重试"
                )
            }
            remote.copy(
                state = state,
                statusMessage = remote.statusMessage ?: state.statusMessage
            )
        } catch (_: StreamingGatewayException) {
            val fallback = localState ?: StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = hasCapturedCookie,
                statusMessage = if (hasCapturedCookie) "网关不可用，已使用本地登录" else "未捕获到登录凭据，请重试"
            )
            StreamingAuthResult(
                provider = provider,
                state = fallback,
                statusMessage = fallback.statusMessage
            )
        }
    }

    private fun mergeLocalAuthState(
        localState: StreamingAuthState?,
        remoteState: StreamingAuthState
    ): StreamingAuthState {
        if (localState?.connected != true || remoteState.connected) {
            return remoteState
        }
        return remoteState.copy(
            kind = if (remoteState.kind == StreamingAuthKind.NONE) localState.kind else remoteState.kind,
            connected = true,
            accountDisplayName = remoteState.accountDisplayName ?: localState.accountDisplayName,
            accountUsername = remoteState.accountUsername ?: localState.accountUsername,
            accountAvatarUrl = remoteState.accountAvatarUrl ?: localState.accountAvatarUrl,
            statusMessage = remoteState.statusMessage ?: localState.statusMessage
        )
    }

    override suspend fun signOut(provider: StreamingProviderName): StreamingAuthState {
        localAuthStore?.signOut(provider)
        if (!configured()) {
            return localAuthStore?.authState(provider) ?: disconnectedAuthState(provider)
        }
        return try {
            StreamingGatewayJson.authState(
                post("auth/signOut", "{\"provider\":\"${provider.wireName}\"}"),
                provider
            )
        } catch (_: StreamingGatewayException) {
            localAuthStore?.authState(provider) ?: disconnectedAuthState(provider)
        }
    }

    private fun localFallbackStart(
        request: StreamingAuthRequest,
        localUrl: String?
    ): StreamingAuthResult {
        val store = localAuthStore
        val baseState = store?.authState(request.provider) ?: StreamingAuthState(
            kind = LocalStreamingAuthStore.providerAuthKind(request.provider),
            connected = false,
            statusMessage = "未连接，点击登录"
        )
        if (localUrl.isNullOrBlank()) {
            return StreamingAuthResult(
                provider = request.provider,
                state = baseState,
                launchUrl = null,
                statusMessage = "未配置该音源的登录入口"
            )
        }
        return StreamingAuthResult(
            provider = request.provider,
            state = baseState.copy(
                kind = LocalStreamingAuthStore.providerAuthKind(request.provider),
                statusMessage = if (baseState.connected) "本地登录已保存" else "请在新页面登录"
            ),
            launchUrl = localUrl,
            statusMessage = if (baseState.connected) "本地登录已保存" else "请在新页面登录"
        )
    }

    private fun disconnectedAuthState(provider: StreamingProviderName): StreamingAuthState {
        if (provider == offlineProvider.descriptor.name) {
            return offlineProvider.descriptor.auth
        }
        val descriptor = StreamingProviderCatalog.gatewayBackedDescriptors()
            .firstOrNull { it.name == provider }
        return descriptor?.auth?.copy(statusMessage = "未连接，点击登录")
            ?: StreamingAuthState(
                kind = StreamingAuthKind.REMOTE_GATEWAY,
                connected = false,
                statusMessage = "未连接，点击登录"
            )
    }

    private fun StreamingSearchResult.withProxiedArtwork(): StreamingSearchResult {
        return copy(
            tracks = tracks.map { it.withProxiedArtwork() },
            albums = albums.map { it.withProxiedArtwork() },
            artists = artists.map { it.withProxiedArtwork() },
            playlists = playlists.map { it.withProxiedArtwork() },
            mvs = mvs.map { it.withProxiedArtwork() },
            items = items.map { it.withProxiedArtwork() }
        )
    }

    private fun StreamingPlaylistDetail.withProxiedArtwork(): StreamingPlaylistDetail {
        return copy(
            playlist = playlist?.withProxiedArtwork(),
            tracks = tracks.map { it.withProxiedArtwork() }
        )
    }

    private fun StreamingSearchItem.withProxiedArtwork(): StreamingSearchItem {
        return copy(
            imageUrl = proxiedArtworkUrl(imageUrl, provider),
            track = track?.withProxiedArtwork(),
            album = album?.withProxiedArtwork(),
            artist = artist?.withProxiedArtwork(),
            playlist = playlist?.withProxiedArtwork(),
            mv = mv?.withProxiedArtwork()
        )
    }

    private fun StreamingTrack.withProxiedArtwork(): StreamingTrack {
        return copy(
            coverUrl = proxiedArtworkUrl(coverUrl, provider),
            coverThumbUrl = proxiedArtworkUrl(coverThumbUrl, provider)
        )
    }

    private fun StreamingAlbum.withProxiedArtwork(): StreamingAlbum {
        return copy(coverUrl = proxiedArtworkUrl(coverUrl, provider))
    }

    private fun StreamingArtist.withProxiedArtwork(): StreamingArtist {
        return copy(avatarUrl = proxiedArtworkUrl(avatarUrl, provider))
    }

    private fun StreamingPlaylist.withProxiedArtwork(): StreamingPlaylist {
        return copy(coverUrl = proxiedArtworkUrl(coverUrl, provider))
    }

    private fun StreamingMvItem.withProxiedArtwork(): StreamingMvItem {
        return copy(thumbnailUrl = proxiedArtworkUrl(thumbnailUrl, provider))
    }

    private fun proxiedArtworkUrl(value: String?, provider: StreamingProviderName): String? {
        val original = value?.trim()?.takeIf { it.isNotBlank() } ?: return value
        if (!original.startsWith("http://") && !original.startsWith("https://")) {
            return value
        }
        if (isGatewayArtworkUrl(original)) {
            return original
        }
        return url("artwork?provider=${encode(provider.wireName)}&url=${encode(original)}")
    }

    private fun isGatewayArtworkUrl(value: String): Boolean {
        val base = endpointBaseUrl.trim().trimEnd('/')
        return value.startsWith("$base/artwork?")
    }

    private fun get(path: String): String {
        return request("GET", path, null)
    }

    private fun post(path: String, body: String): String {
        return request("POST", path, body)
    }

    private fun request(method: String, path: String, body: String?): String {
        val now = clockMs()
        if (now < rateLimitedUntilMs) {
            throw StreamingGatewayException(
                "Streaming gateway is rate limited until $rateLimitedUntilMs",
                code = StreamingErrorCode.RATE_LIMITED,
                retryAfterMs = rateLimitedUntilMs - now
            )
        }
        if (now < circuitOpenUntilMs) {
            throw StreamingGatewayException(
                "Streaming gateway circuit breaker is open until $circuitOpenUntilMs",
                code = StreamingErrorCode.GATEWAY_UNAVAILABLE,
                retryAfterMs = circuitOpenUntilMs - now
            )
        }
        var attempt = 0
        var lastError: StreamingGatewayException? = null
        while (attempt <= maxRetries.coerceAtLeast(0)) {
            try {
                return requestOnce(method, path, body)
            } catch (error: StreamingGatewayException) {
                lastError = error
                if (!error.retryable || attempt >= maxRetries.coerceAtLeast(0)) {
                    throw error
                }
                sleepMs(retryDelayMs.coerceAtLeast(0L))
            }
            attempt += 1
        }
        throw lastError ?: notConnected()
    }

    private fun requestOnce(method: String, path: String, body: String?): String {
        val connection = URL(url(path)).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Yukine-Android")
        providerForRequest(path, body)?.let { provider ->
            localAuthStore?.cookieHeader(provider)?.let { cookie ->
                connection.setRequestProperty("Cookie", cookie)
                connection.setRequestProperty("X-Echo-Streaming-Provider", provider.wireName)
            }
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line)
                            line = reader.readLine()
                        }
                    }
                }
            }.orEmpty().trim()
            if (code !in 200..299) {
                val retryAfterMs = retryAfterMs(connection.getHeaderField("Retry-After"))
                if (code == 429) {
                    val waitMs = retryAfterMs ?: retryDelayMs.coerceAtLeast(0L)
                    rateLimitedUntilMs = clockMs() + waitMs
                    recordGatewayFailure()
                } else if (code >= 500) {
                    recordGatewayFailure()
                }
                val error = StreamingGatewayJson.gatewayError(response)
                val mappedCode = when (code) {
                    429 -> StreamingErrorCode.RATE_LIMITED
                    in 500..599 -> StreamingErrorCode.GATEWAY_UNAVAILABLE
                    else -> error.code
                }
                throw StreamingGatewayException(
                    "Gateway request failed ($code): ${error.message ?: response.ifBlank { path }}",
                    code = mappedCode,
                    retryAfterMs = retryAfterMs,
                    retryable = code >= 500
                )
            }
            if (response.isBlank()) {
                throw StreamingGatewayException("Gateway returned an empty response: $path")
            }
            recordGatewaySuccess()
            return response
        } catch (error: IOException) {
            recordGatewayFailure()
            throw StreamingGatewayException(
                "Gateway request failed: ${error.message ?: path}",
                cause = error,
                code = StreamingErrorCode.GATEWAY_UNAVAILABLE,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun recordGatewaySuccess() {
        consecutiveGatewayFailures = 0
        circuitOpenUntilMs = 0L
    }

    private fun recordGatewayFailure() {
        consecutiveGatewayFailures += 1
        if (consecutiveGatewayFailures >= circuitBreakerThreshold.coerceAtLeast(1)) {
            circuitOpenUntilMs = clockMs() + circuitOpenMs.coerceAtLeast(0L)
        }
    }

    private fun retryAfterMs(header: String?): Long? {
        val value = header?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return value.toLongOrNull()?.let { seconds -> seconds.coerceAtLeast(0L) * 1000L }
    }

    private fun requireConfigured() {
        if (!configured()) {
            throw notConnected()
        }
    }

    private fun configured(): Boolean {
        val value = endpointBaseUrl.trim()
        return value.startsWith("http://") || value.startsWith("https://")
    }

    private fun url(path: String): String {
        val base = endpointBaseUrl.trim().trimEnd('/')
        return "$base/${path.trimStart('/')}"
    }

    private fun providerForRequest(path: String, body: String?): StreamingProviderName? {
        body?.trim()?.takeIf { it.startsWith("{") }?.let { json ->
            runCatching {
                JSONObject(json).optString("provider")
                    .takeIf { it.isNotBlank() }
                    ?.let(StreamingProviderName::fromWireName)
            }.getOrNull()?.let { return it }
        }
        val query = path.substringAfter('?', "")
        if (query.isBlank()) {
            return null
        }
        return query.split('&')
            .asSequence()
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    val key = decode(parameter.substring(0, separator))
                    val value = decode(parameter.substring(separator + 1))
                    if (key == "provider") StreamingProviderName.fromWireName(value) else null
                }
            }
            .firstOrNull()
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun notConnected(): StreamingGatewayException {
        return StreamingGatewayException(
            "在线音乐网关未连接",
            code = StreamingErrorCode.GATEWAY_UNAVAILABLE
        )
    }
}

class StreamingGatewayException(
    message: String,
    cause: Throwable? = null,
    val code: StreamingErrorCode = StreamingErrorCode.UNKNOWN,
    val retryAfterMs: Long? = null,
    val retryable: Boolean = false
) : RuntimeException(message, cause)

private fun StreamingSearchRequest.normalized(): StreamingSearchRequest {
    return copy(
        query = query.trim(),
        mediaTypes = mediaTypes.ifEmpty { setOf(StreamingMediaType.TRACK) },
        page = page.coerceAtLeast(1),
        pageSize = pageSize.coerceIn(1, 50)
    )
}

private fun StreamingPlaylistRequest.normalized(): StreamingPlaylistRequest {
    return copy(
        providerPlaylistId = providerPlaylistId.trim(),
        page = page.coerceAtLeast(1),
        pageSize = pageSize.coerceIn(1, 2000)
    )
}
