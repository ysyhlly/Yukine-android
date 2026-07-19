package app.yukine.streaming

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalKugouStreamingClientTest {
    @Test
    fun searchReturnsFirstClassKugouIdentity() {
        val hash = "ABCDEF0123456789ABCDEF0123456789"
        val fake = FakeHttpClient(
            mapOf(
                "mobiles.kugou.com/api/v3/search/song" to JSONObject()
                    .put(
                        "data",
                        JSONObject().put(
                            "info",
                            JSONArray().put(
                                JSONObject()
                                    .put("hash", hash)
                                    .put("album_id", 22)
                                    .put("album_audio_id", 33)
                                    .put("songname", "Kugou Song")
                                    .put("singername", "Kugou Artist")
                            )
                        )
                    )
                    .toString()
            )
        )
        val client = LocalKugouStreamingClient(LocalLuoxueStreamingClient(fake))

        val result = client.search(
            StreamingSearchRequest(StreamingProviderName.KUGOU, "hello")
        )

        val track = result.tracks.single()
        assertEquals(StreamingProviderName.KUGOU, result.provider)
        assertEquals(StreamingProviderName.KUGOU, track.provider)
        assertEquals("${hash.lowercase()}.22.33", track.providerTrackId)
        assertEquals("streaming:kugou:${hash.lowercase()}.22.33", track.stableKey)
        assertTrue(track.playbackCandidates.all { it.provider == StreamingProviderName.KUGOU })
        assertTrue(track.playbackCandidates.all { it.label == "酷狗" })
        assertNull(track.luoxueMusicInfoJson)
        assertTrue(fake.urls.single().contains("keyword=hello"))
    }

    @Test
    fun playlistAndPlaybackRemoveLegacyKgPrefix() {
        val hash = "0123456789abcdef0123456789abcdef"
        val fake = FakeHttpClient(
            mapOf(
                "mobiles.kugou.com/api/v3/special/song" to JSONObject()
                    .put(
                        "data",
                        JSONObject()
                            .put("specialname", "测试歌单")
                            .put("total", 1)
                            .put(
                                "info",
                                JSONArray().put(
                                    JSONObject()
                                        .put("hash", hash)
                                        .put("album_id", 22)
                                        .put("album_audio_id", 33)
                                        .put("songname", "Kugou Song")
                                )
                            )
                    )
                    .toString(),
                "gateway.kugou.com/v5/url" to JSONObject()
                    .put("data", JSONObject().put("play_url", "https://kugou.example.test/song.flac"))
                    .toString()
            )
        )
        val client = LocalKugouStreamingClient(LocalLuoxueStreamingClient(fake))

        val playlist = client.playlist(
            StreamingPlaylistRequest(StreamingProviderName.KUGOU, "kg:1001")
        )
        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                StreamingProviderName.KUGOU,
                "kg:$hash.22.33",
                StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals(StreamingProviderName.KUGOU, playlist.provider)
        assertEquals("1001", playlist.providerPlaylistId)
        assertEquals("1001", playlist.playlist?.providerPlaylistId)
        assertEquals("$hash.22.33", playlist.tracks.single().providerTrackId)
        assertEquals(StreamingProviderName.KUGOU, source.provider)
        assertEquals("$hash.22.33", source.providerTrackId)
        assertEquals("https://kugou.example.test/song.flac", source.url)
    }

    @Test
    fun identityNormalizesLegacyAndPartialIds() {
        assertEquals(
            "abcdef.22.33",
            KugouIdentity.canonicalTrackId("KG:ABCDEF.22.33")
        )
        assertEquals("abcdef.0.0", KugouIdentity.canonicalTrackId("abcdef"))
        assertEquals("kg:abcdef.0.0", KugouIdentity.legacyLuoxueTrackId("kugou:ABCDEF"))
        assertNull(KugouIdentity.canonicalTrackId(" "))
    }

    private class FakeHttpClient(
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
