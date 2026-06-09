package app.echo.next.streaming

import org.json.JSONArray
import org.json.JSONObject

internal object StreamingGatewayJson {
    data class GatewayError(
        val code: StreamingErrorCode,
        val message: String?
    )

    fun searchRequest(request: StreamingSearchRequest): JSONObject {
        return JSONObject()
            .put("provider", request.provider.wireName)
            .put("query", request.query)
            .put("mediaTypes", JSONArray(request.mediaTypes.map { it.wireName }))
            .put("page", request.page)
            .put("pageSize", request.pageSize)
    }

    fun playbackRequest(request: StreamingPlaybackRequest): JSONObject {
        return JSONObject()
            .put("provider", request.provider.wireName)
            .put("providerTrackId", request.providerTrackId)
            .put("quality", request.quality.wireName)
    }

    fun authRequest(request: StreamingAuthRequest): JSONObject {
        return JSONObject()
            .put("provider", request.provider.wireName)
            .put("redirectUri", request.redirectUri)
            .put("forceRefresh", request.forceRefresh)
    }

    fun completeAuthRequest(provider: StreamingProviderName, callbackUri: String, cookieHeader: String? = null): JSONObject {
        val value = JSONObject()
            .put("provider", provider.wireName)
            .put("callbackUri", callbackUri)
        if (!cookieHeader.isNullOrBlank()) {
            value.put("cookieHeader", cookieHeader)
        }
        return value
    }

    fun searchResultJson(result: StreamingSearchResult): String {
        return JSONObject()
            .put("provider", result.provider.wireName)
            .put("query", result.query)
            .put("page", result.page)
            .put("pageSize", result.pageSize)
            .put("total", result.total)
            .put("hasMore", result.hasMore)
            .put("tracks", JSONArray(result.tracks.map { trackJson(it) }))
            .put("albums", JSONArray(result.albums.map { albumJson(it) }))
            .put("artists", JSONArray(result.artists.map { artistJson(it) }))
            .put("playlists", JSONArray(result.playlists.map { playlistJson(it) }))
            .put("mvs", JSONArray(result.mvs.map { mvJson(it) }))
            .put("items", JSONArray(result.unifiedItems.map { searchItemJson(it) }))
            .put("error", result.error?.let { searchErrorJson(it) })
            .put("cached", result.cached)
            .toString()
    }

    fun playlistDetailJson(detail: StreamingPlaylistDetail): String {
        return JSONObject()
            .put("provider", detail.provider.wireName)
            .put("providerPlaylistId", detail.providerPlaylistId)
            .put("playlist", detail.playlist?.let { playlistJson(it) })
            .put("tracks", JSONArray(detail.tracks.map { trackJson(it) }))
            .put("total", detail.total)
            .put("page", detail.page)
            .put("pageSize", detail.pageSize)
            .put("hasMore", detail.hasMore)
            .put("cached", detail.cached)
            .toString()
    }

    fun playbackSourceJson(source: StreamingPlaybackSource): String {
        return JSONObject()
            .put("provider", source.provider.wireName)
            .put("providerTrackId", source.providerTrackId)
            .put("url", source.url)
            .put("expiresAtEpochMs", source.expiresAtEpochMs)
            .put("mimeType", source.mimeType)
            .put("bitrate", source.bitrate)
            .put("sampleRate", source.sampleRate)
            .put("bitDepth", source.bitDepth)
            .put("codec", source.codec)
            .put("headers", JSONObject(source.headers))
            .put("requiresProxy", source.requiresProxy)
            .put("supportsRange", source.supportsRange)
            .toString()
    }

    fun authStateJson(provider: StreamingProviderName, state: StreamingAuthState): String {
        return JSONObject()
            .put("provider", provider.wireName)
            .put("state", authStateJsonObject(state))
            .toString()
    }

