package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLuoxueStreamingClientTest {
    @Test
    fun searchAggregatesKuwoAndKugouAsLuoxueSources() {
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "search.kuwo.cn" to JSONObject()
                        .put(
                            "abslist",
                            JSONArray().put(
                                JSONObject()
                                    .put("DC_TARGETID", "101")
                                    .put("NAME", "Kuwo Song")
                                    .put("ARTIST", "Kuwo Artist")
                                    .put("FORMATS", "MP3128;MP3H;ALFLAC")
                            )
                        )
                        .toString(),
                    "mobiles.kugou.com/api/v3/search/song" to JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "info",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("hash", "0123456789abcdef0123456789abcdef")
                                            .put("album_id", 22)
                                            .put("album_audio_id", 33)
                                            .put("songname", "Kugou Song")
                                            .put("singername", "Kugou Artist")
                                            .put("HQFileHash", "abcdef0123456789")
                                    )
                                )
                        )
                        .toString()
                )
            )
        )

        val result = client.search(StreamingSearchRequest(StreamingProviderName.LUOXUE, "hello", pageSize = 10))

        assertEquals(2, result.tracks.size)
        assertEquals("kw:101", result.tracks[0].providerTrackId)
        assertEquals("kg:0123456789abcdef0123456789abcdef.22.33", result.tracks[1].providerTrackId)
        assertTrue(result.tracks[1].playbackCandidates.any { it.label == "LX/酷狗" })
    }

    @Test
    fun sourcePrefixSearchLimitsToRequestedSource() {
        val fake = FakeLuoxueHttpClient(
            mapOf(
                "search.kuwo.cn" to JSONObject().put("abslist", JSONArray()).toString(),
                "mobiles.kugou.com/api/v3/search/song" to JSONObject()
                    .put(
                        "data",
                        JSONObject().put(
                            "info",
                            JSONArray().put(
                                JSONObject()
                                    .put("hash", "abcdefabcdefabcdefabcdefabcdef12")
                                    .put("songname", "Only Kugou")
                            )
                        )
                    )
                    .toString()
            )
        )
        val client = LocalLuoxueStreamingClient(fake)

        val result = client.search(StreamingSearchRequest(StreamingProviderName.LUOXUE, "kg:hello"))

        assertEquals(1, result.tracks.size)
        assertEquals("Only Kugou", result.tracks.single().title)
        assertTrue(fake.urls.none { it.contains("search.kuwo.cn") })
    }

    @Test
    fun kugouPlaybackCollectsNestedUrl() {
        val hash = "0123456789abcdef0123456789abcdef"
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "gateway.kugou.com/v5/url" to JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put("play_url", "https://kugou.example.test/song.flac")
                        )
                        .toString()
                )
            )
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:$hash.22.33",
                quality = StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals(StreamingProviderName.LUOXUE, source.provider)
        assertEquals("kg:$hash.22.33", source.providerTrackId)
        assertEquals("https://kugou.example.test/song.flac", source.url)
        assertEquals("audio/flac", source.mimeType)
    }

    private class FakeLuoxueHttpClient(
        private val responses: Map<String, String>
    ) : LuoxueHttpClient {
        val urls = mutableListOf<String>()

        override fun getText(url: String, headers: Map<String, String>): String {
            urls += url
            return responses.entries.firstOrNull { (key, _) -> url.contains(key) }?.value
                ?: error("No fake response for $url")
        }
    }
}
