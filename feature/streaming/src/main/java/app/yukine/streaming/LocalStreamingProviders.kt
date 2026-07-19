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
        throw StreamingGatewayException(
            "QQ 音乐仅用于曲库与同步，不提供播放音源",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )

    override suspend fun authState(): StreamingAuthState =
        authStore?.authState(StreamingProviderName.QQ_MUSIC) ?: descriptor.auth

    override suspend fun signOut(): StreamingAuthState =
        authStore?.signOut(StreamingProviderName.QQ_MUSIC) ?: descriptor.auth
}

class LocalKugouStreamingProvider(
    private val client: LocalKugouStreamingClient,
    private val authStore: StreamingLocalAuthStore?,
    private val experimentalSyncStore: KugouExperimentalSyncStore? = null
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() {
            val auth = authStore?.authState(StreamingProviderName.KUGOU)
                ?: StreamingAuthState(
                    kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                    connected = false,
                    statusMessage = "无需登录即可搜索和播放；账号同步仍为实验功能"
                )
            val base = StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.KUGOU)
            val experiment = experimentalSyncStore?.status(auth.connected)
            val writeEnabled = experiment?.writeEnabled(auth.connected) == true
            return base.copy(
                auth = auth,
                capabilities = base.capabilities.copy(
                    supportsFavorites = writeEnabled,
                    supportsPlaylistCreate = writeEnabled,
                    supportsPlaylistWrite = writeEnabled,
                    supportsPlaylistDelete = writeEnabled,
                    supportsPlaylistRename = writeEnabled,
                    supportsPlaylistReorder = writeEnabled,
                    supportsFavoritesRead = writeEnabled,
                    supportsFavoritesWrite = writeEnabled
                ),
                status = StreamingProviderStatus.READY,
                statusMessage = when {
                    writeEnabled -> "酷狗本机音源与实验双向同步已就绪"
                    experiment?.userEnabled == true ->
                        "酷狗本机音源已就绪；${experiment.degradationReason.orEmpty()}"
                    auth.connected -> "酷狗本机音源已就绪；账号写入等待用户启用与契约验证"
                    else -> "无需登录即可搜索、播放和导入歌单"
                }
            )
        }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult = client.search(request)

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail = client.playlist(request)

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(request)

    override suspend fun authState(): StreamingAuthState =
        authStore?.authState(StreamingProviderName.KUGOU) ?: descriptor.auth

    override suspend fun signOut(): StreamingAuthState =
        authStore?.signOut(StreamingProviderName.KUGOU) ?: descriptor.auth
}

class LocalBilibiliStreamingProvider(
    private val client: LocalBilibiliStreamingClient,
    private val authStore: StreamingLocalAuthStore?
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() {
            val auth = authStore?.authState(StreamingProviderName.BILIBILI)
                ?: StreamingAuthState(
                    kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                    connected = false,
                    statusMessage = "未登录，点击登录"
                )
            return StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.BILIBILI)
                .copy(
                    auth = auth,
                    status = if (auth.connected) {
                        StreamingProviderStatus.READY
                    } else {
                        StreamingProviderStatus.NEEDS_ACCOUNT
                    },
                    statusMessage = auth.statusMessage
                        ?: if (auth.connected) "可导入视频、多 P 与收藏夹" else "登录后可导入视频、多 P 与收藏夹"
                )
        }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
        throw StreamingGatewayException(
            "哔哩哔哩本机音源不提供搜索，请粘贴视频或收藏夹链接",
            code = StreamingErrorCode.UNSUPPORTED_OPERATION
        )
    }

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail =
        client.playlist(request)

    override suspend fun userPlaylists(): List<StreamingPlaylist> = client.userPlaylists()

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(request)

    override suspend fun authState(): StreamingAuthState =
        authStore?.authState(StreamingProviderName.BILIBILI) ?: descriptor.auth

    override suspend fun signOut(): StreamingAuthState =
        authStore?.signOut(StreamingProviderName.BILIBILI) ?: descriptor.auth
}

