package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQqMusicStreamingClientTest {
    @Test
    fun searchParsesDesktopSongListShape() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
                postResponse = JSONObject()
                    .put(
                        "req_1",
                        JSONObject()
                            .put(
                                "data",
                                JSONObject()
                                    .put(
                                        "body",
                                        JSONObject()
                                            .put(
                                                "song",
                                                JSONObject()
                                                    .put(
                                                        "list",
                                                        JSONArray()
                                                            .put(
                                                                JSONObject()
                                                                    .put("songmid", "song-mid-1")
                                                                    .put("songName", "QQ Song")
                                                                    .put(
                                                                        "singer",
                                                                        JSONArray().put(
                                                                            JSONObject()
                                                                                .put("mid", "artist-mid")
                                                                                .put("name", "QQ Artist")
                                                                        )
                                                                    )
                                                                    .put(
                                                                        "album",
                                                                        JSONObject()
                                                                            .put("mid", "album-mid")
                                                                            .put("name", "QQ Album")
                                                                    )
                                                                    .put(
                                                                        "file",
                                                                        JSONObject()
                                                                            .put("media_mid", "media-mid-1")
                                                                            .put("size_320mp3", 1024)
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            )
        )

        val result = client.search(StreamingSearchRequest(StreamingProviderName.QQ_MUSIC, "echo"))

        assertEquals(1, result.tracks.size)
        val track = result.tracks.single()
        assertEquals("song-mid-1|media-mid-1", track.providerTrackId)
        assertEquals("QQ Song", track.title)
        assertEquals("QQ Artist", track.artist)
        assertEquals("QQ Album", track.album)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R500x500M000album-mid.jpg", track.coverUrl)
        assertTrue(track.qualities.contains(StreamingAudioQuality.HIGH))
    }

    @Test
    fun smartboxSearchPreservesMediaMidForPlayback() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
                postResponse = JSONObject(),
                getResponse = JSONObject().put(
                    "data",
                    JSONObject().put(
                        "song",
                        JSONObject().put(
                            "itemlist",
                            JSONArray().put(
                                JSONObject()
                                    .put("mid", "song-mid-1")
                                    .put("strMediaMid", "media-mid-1")
                                    .put("name", "QQ Song")
                                    .put("singer", "QQ Artist")
                            )
                        )
                    )
                )
            )
        )

        val track = client.search(StreamingSearchRequest(StreamingProviderName.QQ_MUSIC, "echo")).tracks.single()

        assertEquals("song-mid-1|media-mid-1", track.providerTrackId)
        assertTrue(track.playbackCandidates.all { it.providerTrackId == "song-mid-1|media-mid-1" })
    }

    @Test
    fun playlistParsesLegacyTopLevelAlbumAndMediaMidFields() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
                getResponse = JSONObject()
                    .put(
                        "cdlist",
                        JSONArray().put(
                            JSONObject()
                                .put("dissname", "QQ Playlist")
                                .put(
                                    "songlist",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("mid", "song-mid-1")
                                            .put("name", "QQ Song")
                                            .put("albumname", "QQ Album")
                                            .put("albumid", 42)
                                            .put("albummid", "album-mid-1")
                                            .put("strMediaMid", "media-mid-1")
                                            .put(
                                                "singer",
                                                JSONArray().put(
                                                    JSONObject().put("mid", "artist-mid").put("name", "QQ Artist")
                                                )
                                            )
                                    )
                                )
                        )
                    )
            )
        )

        val detail = client.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerPlaylistId = "playlist-1"
            )
        )

        val track = detail.tracks.single()
        assertEquals("song-mid-1|media-mid-1", track.providerTrackId)
        assertEquals("QQ Album", track.album)
        assertEquals("42", track.albumId)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R500x500M000album-mid-1.jpg", track.coverUrl)
    }

    @Test
    fun resolvePlaybackParsesTopLevelDataVkeyShape() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("sip", JSONArray().put("https://stream.qq.example/"))
                        .put(
                            "midurlinfo",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("purl", "C400media-mid-1.m4a?vkey=test")
                                )
                        )
                )
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://stream.qq.example/C400media-mid-1.m4a?vkey=test", source.url)
        assertEquals("song-mid-1|media-mid-1", source.providerTrackId)
        assertEquals("m4a", source.codec)
        assertEquals("audio/mp4", source.mimeType)
        assertNull(source.headers["Cookie"])
        val request = httpClient.postBodies.single()
        assertEquals("12345", request.getString("loginUin"))
        assertEquals("json", request.getJSONObject("comm").getString("format"))
        assertEquals(24, request.getJSONObject("comm").getInt("ct"))
        assertEquals(0, request.getJSONObject("comm").getInt("cv"))
        assertEquals("local-key", request.getJSONObject("comm").getString("authst"))
        assertEquals("music.audioCdnDispatch.cdnDispatch", request.getJSONObject("req").getString("module"))
        val params = request.getJSONObject("req_0").getJSONObject("param")
        assertEquals("music.vkey.GetVkey", request.getJSONObject("req_0").getString("module"))
        assertEquals("UrlGetVkey", request.getJSONObject("req_0").getString("method"))
        assertEquals(
            "M800media-mid-1.mp3",
            params.getJSONArray("filename").getString(0)
        )
    }

    @Test
    fun resolvePlaybackFallsBackToAacWhenMp3VariantHasNoPurl() {
        val httpClient = FakeQqMusicHttpClient(
            postResponses = listOf(
                vkeyResponse(purl = ""),
                vkeyResponse(purl = "C400media-mid-1.m4a?vkey=test")
            )
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.STANDARD
            )
        )

        assertEquals("https://stream.qq.example/C400media-mid-1.m4a?vkey=test", source.url)
        assertEquals("m4a", source.codec)
        assertEquals("audio/mp4", source.mimeType)
        assertEquals(
            listOf("M500media-mid-1.mp3", "C400media-mid-1.m4a"),
            httpClient.postBodies.map { body ->
                body.getJSONObject("req_0")
                    .getJSONObject("param")
                    .getJSONArray("filename")
                    .getString(0)
            }
        )
    }

    @Test
    fun resolvePlaybackFallsBackToStandardUrlGetVkeyQuality() {
        val httpClient = FakeQqMusicHttpClient(
            postResponses = listOf(
                vkeyResponse(purl = ""),
                vkeyResponse(purl = ""),
                vkeyResponse(purl = "M500media-mid-1.mp3?vkey=test")
            )
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://stream.qq.example/M500media-mid-1.mp3?vkey=test", source.url)
        assertEquals(
            listOf(
                "M800media-mid-1.mp3",
                "C600media-mid-1.m4a",
                "M500media-mid-1.mp3"
            ),
            httpClient.postBodies.map { body ->
                body.getJSONObject("req_0")
                    .getJSONObject("param")
                    .getJSONArray("filename")
                    .getString(0)
            }
        )
    }

    @Test
    fun resolvePlaybackPreservesExplicitLoginFailureWithoutTryingOtherCandidates() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = vkeyResponse(purl = "", message = "请登录后播放")
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val error = runCatching {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "song-mid-1|media-mid-1",
                    quality = StreamingAudioQuality.HIGH
                )
            )
        }.exceptionOrNull()

        assertTrue(error is StreamingGatewayException)
        assertEquals(StreamingErrorCode.AUTH_REQUIRED, (error as StreamingGatewayException).code)
        assertEquals(1, httpClient.postBodies.size)
    }

    @Test
    fun resolvePlaybackPreservesRegionFailureWithoutTryingOtherCandidates() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = vkeyResponse(purl = "", message = "该歌曲在当前地区不可用")
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val error = runCatching {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "song-mid-1|media-mid-1",
                    quality = StreamingAudioQuality.HIGH
                )
            )
        }.exceptionOrNull()

        assertTrue(error is StreamingGatewayException)
        assertEquals(StreamingErrorCode.REGION_BLOCKED, (error as StreamingGatewayException).code)
        assertEquals(1, httpClient.postBodies.size)
    }

    @Test
    fun resolvePlaybackPromotesCleartextQqSipToHttps() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
                postResponse = vkeyResponse(
                    purl = "M800media-mid-1.mp3?vkey=test",
                    sip = listOf("http://ws.qq.example/", "http://stream.qq.example/")
                )
            )
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://stream.qq.example/M800media-mid-1.mp3?vkey=test", source.url)
    }

    @Test
    fun resolvePlaybackUsesValidDispatchSipWhenVkeySipIsInvalid() {
        val response = vkeyResponse(
            purl = "M800media-mid-1.mp3?vkey=test",
            sip = listOf("163.125.230.232;invalid;")
        ).put(
            "req",
            JSONObject().put(
                "data",
                JSONObject().put("sip", JSONArray().put("https://dispatch.qq.example/"))
            )
        )
        val httpClient = FakeQqMusicHttpClient(postResponse = response)
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://dispatch.qq.example/M800media-mid-1.mp3?vkey=test", source.url)
    }

    @Test
    fun resolvePlaybackUsesDefaultHttpsCdnWhenAllSipsAreInvalid() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
                postResponse = vkeyResponse(
                    purl = "M800media-mid-1.mp3?vkey=test",
                    sip = listOf("163.125.230.232;invalid;")
                )
            )
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals(
            "https://isure.stream.qqmusic.qq.com/M800media-mid-1.mp3?vkey=test",
            source.url
        )
    }

    @Test
    fun resolvePlaybackHidesQqIpInvalidDiagnostic() {
        val response = JSONObject().put(
            "req_0",
            JSONObject()
                .put("code", 104009)
                .put(
                    "data",
                    JSONObject()
                        .put("retcode", 104009)
                        .put("msg", "163.125.230.232;invalid;")
                        .put("midurlinfo", JSONArray().put(JSONObject().put("purl", "")))
                )
        )
        val httpClient = FakeQqMusicHttpClient(postResponse = response)
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val error = runCatching {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "song-mid-1|media-mid-1",
                    quality = StreamingAudioQuality.HIGH
                )
            )
        }.exceptionOrNull()

        assertTrue(error is StreamingGatewayException)
        assertEquals(StreamingErrorCode.SOURCE_UNAVAILABLE, (error as StreamingGatewayException).code)
        assertTrue(error.message.orEmpty().contains("IP 校验失败"))
        assertFalse(error.message.orEmpty().contains("163.125.230.232"))
        assertEquals(1, httpClient.postBodies.size)
    }

    @Test
    fun resolvePlaybackUsesOnlyUrlGetVkeyAndNewCdnDispatch() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = vkeyResponse(purl = "M500media-mid-1.mp3?vkey=test")
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.STANDARD
            )
        )

        assertEquals("https://stream.qq.example/M500media-mid-1.mp3?vkey=test", source.url)
        val request = httpClient.postBodies.last()
        assertEquals("music.vkey.GetVkey", request.getJSONObject("req_0").getString("module"))
        assertEquals("UrlGetVkey", request.getJSONObject("req_0").getString("method"))
        assertEquals("M500media-mid-1.mp3", request.getJSONObject("req_0").getJSONObject("param")
            .getJSONArray("filename").getString(0))
        assertEquals("music.audioCdnDispatch.cdnDispatch", request.getJSONObject("req").getString("module"))
        assertEquals(1, httpClient.postBodies.size)
    }

    @Test
    fun resolvePlaybackUrlGetVkeyUsesDoubleSongMidWhenMediaMidIsMissing() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = vkeyResponse(purl = "M500song-mid-1song-mid-1.mp3?vkey=test")
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1",
                quality = StreamingAudioQuality.STANDARD
            )
        )

        val fileName = httpClient.postBodies.last().getJSONObject("req_0").getJSONObject("param")
            .getJSONArray("filename").getString(0)
        assertEquals("M500song-mid-1song-mid-1.mp3", fileName)
        assertEquals(1, httpClient.postBodies.size)
    }

    @Test
    fun resolvePlaybackRejectsNameOnlyCredentialCookie() {
        // QQ Music can set a name-only placeholder (e.g. `qm_keyst=`) before issuing the real
        // credential. Login must not treat that as success and playback must reject it, otherwise
        // the user sees "connected" but every resolve fails. Value-less credential => AUTH_REQUIRED.
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qm_keyst=; qqmusic_key="),
            FakeQqMusicHttpClient()
        )

        val error = runCatching {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.QQ_MUSIC,
                    providerTrackId = "song-mid-1|media-mid-1",
                    quality = StreamingAudioQuality.HIGH
                )
            )
        }.exceptionOrNull()

        assertTrue(error is StreamingGatewayException)
        assertEquals(StreamingErrorCode.AUTH_REQUIRED, (error as StreamingGatewayException).code)
    }

    @Test
    fun resolvePlaybackAcceptsCredentialCookieWithValue() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("sip", JSONArray().put("https://stream.qq.example/"))
                        .put(
                            "midurlinfo",
                            JSONArray().put(JSONObject().put("purl", "C400media-mid-1.m4a?vkey=test"))
                        )
                )
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qm_keyst=real-keyst-value"),
            httpClient
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://stream.qq.example/C400media-mid-1.m4a?vkey=test", source.url)
        val comm = httpClient.postBodies.single().getJSONObject("comm")
        assertEquals("real-keyst-value", comm.getString("authst"))
    }

    @Test
    fun resolvePlaybackKeepsPsrfCredentialInCookieWithoutSendingItAsAuthst() {
        val httpClient = FakeQqMusicHttpClient(
            postResponse = vkeyResponse(purl = "C400media-mid-1.m4a?vkey=test")
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; psrf_qqaccess_token=access-token"),
            httpClient
        )

        client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        val params = httpClient.postBodies.single().getJSONObject("req_0").getJSONObject("param")
        assertFalse(params.has("authst"))
        assertFalse(httpClient.postBodies.single().getJSONObject("comm").has("authst"))
        assertEquals("uin=o12345; psrf_qqaccess_token=access-token", httpClient.postHeaders.single()["Cookie"])
    }

    @Test
    fun playbackAuthstPrefersQqMusicKeyOverOtherCookieCredentials() {
        assertEquals(
            "music-key",
            qqPlaybackAuthst("psrf_qqaccess_token=access-token; qm_keyst=key-st; qqmusic_key=music-key")
        )
    }

    @Test
    fun setFavoriteUsesAuthenticatedMusicuEnvelope() {
        val httpClient = FakeQqMusicHttpClient(
            postResponses = listOf(
                JSONObject().put(
                    "req_0",
                    JSONObject()
                        .put("code", 0)
                        .put(
                            "data",
                            JSONObject().put(
                                "tracks",
                                JSONArray().put(JSONObject().put("id", 987654L).put("type", 0))
                            )
                        )
                ),
                JSONObject().put("req_0", JSONObject().put("code", 0).put("data", JSONObject()))
            )
        )
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            httpClient
        )

        client.setFavorite("song-mid|media-mid", true)

        assertEquals(2, httpClient.postBodies.size)
        val lookup = httpClient.postBodies.first().getJSONObject("req_0")
        assertEquals("music.trackInfo.UniformRuleCtrl", lookup.getString("module"))
        assertEquals("CgiGetTrackInfo", lookup.getString("method"))
        assertEquals("song-mid", lookup.getJSONObject("param").getJSONArray("mids").getString(0))

        val request = httpClient.postBodies.last()
        assertEquals("12345", request.getString("loginUin"))
        assertEquals("json", request.getJSONObject("comm").getString("format"))
        assertEquals("local-key", request.getJSONObject("comm").getString("authst"))
        val write = request.getJSONObject("req_0")
        assertEquals("music.musicasset.PlaylistDetailWrite", write.getString("module"))
        assertEquals("AddSonglist", write.getString("method"))
        assertEquals(201, write.getJSONObject("param").getInt("dirId"))
        val songInfo = write.getJSONObject("param").getJSONArray("v_songInfo").getJSONObject(0)
        assertEquals(987654L, songInfo.getLong("songId"))
        assertEquals(0, songInfo.getInt("songType"))
    }

    private class FakeAuthStore(
        private val cookie: String
    ) : StreamingLocalAuthStore {
        override fun authState(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState(
                kind = LocalStreamingAuthStore.providerAuthKind(provider),
                connected = true
            )

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState = authState(provider)

        override fun signOut(provider: StreamingProviderName): StreamingAuthState =
            authState(provider).copy(connected = false)

        override fun cookieHeader(provider: StreamingProviderName): String? = cookie

        override fun connected(provider: StreamingProviderName): Boolean = true
    }

    private class FakeQqMusicHttpClient(
        private val getResponse: JSONObject = JSONObject(),
        private val postResponse: JSONObject = JSONObject(),
        private val postResponses: List<JSONObject> = emptyList()
    ) : QqMusicHttpClient {
        val postBodies = mutableListOf<JSONObject>()
        val postHeaders = mutableListOf<Map<String, String>>()
        private var postCallCount = 0

        override fun getJson(url: String, headers: Map<String, String>): JSONObject = getResponse

        override fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
            postBodies += body
            postHeaders += headers.toMap()
            val responseIndex = postCallCount++
            return postResponses.getOrElse(responseIndex) { postResponse }
        }
    }

    private fun vkeyResponse(
        purl: String,
        sip: List<String> = listOf("https://stream.qq.example/"),
        message: String? = null
    ): JSONObject {
        val midUrlInfo = JSONObject().put("purl", purl)
        message?.let { midUrlInfo.put("msg", it) }
        return JSONObject()
            .put(
                "data",
                JSONObject()
                    .put("sip", JSONArray().apply { sip.forEach { value -> put(value) } })
                    .put("midurlinfo", JSONArray().put(midUrlInfo))
            )
    }
}
