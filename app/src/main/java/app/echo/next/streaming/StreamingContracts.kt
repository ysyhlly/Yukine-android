package app.echo.next.streaming

enum class StreamingProviderName(val wireName: String) {
    MOCK("mock"),
    NETEASE("netease"),
    QQ_MUSIC("qqmusic"),
    KUGOU("kugou"),
    BILIBILI("bilibili"),
    YOUTUBE("youtube"),
    SOUNDCLOUD("soundcloud"),
    SPOTIFY("spotify"),
    TIDAL("tidal"),
    M3U8("m3u8"),
    LUOXUE("luoxue"),
    PLUGIN("plugin");

    companion object {
        fun fromWireName(value: String): StreamingProviderName? {
            val normalized = value.trim()
                .lowercase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
            return when (normalized) {
                "lx", "lxmusic", "luoxuemusic", "luoxuemusicsource", "洛雪", "洛雪音乐", "洛雪音源" -> LUOXUE
                "qq", "qqmusic", "tx", "tencent", "tencentmusic" -> QQ_MUSIC
                "kg", "kugou" -> KUGOU
                "kw", "kuwo" -> LUOXUE
                "wy", "netease", "neteasecloud", "neteasemusic", "163", "163music" -> NETEASE
                else -> entries.firstOrNull {
                    it.wireName.replace("_", "") == normalized
                }
            }
        }
    }
}

enum class StreamingMediaType(val wireName: String) {
    TRACK("track"),
    ALBUM("album"),
    ARTIST("artist"),
    PLAYLIST("playlist"),
    MV("mv");

    companion object {
        fun fromWireName(value: String): StreamingMediaType? {
            return entries.firstOrNull { it.wireName == value.trim().lowercase() }
        }
    }
}

enum class StreamingAudioQuality(val wireName: String) {
    STANDARD("standard"),
    HIGH("high"),
    LOSSLESS("lossless"),
    HIRES("hires");

    companion object {
        fun fromWireName(value: String): StreamingAudioQuality? {
            return entries.firstOrNull { it.wireName == value.trim().lowercase() }
        }
    }
}

enum class StreamingProviderStatus {
    READY,
    NEEDS_ACCOUNT,
    DISABLED,
    ERROR
}

enum class StreamingErrorCode(val wireName: String) {
    AUTH_REQUIRED("AUTH_REQUIRED"),
    RATE_LIMITED("RATE_LIMITED"),
    REGION_BLOCKED("REGION_BLOCKED"),
    SOURCE_UNAVAILABLE("SOURCE_UNAVAILABLE"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION"),
    GATEWAY_UNAVAILABLE("GATEWAY_UNAVAILABLE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWireName(value: String?): StreamingErrorCode {
            val normalized = value.orEmpty().trim().uppercase()
            return entries.firstOrNull { it.wireName == normalized } ?: UNKNOWN
        }
    }
}

enum class StreamingAuthKind(val wireName: String) {
    NONE("none"),
    CUSTOM_TABS_APP_LINK("custom_tabs_app_link"),
    ISOLATED_WEB_VIEW_COOKIE("isolated_web_view_cookie"),
    REMOTE_GATEWAY("remote_gateway")
}

data class StreamingProviderCapabilities(
    val supportsSearch: Boolean,
    val supportsPlayback: Boolean,
    val supportsLyrics: Boolean = false,
    val supportsMv: Boolean = false,
    val supportsAuth: Boolean = false,
    val supportsFavorites: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val supportedMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK)
)

data class StreamingAuthState(
    val kind: StreamingAuthKind = StreamingAuthKind.NONE,
    val connected: Boolean = false,
    val accountDisplayName: String? = null,
    val accountUsername: String? = null,
    val accountAvatarUrl: String? = null,
    val statusMessage: String? = null
)

data class StreamingProviderDescriptor(
    val name: StreamingProviderName,
    val displayName: String,
    val enabled: Boolean = true,
    val capabilities: StreamingProviderCapabilities,
    val auth: StreamingAuthState = StreamingAuthState(),
    val status: StreamingProviderStatus = StreamingProviderStatus.READY,
    val statusMessage: String? = null
)

