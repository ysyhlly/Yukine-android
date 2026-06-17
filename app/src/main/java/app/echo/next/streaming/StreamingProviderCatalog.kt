package app.echo.next.streaming

object StreamingProviderCatalog {
    fun gatewayBackedDescriptors(): List<StreamingProviderDescriptor> {
        return listOf(
            descriptor(StreamingProviderName.NETEASE, "NetEase Cloud Music", authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE),
            descriptor(StreamingProviderName.QQ_MUSIC, "QQ Music", authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE),
            descriptor(StreamingProviderName.KUGOU, "Kugou", authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE),
            descriptor(StreamingProviderName.BILIBILI, "Bilibili", supportsMv = true, authKind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE),
            descriptor(StreamingProviderName.YOUTUBE, "YouTube"),
            descriptor(StreamingProviderName.SOUNDCLOUD, "SoundCloud"),
            descriptor(StreamingProviderName.SPOTIFY, "Spotify", authKind = StreamingAuthKind.CUSTOM_TABS_APP_LINK),
            descriptor(StreamingProviderName.TIDAL, "TIDAL", authKind = StreamingAuthKind.CUSTOM_TABS_APP_LINK),
            descriptor(StreamingProviderName.M3U8, "M3U8", supportsAuth = false),
            descriptor(
                StreamingProviderName.LUOXUE,
                "洛雪音源",
                supportsAuth = false,
                statusMessage = "Compatible with LX Music custom-source adapters"
            ),
            descriptor(StreamingProviderName.PLUGIN, "Plugin", supportsAuth = false)
        )
    }

    private fun descriptor(
        name: StreamingProviderName,
        displayName: String,
        supportsAuth: Boolean = true,
        supportsMv: Boolean = false,
        authKind: StreamingAuthKind = StreamingAuthKind.REMOTE_GATEWAY,
        statusMessage: String = "Provided by the streaming gateway"
    ): StreamingProviderDescriptor {
        val auth = StreamingAuthState(
            kind = if (supportsAuth) authKind else StreamingAuthKind.NONE,
            connected = false
        )
        return StreamingProviderDescriptor(
            name = name,
            displayName = displayName,
            enabled = true,
            capabilities = StreamingProviderCapabilities(
                supportsSearch = true,
                supportsPlayback = true,
                supportsLyrics = true,
                supportsMv = supportsMv,
                supportsAuth = supportsAuth,
                supportsFavorites = supportsAuth,
                supportsPlaylists = true,
                supportedMediaTypes = setOf(
                    StreamingMediaType.TRACK,
                    StreamingMediaType.ALBUM,
                    StreamingMediaType.ARTIST,
                    StreamingMediaType.PLAYLIST
                )
            ),
            auth = auth,
            status = StreamingProviderStatus.NEEDS_ACCOUNT.takeIf { supportsAuth } ?: StreamingProviderStatus.READY,
            statusMessage = statusMessage
        )
    }
}
