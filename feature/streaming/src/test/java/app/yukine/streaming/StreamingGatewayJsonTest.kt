package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingGatewayJsonTest {
    @Test
    fun providerDescriptorsParseCapabilitiesAuthAndStatus() {
        val json = JSONObject()
            .put(
                "providers",
                JSONArray().put(
                    JSONObject()
                        .put("name", "spotify")
                        .put("displayName", "Spotify")
                        .put("enabled", true)
                        .put("status", "needs_account")
                        .put("statusMessage", "Login required")
                        .put(
                            "capabilities",
                            JSONObject()
                                .put("supportsSearch", true)
                                .put("supportsPlayback", false)
                                .put("supportsLyrics", true)
                                .put("supportsMv", false)
                                .put("supportsAuth", true)
                                .put("supportsFavorites", true)
                                .put("supportsPlaylists", true)
                                .put("supportedMediaTypes", JSONArray(listOf("track", "album", "playlist")))
                        )
                        .put(
                            "auth",
                            JSONObject()
                                .put("kind", "custom_tabs_app_link")
                                .put("connected", false)
                                .put("statusMessage", "Needs sign-in")
                        )
                )
            )
            .toString()

        val descriptor = StreamingGatewayJson.providerDescriptors(json).single()

        assertEquals(StreamingProviderName.SPOTIFY, descriptor.name)
        assertEquals("Spotify", descriptor.displayName)
        assertEquals(StreamingProviderStatus.NEEDS_ACCOUNT, descriptor.status)
        assertEquals("Login required", descriptor.statusMessage)
        assertTrue(descriptor.capabilities.supportsSearch)
        assertFalse(descriptor.capabilities.supportsPlayback)
        assertTrue(descriptor.capabilities.supportsLyrics)
        assertTrue(descriptor.capabilities.supportsAuth)
        assertEquals(
            setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM, StreamingMediaType.PLAYLIST),
            descriptor.capabilities.supportedMediaTypes
        )
        assertEquals(StreamingAuthKind.CUSTOM_TABS_APP_LINK, descriptor.auth.kind)
        assertFalse(descriptor.auth.connected)
    }

    @Test
    fun searchResultRoundTripsAllMediaTypes() {
        val source = StreamingSearchResult(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            page = 2,
            pageSize = 30,
            total = 42,
            hasMore = true,
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "track-1",
                    title = "Track",
                    artist = "Artist",
                    artists = listOf(
                        StreamingArtistRef(
                            provider = StreamingProviderName.NETEASE,
                            providerArtistId = "artist-1",
                            name = "Artist"
                        )
                    ),
                    album = "Album",
                    albumId = "album-1",
                    durationMs = 123_000L,
                    coverUrl = "https://example.test/cover.jpg",
                    coverThumbUrl = "https://example.test/thumb.jpg",
                    qualities = setOf(StreamingAudioQuality.HIGH, StreamingAudioQuality.LOSSLESS),
                    explicit = true,
                    playable = false,
                    unavailableReason = "Region locked"
                )
            ),
            albums = listOf(
                StreamingAlbum(
                    provider = StreamingProviderName.NETEASE,
                    providerAlbumId = "album-1",
                    title = "Album",
                    artist = "Artist",
                    trackCount = 12
                )
            ),
            artists = listOf(
                StreamingArtist(
                    provider = StreamingProviderName.NETEASE,
                    providerArtistId = "artist-1",
                    name = "Artist",
                    avatarUrl = "https://example.test/avatar.jpg"
                )
            ),
            playlists = listOf(
                StreamingPlaylist(
                    provider = StreamingProviderName.NETEASE,
                    providerPlaylistId = "playlist-1",
                    title = "Playlist",
                    description = "Description",
                    creator = "Creator",
                    trackCount = 20
                )
            ),
            mvs = listOf(
                StreamingMvItem(
                    provider = StreamingProviderName.NETEASE,
                    providerMvId = "mv-1",
                    providerTrackId = "track-1",
                    title = "MV",
                    artist = "Artist",
                    durationMs = 222_000L
                )
            ),
            cached = true
        )

        val json = StreamingGatewayJson.searchResultJson(source)
        val result = StreamingGatewayJson.searchResult(json)

        assertEquals(StreamingProviderName.NETEASE, result.provider)
        assertEquals("echo", result.query)
        assertEquals(2, result.page)
        assertEquals(30, result.pageSize)
        assertEquals(42, result.total)
        assertTrue(result.hasMore)
        assertTrue(result.cached)
        assertEquals("track-1", result.tracks.single().providerTrackId)
        assertEquals(setOf(StreamingAudioQuality.HIGH, StreamingAudioQuality.LOSSLESS), result.tracks.single().qualities)
        assertFalse(result.tracks.single().playable)
        assertEquals("Region locked", result.tracks.single().unavailableReason)
        assertEquals("album-1", result.albums.single().providerAlbumId)
        assertEquals("artist-1", result.artists.single().providerArtistId)
        assertEquals("playlist-1", result.playlists.single().providerPlaylistId)
        assertEquals("mv-1", result.mvs.single().providerMvId)
        assertEquals(5, result.unifiedItems.size)
        assertEquals("track-1", result.unifiedItems.first().id)
        assertEquals(5, JSONObject(json).getJSONArray("items").length())
    }

    @Test
    fun searchCacheRoundTripRetainsCompleteLuoxueMusicInfoObject() {
        val musicInfo = JSONObject()
            .put("hash", "abc123")
            .put("album_id", "22")
            .put("nested", JSONObject().put("label", "完整"))
            .put("qualities", JSONArray().put("320k").put("flac"))
            .toString()
        val source = StreamingSearchResult(
            provider = StreamingProviderName.LUOXUE,
            query = "echo",
            page = 1,
            pageSize = 20,
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "kg:abc123.22.33",
                    title = "Song",
                    artist = "Artist",
                    playbackCandidates = listOf(
                        StreamingPlaybackCandidate(
                            provider = StreamingProviderName.LUOXUE,
                            providerTrackId = "kg:alternate.22.33",
                            luoxueMusicInfoJson = musicInfo
                        )
                    ),
                    luoxueMusicInfoJson = musicInfo
                )
            )
        )

        val restored = StreamingGatewayJson.searchResult(StreamingGatewayJson.searchResultJson(source))

        assertEquals(
            normalizeLuoxueMusicInfoJson(musicInfo),
            restored.tracks.single().luoxueMusicInfoJson
        )
        assertEquals(
            normalizeLuoxueMusicInfoJson(musicInfo),
            restored.tracks.single().playbackCandidates.single().luoxueMusicInfoJson
        )
    }

    @Test
    fun neteaseTracksAndPlaybackSourcesPreferSongId() {
        val tracksJson = JSONObject()
            .put(
                "tracks",
                JSONArray().put(
                    JSONObject()
                        .put("provider", "netease")
                        .put("id", "search-item-id")
                        .put("songId", 260600)
                        .put("providerTrackId", "wrong-provider-id")
                        .put("title", "Song")
                )
            )
            .toString()
        val playbackJson = JSONObject()
            .put("provider", "netease")
            .put("id", "gateway-item-id")
            .put("songId", "260601")
            .put("providerTrackId", "wrong-playback-id")
            .put("url", "https://example.test/audio.flac")
            .toString()

        val track = StreamingGatewayJson.tracks(tracksJson, StreamingProviderName.NETEASE).single()
        val source = StreamingGatewayJson.playbackSource(playbackJson)

        assertEquals("260600", track.providerTrackId)
        assertEquals("260601", source.providerTrackId)
    }

    @Test
    fun luoxueAliasesAndAdapterFieldsParseAsLuoxueProvider() {
        val searchJson = JSONObject()
            .put("provider", "kw")
            .put(
                "tracks",
                JSONArray().put(
                    JSONObject()
                        .put("provider", "migu")
                        .put("songmid", "lx-song-1")
                        .put("title", "LX Song")
                        .put("artist", "LX Artist")
                        .put("pic", "https://example.test/lx.jpg")
                )
            )
            .toString()
        val playbackJson = JSONObject()
            .put("provider", "luoxue")
            .put("mid", "lx-song-1")
            .put("musicUrl", "https://example.test/lx.flac")
            .toString()

        val result = StreamingGatewayJson.searchResult(searchJson)
        val source = StreamingGatewayJson.playbackSource(playbackJson)

        assertEquals(StreamingProviderName.LUOXUE, result.provider)
        assertEquals(StreamingProviderName.LUOXUE, result.tracks.single().provider)
        assertEquals("lx-song-1", result.tracks.single().providerTrackId)
        assertEquals("https://example.test/lx.jpg", result.tracks.single().coverUrl)
        assertEquals(StreamingProviderName.LUOXUE, source.provider)
        assertEquals("lx-song-1", source.providerTrackId)
        assertEquals("https://example.test/lx.flac", source.url)
    }

    @Test
    fun searchResultParsesUnifiedItemsAndError() {
        val json = JSONObject()
            .put("provider", "netease")
            .put("query", "echo")
            .put("page", 1)
            .put("pageSize", 20)
            .put("hasMore", true)
            .put(
                "items",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "track")
                            .put("provider", "netease")
                            .put("id", "track-1")
                            .put("title", "Track")
                            .put("subtitle", "Artist")
                            .put("imageUrl", "https://example.test/track.jpg")
                            .put("playable", false)
                    )
                    .put(
                        JSONObject()
                            .put("type", "album")
                            .put(
                                "album",
                                JSONObject()
                                    .put("provider", "netease")
                                    .put("providerAlbumId", "album-1")
                                    .put("title", "Album")
                                    .put("artist", "Artist")
                            )
                    )
            )
            .put(
                "error",
                JSONObject()
                    .put("code", "REGION_BLOCKED")
                    .put("message", "Region locked")
            )
            .toString()

        val result = StreamingGatewayJson.searchResult(json)

        assertTrue(result.hasMore)
        assertEquals(2, result.items.size)
        assertEquals(2, result.unifiedItems.size)
        assertEquals("track-1", result.tracks.single().providerTrackId)
        assertFalse(result.tracks.single().playable)
        assertEquals("album-1", result.albums.single().providerAlbumId)
        assertEquals(StreamingErrorCode.REGION_BLOCKED, result.error?.code)
        assertEquals("Region locked", result.error?.message)
    }

    @Test
    fun playlistDetailRoundTripsTracksAndMetadata() {
        val source = StreamingPlaylistDetail(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-1",
            playlist = StreamingPlaylist(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "playlist-1",
                title = "Playlist",
                creator = "Creator",
                trackCount = 1
            ),
            tracks = listOf(
                StreamingTrack(
                    provider = StreamingProviderName.NETEASE,
                    providerTrackId = "track-1",
                    title = "Track",
                    artist = "Artist"
                )
            ),
            total = 1,
            cached = true
        )

        val result = StreamingGatewayJson.playlistDetail(StreamingGatewayJson.playlistDetailJson(source))

        assertEquals(StreamingProviderName.NETEASE, result.provider)
        assertEquals("playlist-1", result.providerPlaylistId)
        assertEquals("Playlist", result.playlist?.title)
        assertEquals("track-1", result.tracks.single().providerTrackId)
        assertEquals(1, result.total)
        assertTrue(result.cached)
    }

    @Test
    fun playbackSourceRoundTripsHeadersAndTechnicalFields() {
        val source = StreamingPlaybackSource(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = "track-1",
            url = "https://example.test/audio.flac",
            expiresAtEpochMs = 123_456L,
            mimeType = "audio/flac",
            bitrate = 1_411,
            sampleRate = 44_100,
            bitDepth = 16,
            codec = "flac",
            headers = mapOf("Referer" to "https://y.qq.com/"),
            requiresProxy = true,
            supportsRange = false
        )

        val result = StreamingGatewayJson.playbackSource(StreamingGatewayJson.playbackSourceJson(source))

        assertEquals(source.provider, result.provider)
        assertEquals(source.providerTrackId, result.providerTrackId)
        assertEquals(source.url, result.url)
        assertEquals(source.expiresAtEpochMs, result.expiresAtEpochMs)
        assertEquals(source.mimeType, result.mimeType)
        assertEquals(source.bitrate, result.bitrate)
        assertEquals(source.sampleRate, result.sampleRate)
        assertEquals(source.bitDepth, result.bitDepth)
        assertEquals(source.codec, result.codec)
        assertEquals(source.headers, result.headers)
        assertTrue(result.requiresProxy)
        assertFalse(result.supportsRange)
    }

    @Test
    fun authResultAndCompleteAuthRequestPreserveAuthContractFields() {
        val authResult = StreamingGatewayJson.authResult(
            JSONObject()
                .put("provider", "qqmusic")
                .put("launchUrl", "https://y.qq.com/")
                .put("statusMessage", "Open login")
                .put(
                    "state",
                    JSONObject()
                        .put("kind", "isolated_web_view_cookie")
                        .put("connected", true)
                        .put("accountDisplayName", "QQ User")
                        .put("accountUsername", "10001")
                        .put("accountAvatarUrl", "https://example.test/avatar.jpg")
                        .put("statusMessage", "Connected")
                )
                .toString(),
            StreamingProviderName.NETEASE
        )

        assertEquals(StreamingProviderName.QQ_MUSIC, authResult.provider)
        assertEquals("https://y.qq.com/", authResult.launchUrl)
        assertEquals("Open login", authResult.statusMessage)
        assertEquals(StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE, authResult.state.kind)
        assertTrue(authResult.state.connected)
        assertEquals("QQ User", authResult.state.accountDisplayName)
        assertEquals("10001", authResult.state.accountUsername)

        val completion = StreamingGatewayJson.completeAuthRequest(
            StreamingProviderName.QQ_MUSIC,
            "echo-next://streaming-auth?provider=qqmusic",
            "uin=10001; qqmusic_key=secret"
        )

        assertEquals("qqmusic", completion.getString("provider"))
        assertEquals("echo-next://streaming-auth?provider=qqmusic", completion.getString("callbackUri"))
        assertEquals("uin=10001; qqmusic_key=secret", completion.getString("cookieHeader"))

        val withoutCookie = StreamingGatewayJson.completeAuthRequest(
            StreamingProviderName.QQ_MUSIC,
            "echo-next://streaming-auth?provider=qqmusic",
            " "
        )
        assertFalse(withoutCookie.has("cookieHeader"))
    }

    @Test
    fun authStateJsonParsesFallbackProviderDefaults() {
        val state = StreamingGatewayJson.authState(
            JSONObject()
                .put("connected", false)
                .toString(),
            StreamingProviderName.SPOTIFY
        )

        assertEquals(StreamingAuthKind.CUSTOM_TABS_APP_LINK, state.kind)
        assertFalse(state.connected)
        assertNull(state.accountDisplayName)
    }

    @Test
    fun providerHealthParsesAvailabilityAuthAndErrors() {
        val health = StreamingGatewayJson.providerHealth(
            JSONObject()
                .put(
                    "providers",
                    JSONArray().put(
                        JSONObject()
                            .put("provider", "tidal")
                            .put("available", false)
                            .put("authenticated", true)
                            .put("latencyMs", 340L)
                            .put("errorCode", "RATE_LIMITED")
                            .put("errorMessage", "Too many requests")
                            .put("checkedAtEpochMs", 123_456L)
                    )
                )
                .toString()
        ).single()

        assertEquals(StreamingProviderName.TIDAL, health.provider)
        assertFalse(health.available)
        assertTrue(health.authenticated)
        assertEquals(340L, health.latencyMs)
        assertEquals(StreamingErrorCode.RATE_LIMITED, health.errorCode)
        assertEquals("Too many requests", health.errorMessage)
        assertEquals(123_456L, health.checkedAtEpochMs)
    }

    @Test
    fun providerCapabilitiesParseExplicitAndDescriptorForms() {
        val capabilities = StreamingGatewayJson.providerCapabilities(
            JSONObject()
                .put(
                    "capabilities",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("provider", "spotify")
                                .put("displayName", "Spotify")
                                .put("enabled", true)
                                .put("canSearch", true)
                                .put("canPlayback", false)
                                .put("canAuth", true)
                                .put("supportedSearchMediaTypes", JSONArray(listOf("track", "album")))
                                .put("actions", JSONArray(listOf("search", "auth")))
                        )
                        .put(
                            JSONObject()
                                .put("name", "tidal")
                                .put("displayName", "TIDAL")
                                .put("enabled", false)
                                .put(
                                    "capabilities",
                                    JSONObject()
                                        .put("supportsSearch", true)
                                        .put("supportsPlayback", true)
                                        .put("supportsAuth", true)
                                )
                        )
                )
                .toString()
        )

        val spotify = capabilities.first { it.provider == StreamingProviderName.SPOTIFY }
        assertEquals("Spotify", spotify.displayName)
        assertTrue(spotify.supportsSearch)
        assertFalse(spotify.supportsPlayback)
        assertTrue(spotify.supportsAuth)
        assertEquals(setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM), spotify.supportedSearchMediaTypes)
        assertEquals(listOf("search", "auth"), spotify.actions)

        val tidal = capabilities.first { it.provider == StreamingProviderName.TIDAL }
        assertFalse(tidal.enabled)
        assertFalse(tidal.supportsSearch)
        assertFalse(tidal.supportsPlayback)
        assertEquals(emptyList<String>(), tidal.actions)
    }

    @Test
    fun gatewayErrorParsesWrappedAndFallbackErrors() {
        val wrapped = StreamingGatewayJson.gatewayError(
            JSONObject()
                .put(
                    "error",
                    JSONObject()
                        .put("code", "AUTH_REQUIRED")
                        .put("message", "Login first")
                )
                .toString()
        )
        val plain = StreamingGatewayJson.gatewayError("not json")

        assertEquals(StreamingErrorCode.AUTH_REQUIRED, wrapped.code)
        assertEquals("Login first", wrapped.message)
        assertEquals(StreamingErrorCode.UNKNOWN, plain.code)
        assertEquals("not json", plain.message)
    }
}