data class StreamingProviderHealth(
    val provider: StreamingProviderName,
    val available: Boolean,
    val authenticated: Boolean = false,
    val latencyMs: Long? = null,
    val errorCode: StreamingErrorCode? = null,
    val errorMessage: String? = null,
    val checkedAtEpochMs: Long? = null
)

data class StreamingProviderCapability(
    val provider: StreamingProviderName,
    val displayName: String,
    val enabled: Boolean,
    val status: StreamingProviderStatus = StreamingProviderStatus.READY,
    val supportsSearch: Boolean,
    val supportsPlayback: Boolean,
    val supportsAuth: Boolean = false,
    val supportsFavorites: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val supportsLyrics: Boolean = false,
    val supportsMv: Boolean = false,
    val supportedSearchMediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val actions: List<String> = emptyList()
)

data class StreamingGatewayLogEntry(
    val operation: String,
    val provider: StreamingProviderName? = null,
    val durationMs: Long,
    val cacheHit: Boolean = false,
    val errorCode: StreamingErrorCode? = null,
    val message: String? = null,
    val timestampMs: Long
)

data class StreamingGatewayDiagnostics(
    val totalRequests: Int = 0,
    val cacheHits: Int = 0,
    val recentLogs: List<StreamingGatewayLogEntry> = emptyList()
) {
    val cacheHitRate: Int
        get() = if (totalRequests <= 0) 0 else (cacheHits * 100 / totalRequests)
}

data class StreamingArtistRef(
    val provider: StreamingProviderName,
    val providerArtistId: String,
    val name: String
)

data class StreamingTrack(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val artists: List<StreamingArtistRef> = emptyList(),
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val coverThumbUrl: String? = null,
    val qualities: Set<StreamingAudioQuality> = emptySet(),
    val explicit: Boolean = false,
    val playable: Boolean = true,
    val unavailableReason: String? = null
) {
    val stableKey: String = "streaming:${provider.wireName}:$providerTrackId"
}

data class StreamingAlbum(
    val provider: StreamingProviderName,
    val providerAlbumId: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val trackCount: Int? = null
)

data class StreamingArtist(
    val provider: StreamingProviderName,
    val providerArtistId: String,
    val name: String,
    val avatarUrl: String? = null
)

data class StreamingPlaylist(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val title: String,
    val description: String? = null,
    val creator: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int? = null
)

data class StreamingMvItem(
    val provider: StreamingProviderName,
    val providerMvId: String,
    val providerTrackId: String? = null,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null
)

data class StreamingSearchRequest(
    val provider: StreamingProviderName,
    val query: String,
    val mediaTypes: Set<StreamingMediaType> = setOf(StreamingMediaType.TRACK),
    val page: Int = 1,
    val pageSize: Int = 20
)

data class StreamingSearchError(
    val code: StreamingErrorCode,
    val message: String? = null
)

data class StreamingSearchItem(
    val type: StreamingMediaType,
    val provider: StreamingProviderName,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val playable: Boolean = true,
    val track: StreamingTrack? = null,
    val album: StreamingAlbum? = null,
    val artist: StreamingArtist? = null,
    val playlist: StreamingPlaylist? = null,
    val mv: StreamingMvItem? = null
) {
    companion object {
        fun fromTrack(track: StreamingTrack): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.TRACK,
                provider = track.provider,
                id = track.providerTrackId,
                title = track.title,
                subtitle = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album).joinToString(" - ")
                    .takeIf { it.isNotBlank() },
                imageUrl = track.coverThumbUrl ?: track.coverUrl,
                playable = track.playable,
                track = track
            )
        }

        fun fromAlbum(album: StreamingAlbum): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.ALBUM,
                provider = album.provider,
                id = album.providerAlbumId,
                title = album.title,
                subtitle = album.artist,
                imageUrl = album.coverUrl,
                album = album
            )
        }

        fun fromArtist(artist: StreamingArtist): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.ARTIST,
                provider = artist.provider,
                id = artist.providerArtistId,
                title = artist.name,
                imageUrl = artist.avatarUrl,
                artist = artist
            )
        }

        fun fromPlaylist(playlist: StreamingPlaylist): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.PLAYLIST,
                provider = playlist.provider,
                id = playlist.providerPlaylistId,
                title = playlist.title,
                subtitle = playlist.creator ?: playlist.description,
                imageUrl = playlist.coverUrl,
                playlist = playlist
            )
        }

        fun fromMv(mv: StreamingMvItem): StreamingSearchItem {
            return StreamingSearchItem(
                type = StreamingMediaType.MV,
                provider = mv.provider,
                id = mv.providerMvId,
                title = mv.title,
                subtitle = mv.artist,
                imageUrl = mv.thumbnailUrl,
                mv = mv
            )
        }
    }
}

