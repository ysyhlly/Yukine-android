package app.yukine.data.enrichment

import app.yukine.identity.ProviderCacheFreshness
import app.yukine.identity.ProviderCachedResponse
import app.yukine.identity.ProviderEndpointHealth
import app.yukine.identity.ProviderResponseCacheRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicBrainzMetadataClientTest {
    @Test
    fun usesOfficialThenEuThenCustomAndSendsRequiredHeaders() {
        val cache = FakeCache()
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val transport = MetadataHttpTransport { url, headers ->
            calls += url to headers
            if (url.startsWith(OFFICIAL) || url.startsWith(EU)) MetadataHttpResponse(503, "busy")
            else MetadataHttpResponse(200, RESPONSE)
        }
        val client = client(cache, transport, customProxy = "https://proxy.example/ws/2")

        val result = client.searchRecording(RecordingMetadataQuery("Song", "Artist"))

        assertEquals(listOf(OFFICIAL, EU, "https://proxy.example/ws/2/"), client.endpoints)
        assertEquals(3, calls.size)
        assertTrue(calls[0].first.startsWith(OFFICIAL))
        assertTrue(calls[1].first.startsWith(EU))
        assertTrue(calls[2].first.startsWith("https://proxy.example/ws/2/"))
        assertEquals("application/json", calls[0].second["Accept"])
        assertEquals("EchoAndroid/1.0 (maintainer@example.com)", calls[0].second["User-Agent"])
        assertEquals("recording-1", result.candidates.single().providerItemId)
        assertEquals("artist-1", result.candidates.single().artists.single().providerArtistId)
        assertFalse(result.fromCache)
    }

    @Test
    fun freshCacheAvoidsNetworkCompletely() {
        val cache = FakeCache().apply { defaultResponse = response(ProviderCacheFreshness.FRESH) }
        var networkCalls = 0
        val client = client(cache, MetadataHttpTransport { _, _ ->
            networkCalls++
            MetadataHttpResponse(500, "")
        })

        val result = client.searchRecording(RecordingMetadataQuery("Song"))

        assertEquals(0, networkCalls)
        assertTrue(result.fromCache)
        assertFalse(result.staleCache)
    }

    @Test
    fun staleCacheIsUsableImmediatelyWhileRefreshRemainsBackgroundOnly() {
        val cache = FakeCache().apply {
            defaultResponse = response(ProviderCacheFreshness.STALE)
            allCircuitsOpen = true
        }
        var networkCalls = 0
        val client = client(cache, MetadataHttpTransport { _, _ ->
            networkCalls++
            MetadataHttpResponse(200, RESPONSE)
        })

        val result = client.searchRecording(RecordingMetadataQuery("Song"))

        assertEquals(0, networkCalls)
        assertTrue(result.fromCache)
        assertTrue(result.staleCache)
        assertFalse(result.allEndpointsFailed)
        assertEquals("recording-1", result.candidates.single().providerItemId)
    }

    @Test
    fun staleCacheReturnsImmediatelyAndRefreshesInBackground() {
        val cache = FakeCache().apply { defaultResponse = response(ProviderCacheFreshness.STALE) }
        val scheduled = mutableListOf<() -> Unit>()
        var networkCalls = 0
        val client = client(
            cache,
            MetadataHttpTransport { _, _ ->
                networkCalls++
                MetadataHttpResponse(200, RESPONSE)
            },
            refreshScheduler = { scheduled += it }
        )

        val result = client.searchRecording(RecordingMetadataQuery("Song"))

        assertEquals(0, networkCalls)
        assertTrue(result.staleCache)
        assertEquals(1, scheduled.size)
        scheduled.single().invoke()
        assertEquals(1, networkCalls)
        assertEquals(1, cache.successes.size)
    }

    @Test
    fun isrcUsesStrongIdentifierLookupPath() {
        val calls = mutableListOf<String>()
        val client = client(FakeCache(), MetadataHttpTransport { url, _ ->
            calls += url
            MetadataHttpResponse(200, RESPONSE)
        })

        client.searchRecording(RecordingMetadataQuery(title = "", isrc = "JP-ABC-12-34567"))

        assertTrue(calls.single().contains("isrc/JPABC1234567?inc=artist-credits&fmt=json"))
    }

    @Test
    fun artistSearchParsesMbidAliasesCountryAndType() {
        val client = client(FakeCache(), MetadataHttpTransport { _, _ ->
            MetadataHttpResponse(200, ARTIST_RESPONSE)
        })

        val result = client.searchArtist("Hanser")

        val artist = result.candidates.single()
        assertEquals("artist-mbid", artist.artistMbid)
        assertEquals(setOf("憨色", "Hanser"), artist.aliases)
        assertEquals("CN", artist.countryCode)
        assertEquals(app.yukine.identity.ArtistType.PERSON, artist.artistType)
    }

    @Test
    fun retriesEveryEndpointThenReturnsExplicitOfflineFallbackSignal() {
        val cache = FakeCache()
        val calls = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val client = client(
            cache = cache,
            transport = MetadataHttpTransport { url, _ ->
                calls += url
                MetadataHttpResponse(503, "busy")
            },
            customProxy = "https://proxy.example/ws/2",
            retryDelay = { delays += it },
            maxAttemptsPerEndpoint = 2
        )

        val result = client.searchRecording(RecordingMetadataQuery("Song"))

        assertEquals(6, calls.size)
        assertEquals(listOf(250L, 250L, 250L), delays)
        assertEquals(6, cache.failures.size)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.allEndpointsFailed)
    }

    @Test
    fun defaultClientsShareOneProcessWideMusicBrainzLimiter() {
        val transport = MetadataHttpTransport { _, _ -> MetadataHttpResponse(200, RESPONSE) }
        val first = MusicBrainzMetadataClient(FakeCache(), transport, "1.0", "maintainer@example.com")
        val second = MusicBrainzMetadataClient(FakeCache(), transport, "1.0", "maintainer@example.com")
        val field = MusicBrainzMetadataClient::class.java.getDeclaredField("rateLimiter").apply {
            isAccessible = true
        }

        assertSame(MusicBrainzRequestRateLimiter, field.get(first))
        assertSame(field.get(first), field.get(second))
    }

    private fun client(
        cache: FakeCache,
        transport: MetadataHttpTransport,
        customProxy: String = "",
        refreshScheduler: ((() -> Unit) -> Unit) = { },
        retryDelay: (Long) -> Unit = { },
        maxAttemptsPerEndpoint: Int = 1
    ) = MusicBrainzMetadataClient(
        cache = cache,
        transport = transport,
        applicationVersion = "1.0",
        contact = "maintainer@example.com",
        customProxy = customProxy,
        rateLimiter = RequestRateLimiter { },
        now = { 1_000L },
        retryDelay = retryDelay,
        maxAttemptsPerEndpoint = maxAttemptsPerEndpoint,
        refreshScheduler = refreshScheduler
    )

    private fun response(freshness: ProviderCacheFreshness) = ProviderCachedResponse(
        provider = "musicbrainz",
        endpoint = OFFICIAL,
        requestHash = "ignored",
        responseJson = RESPONSE,
        createdAt = 1L,
        expiresAt = if (freshness == ProviderCacheFreshness.FRESH) 2_000L else 2L,
        freshness = freshness
    )

    private class FakeCache : ProviderResponseCacheRepository {
        var defaultResponse: ProviderCachedResponse? = null
        var allCircuitsOpen: Boolean = false
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()

        override fun response(
            provider: String,
            endpoint: String,
            requestHash: String,
            now: Long
        ): ProviderCachedResponse? = defaultResponse?.copy(endpoint = endpoint, requestHash = requestHash)

        override fun endpointHealth(provider: String, endpoint: String): ProviderEndpointHealth =
            ProviderEndpointHealth(
                provider,
                endpoint,
                circuitOpenUntil = if (allCircuitsOpen) Long.MAX_VALUE else 0L
            )

        override fun saveSuccess(
            provider: String,
            endpoint: String,
            requestHash: String,
            responseJson: String,
            now: Long,
            ttlMs: Long
        ) {
            successes += endpoint
        }

        override fun recordFailure(
            provider: String,
            endpoint: String,
            error: String,
            now: Long
        ): ProviderEndpointHealth {
            failures += endpoint
            return ProviderEndpointHealth(provider, endpoint, failureCount = failures.count { it == endpoint })
        }
    }

    private companion object {
        const val OFFICIAL = "https://musicbrainz.org/ws/2/"
        const val EU = "https://musicbrainz.eu/ws/2/"
        const val RESPONSE = """
            {
              "recordings": [{
                "id": "recording-1",
                "score": "99",
                "title": "Song",
                "length": 180000,
                "isrcs": ["JPABC1234567"],
                "artist-credit": [{
                  "name": "Artist",
                  "artist": {"id": "artist-1", "name": "Artist", "sort-name": "Artist"}
                }],
                "releases": [{"title": "Album"}]
              }]
            }
        """
        const val ARTIST_RESPONSE = """
            {
              "artists": [{
                "id": "artist-mbid",
                "score": "100",
                "name": "Hanser",
                "sort-name": "Hanser",
                "country": "CN",
                "type": "Person",
                "aliases": [{"name": "憨色"}, {"name": "Hanser"}]
              }]
            }
        """
    }
}
