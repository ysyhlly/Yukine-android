package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertTrue(track.qualities.contains(StreamingAudioQuality.HIGH))
    }

    @Test
    fun resolvePlaybackParsesTopLevelDataVkeyShape() {
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qqmusic_key=local-key"),
            FakeQqMusicHttpClient(
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
        assertEquals("mp3", source.codec)
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
        val client = LocalQqMusicStreamingClient(
            FakeAuthStore("uin=o12345; qm_keyst=real-keyst-value"),
            FakeQqMusicHttpClient(
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
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "song-mid-1|media-mid-1",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals("https://stream.qq.example/C400media-mid-1.m4a?vkey=test", source.url)
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
        private val postResponse: JSONObject = JSONObject()
    ) : QqMusicHttpClient {
        override fun getJson(url: String, headers: Map<String, String>): JSONObject = getResponse

        override fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject =
            postResponse
    }
}