data class StreamingSearchResult(
    val provider: StreamingProviderName,
    val query: String,
    val page: Int,
    val pageSize: Int,
    val total: Int? = null,
    val hasMore: Boolean = false,
    val tracks: List<StreamingTrack> = emptyList(),
    val albums: List<StreamingAlbum> = emptyList(),
    val artists: List<StreamingArtist> = emptyList(),
    val playlists: List<StreamingPlaylist> = emptyList(),
    val mvs: List<StreamingMvItem> = emptyList(),
    val cached: Boolean = false,
    val items: List<StreamingSearchItem> = emptyList(),
    val error: StreamingSearchError? = null
) {
    val unifiedItems: List<StreamingSearchItem>
        get() = items.ifEmpty {
            tracks.map { StreamingSearchItem.fromTrack(it) } +
                albums.map { StreamingSearchItem.fromAlbum(it) } +
                artists.map { StreamingSearchItem.fromArtist(it) } +
                playlists.map { StreamingSearchItem.fromPlaylist(it) } +
                mvs.map { StreamingSearchItem.fromMv(it) }
        }
}

data class StreamingPlaylistRequest(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val page: Int = 1,
    val pageSize: Int = 500
)

data class StreamingPlaylistDetail(
    val provider: StreamingProviderName,
    val providerPlaylistId: String,
    val playlist: StreamingPlaylist? = null,
    val tracks: List<StreamingTrack> = emptyList(),
    val total: Int? = null,
    val page: Int = 1,
    val pageSize: Int = 500,
    val hasMore: Boolean = false,
    val cached: Boolean = false
)

data class StreamingHeartbeatRequest(
    val provider: StreamingProviderName,
    val providerTrackId: String? = null,
    val providerPlaylistId: String? = null,
    val count: Int = 30
)

data class StreamingPlaybackRequest(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val quality: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
)

data class StreamingPlaybackSource(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val url: String,
    val expiresAtEpochMs: Long? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val codec: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requiresProxy: Boolean = false,
    val supportsRange: Boolean = true
)

data class StreamingAuthRequest(
    val provider: StreamingProviderName,
    val redirectUri: String? = null,
    val forceRefresh: Boolean = false
)

data class StreamingAuthResult(
    val provider: StreamingProviderName,
    val state: StreamingAuthState,
    val launchUrl: String? = null,
    val statusMessage: String? = null
)

interface StreamingProvider {
    val descriptor: StreamingProviderDescriptor

    suspend fun search(request: StreamingSearchRequest): StreamingSearchResult

    suspend fun resolvePlayback(request: StreamingPlaybackRequest): StreamingPlaybackSource

    suspend fun playlist(request: StreamingPlaylistRequest): StreamingPlaylistDetail {
        return StreamingPlaylistDetail(
            provider = request.provider,
            providerPlaylistId = request.providerPlaylistId
        )
    }

    suspend fun authState(): StreamingAuthState = descriptor.auth

    suspend fun startAuth(request: StreamingAuthRequest): StreamingAuthResult {
        return StreamingAuthResult(request.provider, authState(), statusMessage = "Auth is not supported")
    }

    suspend fun completeAuth(callbackUri: String): StreamingAuthResult {
        return StreamingAuthResult(descriptor.name, authState(), statusMessage = "Auth is not supported")
    }

    suspend fun signOut(): StreamingAuthState = authState()

    suspend fun health(): StreamingProviderHealth {
        val state = authState()
        return StreamingProviderHealth(
            provider = descriptor.name,
            available = descriptor.enabled && descriptor.status != StreamingProviderStatus.ERROR,
            authenticated = state.connected,
            errorCode = if (descriptor.status == StreamingProviderStatus.ERROR) StreamingErrorCode.SOURCE_UNAVAILABLE else null,
            errorMessage = descriptor.statusMessage ?: state.statusMessage
        )
    }
}