class LocalLuoxueStreamingProvider(
    private val client: LocalLuoxueStreamingClient = LocalLuoxueStreamingClient(),
    private val sourceStore: LuoxueSourceStore? = null
) : StreamingProvider {
    override val descriptor: StreamingProviderDescriptor
        get() {
            val sources = sourceStore?.load().orEmpty()
            val activeSources = sources.filter { it.enabled && it.script.isNotBlank() }
            val sourceStatus = if (activeSources.isNotEmpty()) {
                "已启用 ${activeSources.size}/${sources.size} 个 LX JS 音源：" +
                    activeSources.take(3).joinToString("、") { it.name }
            } else if (sources.isNotEmpty()) {
                "已导入 ${sources.size} 个 LX JS 音源，当前均已停用或脚本文件不可用"
            } else {
                "可选择本地 JS 文件或网络链接导入 LX 音源；内置 kw/kg/wy/tx 子源仍可用"
            }
            return StreamingProviderCatalog.localFirstDescriptor(StreamingProviderName.LUOXUE)
                .copy(
                auth = StreamingAuthState(
                    kind = StreamingAuthKind.NONE,
                    connected = false,
                    statusMessage = sourceStatus
                ),
                status = StreamingProviderStatus.READY,
                statusMessage = sourceStatus
            )
        }

    override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult =
        client.search(
            request,
            sourceStore?.load().orEmpty().filter { it.enabled && it.script.isNotBlank() }
        )

    override suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail = client.playlist(request)

    override suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource =
        client.resolvePlayback(
            request,
            sourceStore?.load().orEmpty().filter { it.enabled && it.script.isNotBlank() }
        )

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
                supportsPlaylists = false,
                supportsPlaylistImport = false,
                supportsPlaylistReadSync = false,
                supportsPlaylistCreate = false,
                supportsPlaylistWrite = false,
                supportsPlaylistDelete = false,
                supportsPlaylistRename = false,
                supportsPlaylistReorder = false,
                supportsFavoritesRead = false,
                supportsFavoritesWrite = false,
                supportsAudioResolve = false,
                supportsAudioFallback = false,
                supportsAudioDownload = false,
                supportsAudioCache = false
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
    ),
    private val luoxueSourceStore: LuoxueSourceStore? = null,
    private val bilibiliClient: LocalBilibiliStreamingClient = LocalBilibiliStreamingClient(authStore),
    private val kugouClient: LocalKugouStreamingClient = LocalKugouStreamingClient(luoxueClient),
    private val kugouExperimentalSyncStore: KugouExperimentalSyncStore? = null
) {
    private val providersByName: Map<StreamingProviderName, StreamingProvider> =
        StreamingProviderCatalog.localFirstDescriptors().associate { descriptor ->
            val provider = when (descriptor.name) {
                StreamingProviderName.NETEASE -> LocalNeteaseStreamingProvider(neteaseClient, authStore)
                StreamingProviderName.QQ_MUSIC -> LocalQqMusicStreamingProvider(qqMusicClient, authStore)
                StreamingProviderName.KUGOU -> LocalKugouStreamingProvider(
                    kugouClient,
                    authStore,
                    kugouExperimentalSyncStore
                )
                StreamingProviderName.BILIBILI -> LocalBilibiliStreamingProvider(bilibiliClient, authStore)
                StreamingProviderName.LUOXUE -> LocalLuoxueStreamingProvider(luoxueClient, luoxueSourceStore)
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
    StreamingProviderName.KUGOU -> "酷狗音乐本机直连已接入"
    StreamingProviderName.BILIBILI -> "哔哩哔哩本机直连已接入，登录后可导入视频与收藏夹"
    StreamingProviderName.LUOXUE -> "LX/洛雪本机直连已接入，支持 kw/kg，wy/tx 复用对应本机登录"
    StreamingProviderName.M3U8 -> "M3U8 本机导入请走曲库导入；在线网关可提供更多能力"
    StreamingProviderName.PLUGIN -> "自定义插件本机运行时待实现；可先配置网关使用"
    else -> "该音源本机解析待实现；可先配置网关使用"
}