    fun providerDescriptors(json: String): List<StreamingProviderDescriptor> {
        val root = json.trim()
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            JSONObject(root).optJSONArray("providers") ?: JSONArray()
        }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { providerDescriptor(it) }
    }

    fun providerHealth(json: String): List<StreamingProviderHealth> {
        val root = json.trim()
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            JSONObject(root).optJSONArray("providers")
                ?: JSONObject(root).optJSONArray("health")
                ?: JSONArray()
        }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { providerHealth(it) }
    }

    fun providerCapabilities(json: String): List<StreamingProviderCapability> {
        val root = json.trim()
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            JSONObject(root).optJSONArray("providers")
                ?: JSONObject(root).optJSONArray("capabilities")
                ?: JSONArray()
        }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { providerCapability(it) }
    }

    fun gatewayError(json: String): GatewayError {
        if (json.isBlank()) {
            return GatewayError(StreamingErrorCode.UNKNOWN, null)
        }
        return try {
            val value = JSONObject(json)
            val error = value.optJSONObject("error") ?: value
            GatewayError(
                code = StreamingErrorCode.fromWireName(error.optString("code")),
                message = error.optionalString("message") ?: error.optionalString("statusMessage")
            )
        } catch (_: Exception) {
            GatewayError(StreamingErrorCode.UNKNOWN, json)
        }
    }

    fun searchResult(json: String): StreamingSearchResult {
        val value = JSONObject(json)
        val provider = providerName(value.optString("provider")) ?: StreamingProviderName.MOCK
        val explicitItems = searchItems(value.optJSONArray("items"), provider)
        val legacyTracks = tracks(value.optJSONArray("tracks"), provider)
        val legacyAlbums = albums(value.optJSONArray("albums"), provider)
        val legacyArtists = artists(value.optJSONArray("artists"), provider)
        val legacyPlaylists = playlists(value.optJSONArray("playlists"), provider)
        val legacyMvs = mvs(value.optJSONArray("mvs"), provider)
        val resultTracks = legacyTracks.ifEmpty { explicitItems.mapNotNull { it.track } }
        val resultAlbums = legacyAlbums.ifEmpty { explicitItems.mapNotNull { it.album } }
        val resultArtists = legacyArtists.ifEmpty { explicitItems.mapNotNull { it.artist } }
        val resultPlaylists = legacyPlaylists.ifEmpty { explicitItems.mapNotNull { it.playlist } }
        val resultMvs = legacyMvs.ifEmpty { explicitItems.mapNotNull { it.mv } }
        return StreamingSearchResult(
            provider = provider,
            query = value.optString("query"),
            page = value.optInt("page", 1),
            pageSize = value.optInt("pageSize", 20),
            total = value.optionalInt("total"),
            hasMore = value.optBoolean("hasMore", false),
            tracks = resultTracks,
            albums = resultAlbums,
            artists = resultArtists,
            playlists = resultPlaylists,
            mvs = resultMvs,
            cached = value.optBoolean("cached", false),
            items = explicitItems.ifEmpty {
                resultTracks.map { StreamingSearchItem.fromTrack(it) } +
                    resultAlbums.map { StreamingSearchItem.fromAlbum(it) } +
                    resultArtists.map { StreamingSearchItem.fromArtist(it) } +
                    resultPlaylists.map { StreamingSearchItem.fromPlaylist(it) } +
                    resultMvs.map { StreamingSearchItem.fromMv(it) }
            },
            error = searchError(value.optJSONObject("error"))
        )
    }

    fun playlistDetail(json: String): StreamingPlaylistDetail {
        val value = JSONObject(json)
        val playlistValue = value.optJSONObject("playlist")
        val provider = providerName(value.optString("provider"))
            ?: providerName(playlistValue?.optString("provider").orEmpty())
            ?: StreamingProviderName.MOCK
        val playlist = playlistValue?.let { playlists(JSONArray().put(it), provider).firstOrNull() }
        val providerPlaylistId = value.optionalString("providerPlaylistId")
            ?: value.optionalString("id")
            ?: playlist?.providerPlaylistId
            ?: ""
        return StreamingPlaylistDetail(
            provider = provider,
            providerPlaylistId = providerPlaylistId,
            playlist = playlist,
            tracks = tracks(value.optJSONArray("tracks"), provider),
            total = value.optionalInt("total") ?: playlist?.trackCount,
            page = value.optInt("page", 1),
            pageSize = value.optInt("pageSize", 500),
            hasMore = value.optBoolean("hasMore", false),
            cached = value.optBoolean("cached", false)
        )
    }

    fun userPlaylists(json: String, fallbackProvider: StreamingProviderName): List<StreamingPlaylist> {
        val root = json.trim()
        if (root.isEmpty()) {
            return emptyList()
        }
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            val obj = JSONObject(root)
            obj.optJSONArray("playlists")
                ?: obj.optJSONArray("items")
                ?: JSONArray()
        }
        return playlists(array, fallbackProvider)
    }

    fun tracks(json: String, fallbackProvider: StreamingProviderName): List<StreamingTrack> {
        val root = json.trim()
        if (root.isEmpty()) {
            return emptyList()
        }
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            val obj = JSONObject(root)
            obj.optJSONArray("tracks")
                ?: obj.optJSONArray("items")
                ?: JSONArray()
        }
        return tracks(array, fallbackProvider)
    }

    fun playbackSource(json: String): StreamingPlaybackSource {
        val value = JSONObject(json)
        val provider = providerName(value.optString("provider")) ?: StreamingProviderName.MOCK
        return StreamingPlaybackSource(
            provider = provider,
            providerTrackId = value.optString("providerTrackId"),
            url = value.optString("url"),
            expiresAtEpochMs = value.optionalLong("expiresAtEpochMs"),
            mimeType = value.optionalString("mimeType"),
            bitrate = value.optionalInt("bitrate"),
            sampleRate = value.optionalInt("sampleRate"),
            bitDepth = value.optionalInt("bitDepth"),
            codec = value.optionalString("codec"),
            headers = stringMap(value.optJSONObject("headers")),
            requiresProxy = value.optBoolean("requiresProxy", false),
            supportsRange = value.optBoolean("supportsRange", true)
        )
    }

    fun authState(json: String, fallbackProvider: StreamingProviderName): StreamingAuthState {
        val value = JSONObject(json)
        return authState(value.optJSONObject("state") ?: value, fallbackProvider)
    }

    fun authResult(json: String, fallbackProvider: StreamingProviderName): StreamingAuthResult {
        val value = JSONObject(json)
        val provider = providerName(value.optString("provider")) ?: fallbackProvider
        return StreamingAuthResult(
            provider = provider,
            state = authState(value.optJSONObject("state") ?: value, provider),
            launchUrl = value.optionalString("launchUrl"),
            statusMessage = value.optionalString("statusMessage")
        )
    }

    private fun providerDescriptor(value: JSONObject): StreamingProviderDescriptor? {
        val provider = providerName(value.optString("name")) ?: return null
        val capabilities = capabilities(value.optJSONObject("capabilities") ?: value)
        val auth = authState(value.optJSONObject("auth") ?: JSONObject(), provider)
        return StreamingProviderDescriptor(
            name = provider,
            displayName = value.optString("displayName", provider.wireName),
            enabled = value.optBoolean("enabled", true),
            capabilities = capabilities,
            auth = auth,
            status = providerStatus(value.optString("status")) ?: StreamingProviderStatus.READY,
            statusMessage = value.optionalString("statusMessage")
        )
    }

    private fun providerHealth(value: JSONObject): StreamingProviderHealth? {
        val provider = providerName(value.optString("provider", value.optString("name"))) ?: return null
        return StreamingProviderHealth(
            provider = provider,
            available = value.optBoolean("available", value.optBoolean("ok", false)),
            authenticated = value.optBoolean("authenticated", value.optBoolean("connected", false)),
            latencyMs = value.optionalLong("latencyMs"),
            errorCode = value.optionalString("errorCode")
                ?.let { StreamingErrorCode.fromWireName(it) }
                ?: value.optJSONObject("error")?.optionalString("code")?.let { StreamingErrorCode.fromWireName(it) },
            errorMessage = value.optionalString("errorMessage")
                ?: value.optionalString("statusMessage")
                ?: value.optJSONObject("error")?.optionalString("message"),
            checkedAtEpochMs = value.optionalLong("checkedAtEpochMs")
                ?: value.optionalLong("checkedAt")
        )
    }

    private fun providerCapability(value: JSONObject): StreamingProviderCapability? {
        val descriptor = providerDescriptor(value)
        val provider = descriptor?.name ?: providerName(value.optString("provider", value.optString("name"))) ?: return null
        val derived = descriptor?.let { StreamingCapabilityResolver.providerCapability(it) }
        val enabled = value.optionalBoolean("enabled") ?: derived?.enabled ?: true
        val rawCapabilities = value.optJSONObject("capabilities") ?: value
        val rawSupportsSearch = rawCapabilities.optionalBoolean("canSearch")
            ?: rawCapabilities.optionalBoolean("supportsSearch")
            ?: derived?.supportsSearch
            ?: false
        val rawSupportsPlayback = rawCapabilities.optionalBoolean("canPlayback")
            ?: rawCapabilities.optionalBoolean("supportsPlayback")
            ?: derived?.supportsPlayback
            ?: false
        val rawSupportsAuth = rawCapabilities.optionalBoolean("canAuth")
            ?: rawCapabilities.optionalBoolean("supportsAuth")
            ?: derived?.supportsAuth
            ?: false
        val rawSupportsFavorites = rawCapabilities.optionalBoolean("canFavorites")
            ?: rawCapabilities.optionalBoolean("supportsFavorites")
            ?: derived?.supportsFavorites
            ?: false
        val rawSupportsPlaylists = rawCapabilities.optionalBoolean("canPlaylists")
            ?: rawCapabilities.optionalBoolean("supportsPlaylists")
            ?: derived?.supportsPlaylists
            ?: false
        val rawSupportsLyrics = rawCapabilities.optionalBoolean("canLyrics")
            ?: rawCapabilities.optionalBoolean("supportsLyrics")
            ?: derived?.supportsLyrics
            ?: false
        val rawSupportsMv = rawCapabilities.optionalBoolean("canMv")
            ?: rawCapabilities.optionalBoolean("supportsMv")
            ?: derived?.supportsMv
            ?: false
        val supportsSearch = enabled && rawSupportsSearch
        val supportsPlayback = enabled && rawSupportsPlayback
        val supportsAuth = enabled && rawSupportsAuth
        val supportsFavorites = enabled && rawSupportsFavorites
        val supportsPlaylists = enabled && rawSupportsPlaylists
        val supportsLyrics = enabled && rawSupportsLyrics
        val supportsMv = enabled && rawSupportsMv
        val mediaTypeArray = rawCapabilities.optJSONArray("supportedSearchMediaTypes")
            ?: rawCapabilities.optJSONArray("supportedMediaTypes")
        val supportedMediaTypes = if (!supportsSearch) {
            emptySet()
        } else mediaTypeArray?.let { mediaTypes(it) }
            ?: derived?.supportedSearchMediaTypes
            ?: if (supportsSearch) setOf(StreamingMediaType.TRACK) else emptySet()
        val actions = stringList(value.optJSONArray("actions")).ifEmpty {
            derived?.actions ?: listOfNotNull(
                "search".takeIf { supportsSearch },
                "playback".takeIf { supportsPlayback },
                "auth".takeIf { supportsAuth },
                "favorites".takeIf { supportsFavorites },
                "playlists".takeIf { supportsPlaylists }
            )
        }
        return StreamingProviderCapability(
            provider = provider,
            displayName = value.optionalString("displayName") ?: derived?.displayName ?: provider.wireName,
            enabled = enabled,
            status = providerStatus(value.optString("status")) ?: derived?.status ?: StreamingProviderStatus.READY,
            supportsSearch = supportsSearch,
            supportsPlayback = supportsPlayback,
            supportsAuth = supportsAuth,
            supportsFavorites = supportsFavorites,
            supportsPlaylists = supportsPlaylists,
            supportsLyrics = supportsLyrics,
            supportsMv = supportsMv,
            supportedSearchMediaTypes = supportedMediaTypes,
            actions = actions
        )
    }

    private fun capabilities(value: JSONObject): StreamingProviderCapabilities {
        return StreamingProviderCapabilities(
            supportsSearch = value.optBoolean("supportsSearch", false),
            supportsPlayback = value.optBoolean("supportsPlayback", false),
            supportsLyrics = value.optBoolean("supportsLyrics", false),
            supportsMv = value.optBoolean("supportsMv", false),
            supportsAuth = value.optBoolean("supportsAuth", value.optBoolean("requiresAccount", false)),
            supportsFavorites = value.optBoolean("supportsFavorites", false),
            supportsPlaylists = value.optBoolean("supportsPlaylists", true),
            supportedMediaTypes = mediaTypes(value.optJSONArray("supportedMediaTypes"))
        )
    }

    private fun authState(value: JSONObject, provider: StreamingProviderName): StreamingAuthState {
        val catalogAuth = StreamingProviderCatalog.gatewayBackedDescriptors()
            .firstOrNull { it.name == provider }
            ?.auth
        return StreamingAuthState(
            kind = authKind(value.optString("kind")) ?: catalogAuth?.kind ?: StreamingAuthKind.REMOTE_GATEWAY,
            connected = value.optBoolean("connected", false),
            accountDisplayName = value.optionalString("accountDisplayName"),
            accountUsername = value.optionalString("accountUsername"),
            accountAvatarUrl = value.optionalString("accountAvatarUrl"),
            statusMessage = value.optionalString("statusMessage")
        )
    }

    private fun tracks(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingTrack> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            val provider = providerName(value.optString("provider")) ?: fallbackProvider
            StreamingTrack(
                provider = provider,
                providerTrackId = value.optString("providerTrackId", value.optString("id")),
                title = value.optString("title"),
                artist = value.optString("artist"),
                artists = artistRefs(value.optJSONArray("artists"), provider),
                album = value.optionalString("album"),
                albumId = value.optionalString("albumId"),
                durationMs = value.optionalLong("durationMs") ?: value.optionalLong("duration"),
                coverUrl = value.optionalString("coverUrl"),
                coverThumbUrl = value.optionalString("coverThumb") ?: value.optionalString("coverThumbUrl"),
                qualities = audioQualities(value.optJSONArray("qualities")),
                explicit = value.optBoolean("explicit", false),
                playable = value.optBoolean("playable", true),
                unavailableReason = value.optionalString("unavailableReason")
            )
        }
    }

    private fun albums(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingAlbum> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            val provider = providerName(value.optString("provider")) ?: fallbackProvider
            StreamingAlbum(
                provider = provider,
                providerAlbumId = value.optString("providerAlbumId", value.optString("id")),
                title = value.optString("title"),
                artist = value.optString("artist"),
                coverUrl = value.optionalString("coverUrl"),
                trackCount = value.optionalInt("trackCount")
            )
        }
    }

    private fun artists(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingArtist> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            val provider = providerName(value.optString("provider")) ?: fallbackProvider
            StreamingArtist(
                provider = provider,
                providerArtistId = value.optString("providerArtistId", value.optString("id")),
                name = value.optString("name"),
                avatarUrl = value.optionalString("avatarUrl")
            )
        }
    }

    private fun playlists(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingPlaylist> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            val provider = providerName(value.optString("provider")) ?: fallbackProvider
            StreamingPlaylist(
                provider = provider,
                providerPlaylistId = value.optString("providerPlaylistId", value.optString("id")),
                title = value.optString("title"),
                description = value.optionalString("description"),
                creator = value.optionalString("creator"),
                coverUrl = value.optionalString("coverUrl"),
                trackCount = value.optionalInt("trackCount")
            )
        }
    }

    private fun mvs(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingMvItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            val provider = providerName(value.optString("provider")) ?: fallbackProvider
            StreamingMvItem(
                provider = provider,
                providerMvId = value.optString("providerMvId", value.optString("id")),
                providerTrackId = value.optionalString("providerTrackId"),
                title = value.optString("title"),
                artist = value.optString("artist"),
                durationMs = value.optionalLong("durationMs") ?: value.optionalLong("duration"),
                thumbnailUrl = value.optionalString("thumbnailUrl")
            )
        }
    }

    private fun searchItems(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingSearchItem> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { searchItem(it, fallbackProvider) }
    }

    private fun searchItem(value: JSONObject, fallbackProvider: StreamingProviderName): StreamingSearchItem? {
        val type = StreamingMediaType.fromWireName(value.optString("type")) ?: return null
        val provider = providerName(value.optString("provider")) ?: fallbackProvider
        return when (type) {
            StreamingMediaType.TRACK -> {
                val track = value.optJSONObject("track")
                    ?.let { tracks(JSONArray().put(it), provider).firstOrNull() }
                    ?: StreamingTrack(
                        provider = provider,
                        providerTrackId = searchItemId(value, type),
                        title = searchItemTitle(value, type),
                        artist = value.optionalString("artist") ?: value.optionalString("subtitle").orEmpty(),
                        album = value.optionalString("album"),
                        durationMs = value.optionalLong("durationMs") ?: value.optionalLong("duration"),
                        coverUrl = value.optionalString("coverUrl") ?: value.optionalString("imageUrl"),
                        coverThumbUrl = value.optionalString("coverThumbUrl") ?: value.optionalString("coverThumb"),
                        playable = value.optBoolean("playable", true),
                        unavailableReason = value.optionalString("unavailableReason")
                    )
                StreamingSearchItem.fromTrack(track).copy(
                    title = value.optionalString("title") ?: track.title,
                    subtitle = value.optionalString("subtitle") ?: StreamingSearchItem.fromTrack(track).subtitle,
                    imageUrl = value.optionalString("imageUrl") ?: track.coverThumbUrl ?: track.coverUrl,
                    playable = value.optBoolean("playable", track.playable)
                )
            }
            StreamingMediaType.ALBUM -> {
                val album = value.optJSONObject("album")
                    ?.let { albums(JSONArray().put(it), provider).firstOrNull() }
                    ?: StreamingAlbum(
                        provider = provider,
                        providerAlbumId = searchItemId(value, type),
                        title = searchItemTitle(value, type),
                        artist = value.optionalString("artist") ?: value.optionalString("subtitle").orEmpty(),
                        coverUrl = value.optionalString("coverUrl") ?: value.optionalString("imageUrl"),
                        trackCount = value.optionalInt("trackCount")
                    )
                StreamingSearchItem.fromAlbum(album).copy(
                    title = value.optionalString("title") ?: album.title,
                    subtitle = value.optionalString("subtitle") ?: album.artist,
                    imageUrl = value.optionalString("imageUrl") ?: album.coverUrl,
                    playable = value.optBoolean("playable", true)
                )
            }
            StreamingMediaType.ARTIST -> {
                val artist = value.optJSONObject("artist")
                    ?.let { artists(JSONArray().put(it), provider).firstOrNull() }
                    ?: StreamingArtist(
                        provider = provider,
                        providerArtistId = searchItemId(value, type),
                        name = value.optionalString("name") ?: searchItemTitle(value, type),
                        avatarUrl = value.optionalString("avatarUrl") ?: value.optionalString("imageUrl")
                    )
                StreamingSearchItem.fromArtist(artist).copy(
                    title = value.optionalString("title") ?: artist.name,
                    subtitle = value.optionalString("subtitle"),
                    imageUrl = value.optionalString("imageUrl") ?: artist.avatarUrl,
                    playable = value.optBoolean("playable", true)
                )
            }
            StreamingMediaType.PLAYLIST -> {
                val playlist = value.optJSONObject("playlist")
                    ?.let { playlists(JSONArray().put(it), provider).firstOrNull() }
                    ?: StreamingPlaylist(
                        provider = provider,
                        providerPlaylistId = searchItemId(value, type),
                        title = searchItemTitle(value, type),
                        description = value.optionalString("description"),
                        creator = value.optionalString("creator") ?: value.optionalString("subtitle"),
                        coverUrl = value.optionalString("coverUrl") ?: value.optionalString("imageUrl"),
                        trackCount = value.optionalInt("trackCount")
                    )
                StreamingSearchItem.fromPlaylist(playlist).copy(
                    title = value.optionalString("title") ?: playlist.title,
                    subtitle = value.optionalString("subtitle") ?: playlist.creator ?: playlist.description,
                    imageUrl = value.optionalString("imageUrl") ?: playlist.coverUrl,
                    playable = value.optBoolean("playable", true)
                )
            }
            StreamingMediaType.MV -> {
                val mv = value.optJSONObject("mv")
                    ?.let { mvs(JSONArray().put(it), provider).firstOrNull() }
                    ?: StreamingMvItem(
                        provider = provider,
                        providerMvId = searchItemId(value, type),
                        providerTrackId = value.optionalString("providerTrackId"),
                        title = searchItemTitle(value, type),
                        artist = value.optionalString("artist") ?: value.optionalString("subtitle").orEmpty(),
                        durationMs = value.optionalLong("durationMs") ?: value.optionalLong("duration"),
                        thumbnailUrl = value.optionalString("thumbnailUrl") ?: value.optionalString("imageUrl")
                    )
                StreamingSearchItem.fromMv(mv).copy(
                    title = value.optionalString("title") ?: mv.title,
                    subtitle = value.optionalString("subtitle") ?: mv.artist,
                    imageUrl = value.optionalString("imageUrl") ?: mv.thumbnailUrl,
                    playable = value.optBoolean("playable", true)
                )
            }
        }
    }

    private fun searchItemId(value: JSONObject, type: StreamingMediaType): String {
        return value.optionalString("id")
            ?: when (type) {
                StreamingMediaType.TRACK -> value.optionalString("providerTrackId")
                StreamingMediaType.ALBUM -> value.optionalString("providerAlbumId")
                StreamingMediaType.ARTIST -> value.optionalString("providerArtistId")
                StreamingMediaType.PLAYLIST -> value.optionalString("providerPlaylistId")
                StreamingMediaType.MV -> value.optionalString("providerMvId")
            }
            ?: ""
    }

    private fun searchItemTitle(value: JSONObject, type: StreamingMediaType): String {
        val fallback = if (type == StreamingMediaType.ARTIST) value.optionalString("name") else null
        return value.optionalString("title") ?: fallback ?: ""
    }

    private fun artistRefs(array: JSONArray?, fallbackProvider: StreamingProviderName): List<StreamingArtistRef> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { value ->
            StreamingArtistRef(
                provider = providerName(value.optString("provider")) ?: fallbackProvider,
                providerArtistId = value.optString("providerArtistId", value.optString("id")),
                name = value.optString("name")
            )
        }
    }

    private fun trackJson(track: StreamingTrack): JSONObject {
        return JSONObject()
            .put("provider", track.provider.wireName)
            .put("providerTrackId", track.providerTrackId)
            .put("title", track.title)
            .put("artist", track.artist)
            .put("artists", JSONArray(track.artists.map { artistRefJson(it) }))
            .put("album", track.album)
            .put("albumId", track.albumId)
            .put("durationMs", track.durationMs)
            .put("coverUrl", track.coverUrl)
            .put("coverThumb", track.coverThumbUrl)
            .put("qualities", JSONArray(track.qualities.map { it.wireName }))
            .put("explicit", track.explicit)
            .put("playable", track.playable)
            .put("unavailableReason", track.unavailableReason)
    }

    private fun albumJson(album: StreamingAlbum): JSONObject {
        return JSONObject()
            .put("provider", album.provider.wireName)
            .put("providerAlbumId", album.providerAlbumId)
            .put("title", album.title)
            .put("artist", album.artist)
            .put("coverUrl", album.coverUrl)
            .put("trackCount", album.trackCount)
    }

    private fun artistJson(artist: StreamingArtist): JSONObject {
        return JSONObject()
            .put("provider", artist.provider.wireName)
            .put("providerArtistId", artist.providerArtistId)
            .put("name", artist.name)
            .put("avatarUrl", artist.avatarUrl)
    }

    private fun playlistJson(playlist: StreamingPlaylist): JSONObject {
        return JSONObject()
            .put("provider", playlist.provider.wireName)
            .put("providerPlaylistId", playlist.providerPlaylistId)
            .put("title", playlist.title)
            .put("description", playlist.description)
            .put("creator", playlist.creator)
            .put("coverUrl", playlist.coverUrl)
            .put("trackCount", playlist.trackCount)
    }

    private fun mvJson(mv: StreamingMvItem): JSONObject {
        return JSONObject()
            .put("provider", mv.provider.wireName)
            .put("providerMvId", mv.providerMvId)
            .put("providerTrackId", mv.providerTrackId)
            .put("title", mv.title)
            .put("artist", mv.artist)
            .put("durationMs", mv.durationMs)
            .put("thumbnailUrl", mv.thumbnailUrl)
    }

    private fun searchItemJson(item: StreamingSearchItem): JSONObject {
        val value = JSONObject()
            .put("type", item.type.wireName)
            .put("provider", item.provider.wireName)
            .put("id", item.id)
            .put("title", item.title)
            .put("subtitle", item.subtitle)
            .put("imageUrl", item.imageUrl)
            .put("playable", item.playable)
        when (item.type) {
            StreamingMediaType.TRACK -> item.track?.let { value.put("track", trackJson(it)) }
            StreamingMediaType.ALBUM -> item.album?.let { value.put("album", albumJson(it)) }
            StreamingMediaType.ARTIST -> item.artist?.let { value.put("artist", artistJson(it)) }
            StreamingMediaType.PLAYLIST -> item.playlist?.let { value.put("playlist", playlistJson(it)) }
            StreamingMediaType.MV -> item.mv?.let { value.put("mv", mvJson(it)) }
        }
        return value
    }

    private fun searchErrorJson(error: StreamingSearchError): JSONObject {
        return JSONObject()
            .put("code", error.code.wireName)
            .put("message", error.message)
    }

    private fun searchError(value: JSONObject?): StreamingSearchError? {
        if (value == null) return null
        return StreamingSearchError(
            code = StreamingErrorCode.fromWireName(value.optString("code")),
            message = value.optionalString("message") ?: value.optionalString("statusMessage")
        )
    }

    private fun artistRefJson(artist: StreamingArtistRef): JSONObject {
        return JSONObject()
            .put("provider", artist.provider.wireName)
            .put("providerArtistId", artist.providerArtistId)
            .put("name", artist.name)
    }

    private fun authStateJsonObject(state: StreamingAuthState): JSONObject {
        return JSONObject()
            .put("kind", state.kind.wireName)
            .put("connected", state.connected)
            .put("accountDisplayName", state.accountDisplayName)
            .put("accountUsername", state.accountUsername)
            .put("accountAvatarUrl", state.accountAvatarUrl)
            .put("statusMessage", state.statusMessage)
    }

    private fun mediaTypes(array: JSONArray?): Set<StreamingMediaType> {
        if (array == null || array.length() == 0) return setOf(StreamingMediaType.TRACK)
        return (0 until array.length())
            .mapNotNull { StreamingMediaType.fromWireName(array.optString(it)) }
            .toSet()
            .ifEmpty { setOf(StreamingMediaType.TRACK) }
    }

    private fun audioQualities(array: JSONArray?): Set<StreamingAudioQuality> {
        if (array == null) return emptySet()
        return (0 until array.length())
            .mapNotNull { StreamingAudioQuality.fromWireName(array.optString(it)) }
            .toSet()
    }

    private fun stringMap(value: JSONObject?): Map<String, String> {
        if (value == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = value.optString(key)
        }
        return result
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
    }

    private fun providerName(value: String): StreamingProviderName? = StreamingProviderName.fromWireName(value)

    private fun authKind(value: String): StreamingAuthKind? {
        return when (value.trim().lowercase()) {
            "none" -> StreamingAuthKind.NONE
            "custom_tabs_app_link", "customtabsapplink", "custom_tabs" -> StreamingAuthKind.CUSTOM_TABS_APP_LINK
            "isolated_web_view_cookie", "isolatedwebviewcookie", "webview_cookie" -> StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE
            "remote_gateway", "remotegateway", "gateway" -> StreamingAuthKind.REMOTE_GATEWAY
            else -> null
        }
    }

    private fun providerStatus(value: String): StreamingProviderStatus? {
        return when (value.trim().lowercase()) {
            "ready" -> StreamingProviderStatus.READY
            "needs_account", "needsaccount" -> StreamingProviderStatus.NEEDS_ACCOUNT
            "disabled" -> StreamingProviderStatus.DISABLED
            "error" -> StreamingProviderStatus.ERROR
            else -> null
        }
    }

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    private fun JSONObject.optionalLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return optLong(name)
    }

    private fun JSONObject.optionalBoolean(name: String): Boolean? {
        if (!has(name) || isNull(name)) return null
        return optBoolean(name)
    }
}
