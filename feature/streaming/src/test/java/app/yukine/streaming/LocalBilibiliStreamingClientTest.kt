package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBilibiliStreamingClientTest {

    @Test
    fun videoPlaylistExpandsMultiPageVideo() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(viewResponse())
        )
        val client = LocalBilibiliStreamingClient(FakeAuthStore(), http)

        val detail = client.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.BILIBILI,
                providerPlaylistId = "video:BV1U64y1a785",
                pageSize = 20
            )
        )

        assertEquals("测试视频", detail.playlist?.title)
        assertEquals(2, detail.total)
        assertEquals(listOf("video:BV1U64y1a785:cid:101", "video:BV1U64y1a785:cid:102"), detail.tracks.map { it.providerTrackId })
        assertEquals("测试视频 · 开场", detail.tracks.first().title)
        assertEquals("测试 UP", detail.tracks.first().artist)
        assertTrue(http.headers.single()["Cookie"]?.contains("SESSDATA=test") == true)
    }

    @Test
    fun favoritePlaylistMapsVideoEntries() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(
                ok(
                    JSONObject()
                        .put(
                            "info",
                            JSONObject()
                                .put("title", "我的收藏")
                                .put("media_count", 1)
                                .put("upper", JSONObject().put("name", "测试用户"))
                        )
                        .put(
                            "medias",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", 2)
                                    .put("bvid", "BV1U64y1a785")
                                    .put("title", "收藏视频")
                                    .put("duration", 65)
                                    .put("cover", "//i.example/cover.jpg")
                                    .put("upper", JSONObject().put("mid", 9).put("name", "视频 UP"))
                                    .put("ugc", JSONObject().put("first_cid", 101))
                            )
                        )
                        .put("has_more", false)
                )
            )
        )
        val client = LocalBilibiliStreamingClient(FakeAuthStore(), http)

        val detail = client.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.BILIBILI,
                providerPlaylistId = "favorite:42",
                pageSize = 20
            )
        )

        assertEquals("我的收藏", detail.playlist?.title)
        assertEquals("video:BV1U64y1a785:cid:101", detail.tracks.single().providerTrackId)
        assertEquals("https://i.example/cover.jpg", detail.tracks.single().coverUrl)
        assertFalse(detail.hasMore)
    }

    @Test
    fun resolvePlaybackSelectsHighestDashAudioAndKeepsCookieOutOfPlaybackHeaders() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(
                viewResponse(),
                ok(
                    JSONObject().put(
                        "dash",
                        JSONObject().put(
                            "audio",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("baseUrl", "https://audio.example/low.m4s?deadline=2000000000")
                                        .put("bandwidth", 64000)
                                        .put("mimeType", "audio/mp4")
                                        .put("codecs", "mp4a.40.2")
                                )
                                .put(
                                    JSONObject()
                                        .put("base_url", "https://audio.example/high.m4s?deadline=2000000100")
                                        .put("bandwidth", 192000)
                                        .put("mime_type", "audio/mp4")
                                        .put("codecs", "mp4a.40.2")
                                )
                        )
                    )
                )
            )
        )
        val client = LocalBilibiliStreamingClient(FakeAuthStore(), http)

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.BILIBILI,
                providerTrackId = "video:BV1U64y1a785:cid:101",
                quality = StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals("https://audio.example/high.m4s?deadline=2000000100", source.url)
        assertEquals(192, source.bitrate)
        assertEquals(2_000_000_100_000L, source.expiresAtEpochMs)
        assertEquals("https://www.bilibili.com/video/BV1U64y1a785", source.headers["Referer"])
        assertTrue(source.headers["User-Agent"].orEmpty().contains("Chrome/"))
        assertFalse(source.headers.containsKey("Cookie"))
    }

    @Test
    fun publicVideoPlaybackDoesNotRequireLogin() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(
                viewResponse(),
                ok(
                    JSONObject().put(
                        "dash",
                        JSONObject().put(
                            "audio",
                            JSONArray().put(
                                JSONObject()
                                    .put("baseUrl", "https://audio.example/public.m4s")
                                    .put("bandwidth", 128000)
                                    .put("mimeType", "audio/mp4")
                            )
                        )
                    )
                )
            )
        )
        val client = LocalBilibiliStreamingClient(authStore = null, httpClient = http)

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.BILIBILI,
                providerTrackId = "video:BV1U64y1a785:cid:101",
                quality = StreamingAudioQuality.HIGH
            )
        )

        assertEquals(StreamingProviderName.BILIBILI, source.provider)
        assertEquals("https://audio.example/public.m4s", source.url)
        assertEquals("audio/mp4", source.mimeType)
        assertTrue(http.headers.all { headers -> headers.containsKey("Cookie").not() })
    }

    @Test
    fun accountFavoriteFoldersBecomeImportablePlaylists() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(
                ok(
                    JSONObject()
                        .put("isLogin", true)
                        .put("mid", 123)
                        .put("uname", "测试用户")
                        .put("face", "//i.example/avatar.jpg")
                ),
                ok(
                    JSONObject().put(
                        "list",
                        JSONArray().put(
                            JSONObject()
                                .put("id", 42)
                                .put("title", "音乐收藏")
                                .put("media_count", 8)
                        )
                    )
                )
            )
        )
        val client = LocalBilibiliStreamingClient(FakeAuthStore(), http)

        val playlists = client.userPlaylists()

        assertEquals(1, playlists.size)
        assertEquals("favorite:42", playlists.single().providerPlaylistId)
        assertEquals("音乐收藏", playlists.single().title)
        assertEquals(8, playlists.single().trackCount)
    }

    @Test
    fun shortLinkIsResolvedBeforeLoadingVideo() {
        val http = FakeBilibiliHttpClient(
            responses = mutableListOf(viewResponse()),
            redirectTarget = "https://www.bilibili.com/video/BV1U64y1a785?p=2"
        )
        val client = LocalBilibiliStreamingClient(FakeAuthStore(), http)
        val shortId = BilibiliTarget.ShortLink("https://b23.tv/example").providerPlaylistId

        val detail = client.playlist(
            StreamingPlaylistRequest(
                provider = StreamingProviderName.BILIBILI,
                providerPlaylistId = shortId,
                pageSize = 20
            )
        )

        assertEquals(1, detail.tracks.size)
        assertEquals("video:BV1U64y1a785:cid:102", detail.tracks.single().providerTrackId)
        assertEquals("https://b23.tv/example", http.resolvedUrl)
    }

    private class FakeAuthStore : StreamingLocalAuthStore {
        override fun authState(provider: StreamingProviderName): StreamingAuthState =
            StreamingAuthState(
                kind = StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE,
                connected = true
            )

        override fun saveLogin(
            provider: StreamingProviderName,
            cookieHeader: String?,
            displayName: String?
        ): StreamingAuthState = authState(provider)

        override fun signOut(provider: StreamingProviderName): StreamingAuthState =
            authState(provider).copy(connected = false)

        override fun cookieHeader(provider: StreamingProviderName): String = "SESSDATA=test; bili_jct=csrf"

        override fun connected(provider: StreamingProviderName): Boolean = true
    }

    private class FakeBilibiliHttpClient(
        private val responses: MutableList<JSONObject>,
        private val redirectTarget: String = ""
    ) : BilibiliHttpClient {
        val headers = mutableListOf<Map<String, String>>()
        var resolvedUrl: String? = null

        override fun getJson(url: String, headers: Map<String, String>): JSONObject {
            this.headers += headers.toMap()
            return responses.removeAt(0)
        }

        override fun resolveRedirect(url: String): String {
            resolvedUrl = url
            return redirectTarget
        }
    }

    private fun viewResponse(): JSONObject =
        ok(
            JSONObject()
                .put("bvid", "BV1U64y1a785")
                .put("aid", 1234)
                .put("title", "测试视频")
                .put("desc", "视频简介")
                .put("pic", "//i.example/video.jpg")
                .put("owner", JSONObject().put("mid", 88).put("name", "测试 UP"))
                .put(
                    "pages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("cid", 101)
                                .put("page", 1)
                                .put("part", "开场")
                                .put("duration", 60)
                        )
                        .put(
                            JSONObject()
                                .put("cid", 102)
                                .put("page", 2)
                                .put("part", "正片")
                                .put("duration", 120)
                        )
                )
        )

    private fun ok(data: JSONObject): JSONObject =
        JSONObject().put("code", 0).put("data", data)
}
