package app.yukine.streaming

object StreamingProviderCatalog {
    fun gatewayBackedDescriptors(): List<StreamingProviderDescriptor> = providerDescriptors(localFirst = false)

    fun localFirstDescriptors(): List<StreamingProviderDescriptor> = providerDescriptors(localFirst = true)

    fun localFirstDescriptor(provider: StreamingProviderName): StreamingProviderDescriptor =
        localFirstDescriptors().firstOrNull { it.name == provider }
            ?: gatewayBackedDescriptors().first { it.name == provider }

    private fun providerDescriptors(localFirst: Boolean): List<StreamingProviderDescriptor> {
        return listOf(
            descriptor(
                StreamingProviderName.NETEASE,
                "网易云音乐",
                authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                statusMessage = if (localFirst) "本机直连，登录后可用搜索、播放、歌单和推荐" else ""
            ),
            descriptor(
                StreamingProviderName.QQ_MUSIC,
                "QQ 音乐",
                supportsAuth = true,
                authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                localFirst = localFirst,
                statusMessage = "红心与歌单双向同步已启用；播放音源已禁用"
            ),
            descriptor(
                StreamingProviderName.KUGOU,
                "酷狗音乐",
                authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                localFirst = localFirst
            ),
            descriptor(
                StreamingProviderName.BILIBILI,
                "哔哩哔哩",
                supportsMv = true,
                authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                localFirst = localFirst
            ),
            descriptor(StreamingProviderName.YOUTUBE, "YouTube", localFirst = localFirst),
            descriptor(StreamingProviderName.SOUNDCLOUD, "SoundCloud", localFirst = localFirst),
            descriptor(
                StreamingProviderName.SPOTIFY,
                "Spotify",
                authKind = StreamingAuthKind.CUSTOM_TABS_APP_LINK,
                localFirst = localFirst
            ),
            descriptor(
                StreamingProviderName.TIDAL,
                "TIDAL",
                authKind = StreamingAuthKind.CUSTOM_TABS_APP_LINK,
                localFirst = localFirst
            ),
            descriptor(StreamingProviderName.M3U8, "M3U8 列表", supportsAuth = false, localFirst = localFirst),
            descriptor(
                StreamingProviderName.LUOXUE,
                "LX/洛雪音源",
                supportsAuth = false,
                localFirst = localFirst,
                statusMessage = if (localFirst) {
                    "无需登录，支持 LX 的 kw/kg 子源；wy/tx 复用网易云/QQ 登录"
                } else {
                    "支持 LX 兼容网关聚合搜索酷我、咪咕等音源"
                }
            ),
            descriptor(
                StreamingProviderName.PLUGIN,
                "自定义插件",
                supportsAuth = false,
                localFirst = localFirst
            )
        )
    }

    private fun descriptor(
        name: StreamingProviderName,
        displayName: String,
        supportsAuth: Boolean = true,
        supportsMv: Boolean = false,
        authKind: StreamingAuthKind = StreamingAuthKind.REMOTE_GATEWAY,
        localFirst: Boolean = false,
        statusMessage: String = ""
    ): StreamingProviderDescriptor {
        val localPending = localFirst && !localCapableProvider(name)
        val auth = StreamingAuthState(
            kind = if (supportsAuth) authKind else StreamingAuthKind.NONE,
            connected = false,
            statusMessage = if (localPending) localPendingMessage(name) else null
        )
        return StreamingProviderDescriptor(
            name = name,
            displayName = displayName,
            enabled = !localPending,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = !localPending,
                supportsPlayback = !localPending && name != StreamingProviderName.QQ_MUSIC,
                supportsLyrics = !localPending,
                supportsMv = supportsMv && !localPending,
                supportsAuth = supportsAuth && !localPending,
                supportsFavorites = supportsAuth && !localPending,
                supportsPlaylists = !localPending,
                supportedMediaTypes = if (localPending) {
                    emptySet()
                } else {
                    setOf(
                        StreamingMediaType.TRACK,
                        StreamingMediaType.ALBUM,
                        StreamingMediaType.ARTIST,
                        StreamingMediaType.PLAYLIST
                    )
                },
                supportsPlaylistImport = !localPending,
                supportsPlaylistReadSync = !localPending,
                supportsPlaylistCreate = !localPending && supportsAuth,
                supportsPlaylistWrite = !localPending && supportsAuth,
                supportsPlaylistDelete = !localPending && supportsAuth,
                supportsPlaylistRename = !localPending && supportsAuth,
                supportsPlaylistReorder = !localPending && supportsAuth,
                supportsFavoritesRead = !localPending && supportsAuth,
                supportsFavoritesWrite = !localPending && supportsAuth,
                supportsAudioResolve = !localPending && name != StreamingProviderName.QQ_MUSIC,
                supportsAudioFallback = !localPending && name != StreamingProviderName.QQ_MUSIC,
                supportsAudioDownload = !localPending && name != StreamingProviderName.QQ_MUSIC,
                supportsAudioCache = !localPending && name != StreamingProviderName.QQ_MUSIC
            ),
            auth = auth,
            status = when {
                localPending -> StreamingProviderStatus.DISABLED
                supportsAuth -> StreamingProviderStatus.NEEDS_ACCOUNT
                else -> StreamingProviderStatus.READY
            },
            statusMessage = statusMessage.ifBlank {
                if (localPending) localPendingMessage(name) else ""
            }
        )
    }

    private fun localCapableProvider(name: StreamingProviderName): Boolean {
        return name == StreamingProviderName.NETEASE ||
            name == StreamingProviderName.QQ_MUSIC ||
            name == StreamingProviderName.LUOXUE
    }
}
