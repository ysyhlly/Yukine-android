package app.yukine.streaming

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteStreamingGatewayTest {
    @Test
    fun unconfiguredEndpointUsesLocalFallbacksAndRejectsRemoteOnlyOperations() = runTest {
        val gateway = RemoteStreamingGateway("gateway://unconfigured")

        assertTrue(gateway.providers().isNotEmpty())
        assertEquals(StreamingProviderName.NETEASE, gateway.providers().first().name)
        assertFalse(gateway.authState(StreamingProviderName.SPOTIFY).connected)
        assertEquals(
            "请在新页面登录",
            gateway.startAuth(StreamingAuthRequest(StreamingProviderName.SPOTIFY)).statusMessage
        )

        val searchError = try {
            gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            null
        } catch (error: StreamingGatewayException) {
            error
        }
        assertEquals(StreamingErrorCode.GATEWAY_UNAVAILABLE, searchError?.code)
        assertTrue(searchError?.message.orEmpty().isNotBlank())

        val playlistsError = try {
            gateway.userPlaylists(StreamingProviderName.NETEASE)
            null
        } catch (error: StreamingGatewayException) {
            error
        }
        assertEquals(StreamingErrorCode.GATEWAY_UNAVAILABLE, playlistsError?.code)
    }

    @Test
    fun unconfiguredEndpointDisablesProvidersWithoutLocalClient() = runTest {
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = FakeLocalAuthStore(
                cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
            )
        )

        val providers = gateway.providers()
        // NetEase has a local client, so it stays enabled even though no gateway is configured.
        val netease = providers.first { it.name == StreamingProviderName.NETEASE }
        assertTrue(netease.enabled)
        // QQ now has a local-first client, so it stays enabled but must not pretend to be signed in.
        val qq = providers.first { it.name == StreamingProviderName.QQ_MUSIC }
        assertTrue(qq.enabled)
        assertEquals(StreamingProviderStatus.NEEDS_ACCOUNT, qq.status)
        assertEquals(StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE, qq.auth.kind)
        assertFalse(qq.auth.connected)
        val qqCapability = gateway.providerCapabilities()
            .first { it.provider == StreamingProviderName.QQ_MUSIC }
        assertTrue(qqCapability.enabled)
        assertTrue(qqCapability.supportsSearch)
        val luoxue = providers.first { it.name == StreamingProviderName.LUOXUE }
        assertTrue(luoxue.enabled)
        assertEquals(StreamingProviderStatus.READY, luoxue.status)
        assertFalse(luoxue.auth.connected)
        val luoxueCapability = gateway.providerCapabilities()
            .first { it.provider == StreamingProviderName.LUOXUE }
        assertTrue(luoxueCapability.enabled)
        assertTrue(luoxueCapability.supportsSearch)
        // Providers without a local client are still disabled so the UI does not offer a dead entry.
        val spotify = providers.first { it.name == StreamingProviderName.SPOTIFY }
        assertFalse(spotify.enabled)
        assertEquals(StreamingProviderStatus.DISABLED, spotify.status)
    }

    @Test
    fun unconfiguredEndpointLoadsQqAccountPlaylistsLocallyWithSavedCookie() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.QQ_MUSIC to "uin=o12345; qqmusic_key=local-key")
        )
        val qq = FakeQqMusicHttpClient(
            responses = mapOf(
                "/rsc/fcgi-bin/fcg_user_created_diss" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put(
                                "disslist",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("dissid", "7001")
                                            .put("dissname", "QQ Favorite")
                                            .put("songnum", 12)
                                            .put("logo", "https://y.qq.com/cover.jpg")
                                    )
                            )
                    ),
                "/fav/fcgi-bin/fcg_get_profile_order_asset.fcg" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put(
                                "cdlist",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("dissid", "8001")
                                            .put("dissname", "Collected Playlist")
                                            .put("songnum", 4)
                                    )
                            )
                    ),
                "/rsc/fcgi-bin/fcg_get_profile_homepage.fcg" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put(
                                "mymusic",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("id", "201")
                                            .put("num0", 99)
                                    )
                            )
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localQqMusicClient = LocalQqMusicStreamingClient(authStore, qq)
        )

        val playlists = gateway.userPlaylists(StreamingProviderName.QQ_MUSIC)

        assertEquals(listOf("201", "7001", "8001"), playlists.map { it.providerPlaylistId })
        assertEquals(listOf("我喜欢", "QQ Favorite", "Collected Playlist"), playlists.map { it.title })
        assertEquals(99, playlists.first().trackCount)
        assertEquals(
            listOf(
                "/rsc/fcgi-bin/fcg_user_created_diss",
                "/fav/fcgi-bin/fcg_get_profile_order_asset.fcg",
                "/rsc/fcgi-bin/fcg_get_profile_homepage.fcg"
            ),
            qq.paths
        )
        assertTrue(qq.cookies.all { it == "uin=o12345; qqmusic_key=local-key" })
    }

    @Test
    fun unconfiguredEndpointLoadsQqPlaylistDetailWithCookieAndDiridFallback() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.QQ_MUSIC to "uin=o12345; qqmusic_key=local-key")
        )
        val emptyDetail = JSONObject()
            .put("cdlist", JSONArray().put(JSONObject().put("dissname", "Empty")))
        val diridDetail = JSONObject()
            .put(
                "cdlist",
                JSONArray().put(
                    JSONObject()
                        .put("dissname", "Imported QQ Playlist")
                        .put(
                            "songlist",
                            JSONArray().put(
                                JSONObject()
                                    .put("mid", "song-mid-1")
                                    .put("title", "QQ Song")
                                    .put("singer", JSONArray().put(JSONObject().put("name", "QQ Artist")))
                                    .put("album", JSONObject().put("mid", "album-mid-1").put("title", "QQ Album"))
                                    .put("interval", 120)
                            )
                        )
                )
            )
        val qq = FakeQqMusicHttpClient(
            responses = mapOf("/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" to emptyDetail),
            dynamicResponse = { url ->
                if (java.net.URL(url).query.contains("dirid=dir-7001")) diridDetail else null
            }
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localQqMusicClient = LocalQqMusicStreamingClient(authStore, qq)
        )

        val detail = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerPlaylistId = "dir-7001"
            )
        )

        assertEquals("Imported QQ Playlist", detail.playlist?.title)
        assertEquals(listOf("song-mid-1"), detail.tracks.map { it.providerTrackId })
        assertTrue(qq.queries[0].contains("disstid=dir-7001"))
        assertTrue(qq.queries[1].contains("dirid=dir-7001"))
        assertTrue(qq.cookies.all { it == "uin=o12345; qqmusic_key=local-key" })
    }

    @Test
    fun qqPlaybackRejectsUinOnlyCookieBeforeCallingProvider() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.QQ_MUSIC to "uin=o12345; p_uin=o12345")
        )
        val qq = FakeQqMusicHttpClient(responses = emptyMap())
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localQqMusicClient = LocalQqMusicStreamingClient(authStore, qq)
        )

        val error = try {
            gateway.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "song-mid"
                )
            )
            null
        } catch (error: StreamingGatewayException) {
            error
        }

        assertEquals(StreamingErrorCode.AUTH_REQUIRED, error?.code)
        assertTrue(error?.message.orEmpty().contains("qqmusic_key/qm_keyst"))
        assertTrue(qq.paths.isEmpty())
    }

    @Test
    fun unconfiguredEndpointSupportsOfflineMockProvider() = runTest {
        val gateway = RemoteStreamingGateway("gateway://unconfigured")

        val search = gateway.search(
            StreamingSearchRequest(
                provider = StreamingProviderName.MOCK,
                query = "echo",
                mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.PLAYLIST)
            )
        )
        val playlist = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.MOCK,
                providerPlaylistId = "mock-playlist-daily"
            )
        )
        val playback = gateway.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.MOCK,
                providerTrackId = search.tracks.single().providerTrackId
            )
        )

        assertEquals(StreamingProviderName.MOCK, search.provider)
        assertEquals("echo", search.query)
        assertEquals("Echo test", search.tracks.single().title)
        assertEquals(3, playlist.tracks.size)
        assertEquals("audio/mpeg", playback.mimeType)
        assertTrue(playback.url.startsWith("https://"))
        assertTrue(gateway.authState(StreamingProviderName.MOCK).connected)
    }

    @Test
    fun sendsExpectedHttpMethodsPathsAndBodies() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val gateway = RemoteStreamingGateway(server.baseUrl)

            val providers = gateway.providers()
            assertEquals(StreamingProviderName.SPOTIFY, providers.first { it.name == StreamingProviderName.SPOTIFY }.name)
            val health = gateway.providersHealth()
            assertTrue(health.single().available)

            val search = gateway.search(
                StreamingSearchRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    query = "  echo  ",
                    mediaTypes = setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM),
                    page = 0,
                    pageSize = 99
                )
            )
            assertEquals("echo", search.query)
            assertEquals(1, search.page)
            assertEquals(50, search.pageSize)

            val playlist = gateway.playlist(
                StreamingPlaylistRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    providerPlaylistId = " playlist-1 "
                )
            )
            assertEquals("playlist-1", playlist.providerPlaylistId)
            assertEquals("track-1", playlist.tracks.single().providerTrackId)

            val playback = gateway.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    providerTrackId = "track-1",
                    quality = StreamingAudioQuality.HIGH
                )
            )
            assertEquals("https://example.test/audio.mp3", playback.url)

            val authState = gateway.authState(StreamingProviderName.SPOTIFY)
            assertFalse(authState.connected)

            val authStart = gateway.startAuth(
                StreamingAuthRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    redirectUri = "echo-next://streaming-auth",
                    forceRefresh = true
                )
            )
            assertEquals("https://accounts.spotify.com/login", authStart.launchUrl)

            val authComplete = gateway.completeAuth(
                StreamingProviderName.SPOTIFY,
                "echo-next://streaming-auth?code=abc",
                "sp_dc=cookie"
            )
            assertTrue(authComplete.state.connected)

            val signedOut = gateway.signOut(StreamingProviderName.SPOTIFY)
            assertFalse(signedOut.connected)

            val requests = server.requests
            assertEquals("GET", requests[0].method)
            assertEquals("/providers", requests[0].path)
            assertEquals("application/json", requests[0].accept)
            assertEquals("Yukine-Android", requests[0].userAgent)

            assertEquals("GET", requests[1].method)
            assertEquals("/providers/health", requests[1].path)

            assertEquals("POST", requests[2].method)
            assertEquals("/search", requests[2].path)
            JSONObject(requests[2].body).also { body ->
                assertEquals("spotify", body.getString("provider"))
                assertEquals("echo", body.getString("query"))
                assertEquals(1, body.getInt("page"))
                assertEquals(50, body.getInt("pageSize"))
                assertEquals("track", body.getJSONArray("mediaTypes").getString(0))
                assertEquals("album", body.getJSONArray("mediaTypes").getString(1))
            }

            assertEquals("GET", requests[3].method)
            assertEquals("/playlist", requests[3].path)
            assertEquals("provider=spotify&providerPlaylistId=playlist-1&page=1&pageSize=500", requests[3].query)

            assertEquals("POST", requests[4].method)
            assertEquals("/resolvePlayback", requests[4].path)
            JSONObject(requests[4].body).also { body ->
                assertEquals("spotify", body.getString("provider"))
                assertEquals("track-1", body.getString("providerTrackId"))
                assertEquals("high", body.getString("quality"))
            }

            assertEquals("GET", requests[5].method)
            assertEquals("/auth/state", requests[5].path)
            assertEquals("provider=spotify", requests[5].query)

            assertEquals("POST", requests[6].method)
            assertEquals("/auth/start", requests[6].path)
            JSONObject(requests[6].body).also { body ->
                assertEquals("spotify", body.getString("provider"))
                assertEquals("echo-next://streaming-auth", body.getString("redirectUri"))
                assertTrue(body.getBoolean("forceRefresh"))
            }

            assertEquals("POST", requests[7].method)
            assertEquals("/auth/complete", requests[7].path)
            JSONObject(requests[7].body).also { body ->
                assertEquals("spotify", body.getString("provider"))
                assertEquals("echo-next://streaming-auth?code=abc", body.getString("callbackUri"))
                assertEquals("sp_dc=cookie", body.getString("cookieHeader"))
            }

            assertEquals("POST", requests[8].method)
            assertEquals("/auth/signOut", requests[8].path)
            assertEquals("spotify", JSONObject(requests[8].body).getString("provider"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun sendsSavedProviderCookieWithGatewayRequests() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                localAuthStore = FakeLocalAuthStore(
                    cookies = mapOf(StreamingProviderName.SPOTIFY to "sp_dc=local-token")
                )
            )

            gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            gateway.playlist(
                StreamingPlaylistRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    providerPlaylistId = "playlist-1"
                )
            )

            assertEquals("sp_dc=local-token", server.requests[0].cookie)
            assertEquals("spotify", server.requests[0].streamingProvider)
            assertEquals("sp_dc=local-token", server.requests[1].cookie)
            assertEquals("spotify", server.requests[1].streamingProvider)
        } finally {
            server.stop()
        }
    }

    @Test
    fun completeAuthKeepsLocalCookieLoginWhenGatewayReturnsDisconnectedState() = runTest {
        val server = GatewayTestServer(
            responseOverrides = mapOf(
                "/auth/complete" to JSONObject()
                    .put("provider", "netease")
                    .put(
                        "state",
                        JSONObject()
                            .put("kind", "isolated_web_view_cookie")
                            .put("connected", false)
                    )
                    .toString()
            )
        )
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                localAuthStore = FakeLocalAuthStore()
            )

            val result = gateway.completeAuth(
                StreamingProviderName.NETEASE,
                "echo-next://streaming-auth?provider=netease",
                "MUSIC_U=local-token"
            )

            assertTrue(result.state.connected)
            assertEquals(StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE, result.state.kind)
        } finally {
            server.stop()
        }
    }

    @Test
    fun providersIncludeCatalogEntriesWhenGatewayReturnsPartialProviderList() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                localAuthStore = FakeLocalAuthStore(
                    cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
                )
            )

            val providers = gateway.providers()
            val netease = providers.first { it.name == StreamingProviderName.NETEASE }

            assertTrue(netease.auth.connected)
            assertTrue(providers.any { it.name == StreamingProviderName.SPOTIFY })
        } finally {
            server.stop()
        }
    }

    @Test
    fun refreshNeteaseSessionVerifiesCookieAndKeepsItConnected() = runTest {
        val authStore = MaintenanceAuthStore(
            mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val http = FakeNeteaseHttpClient(
            responses = mapOf(
                "/api/nuser/account/get" to JSONObject()
                    .put("account", JSONObject().put("userId", 42L))
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, http)
        )

        val state = gateway.refreshAuthSession(StreamingProviderName.NETEASE, force = true)

        assertTrue(state.connected)
        assertEquals(StreamingCredentialState.VALID, state.credentialState)
        assertTrue(state.lastVerifiedAtEpochMs != null)
        assertEquals(listOf("/api/nuser/account/get"), http.paths)
    }

    @Test
    fun refreshNeteaseSessionMarksExplicitAuthRejectionInvalidWithoutDeletingCookie() = runTest {
        val authStore = MaintenanceAuthStore(
            mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(
                authStore,
                FakeNeteaseHttpClient(responses = mapOf("/api/nuser/account/get" to JSONObject()))
            )
        )

        val state = gateway.refreshAuthSession(StreamingProviderName.NETEASE, force = true)

        assertFalse(state.connected)
        assertEquals(StreamingCredentialState.INVALID, state.credentialState)
        assertEquals("MUSIC_U=local-token", authStore.cookieHeader(StreamingProviderName.NETEASE))
    }

    @Test
    fun refreshQqSessionAcceptsAnEmptyPlaylistListAsAValidAccount() = runTest {
        val authStore = MaintenanceAuthStore(
            mapOf(StreamingProviderName.QQ_MUSIC to "uin=o12345; qqmusic_key=local-key")
        )
        val qq = FakeQqMusicHttpClient(
            responses = mapOf(
                "/rsc/fcgi-bin/fcg_user_created_diss" to JSONObject()
                    .put("data", JSONObject().put("disslist", JSONArray()))
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localQqMusicClient = LocalQqMusicStreamingClient(authStore, qq)
        )

        val state = gateway.refreshAuthSession(StreamingProviderName.QQ_MUSIC, force = true)

        assertTrue(state.connected)
        assertEquals(StreamingCredentialState.VALID, state.credentialState)
        assertEquals(listOf("/rsc/fcgi-bin/fcg_user_created_diss"), qq.paths)
    }

    private class FakeLocalAuthStore(
        cookies: Map<StreamingProviderName, String> = emptyMap()
    ) : StreamingLocalAuthStore {
        private val cookies = cookies.toMutableMap()

        override fun authState(provider: StreamingProviderName): StreamingAuthState {
            return StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = !cookies[provider].isNullOrBlank()
            )
        }

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState {
            if (cookieHeader.isNullOrBlank()) {
                cookies.remove(provider)
            } else {
                cookies[provider] = cookieHeader
            }
            return authState(provider)
        }

        override fun signOut(provider: StreamingProviderName): StreamingAuthState {
            return StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = false
            )
        }

        override fun cookieHeader(provider: StreamingProviderName): String? {
            return cookies[provider]
        }

        override fun connected(provider: StreamingProviderName): Boolean {
            return !cookies[provider].isNullOrBlank()
        }
    }

    private class MaintenanceAuthStore(
        initialCookies: Map<StreamingProviderName, String>
    ) : StreamingLocalAuthStore {
        private val cookies = initialCookies.toMutableMap()
        private val states = initialCookies.keys.associateWith {
            StreamingCredentialState.PENDING_VERIFICATION
        }.toMutableMap()
        private val verifiedAt = mutableMapOf<StreamingProviderName, Long>()

        override fun authState(provider: StreamingProviderName): StreamingAuthState {
            val credentialState = states[provider] ?: if (cookies[provider].isNullOrBlank()) {
                StreamingCredentialState.NOT_LOGGED_IN
            } else {
                StreamingCredentialState.PENDING_VERIFICATION
            }
            return StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = !cookies[provider].isNullOrBlank() && credentialState != StreamingCredentialState.INVALID,
                credentialState = credentialState,
                lastVerifiedAtEpochMs = verifiedAt[provider]
            )
        }

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState {
            if (cookieHeader.isNullOrBlank()) {
                cookies.remove(provider)
                states[provider] = StreamingCredentialState.NOT_LOGGED_IN
            } else {
                cookies[provider] = cookieHeader
                states[provider] = StreamingCredentialState.PENDING_VERIFICATION
                verifiedAt.remove(provider)
            }
            return authState(provider)
        }

        override fun signOut(provider: StreamingProviderName): StreamingAuthState {
            cookies.remove(provider)
            states[provider] = StreamingCredentialState.NOT_LOGGED_IN
            verifiedAt.remove(provider)
            return authState(provider)
        }

        override fun cookieHeader(provider: StreamingProviderName): String? = cookies[provider]

        override fun connected(provider: StreamingProviderName): Boolean = authState(provider).connected

        override fun hasStoredCredential(provider: StreamingProviderName): Boolean = cookies.containsKey(provider)

        override fun markVerified(provider: StreamingProviderName, verifiedAtEpochMs: Long): StreamingAuthState {
            states[provider] = StreamingCredentialState.VALID
            verifiedAt[provider] = verifiedAtEpochMs
            return authState(provider)
        }

        override fun markPendingVerification(
            provider: StreamingProviderName,
            message: String?
        ): StreamingAuthState {
            states[provider] = StreamingCredentialState.PENDING_VERIFICATION
            return authState(provider)
        }

        override fun markInvalid(
            provider: StreamingProviderName,
            message: String?,
            checkedAtEpochMs: Long
        ): StreamingAuthState {
            states[provider] = StreamingCredentialState.INVALID
            return authState(provider)
        }
    }

    private class FakeNeteaseHttpClient(
        private val responses: Map<String, JSONObject>,
        private val dynamicResponse: ((String, Map<String, String>) -> JSONObject?)? = null,
        private val expectedCookie: String? = "MUSIC_U=local-token"
    ) : NeteaseHttpClient {
        val paths = mutableListOf<String>()
        val queries = mutableListOf<Map<String, String>>()

        override fun getJson(path: String, query: Map<String, String>, cookieHeader: String?): JSONObject {
            paths.add(path)
            queries.add(query)
            assertEquals(expectedCookie, cookieHeader)
            dynamicResponse?.invoke(path, query)?.let { return it }
            return responses[path] ?: throw StreamingGatewayException(
                "Missing fake response for $path",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
    }

    private class FakeQqMusicHttpClient(
        private val responses: Map<String, JSONObject>,
        private val dynamicResponse: ((String) -> JSONObject?)? = null
    ) : QqMusicHttpClient {
        val paths = mutableListOf<String>()
        val queries = mutableListOf<String>()
        val cookies = mutableListOf<String?>()

        override fun getJson(url: String, headers: Map<String, String>): JSONObject {
            val parsed = java.net.URL(url)
            paths += parsed.path
            queries += parsed.query.orEmpty()
            cookies += headers["Cookie"]
            dynamicResponse?.invoke(url)?.let { return it }
            return responses[parsed.path] ?: throw StreamingGatewayException(
                "Missing fake QQ response for $url",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }

        override fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
            val parsed = java.net.URL(url)
            paths += parsed.path
            queries += parsed.query.orEmpty()
            cookies += headers["Cookie"]
            dynamicResponse?.invoke(url)?.let { return it }
            return responses[parsed.path] ?: throw StreamingGatewayException(
                "Missing fake QQ response for $url",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE
            )
        }
    }

    @Test
    fun remoteArtworkUrlsUseGatewayProxy() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val gateway = RemoteStreamingGateway(server.baseUrl)

            val search = gateway.search(
                StreamingSearchRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    query = "echo"
                )
            )
            val playlist = gateway.playlist(
                StreamingPlaylistRequest(
                    provider = StreamingProviderName.SPOTIFY,
                    providerPlaylistId = "playlist-1"
                )
            )

            assertProxiedArtworkUrl(
                server.baseUrl,
                "https://image.example.test/cover art.jpg",
                search.tracks.single().coverUrl
            )
            assertProxiedArtworkUrl(
                server.baseUrl,
                "https://image.example.test/cover thumb.jpg",
                search.unifiedItems.single().imageUrl
            )
            assertProxiedArtworkUrl(
                server.baseUrl,
                "https://image.example.test/playlist.jpg",
                playlist.playlist?.coverUrl
            )
            assertProxiedArtworkUrl(
                server.baseUrl,
                "https://image.example.test/playlist-track.jpg",
                playlist.tracks.single().coverUrl
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun nonSuccessResponsesExposeUnifiedErrorCodes() = runTest {
        val server = GatewayTestServer(statusOverrides = mapOf("/search" to 401))
        server.start()
        try {
            val gateway = RemoteStreamingGateway(server.baseUrl)
            val error = try {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
                throw AssertionError("Expected gateway error")
            } catch (error: StreamingGatewayException) {
                error
            }

            assertEquals(StreamingErrorCode.AUTH_REQUIRED, error.code)
            assertTrue(error.message.orEmpty().contains("Login required"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun retriesTransientGatewayFailuresBeforeReturningSuccess() = runTest {
        val server = GatewayTestServer(statusSequences = mapOf("/search" to ArrayDeque(listOf(503, 200))))
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                sleepMs = {}
            )

            val result = gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))

            assertEquals("track-1", result.tracks.single().providerTrackId)
            assertEquals(listOf("/search", "/search"), server.requests.map { it.path })
        } finally {
            server.stop()
        }
    }

    @Test
    fun rateLimitedResponsesShortCircuitUntilRetryWindowExpires() = runTest {
        var now = 1_000L
        val server = GatewayTestServer(
            statusSequences = mapOf("/search" to ArrayDeque(listOf(429, 200))),
            retryAfterSeconds = mapOf("/search" to 2L)
        )
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                clockMs = { now },
                sleepMs = {},
                maxRetries = 0
            )

            val first = captureGatewayError {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            }
            now = 2_000L
            val second = captureGatewayError {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            }
            now = 3_001L
            val recovered = gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))

            assertEquals(StreamingErrorCode.RATE_LIMITED, first.code)
            assertEquals(2_000L, first.retryAfterMs)
            assertEquals(StreamingErrorCode.RATE_LIMITED, second.code)
            assertEquals(1_000L, second.retryAfterMs)
            assertEquals("track-1", recovered.tracks.single().providerTrackId)
            assertEquals(listOf("/search", "/search"), server.requests.map { it.path })
        } finally {
            server.stop()
        }
    }

    @Test
    fun circuitBreakerOpensAfterRepeatedGatewayFailures() = runTest {
        var now = 10_000L
        val server = GatewayTestServer(statusOverrides = mapOf("/search" to 503))
        server.start()
        try {
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                clockMs = { now },
                sleepMs = {},
                maxRetries = 0,
                circuitBreakerThreshold = 2,
                circuitOpenMs = 5_000L
            )

            captureGatewayError {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            }
            val opened = captureGatewayError {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            }
            val shortCircuited = captureGatewayError {
                gateway.search(StreamingSearchRequest(StreamingProviderName.SPOTIFY, "echo"))
            }

            assertEquals(StreamingErrorCode.GATEWAY_UNAVAILABLE, opened.code)
            assertEquals(StreamingErrorCode.GATEWAY_UNAVAILABLE, shortCircuited.code)
            assertEquals(5_000L, shortCircuited.retryAfterMs)
            assertEquals(2, server.requests.size)
        } finally {
            server.stop()
        }
    }

    @Test
    fun providerCapabilitiesUseDedicatedEndpoint() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val gateway = RemoteStreamingGateway(server.baseUrl)

            val capabilities = gateway.providerCapabilities()

            assertEquals(StreamingProviderName.SPOTIFY, capabilities.single().provider)
            assertTrue(capabilities.single().supportsSearch)
            assertFalse(capabilities.single().supportsPlayback)
            assertEquals(setOf(StreamingMediaType.TRACK, StreamingMediaType.ALBUM), capabilities.single().supportedSearchMediaTypes)
            assertEquals("GET", server.requests.single().method)
            assertEquals("/providers/capabilities", server.requests.single().path)
        } finally {
            server.stop()
        }
    }

    @Test
    fun providerCapabilitiesFallbackToProviderDescriptorsForOlderGateways() = runTest {
        val server = GatewayTestServer(statusOverrides = mapOf("/providers/capabilities" to 404))
        server.start()
        try {
            val gateway = RemoteStreamingGateway(server.baseUrl)

            val capabilities = gateway.providerCapabilities()
            val spotify = capabilities.first { it.provider == StreamingProviderName.SPOTIFY }

            assertEquals(StreamingProviderName.SPOTIFY, spotify.provider)
            assertTrue(spotify.supportsSearch)
            assertTrue(spotify.supportsPlayback)
            assertEquals(listOf("/providers/capabilities", "/providers"), server.requests.map { it.path })
        } finally {
            server.stop()
        }
    }

    @Test
    fun configuredButUnavailableGatewayKeepsLocalProviderCatalogVisible() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = FakeLocalAuthStore(
                cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
            )
        )

        val providers = gateway.providers()
        val capabilities = gateway.providerCapabilities()
        val health = gateway.providersHealth()

        assertTrue(providers.any { it.name == StreamingProviderName.NETEASE })
        assertTrue(providers.any { it.name == StreamingProviderName.LUOXUE })
        assertTrue(providers.first { it.name == StreamingProviderName.NETEASE }.auth.connected)
        assertEquals(StreamingAuthKind.NONE, providers.first { it.name == StreamingProviderName.LUOXUE }.auth.kind)
        assertEquals(StreamingProviderStatus.READY, providers.first { it.name == StreamingProviderName.LUOXUE }.status)
        assertTrue(capabilities.any { it.provider == StreamingProviderName.NETEASE && it.supportsSearch })
        assertTrue(capabilities.any { it.provider == StreamingProviderName.LUOXUE && it.supportsPlayback })
        assertTrue(health.first { it.provider == StreamingProviderName.NETEASE }.authenticated)
        val qqHealth = health.first { it.provider == StreamingProviderName.QQ_MUSIC }
        assertTrue(qqHealth.available)
        assertEquals(null, qqHealth.errorCode)
    }

    @Test
    fun configuredButUnavailableGatewayLoadsNeteaseAccountPlaylistsLocally() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/nuser/account/get" to JSONObject()
                    .put("profile", JSONObject().put("userId", 42L)),
                "/api/user/playlist" to JSONObject()
                    .put(
                        "playlist",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", 1001L)
                                    .put("name", "My NetEase Playlist")
                                    .put("trackCount", 201)
                                    .put("coverImgUrl", "https://p.music.126.net/cover.jpg")
                                    .put("creator", JSONObject().put("nickname", "Echo"))
                            )
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val playlists = gateway.userPlaylists(StreamingProviderName.NETEASE)

        assertEquals("My NetEase Playlist", playlists.single().title)
        assertEquals("1001", playlists.single().providerPlaylistId)
        assertEquals(201, playlists.single().trackCount)
        assertEquals(listOf("/api/nuser/account/get", "/api/user/playlist"), netease.paths)
    }

    @Test
    fun configuredButUnavailableGatewayLoadsNeteaseLikedTracksLocally() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/nuser/account/get" to JSONObject()
                    .put("profile", JSONObject().put("userId", 42L)),
                "/api/likelist" to JSONObject()
                    .put("ids", JSONArray().put(11L).put(22L)),
                "/api/song/detail/" to JSONObject()
                    .put(
                        "songs",
                        JSONArray()
                            .put(neteaseSong(11L, "Liked 11"))
                            .put(neteaseSong(22L, "Liked 22"))
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val tracks = gateway.userLikedTracks(StreamingProviderName.NETEASE)

        assertEquals(listOf("Liked 11", "Liked 22"), tracks.map { it.title })
        assertEquals(listOf("/api/nuser/account/get", "/api/likelist", "/api/song/detail/"), netease.paths)
    }

    @Test
    fun localNeteaseSearchUsesCloudsearchWhenGatewayIsUnconfigured() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/cloudsearch/pc" to JSONObject()
                    .put(
                        "result",
                        JSONObject()
                            .put("songCount", 1)
                            .put("songs", JSONArray().put(neteaseSong(11L, "Echo Seed")))
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val result = gateway.search(
            StreamingSearchRequest(
                provider = StreamingProviderName.NETEASE,
                query = " Echo Seed ",
                pageSize = 5
            )
        )

        assertEquals("Echo Seed", result.tracks.single().title)
        assertEquals("11", result.tracks.single().providerTrackId)
        assertEquals(listOf("/api/cloudsearch/pc"), netease.paths)
        assertEquals("Echo Seed", netease.queries.single()["s"])
        assertEquals("1", netease.queries.single()["type"])
        assertEquals("5", netease.queries.single()["limit"])
        assertEquals("0", netease.queries.single()["offset"])
    }

    @Test
    fun localNeteaseSearchDoesNotRequireConnectedAuth() = runTest {
        val authStore = FakeLocalAuthStore()
        val netease = FakeNeteaseHttpClient(
            responses = mapOf(
                "/api/cloudsearch/pc" to JSONObject()
                    .put(
                        "result",
                        JSONObject()
                            .put("songCount", 1)
                            .put("songs", JSONArray().put(neteaseSong(1336840812L, "No Login Song")))
                    )
            ),
            expectedCookie = null
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val result = gateway.search(
            StreamingSearchRequest(
                provider = StreamingProviderName.NETEASE,
                query = "No Login Song",
                pageSize = 5
            )
        )

        assertEquals("No Login Song", result.tracks.single().title)
        assertEquals("1336840812", result.tracks.single().providerTrackId)
        assertEquals(listOf("/api/cloudsearch/pc"), netease.paths)
    }

    @Test
    fun localNeteaseHeartbeatUsesIntelligenceSeedParamsAndSongInfoDto() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/nuser/account/get" to JSONObject()
                    .put("profile", JSONObject().put("userId", 42L)),
                "/api/likelist" to JSONObject()
                    .put("ids", JSONArray().put(11L)),
                "/api/song/detail/" to JSONObject()
                    .put("songs", JSONArray().put(neteaseSong(11L, "Seed Song"))),
                "/api/user/playlist" to JSONObject()
                    .put(
                        "playlist",
                        JSONArray()
                            .put(JSONObject().put("id", 1_000_000_042L).put("name", "Liked Music"))
                    ),
                "/api/playmode/intelligence/list" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put(
                                "list",
                                JSONArray()
                                    .put(JSONObject().put("songInfoDTO", neteaseSong(22L, "Heartbeat 22")))
                            )
                    )
            )
        )
        val client = LocalNeteaseStreamingClient(authStore, netease)

        val tracks = client.heartbeatRecommendedTracks(count = 12)

        val intelligenceIndex = netease.paths.indexOf("/api/playmode/intelligence/list")
        val query = netease.queries[intelligenceIndex]
        assertEquals("11", query["id"])
        assertEquals("1000000042", query["pid"])
        assertEquals("11", query["sid"])
        assertEquals("12", query["count"])
        assertFalse(query.containsKey("songId"))
        assertFalse(query.containsKey("playlistId"))
        assertEquals(listOf("Seed Song", "Heartbeat 22"), tracks.map { it.title })
    }

    @Test
    fun localNeteaseHeartbeatKeepsFillingUntilTargetCount() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            responses = mapOf(
                "/api/nuser/account/get" to JSONObject()
                    .put("profile", JSONObject().put("userId", 42L)),
                "/api/likelist" to JSONObject()
                    .put("ids", JSONArray().put(11L)),
                "/api/song/detail/" to JSONObject()
                    .put("songs", JSONArray().put(neteaseSong(11L, "Seed Song"))),
                "/api/user/playlist" to JSONObject()
                    .put(
                        "playlist",
                        JSONArray()
                            .put(JSONObject().put("id", 1_000_000_042L).put("name", "Liked Music"))
                    ),
                "/api/v1/discovery/simiSong" to JSONObject()
                    .put("songs", JSONArray().put(neteaseSong(99L, "Similar 99")))
            ),
            dynamicResponse = { path, query ->
                if (path != "/api/playmode/intelligence/list") {
                    return@FakeNeteaseHttpClient null
                }
                val sid = query["sid"].orEmpty()
                when (sid) {
                    "11" -> JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "list",
                                    JSONArray()
                                        .put(JSONObject().put("songInfoDTO", neteaseSong(22L, "Heartbeat 22")))
                                )
                        )
                    "22" -> JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "list",
                                    JSONArray()
                                        .put(JSONObject().put("songInfoDTO", neteaseSong(33L, "Heartbeat 33")))
                                )
                        )
                    else -> JSONObject().put("data", JSONObject().put("list", JSONArray()))
                }
            }
        )
        val client = LocalNeteaseStreamingClient(authStore, netease)

        val tracks = client.heartbeatRecommendedTracks(count = 4)

        assertEquals(listOf("Seed Song", "Heartbeat 22", "Heartbeat 33", "Similar 99"), tracks.map { it.title })
        assertEquals(3, netease.paths.count { it == "/api/playmode/intelligence/list" })
        assertEquals(1, netease.paths.count { it == "/api/v1/discovery/simiSong" })
    }

    @Test
    fun localNeteaseHeartbeatRotatesRepeatedResultOrder() = runTest {
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            responses = mapOf(
                "/api/playmode/intelligence/list" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put(
                                "list",
                                JSONArray()
                                    .put(JSONObject().put("songInfoDTO", neteaseSong(22L, "Heartbeat 22")))
                                    .put(JSONObject().put("songInfoDTO", neteaseSong(33L, "Heartbeat 33")))
                            )
                    )
            )
        )
        val client = LocalNeteaseStreamingClient(authStore, netease)

        val first = client.heartbeatRecommendedTracks(seedTrackId = "11", playlistId = "11", count = 2)
        val second = client.heartbeatRecommendedTracks(seedTrackId = "11", playlistId = "11", count = 2)

        assertEquals(listOf("Heartbeat 22", "Heartbeat 33"), first.map { it.title })
        assertEquals(listOf("Heartbeat 33", "Heartbeat 22"), second.map { it.title })
        assertEquals(2, netease.paths.count { it == "/api/playmode/intelligence/list" })
    }

    @Test
    fun localNeteaseHeartbeatFallsBackToSimilarSongsWithoutAuth() = runTest {
        val authStore = FakeLocalAuthStore()
        val netease = FakeNeteaseHttpClient(
            responses = mapOf(
                "/api/v1/discovery/simiSong" to JSONObject()
                    .put(
                        "songs",
                        JSONArray()
                            .put(neteaseSong(22L, "Similar 22"))
                            .put(neteaseSong(33L, "Similar 33"))
                    )
            ),
            expectedCookie = null
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = "gateway://unconfigured",
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val tracks = gateway.heartbeatRecommendations(
            StreamingHeartbeatRequest(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "11",
                providerPlaylistId = "11",
                count = 2
            )
        )

        assertEquals(listOf("Similar 22", "Similar 33"), tracks.map { it.title })
        assertEquals(listOf("/api/v1/discovery/simiSong"), netease.paths)
        assertEquals("11", netease.queries.single()["songid"])
        assertEquals("2", netease.queries.single()["limit"])
    }

    @Test
    fun neteaseHttpClientMapsJsonLoginCodeToAuthRequired() {
        val server = JsonTestServer(JSONObject().put("code", 301).put("message", "Need login").toString())
        server.start()
        try {
            val client = DefaultNeteaseHttpClient(server.baseUrl)

            val error = try {
                client.getJson("/api/likelist", emptyMap(), "MUSIC_U=expired-token")
                throw AssertionError("Expected NetEase auth error")
            } catch (error: StreamingGatewayException) {
                error
            }

            assertEquals(StreamingErrorCode.AUTH_REQUIRED, error.code)
            assertTrue(error.message.orEmpty().contains("Need login"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun configuredButUnavailableGatewayLoadsNeteasePlaylistDetailLocally() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/v3/playlist/detail" to JSONObject()
                    .put(
                        "playlist",
                        JSONObject()
                            .put("id", 1001L)
                            .put("name", "Paged Playlist")
                            .put("trackCount", 3)
                            .put(
                                "trackIds",
                                JSONArray()
                                    .put(JSONObject().put("id", 11L))
                                    .put(JSONObject().put("id", 22L))
                                    .put(JSONObject().put("id", 33L))
                            )
                    ),
                "/api/song/detail/" to JSONObject()
                    .put(
                        "songs",
                        JSONArray()
                            .put(neteaseSong(11L, "Song 11"))
                            .put(neteaseSong(22L, "Song 22"))
                    ),
                "/api/playlist/track/all" to JSONObject()
                    .put(
                        "songs",
                        JSONArray()
                            .put(neteaseSong(11L, "Song 11"))
                            .put(neteaseSong(22L, "Song 22"))
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val detail = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "1001",
                page = 1,
                pageSize = 2
            )
        )

        assertEquals("Paged Playlist", detail.playlist?.title)
        assertEquals(3, detail.total)
        assertTrue(detail.hasMore)
        assertEquals(listOf("11", "22"), detail.tracks.map { it.providerTrackId })
        assertEquals("Artist", detail.tracks.first().artist)
        assertEquals(listOf("/api/v3/playlist/detail", "/api/playlist/track/all"), netease.paths)
    }

    @Test
    fun configuredButUnavailableGatewayLoadsNeteasePlaylistBeyondEmbeddedLimitLocally() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/v3/playlist/detail" to JSONObject()
                    .put(
                        "playlist",
                        JSONObject()
                            .put("id", 1001L)
                            .put("name", "Large Playlist")
                            .put("trackCount", 1200)
                            .put(
                                "trackIds",
                                JSONArray()
                                    .put(JSONObject().put("id", 11L))
                                    .put(JSONObject().put("id", 22L))
                            )
                    ),
                "/api/playlist/track/all" to JSONObject()
                    .put(
                        "songs",
                        JSONArray()
                            .put(neteaseSong(11L, "Song 11"))
                            .put(neteaseSong(22L, "Song 22"))
                            .put(neteaseSong(33L, "Song 33"))
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val detail = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "1001",
                page = 1,
                pageSize = 2000
            )
        )

        assertEquals("Large Playlist", detail.playlist?.title)
        assertEquals(1200, detail.total)
        assertEquals(listOf("11", "22", "33"), detail.tracks.map { it.providerTrackId })
        assertTrue(detail.hasMore)
        assertEquals(listOf("/api/v3/playlist/detail", "/api/playlist/track/all", "/api/playlist/track/all"), netease.paths)
    }

    @Test
    fun configuredGatewayUsesLocalNeteaseWhenRemotePlaylistIsTruncated() = runTest {
        val server = GatewayTestServer()
        server.start()
        try {
            val authStore = FakeLocalAuthStore(
                cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
            )
            val netease = FakeNeteaseHttpClient(
                mapOf(
                    "/api/v3/playlist/detail" to JSONObject()
                        .put(
                            "playlist",
                            JSONObject()
                                .put("id", 1001L)
                                .put("name", "Large Local Playlist")
                                .put("trackCount", 1200)
                                .put("trackIds", JSONArray().put(JSONObject().put("id", 11L)))
                        ),
                    "/api/playlist/track/all" to JSONObject()
                        .put(
                            "songs",
                            JSONArray()
                                .put(neteaseSong(11L, "Song 11"))
                                .put(neteaseSong(22L, "Song 22"))
                                .put(neteaseSong(33L, "Song 33"))
                        )
                )
            )
            val gateway = RemoteStreamingGateway(
                endpointBaseUrl = server.baseUrl,
                localAuthStore = authStore,
                localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
            )

            val detail = gateway.playlist(
                StreamingPlaylistRequest(
                    provider = StreamingProviderName.NETEASE,
                    providerPlaylistId = "1001",
                    page = 1,
                    pageSize = 2000
                )
            )

            assertEquals("Large Local Playlist", detail.playlist?.title)
            assertEquals(1200, detail.total)
            assertEquals(listOf("11", "22", "33"), detail.tracks.map { it.providerTrackId })
            assertEquals(listOf("/api/v3/playlist/detail", "/api/playlist/track/all", "/api/playlist/track/all"), netease.paths)
        } finally {
            server.stop()
        }
    }

    @Test
    fun localNeteasePlaylistFillsLargeImportWindowFromTruncatedTrackAllPages() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val detail = JSONObject()
            .put(
                "playlist",
                JSONObject()
                    .put("id", 1001L)
                    .put("name", "Truncated API Playlist")
                    .put("trackCount", 450)
                    .put(
                        "trackIds",
                        JSONArray().also { ids ->
                            repeat(450) { index ->
                                ids.put(JSONObject().put("id", index + 1L))
                            }
                        }
                    )
            )
        val netease = FakeNeteaseHttpClient(
            responses = mapOf("/api/v3/playlist/detail" to detail),
            dynamicResponse = { path, query ->
            if (path != "/api/playlist/track/all") {
                return@FakeNeteaseHttpClient null
            }
            val offset = query["offset"]?.toIntOrNull() ?: 0
            val remaining = (450 - offset).coerceAtLeast(0)
            val count = remaining.coerceAtMost(201)
            JSONObject().put(
                "songs",
                JSONArray().also { songs ->
                    repeat(count) { index ->
                        val id = offset + index + 1L
                        songs.put(neteaseSong(id, "Song $id"))
                    }
                }
            )
            }
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val detailResult = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "1001",
                page = 1,
                pageSize = 2000
            )
        )

        assertEquals(450, detailResult.total)
        assertEquals(450, detailResult.tracks.size)
        assertFalse(detailResult.hasMore)
        assertEquals("1", detailResult.tracks.first().providerTrackId)
        assertEquals("450", detailResult.tracks.last().providerTrackId)
        assertEquals(
            listOf("0", "201", "402"),
            netease.queries
                .filterIndexed { index, _ -> netease.paths[index] == "/api/playlist/track/all" }
                .map { it["offset"] }
        )
    }

    @Test
    fun localNeteasePlaylistUsesV3EmbeddedTracksForLargeLikedPlaylist() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val tracks = JSONArray()
        val trackIds = JSONArray()
        repeat(239) { index ->
            val id = index + 1L
            tracks.put(neteaseSong(id, "Song $id"))
            trackIds.put(JSONObject().put("id", id))
        }
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/v3/playlist/detail" to JSONObject()
                    .put(
                        "playlist",
                        JSONObject()
                            .put("id", 1001L)
                            .put("name", "Liked Playlist")
                            .put("trackCount", 239)
                            .put("trackIds", trackIds)
                            .put("tracks", tracks)
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val detail = gateway.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.NETEASE,
                providerPlaylistId = "1001",
                page = 1,
                pageSize = 2000
            )
        )

        assertEquals(239, detail.total)
        assertEquals(239, detail.tracks.size)
        assertFalse(detail.hasMore)
        assertEquals("1", detail.tracks.first().providerTrackId)
        assertEquals("239", detail.tracks.last().providerTrackId)
        assertEquals(listOf("/api/v3/playlist/detail"), netease.paths)
    }

    @Test
    fun configuredButUnavailableGatewayResolvesNeteasePlaybackLocally() = runTest {
        val closedServer = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${closedServer.localPort}"
        closedServer.close()
        val authStore = FakeLocalAuthStore(
            cookies = mapOf(StreamingProviderName.NETEASE to "MUSIC_U=local-token")
        )
        val netease = FakeNeteaseHttpClient(
            mapOf(
                "/api/song/enhance/player/url/v1" to JSONObject()
                    .put(
                        "data",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", 11L)
                                    .put("url", "https://m701.music.126.net/audio.flac")
                                    .put("type", "flac")
                                    .put("br", 999000)
                                    .put("expi", 1200L)
                            )
                    )
            )
        )
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = baseUrl,
            maxRetries = 0,
            localAuthStore = authStore,
            localNeteaseClient = LocalNeteaseStreamingClient(authStore, netease)
        )

        val source = gateway.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "11",
                quality = StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals("https://m701.music.126.net/audio.flac", source.url)
        assertEquals("audio/flac", source.mimeType)
        assertEquals(999000, source.bitrate)
        assertEquals("MUSIC_U=local-token", source.headers["Cookie"])
        assertEquals(listOf("/api/song/enhance/player/url/v1"), netease.paths)
    }

    private fun assertProxiedArtworkUrl(baseUrl: String, originalUrl: String, actualUrl: String?) {
        val value = actualUrl.orEmpty()
        assertTrue(value.startsWith("$baseUrl/artwork?"))
        assertTrue(value.contains("provider=spotify"))
        assertTrue(value.contains("url=${encode(originalUrl)}"))
    }

    private suspend fun captureGatewayError(block: suspend () -> Unit): StreamingGatewayException {
        return try {
            block()
            throw AssertionError("Expected gateway error")
        } catch (error: StreamingGatewayException) {
            error
        }
    }

    private fun neteaseSong(id: Long, name: String): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("dt", 180_000L)
            .put(
                "ar",
                JSONArray().put(
                    JSONObject()
                        .put("id", 7L)
                        .put("name", "Artist")
                )
            )
            .put(
                "al",
                JSONObject()
                    .put("id", 8L)
                    .put("name", "Album")
                    .put("picUrl", "https://p.music.126.net/song.jpg")
            )
    }

    private class GatewayTestServer(
        private val statusOverrides: Map<String, Int> = emptyMap(),
        private val statusSequences: Map<String, ArrayDeque<Int>> = emptyMap(),
        private val retryAfterSeconds: Map<String, Long> = emptyMap(),
        private val responseOverrides: Map<String, String> = emptyMap()
    ) {
        private val server = ServerSocket(0)
        private val ready = CountDownLatch(1)
        private lateinit var thread: Thread
        @Volatile
        private var running = false
        val requests = mutableListOf<RecordedRequest>()
        val baseUrl: String
            get() = "http://127.0.0.1:${server.localPort}"

        fun start() {
            running = true
            thread = Thread {
                ready.countDown()
                while (running && !server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            val request = socket.record()
                            requests.add(request)
                            val statusCode = statusSequences[request.path]?.removeFirstOrNull()
                                ?: statusOverrides[request.path]
                                ?: 200
                            socket.respond(
                                responseFor(request, statusCode),
                                statusCode,
                                retryAfterSeconds[request.path]
                            )
                        }
                    } catch (_: Exception) {
                        if (running) throw RuntimeException("Gateway test server failed")
                    }
                }
            }
            thread.name = "RemoteStreamingGatewayTestServer"
            thread.isDaemon = true
            thread.start()
            ready.await(2, TimeUnit.SECONDS)
        }

        fun stop() {
            running = false
            server.close()
        }

        private fun responseFor(request: RecordedRequest, statusCode: Int = 200): String {
            if (statusCode !in 200..299) {
                return JSONObject()
                    .put(
                        "error",
                        JSONObject()
                            .put("code", "AUTH_REQUIRED")
                            .put("message", "Login required")
                    )
                    .toString()
            }
            responseOverrides[request.path]?.let { return it }
            return when (request.path) {
                "/providers" -> JSONObject()
                    .put(
                        "providers",
                        listOf(
                            JSONObject()
                                .put("name", "spotify")
                                .put("displayName", "Spotify")
                                .put("enabled", true)
                                .put(
                                    "capabilities",
                                    JSONObject()
                                        .put("supportsSearch", true)
                                        .put("supportsPlayback", true)
                                        .put("supportsAuth", true)
                                        .put("supportedMediaTypes", listOf("track", "album"))
                                )
                                .put(
                                    "auth",
                                    JSONObject()
                                        .put("kind", "custom_tabs_app_link")
                                        .put("connected", false)
                                )
                        )
                    )
                    .toString()

                "/providers/capabilities" -> JSONObject()
                    .put(
                        "capabilities",
                        listOf(
                            JSONObject()
                                .put("provider", "spotify")
                                .put("displayName", "Spotify")
                                .put("enabled", true)
                                .put("canSearch", true)
                                .put("canPlayback", false)
                                .put("canAuth", true)
                                .put("supportedSearchMediaTypes", listOf("track", "album"))
                                .put("actions", listOf("search", "auth"))
                        )
                    )
                    .toString()

                "/search" -> StreamingGatewayJson.searchResultJson(
                    StreamingSearchResult(
                        provider = StreamingProviderName.SPOTIFY,
                        query = "echo",
                        page = 1,
                        pageSize = 50,
                        hasMore = false,
                        tracks = listOf(
                            StreamingTrack(
                                provider = StreamingProviderName.SPOTIFY,
                                providerTrackId = "track-1",
                                title = "Echo",
                                artist = "Artist",
                                coverUrl = "https://image.example.test/cover art.jpg",
                                coverThumbUrl = "https://image.example.test/cover thumb.jpg"
                            )
                        )
                    )
                )

                "/providers/health" -> JSONObject()
                    .put(
                        "providers",
                        listOf(
                            JSONObject()
                                .put("provider", "spotify")
                                .put("available", true)
                                .put("authenticated", false)
                                .put("latencyMs", 42L)
                        )
                    )
                    .toString()

                "/playlist" -> playlistResponse(request.query)

                "/resolvePlayback" -> StreamingGatewayJson.playbackSourceJson(
                    StreamingPlaybackSource(
                        provider = StreamingProviderName.SPOTIFY,
                        providerTrackId = "track-1",
                        url = "https://example.test/audio.mp3",
                        mimeType = "audio/mpeg"
                    )
                )

                "/auth/state" -> StreamingGatewayJson.authStateJson(
                    StreamingProviderName.SPOTIFY,
                    StreamingAuthState(
                        kind = StreamingAuthKind.CUSTOM_TABS_APP_LINK,
                        connected = false,
                        statusMessage = "Needs sign-in"
                    )
                )

                "/auth/start" -> JSONObject()
                    .put("provider", "spotify")
                    .put("launchUrl", "https://accounts.spotify.com/login")
                    .put(
                        "state",
                        JSONObject()
                            .put("kind", "custom_tabs_app_link")
                            .put("connected", false)
                    )
                    .toString()

                "/auth/complete" -> JSONObject()
                    .put("provider", "spotify")
                    .put(
                        "state",
                        JSONObject()
                            .put("kind", "custom_tabs_app_link")
                            .put("connected", true)
                            .put("accountDisplayName", "Spotify User")
                    )
                    .toString()

                "/auth/signOut" -> StreamingGatewayJson.authStateJson(
                    StreamingProviderName.SPOTIFY,
                    StreamingAuthState(
                        kind = StreamingAuthKind.CUSTOM_TABS_APP_LINK,
                        connected = false
                    )
                )

                else -> JSONObject()
                    .put(
                        "error",
                        JSONObject()
                            .put("code", "AUTH_REQUIRED")
                            .put("message", "Login required")
                    )
                    .toString()
            }
        }

        private fun playlistResponse(query: String): String {
            if (query.contains("provider=netease")) {
                return StreamingGatewayJson.playlistDetailJson(
                    StreamingPlaylistDetail(
                        provider = StreamingProviderName.NETEASE,
                        providerPlaylistId = "1001",
                        playlist = StreamingPlaylist(
                            provider = StreamingProviderName.NETEASE,
                            providerPlaylistId = "1001",
                            title = "Truncated Remote Playlist",
                            trackCount = 1200
                        ),
                        tracks = listOf(
                            StreamingTrack(
                                provider = StreamingProviderName.NETEASE,
                                providerTrackId = "remote-1",
                                title = "Remote 1",
                                artist = "Artist"
                            )
                        ),
                        total = 1200,
                        hasMore = false
                    )
                )
            }
            return StreamingGatewayJson.playlistDetailJson(
                StreamingPlaylistDetail(
                    provider = StreamingProviderName.SPOTIFY,
                    providerPlaylistId = "playlist-1",
                    playlist = StreamingPlaylist(
                        provider = StreamingProviderName.SPOTIFY,
                        providerPlaylistId = "playlist-1",
                        title = "Playlist",
                        coverUrl = "https://image.example.test/playlist.jpg",
                        trackCount = 1
                    ),
                    tracks = listOf(
                        StreamingTrack(
                            provider = StreamingProviderName.SPOTIFY,
                            providerTrackId = "track-1",
                            title = "Echo",
                            artist = "Artist",
                            coverUrl = "https://image.example.test/playlist-track.jpg"
                        )
                    ),
                    total = 1
                )
            )
        }

        private fun Socket.record(): RecordedRequest {
            val reader = BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "" }
            val target = parts.getOrElse(1) { "/" }
            val headers = linkedMapOf<String, String>()
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                val separator = headerLine.indexOf(':')
                if (separator > 0) {
                    headers[headerLine.substring(0, separator).trim().lowercase()] =
                        headerLine.substring(separator + 1).trim()
                }
                headerLine = reader.readLine()
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyChars = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val count = reader.read(bodyChars, read, contentLength - read)
                if (count < 0) break
                read += count
            }
            val body = String(bodyChars, 0, read)
            val pathAndQuery = target.split("?", limit = 2)
            return RecordedRequest(
                method = method,
                path = pathAndQuery[0],
                query = pathAndQuery.getOrElse(1) { "" },
                body = body,
                accept = headers["accept"].orEmpty(),
                userAgent = headers["user-agent"].orEmpty(),
                cookie = headers["cookie"].orEmpty(),
                streamingProvider = headers["x-echo-streaming-provider"].orEmpty()
            )
        }

        private fun Socket.respond(body: String, statusCode: Int = 200, retryAfterSeconds: Long? = null) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val statusText = if (statusCode in 200..299) "OK" else "Error"
            val headers = buildString {
                append("HTTP/1.1 $statusCode $statusText\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                retryAfterSeconds?.let { append("Retry-After: $it\r\n") }
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.UTF_8)
            getOutputStream().use { output ->
                output.write(headers)
                output.write(bytes)
                output.flush()
            }
        }
    }

    private class JsonTestServer(private val responseBody: String) {
        private val server = ServerSocket(0)
        private val ready = CountDownLatch(1)
        private lateinit var thread: Thread
        @Volatile
        private var running = false
        val baseUrl: String
            get() = "http://127.0.0.1:${server.localPort}"

        fun start() {
            running = true
            thread = Thread {
                ready.countDown()
                try {
                    server.accept().use { socket ->
                        drainRequest(socket)
                        respond(socket)
                    }
                } catch (_: Exception) {
                    if (running) throw RuntimeException("JSON test server failed")
                }
            }
            thread.name = "NeteaseJsonTestServer"
            thread.isDaemon = true
            thread.start()
            ready.await(2, TimeUnit.SECONDS)
        }

        fun stop() {
            running = false
            server.close()
        }

        private fun drainRequest(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                line = reader.readLine()
            }
        }

        private fun respond(socket: Socket) {
            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.UTF_8)
            socket.getOutputStream().use { output ->
                output.write(headers)
                output.write(bytes)
                output.flush()
            }
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String,
        val body: String,
        val accept: String,
        val userAgent: String,
        val cookie: String,
        val streamingProvider: String
    )

    private companion object {
        fun encode(value: String): String {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
    }
}
