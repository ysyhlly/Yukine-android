package app.yukine.data.enrichment

import app.yukine.identity.CanonicalRecording
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalAlbum
import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderCachedResponse
import app.yukine.identity.ProviderEndpointHealth
import app.yukine.identity.ProviderResponseCacheRepository
import java.security.MessageDigest
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
            MetadataHttpResponse(200, RECORDING_RESPONSE)
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

        assertTrue(requestedUrl.startsWith("https://gateway.example/base/v2/recordings/search?"))
        assertTrue(requestedUrl.contains("fingerprint=AQAA-test-fingerprint"))
        assertTrue(requestedUrl.contains("fingerprintDuration=180"))
        assertFalse(requestedUrl.contains("audio="))
        assertFalse(requestedUrl.contains("durationMs="))
        assertEquals("", cache.lastError)
        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals("acoustid", candidate.provider)
        assertEquals("acoust-id", candidate.providerItemId)
        assertEquals("recording-mbid", candidate.recordingMbid)
        assertEquals("work-mbid", candidate.workMbid)
        assertEquals("USRC17607839", candidate.isrc)
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
            MetadataHttpResponse(200, RECORDING_RESPONSE)
        }, "file:///private", "1.0")

        val result = client.searchRecording(CanonicalRecording(1L, "id", title = "Song"), "", null)

        assertTrue(result.allEndpointsFailed)
        assertEquals(0, calls)
    }

    @Test
    fun exhaustedDailyQuotaSkipsNetworkWithoutPenalizingEndpointHealth() {
        var calls = 0
        var quotaChecks = 0
        val cache = FakeCache()
        val client = MetadataGatewayClient(
            cache = cache,
            transport = MetadataHttpTransport { _, _ ->
                calls++
                MetadataHttpResponse(200, RECORDING_RESPONSE)
            },
            endpoint = "https://gateway.example",
            applicationVersion = "1.0",
            now = { 123L },
            requestQuota = MetadataGatewayRequestQuota {
                quotaChecks++
                false
            }
        )

        val result = client.searchRecording(
            CanonicalRecording(1L, "id", title = "Song"),
            "",
            null
        )

        assertTrue(result.allEndpointsFailed)
        assertEquals(1, quotaChecks)
        assertEquals(0, calls)
        assertEquals("", cache.lastError)
    }

    @Test
    fun artistSearchUsesStrictV2QueryAndParsesProfile() {
        var requestedUrl = ""
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { url, _ ->
                requestedUrl = url
                MetadataHttpResponse(
                    200,
                    ARTIST_RESPONSE
                )
            },
            "https://gateway.example/base",
            "1.0"
        )

        val result = client.searchArtist(
            CanonicalArtist(artistKey = 9L, artistId = "artist-9", displayName = "Aimer"),
            listOf("エメ")
        )

        assertTrue(requestedUrl.startsWith("https://gateway.example/base/v2/artists/search?"))
        assertTrue(requestedUrl.contains("name=Aimer"))
        assertFalse(requestedUrl.contains("aliases="))
        assertFalse(requestedUrl.contains("responseVersion="))
        val candidate = result.candidates.single()
        assertEquals("musicbrainz", candidate.provider)
        assertEquals("artist-mbid", candidate.providerItemId)
        assertEquals("artist-mbid", candidate.artistMbid)
        assertEquals(1.0, candidate.providerScore, 0.0001)
        assertEquals("https://commons.wikimedia.org/avatar.jpg", candidate.avatarUrl)
        assertEquals("日本の女性歌手、作詞家。", candidate.description)
    }

    @Test
    fun artistProviderBackfillsMissingGatewayAvatarFromStrongMbidRelation() {
        val lookedUpMbids = mutableListOf<String>()
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ ->
                MetadataHttpResponse(
                    200,
                    ARTIST_RESPONSE.replace(
                        "https://commons.wikimedia.org/avatar.jpg",
                        ""
                    )
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
        assertEquals("日本の女性歌手、作詞家。", result.candidates.single().description)
    }

    @Test
    fun albumSearchUsesCanonicalContextAndParsesAliasesAndReleaseIds() {
        var requestedUrl = ""
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { url, _ ->
                requestedUrl = url
                MetadataHttpResponse(200, ALBUM_RESPONSE)
            },
            "https://gateway.example/base",
            "1.0"
        )

        val result = client.searchAlbum(
            CanonicalAlbum(
                albumKey = 5L,
                albumId = "album-5",
                displayName = "Echo",
                albumArtistDisplay = "Artist",
                releaseType = "Album",
                year = 2024
            ),
            listOf("回声")
        )

        assertTrue(requestedUrl.startsWith("https://gateway.example/base/v2/albums/search?"))
        assertTrue(requestedUrl.contains("title=Echo"))
        assertTrue(requestedUrl.contains("artist=Artist"))
        assertTrue(requestedUrl.contains("year=2024"))
        assertFalse(requestedUrl.contains("aliases="))
        val candidate = result.candidates.single()
        assertEquals("musicbrainz", candidate.provider)
        assertEquals("release-group-mbid", candidate.providerAlbumId)
        assertEquals(setOf("回声", "Echo (Deluxe)"), candidate.aliases)
        assertEquals("release-group-mbid", candidate.musicBrainzReleaseGroupId)
        assertEquals("release-mbid", candidate.musicBrainzReleaseId)
        assertEquals(2024, candidate.year)
        assertEquals(0.97, candidate.providerScore, 0.0001)
    }

    @Test
    fun recordingSearchRejectsUntrustedOrNonHttpsCoverUrls() {
        val responses = ArrayDeque(listOf(
            RECORDING_RESPONSE.replace(
                "https://coverartarchive.org/release/release-id/front-500",
                "http://coverartarchive.org/release/release-id/front-500"
            ),
            RECORDING_RESPONSE.replace(
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
    fun recordingResponseWithoutCoverUrlStillParses() {
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ ->
                MetadataHttpResponse(
                    200,
                    RECORDING_RESPONSE.replace(
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
    fun sourceSelectionPrefersIdentityThenFirstValidThenCanonicalId() {
        val responses = ArrayDeque(listOf(
            recordingResponseWithSources(
                "recording:identity",
                """
                    [
                      {"provider":"metadata-provider","id":"metadata-id","role":"metadata","matchedBy":[],"fields":[],"confidence":0.8},
                      {"provider":"identity-provider","id":"identity-id","role":"identity","matchedBy":[],"fields":[],"confidence":0.9}
                    ]
                """
            ),
            recordingResponseWithSources(
                "recording:metadata",
                """
                    [
                      {"provider":"metadata-provider","id":"metadata-id","role":"metadata","matchedBy":[],"fields":[],"confidence":0.8}
                    ]
                """
            ),
            recordingResponseWithSources("recording:fallback", "[]")
        ))
        val client = MetadataGatewayClient(
            FakeCache(),
            MetadataHttpTransport { _, _ -> MetadataHttpResponse(200, responses.removeFirst()) },
            "https://gateway.example",
            "1.0"
        )

        val identity = client.searchRecording(CanonicalRecording(1L, "one", title = "Song"), "", null)
            .candidates.single()
        val metadata = client.searchRecording(CanonicalRecording(2L, "two", title = "Song"), "", null)
            .candidates.single()
        val fallback = client.searchRecording(CanonicalRecording(3L, "three", title = "Song"), "", null)
            .candidates.single()

        assertEquals("identity-provider", identity.provider)
        assertEquals("identity-id", identity.providerItemId)
        assertEquals("metadata-provider", metadata.provider)
        assertEquals("metadata-id", metadata.providerItemId)
        assertEquals(MetadataGatewayClient.PROVIDER, fallback.provider)
        assertEquals("recording:fallback", fallback.providerItemId)
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
                    LYRICS_RESPONSE
                )
            },
            "https://gateway.example",
            "1.0"
        )

        val result = client.searchLyrics("歌 名", "歌手 & A", "专辑", 180_000L)

        assertTrue(requestedUrl.contains("title=%E6%AD%8C%20%E5%90%8D"))
        assertTrue(requestedUrl.contains("artist=%E6%AD%8C%E6%89%8B%20%26%20A"))
        assertTrue(requestedUrl.startsWith("https://gateway.example/v2/lyrics/search?"))
        assertEquals("lrclib", result.lyrics?.provider)
        assertEquals("lyrics:lrclib:7", result.lyrics?.id)
        assertEquals("[00:01.00]歌词", result.lyrics?.syncedLyrics)
        assertEquals(MetadataGatewayClient.LYRICS_CACHE_TTL_MS, cache.lastTtlMs)
    }

    @Test
    fun nullLyricsIsALegalEmptyResult() {
        val cache = FakeCache()
        val client = MetadataGatewayClient(
            cache,
            MetadataHttpTransport { _, _ -> MetadataHttpResponse(200, """{"lyrics":null}""") },
            "https://gateway.example",
            "1.0"
        )

        val result = client.searchLyrics("Song", "", "", 0L)

        assertFalse(result.allEndpointsFailed)
        assertEquals(null, result.lyrics)
        assertEquals("", cache.lastError)
        assertEquals(1, cache.successes)
    }

    @Test
    fun malformedAndErrorLyricsResponsesFailClosedForCallerFallback() {
        listOf(
            MetadataHttpResponse(400, """{"error":"invalid_request","requestId":"request-0","issues":[]}"""),
            MetadataHttpResponse(404, """{"error":"not_found"}"""),
            MetadataHttpResponse(429, """{"error":"server_rate_limited","requestId":"request-rate"}"""),
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

    @Test
    fun staleV2CacheIsReturnedAfterNetworkFailure() {
        val requestHash = sha256("v2/recordings/search?title=Song&limit=12")
        val cache = FakeCache(
            ProviderCachedResponse(
                provider = MetadataGatewayClient.PROVIDER,
                endpoint = "https://gateway.example/",
                requestHash = requestHash,
                responseJson = RECORDING_RESPONSE,
                createdAt = 0L,
                expiresAt = 999L,
                freshness = ProviderCacheFreshness.STALE
            )
        )
        var networkCalls = 0
        val client = MetadataGatewayClient(
            cache,
            MetadataHttpTransport { _, _ ->
                networkCalls++
                MetadataHttpResponse(502, """{"error":"upstream_failure"}""")
            },
            "https://gateway.example",
            "1.0",
            now = { 1_000L }
        )

        val result = client.searchRecording(CanonicalRecording(1L, "id", title = "Song"), "", null)

        assertEquals(1, networkCalls)
        assertTrue(result.fromCache)
        assertEquals("acoust-id", result.candidates.single().providerItemId)
        assertEquals("HTTP 502", cache.lastError)
    }

    @Test
    fun v1CacheHashIsNotReusedForV2Request() {
        val legacyHash = sha256(
            "v1/recordings/search?title=Song&artist=Artist&durationMs=180000&limit=12"
        )
        val cache = FakeCache(
            ProviderCachedResponse(
                provider = MetadataGatewayClient.PROVIDER,
                endpoint = "https://gateway.example/",
                requestHash = legacyHash,
                responseJson = RECORDING_RESPONSE,
                createdAt = 0L,
                expiresAt = Long.MAX_VALUE,
                freshness = ProviderCacheFreshness.FRESH
            )
        )
        var networkCalls = 0
        val client = MetadataGatewayClient(
            cache,
            MetadataHttpTransport { _, _ ->
                networkCalls++
                MetadataHttpResponse(200, RECORDING_RESPONSE)
            },
            "https://gateway.example",
            "1.0"
        )

        client.searchRecording(
            CanonicalRecording(
                recordingId = 1L,
                canonicalId = "id",
                title = "Song",
                durationMs = 180_000L
            ),
            "Artist",
            null
        )

        val expectedV2Hash = sha256("v2/recordings/search?title=Song&artist=Artist&limit=12")
        assertEquals(1, networkCalls)
        assertEquals(expectedV2Hash, cache.lastRequestHash)
        assertEquals(expectedV2Hash, cache.savedRequestHash)
        assertFalse(cache.lastRequestHash == legacyHash)
    }

    private class FakeCache(
        var cachedResponse: ProviderCachedResponse? = null
    ) : ProviderResponseCacheRepository {
        var successes = 0
        var lastError = ""
        var lastTtlMs = 0L
        var lastRequestHash = ""
        var savedRequestHash = ""
        var savedResponseJson = ""
        var responseCalls = 0
        override fun response(
            provider: String,
            endpoint: String,
            requestHash: String,
            now: Long
        ): ProviderCachedResponse? {
            responseCalls++
            lastRequestHash = requestHash
            return cachedResponse?.takeIf {
                it.provider == provider &&
                    it.endpoint == endpoint &&
                    it.requestHash == requestHash
            }
        }

        override fun endpointHealth(provider: String, endpoint: String) = ProviderEndpointHealth(provider, endpoint)

        override fun saveSuccess(
            provider: String,
            endpoint: String,
            requestHash: String,
            responseJson: String,
            now: Long,
            ttlMs: Long
        ) {
            successes++
            lastTtlMs = ttlMs
            savedRequestHash = requestHash
            savedResponseJson = responseJson
        }

        override fun recordFailure(provider: String, endpoint: String, error: String, now: Long): ProviderEndpointHealth {
            lastError = error
            return ProviderEndpointHealth(provider, endpoint, 1)
        }
    }

    private fun recordingResponseWithSources(canonicalId: String, sourcesJson: String): String = """
        {
          "recordings":[{
            "canonicalId":"$canonicalId",
            "title":"Song",
            "artists":[{"id":"artist-id","name":"Artist"}],
            "album":"Album",
            "coverUrl":"",
            "durationMs":180000,
            "identifiers":{},
            "fingerprintVerified":false,
            "confidence":0.8,
            "sources":$sourcesJson,
            "possibleDuplicates":[]
          }]
        }
    """

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val RECORDING_RESPONSE = """
            {"recordings":[{
              "canonicalId":"recording:mbid:recording-mbid","title":"Song",
              "artists":[{"id":"artist-mbid","name":"Artist","sortName":"Artist"}],
              "album":"Album","coverUrl":"https://coverartarchive.org/release/release-id/front-500",
              "durationMs":180000,
              "identifiers":{
                "recordingMbid":"recording-mbid","workMbid":"work-mbid",
                "isrc":"USRC17607839","acoustId":"acoust-id"
              },
              "fingerprintVerified":true,"confidence":0.99,
              "sources":[
                {
                  "provider":"musicbrainz","id":"recording-mbid","role":"metadata",
                  "matchedBy":["recording_mbid"],"fields":["title","artists"],"confidence":1.0
                },
                {
                  "provider":"acoustid","id":"acoust-id","role":"identity",
                  "matchedBy":["fingerprint"],"fields":["identifiers.acoustId"],"confidence":0.99
                }
              ],
              "possibleDuplicates":[]
            }]}
        """

        const val ARTIST_RESPONSE = """
            {"artists":[{
              "canonicalId":"artist:mbid:artist-mbid","name":"Aimer","sortName":"Aimer",
              "aliases":["エメ"],"country":"JP","type":"PERSON",
              "identifiers":{"artistMbid":"artist-mbid","wikidata":"Q123"},
              "avatarUrl":"https://commons.wikimedia.org/avatar.jpg",
              "description":"日本の女性歌手、作詞家。","confidence":1.0,
              "sources":[
                {
                  "provider":"wikidata","id":"Q123","role":"enrichment",
                  "matchedBy":["artist_mbid"],"fields":["avatarUrl","description"],"confidence":1.0
                },
                {
                  "provider":"musicbrainz","id":"artist-mbid","role":"identity",
                  "matchedBy":["artist_mbid"],"fields":["name","identifiers.artistMbid"],"confidence":1.0
                }
              ]
            }]}
        """

        const val ALBUM_RESPONSE = """
            {"albums":[{
              "canonicalId":"album:mbid:release-group-mbid","title":"Echo",
              "aliases":["回声","Echo (Deluxe)"],
              "artists":[{"id":"artist-mbid","name":"Artist","sortName":"Artist"}],
              "type":"Album","year":2024,
              "identifiers":{
                "releaseGroupMbid":"release-group-mbid","releaseMbid":"release-mbid"
              },
              "confidence":0.97,
              "sources":[{
                "provider":"musicbrainz","id":"release-group-mbid","role":"identity",
                "matchedBy":["release_group_mbid"],"fields":["title","artists"],"confidence":1.0
              }]
            }]}
        """

        const val LYRICS_RESPONSE = """
            {"lyrics":{
              "canonicalId":"lyrics:lrclib:7","title":"歌 名","artist":"歌手","album":"专辑",
              "durationMs":180000,"syncedLyrics":"[00:01.00]歌词","plainLyrics":"歌词",
              "confidence":1.0,
              "sources":[{
                "provider":"lrclib","id":"7","role":"identity","matchedBy":["exact_metadata"],
                "fields":["syncedLyrics","plainLyrics"],"confidence":1.0
              }]
            }}
        """
    }
}
