package app.yukine.streaming

class LocalNeteaseStreamingProvider(
    private val client: LocalNeteaseStreamingClient,
    private val authStore: StreamingLocalAuthStore?
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() {
            val auth = authStore?.authState(StreamingProviderName.NETEASE)
                ?: StreamingAuthState(
                    kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                    connected = false,
                    statusMessage = "未登录，点击登录"
                )
            return StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.NETEASE)
                .copy(
                    auth = auth,
                    status = if (auth.connected) {
                        StreamingProviderStatus.READY
                    } else {
                        StreamingProviderStatus.NEEDS_ACCOUNT
                    },
                    statusMessage = auth.statusMessage
                        ?: if (auth.connected) "本机直连已就绪" else "未登录，点击登录"
                )
        }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult = client.search(request)

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail = client.playlist(request)

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(request)

    override suspend fun authState(): StreamingAuthState =
        authStore?.authState(StreamingProviderName.NETEASE) ?: descriptor.auth

    override suspend fun signOut(): StreamingAuthState =
        authStore?.signOut(StreamingProviderName.NETEASE) ?: descriptor.auth
}

class LocalQqMusicStreamingProvider(
    private val client: LocalQqMusicStreamingClient = LocalQqMusicStreamingClient(),
    private val authStore: StreamingLocalAuthStore?
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() {
            val auth = authStore?.authState(StreamingProviderName.QQ_MUSIC)
                ?: StreamingAuthState(
                    kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                    connected = false,
                    statusMessage = "未登录，点击登录"
                )
            return StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.QQ_MUSIC)
            .copy(
                auth = auth,
                status = if (auth.connected) {
                    StreamingProviderStatus.READY
                } else {
                    StreamingProviderStatus.NEEDS_ACCOUNT
                },
                statusMessage = auth.statusMessage
                    ?: if (auth.connected) "本机直连已就绪" else "未登录，点击登录"
            )
        }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult = client.search(request)

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail = client.playlist(request)

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(request)

    override suspend fun authState(): StreamingAuthState =
        authStore?.authState(StreamingProviderName.QQ_MUSIC) ?: descriptor.auth

    override suspend fun signOut(): StreamingAuthState =
        authStore?.signOut(StreamingProviderName.QQ_MUSIC) ?: descriptor.auth
}

class LocalLuoxueStreamingProvider(
    private val client: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient()
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() = StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.LUOXUE)
            .copy(
                auth = StreamingAuthState(
                    kind = StreamingAuthKind.NONE,
                    connected = false,
                    statusMessage = "无需登录，支持 LX 的 kw/kg 子源；wy/tx 会复用网易云/QQ 登录"
                ),
                status = StreamingProviderStatus.READY,
                statusMessage = "按 LX Music 方式支持 kw/kg/wy/tx 子源搜索、播放和歌单导入"
            )

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult = client.search(request)

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail = client.playlist(request)

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(request)

    override suspend fun authState(): StreamingAuthState = descriptor.auth
}

class LocalUnsupportedStreamingProvider(
    private val baseDescriptor: StreamingProviderDescriptor
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() = baseDescriptor.copy(
            enabled = false,
            capabilities = baseDescriptor.capabilities.copy(
                supportsSearch = false,
                supportsPlayback = false,
                supportsLyrics = false,
                supportsMv = false,
                supportsFavorites = false,
                supportsPlaylists = false
            ),
            status = StreamingProviderStatus.DISABLED,
            statusMessage = localPendingMessage(baseDescriptor.name)
        )

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        throw unsupported()
    }

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource {
        throw unsupported()
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        throw unsupported()
    }

    override suspend fun authState(): StreamingAuthState =
        baseDescriptor.auth.copy(statusMessage = localPendingMessage(baseDescriptor.name))

    private fun unsupported(): StreamingGatewayException =
        StreamingGatewayException(
            localPendingMessage(baseDescriptor.name),
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )
}

class LocalStreamingProviderRegistry(
    authStore: StreamingLocalAuthStore?,
    private val neteaseClient: LocalNeteaseStreamingClient = LocalNeteaseStreamingClient(authStore),
    private val qqMusicClient: LocalQqMusicStreamingClient = LocalQqMusicStreamingClient(authStore),
    private val luoxueClient: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient(
        neteaseClient = neteaseClient,
        qqMusicClient = qqMusicClient
    )
) {
    private val providersByName: Map<StreamingProviderName, StreamingProvider> =
        StreamingProviderCatalog.localFirstDescriptors().associate { descriptor ->
            val provider = when (descriptor.name) {
                StreamingProviderName.NETEASE -> LocalNeteaseStreamingProvider(neteaseClient, authStore)
                StreamingProviderName.QQ_MUSIC -> LocalQqMusicStreamingProvider(qqMusicClient, authStore)
                StreamingProviderName.LUOXUE -> LocalLuoxueStreamingProvider(luoxueClient)
                else -> LocalUnsupportedStreamingProvider(descriptor)
            }
            descriptor.name to provider
        }

    fun provider(name: StreamingProviderName): StreamingProvider? = providersByName[name]

    fun providers(): List<StreamingProvider> = providersByName.values.toList()

    fun descriptors(): List<StreamingProviderDescriptor> = providers().map { it.descriptor }

    fun canHandle(provider: StreamingProviderName): Boolean =
        provider(provider)?.descriptor?.enabled == true
}

internal fun localPendingMessage(provider: StreamingProviderName): String = when (provider) {
    StreamingProviderName.QQ_MUSIC -> "QQ 音乐本机直连已接入"
    StreamingProviderName.KUGOU -> "酷狗音乐本机解析待实现；可先配置网关使用"
    StreamingProviderName.BILIBILI -> "哔哩哔哩本机解析待实现；可先配置网关使用"
    StreamingProviderName.LUOXUE -> "LX/洛雪本机直连已接入，支持 kw/kg，wy/tx 复用对应本机登录"
    StreamingProviderName.M3U8 -> "M3U8 本机导入请走曲库导入；在线网关可提供更多能力"
    StreamingProviderName.PLUGIN -> "自定义插件本机运行时待实现；可先配置网关使用"
    else -> "该音源本机解析待实现；可先配置网关使用"
}
