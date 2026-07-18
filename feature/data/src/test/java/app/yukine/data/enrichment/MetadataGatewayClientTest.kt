package app.yukine.data.enrichment

import app.yukine.identity.CanonicalRecording
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.ProviderCachedResponse
import app.yukine.identity.ProviderEndpointHealth
import app.yukine.identity.ProviderResponseCacheRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MetadataGatewayClientTest {
    @Test
    fun sendsOnlyMetadataAndFingerprintAndParsesAcoustIdEvidence() {
        var requestedUrl = ""
        val transport = MetadataHttpTransport { url, _ ->
            requestedUrl = url
            MetadataHttpResponse(200, RESPONSE)
        }
        val cache = FakeCache()
        val client = MetadataGatewayClient(cache, transport, "https://gateway.example/base", "1.0")

        val result = client.searchRecording(
            CanonicalRecording(
                recordingId = 7L,
                canonicalId = "canonical-7",
                title = "Song",
                primaryArtistDisplay = "Artist",
                durationMs = 180_000L
            ),
            "Artist",
            GatewayAudioFingerprint("AQAA-test-fingerprint", 180)
        )

        assertTrue(requestedUrl.startsWith("https://gateway.example/base/v1/recordings/search?"))
        assertTrue(requestedUrl.contains("fingerprint=AQAA-test-fingerprint"))
        assertTrue(requestedUrl.contains("fingerprintDuration=180"))
        assertFalse(requestedUrl.contains("audio="))
        assertEquals("", cache.lastError)
        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals("acoustid", candidate.provider)
        assertEquals("recording-mbid", candidate.recordingMbid)
        assertEquals("acoust-id", candidate.acoustId)
        assertEquals("https://coverartarchive.org/release/release-id/front-500", candidate.coverUrl)
        assertTrue(candidate.fingerprintVerified)
        assertEquals(0.99, candidate.providerScore, 0.0001)
        assertEquals(1, cache.successes)
    }

    @Test
    fun invalidEndpointFailsClosedWithoutNetwork() {
        var calls = 0
        val client = MetadataGatewayClient(FakeCache(), MetadataHttpTransport { _, _ ->
            calls++
            MetadataHttpResponse(200, RESPONSE)
        }, "file:///private", "1.0")

        val result = client.searchRecording(CanonicalRecording(1L, "id", title = "Song"), "", null)

        assertTrue(result.allEndpointsFailed)
        assertEquals(0, calls)
    }

    @Test
    fun artistSearchParsesProfileAndUsesProfileContractCacheKey() {
        var requestedUrl = ""
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { url, _ ->
                requestedUrl = url
                MetadataHttpResponse(
                    200,
                    """{"artists":[{"provider":"musicbrainz","id":"artist-mbid","name":"Aimer","artistMbid":"artist-mbid","avatarUrl":"https://commons.wikimedia.org/avatar.jpg","description":"日本の女性歌手、作詞家。","score":1.0}]}"""
                )
            },
            "https://gateway.example/base",
            "1.0"
        )

        val result = client.searchArtist(
            CanonicalArtist(artistKey = 9L, artistId = "artist-9", displayName = "Aimer"),
            listOf("エメ")
        )

        assertTrue(requestedUrl.contains("responseVersion=artist-profile-v2"))
        assertEquals("https://commons.wikimedia.org/avatar.jpg", result.candidates.single().avatarUrl)
        assertEquals("日本の女性歌手、作詞家。", result.candidates.single().description)
    }

    @Test
    fun artistProviderBackfillsMissingGatewayAvatarFromStrongMbidRelation() {
        val lookedUpMbids = mutableListOf<String>()
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ ->
                MetadataHttpResponse(
                    200,
                    """{"artists":[{"provider":"musicbrainz","id":"artist-mbid","name":"Aimer","artistMbid":"artist-mbid","avatarUrl":"","score":1.0}]}"""
                )
            },
            "https://gateway.example",
            "1.0"
        )
        val provider = MetadataGatewayArtistProvider(
            client,
            ArtistAvatarLookup { mbid ->
                lookedUpMbids += mbid
                "https://api.deezer.com/artist/123/image?size=big"
            }
        )

        val result = provider.search(
            CanonicalArtist(artistKey = 9L, artistId = "artist-9", displayName = "Aimer"),
            emptyList()
        )

        assertEquals(listOf("artist-mbid"), lookedUpMbids)
        assertEquals(
            "https://api.deezer.com/artist/123/image?size=big",
            result.candidates.single().avatarUrl
        )
        assertEquals("", result.candidates.single().description)
    }

    @Test
    fun recordingSearchRejectsUntrustedOrNonHttpsCoverUrls() {
        val responses = ArrayDeque(listOf(
            RESPONSE.replace(
                "https://coverartarchive.org/release/release-id/front-500",
                "http://coverartarchive.org/release/release-id/front-500"
            ),
            RESPONSE.replace(
                "https://coverartarchive.org/release/release-id/front-500",
                "https://images.example.com/cover.jpg"
            )
        ))
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ -> MetadataHttpResponse(200, responses.removeFirst()) },
            "https://gateway.example",
            "1.0"
        )

        assertEquals(
            "",
            client.searchRecording(CanonicalRecording(1L, "one", title = "Song"), "", null)
                .candidates.single().coverUrl
        )
        assertEquals(
            "",
            client.searchRecording(CanonicalRecording(2L, "two", title = "Song"), "", null)
                .candidates.single().coverUrl
        )
    }

    @Test
    fun oldRecordingResponseWithoutCoverUrlStillParses() {
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ ->
                MetadataHttpResponse(
                    200,
                    RESPONSE.replace(
                        ""","coverUrl":"https://coverartarchive.org/release/release-id/front-500"""",
                        ""
                    )
                )
            },
            "https://gateway.example",
            "1.0"
        )

        assertEquals(
            "",
            client.searchRecording(CanonicalRecording(1L, "old", title = "Song"), "", null)
                .candidates.single().coverUrl
        )
    }

    @Test
    fun lyricsSearchEncodesMetadataAndUsesSevenDayCacheTtl() {
        var requestedUrl = ""
        val cache = FakeCache()
        val client = MetadataGatewayClient(
            cache,
            MetadataHttpTransport { url, _ ->
                requestedUrl = url
                MetadataHttpResponse(
                    200,
                    """{"lyrics":{"provider":"lrclib","id":"7","title":"歌 名","artist":"歌手","album":"专辑","durationMs":180000,"syncedLyrics":"[00:01.00]歌词","plainLyrics":"歌词"}}"""
                )
            },
            "https://gateway.example",
            "1.0"
        )

        val result = client.searchLyrics("歌 名", "歌手 & A", "专辑", 180_000L)

        assertTrue(requestedUrl.contains("title=%E6%AD%8C%20%E5%90%8D"))
        assertTrue(requestedUrl.contains("artist=%E6%AD%8C%E6%89%8B%20%26%20A"))
        assertEquals("[00:01.00]歌词", result.lyrics?.syncedLyrics)
        assertEquals(MetadataGatewayClient.LYRICS_CACHE_TTL_MS, cache.lastTtlMs)
    }

    @Test
    fun malformedAndErrorLyricsResponsesFailClosedForCallerFallback() {
        listOf(
            MetadataHttpResponse(404, """{"error":"not_found"}"""),
            MetadataHttpResponse(502, """{"error":"upstream_failure","requestId":"request-1"}"""),
            MetadataHttpResponse(200, "{malformed")
        ).forEach { response ->
            val cache = FakeCache()
            val client = MetadataGatewayClient(
                cache,
                MetadataHttpTransport { _, _ -> response },
                "https://gateway.example",
                "1.0"
            )

            val result = client.searchLyrics("Song", "Artist", "Album", 180_000L)

            assertTrue(result.allEndpointsFailed)
            assertEquals(null, result.lyrics)
            assertTrue(cache.lastError.isNotBlank())
        }
    }

    private class FakeCache : ProviderResponseCacheRepository {
        var successes = 0
        var lastError = ""
        var lastTtlMs = 0L
        override fun response(provider: String, endpoint: String, requestHash: String, now: Long): ProviderCachedResponse? = null
        override fun endpointHealth(provider: String, endpoint: String) = ProviderEndpointHealth(provider, endpoint)
        override fun saveSuccess(provider: String, endpoint: String, requestHash: String, responseJson: String, now: Long, ttlMs: Long) {
            successes++
            lastTtlMs = ttlMs
        }
        override fun recordFailure(provider: String, endpoint: String, error: String, now: Long): ProviderEndpointHealth {
            lastError = error
            return ProviderEndpointHealth(provider, endpoint, 1)
        }
    }

    private companion object {
        const val RESPONSE = """
            {"recordings":[{
              "provider":"acoustid","id":"acoust-id","title":"Song",
              "artists":[{"id":"artist-mbid","name":"Artist"}],
              "album":"Album","durationMs":180000,"recordingMbid":"recording-mbid",
              "acoustId":"acoust-id","coverUrl":"https://coverartarchive.org/release/release-id/front-500",
              "fingerprintVerified":true,"score":0.99
            }]}
        """
    }
}
