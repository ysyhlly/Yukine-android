package app.yukine.streaming

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNeteaseStreamingClientTest {
    @Test
    fun resolvePlaybackConvertsBitrateFromBitsPerSecondToKbps() {
        val client = LocalNeteaseStreamingClient(
            authStore = FakeAuthStore("MUSIC_U=session"),
            httpClient = PlaybackNeteaseHttpClient()
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "2054974200",
                quality = StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals(2_945, source.bitrate)
    }

    @Test
    fun setFavoriteUsesEncryptedWeApiEnvelopeAndDesktopCookie() {
        val http = RecordingNeteaseHttpClient()
        val client = LocalNeteaseStreamingClient(
            authStore = FakeAuthStore("MUSIC_U=session; __csrf=csrf-value"),
            httpClient = http
        )

        client.setFavorite("123456", true)

        assertEquals(
            "/weapi/playlist/manipulate/tracks?csrf_token=csrf-value",
            http.path
        )
        assertEquals(setOf("params", "encSecKey"), http.form.keys)
        assertTrue(http.form.getValue("params").isNotBlank())
        assertTrue(http.form.getValue("encSecKey").isNotBlank())
        assertFalse(http.form.values.any { it.contains("123456") || it.contains("csrf-value") })
        assertTrue(http.cookie.orEmpty().contains("os=pc"))
        assertEquals(2, http.getPaths.size)

        client.setFavorite("654321", false)

        assertEquals(2, http.getPaths.size)
    }

    private class PlaybackNeteaseHttpClient : NeteaseHttpClient {
        override fun getJson(
            path: String,
            query: Map<String, String>,
            cookieHeader: String?
        ): JSONObject = JSONObject().put(
            "data",
            org.json.JSONArray().put(
                JSONObject()
                    .put("id", "2054974200")
                    .put("url", "https://example.test/audio.flac")
                    .put("type", "flac")
                    .put("br", 2_945_010)
            )
        )
    }

    private class RecordingNeteaseHttpClient : NeteaseHttpClient {
        var path: String = ""
        var form: Map<String, String> = emptyMap()
        var cookie: String? = null
        val getPaths = mutableListOf<String>()

        override fun getJson(path: String, query: Map<String, String>, cookieHeader: String?): JSONObject {
            getPaths += path
            return when (path) {
                "/api/nuser/account/get" -> JSONObject().put(
                    "profile",
                    JSONObject().put("userId", "42")
                )
                "/api/user/playlist" -> JSONObject().put(
                    "playlist",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("id", "liked-playlist")
                            .put("name", "我喜欢的音乐")
                            .put("specialType", 5)
                    )
                )
                else -> JSONObject()
            }
        }

        override fun postForm(
            path: String,
            form: Map<String, String>,
            cookieHeader: String?
        ): NeteaseHttpResponse {
            this.path = path
            this.form = form
            this.cookie = cookieHeader
            return NeteaseHttpResponse(JSONObject().put("code", 200))
        }
    }

    private class FakeAuthStore(private val cookie: String) : StreamingLocalAuthStore {
        override fun authState(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState(LocalStreamingAuthStore.providerAuthKind(provider), connected = true)

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState = authState(provider)

        override fun signOut(provider: StreamingProviderName): StreamingAuthState =
            authState(provider).copy(connected = false)

        override fun cookieHeader(provider: StreamingProviderName): String = cookie

        override fun connected(provider: StreamingProviderName): Boolean = true
    }
}
