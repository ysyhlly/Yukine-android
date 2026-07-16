package app.yukine.data.enrichment

import app.yukine.identity.ArtistType
import app.yukine.identity.ProviderCacheFreshness
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
class WikimediaArtistMetadataClientTest {
    @Test
    fun searchesWikidataAndWikipediaInBackgroundCandidateShape() {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val client = client(FakeCache(), MetadataHttpTransport { url, headers ->
            calls += url to headers
            if (url.startsWith(WIKIDATA)) {
                MetadataHttpResponse(200, WIKIDATA_RESPONSE)
            } else {
                MetadataHttpResponse(200, WIKIPEDIA_RESPONSE)
            }
        })

        val result = client.searchArtist("Hanser")

        assertEquals(2, calls.size)
        assertTrue(calls[0].first.contains("language=en"))
        assertTrue(calls[1].first.startsWith(WIKIPEDIA_EN))
        assertEquals("EchoAndroid/1.0 (maintainer@example.com)", calls[0].second["User-Agent"])
        assertEquals("application/json", calls[0].second["Accept"])
        assertFalse(result.fromCache)
        assertFalse(result.allEndpointsFailed)
        val wikidata = result.candidates.first { it.provider == "wikidata" }
        assertEquals("Q123", wikidata.providerItemId)
        assertEquals(setOf("Hanser", "憨色"), wikidata.aliases)
        assertEquals(ArtistType.PERSON, wikidata.artistType)
        val wikipedia = result.candidates.first { it.provider == "wikipedia" }
        assertEquals("en:456", wikipedia.providerItemId)
    }

    @Test
    fun selectsJapaneseEndpointFromKanaAlias() {
        val calls = mutableListOf<String>()
        val client = client(FakeCache(), MetadataHttpTransport { url, _ ->
            calls += url
            MetadataHttpResponse(200, if (url.startsWith(WIKIDATA)) WIKIDATA_RESPONSE else WIKIPEDIA_RESPONSE)
        })

        client.searchArtist("Hanser", listOf("ハンサー"))

        assertTrue(calls[0].contains("language=ja"))
        assertTrue(calls[1].startsWith("https://ja.wikipedia.org/w/api.php"))
    }

    @Test
    fun staleCachesReturnImmediatelyAndRefreshWithoutBlocking() {
        val cache = FakeCache().apply {
            responses[WIKIDATA] = cached(WIKIDATA, WIKIDATA_RESPONSE, ProviderCacheFreshness.STALE)
            responses[WIKIPEDIA_EN] = cached(WIKIPEDIA_EN, WIKIPEDIA_RESPONSE, ProviderCacheFreshness.STALE)
        }
        val scheduled = mutableListOf<() -> Unit>()
        var networkCalls = 0
        val client = client(
            cache,
            MetadataHttpTransport { url, _ ->
                networkCalls++
                MetadataHttpResponse(200, if (url.startsWith(WIKIDATA)) WIKIDATA_RESPONSE else WIKIPEDIA_RESPONSE)
            },
            refreshScheduler = { scheduled += it }
        )

        val result = client.searchArtist("Hanser")

        assertEquals(0, networkCalls)
        assertEquals(2, scheduled.size)
        assertTrue(result.fromCache)
        assertTrue(result.staleCache)
        assertTrue(result.allEndpointsFailed)
        scheduled.forEach { it.invoke() }
        assertEquals(2, networkCalls)
        assertEquals(2, cache.successes.size)
    }

    private fun client(
        cache: FakeCache,
        transport: MetadataHttpTransport,
        refreshScheduler: ((() -> Unit) -> Unit) = { }
    ) = WikimediaArtistMetadataClient(
        cache = cache,
        transport = transport,
        applicationVersion = "1.0",
        contact = "maintainer@example.com",
        rateLimiter = RequestRateLimiter { },
        now = { 1_000L },
        retryDelay = { },
        maxAttemptsPerEndpoint = 1,
        refreshScheduler = refreshScheduler
    )

    private fun cached(
        endpoint: String,
        body: String,
        freshness: ProviderCacheFreshness
    ) = ProviderCachedResponse(
        provider = "wikimedia",
        endpoint = endpoint,
        requestHash = "ignored",
        responseJson = body,
        createdAt = 1L,
        expiresAt = if (freshness == ProviderCacheFreshness.FRESH) 2_000L else 2L,
        freshness = freshness
    )

    private class FakeCache : ProviderResponseCacheRepository {
        val responses = mutableMapOf<String, ProviderCachedResponse>()
        val successes = mutableListOf<String>()

        override fun response(
            provider: String,
            endpoint: String,
            requestHash: String,
            now: Long
        ): ProviderCachedResponse? = responses[endpoint]?.copy(requestHash = requestHash)

        override fun endpointHealth(provider: String, endpoint: String): ProviderEndpointHealth =
            ProviderEndpointHealth(provider, endpoint)

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
        ): ProviderEndpointHealth = ProviderEndpointHealth(provider, endpoint, failureCount = 1)
    }

    private companion object {
        const val WIKIDATA = "https://www.wikidata.org/w/api.php"
        const val WIKIPEDIA_EN = "https://en.wikipedia.org/w/api.php"
        const val WIKIDATA_RESPONSE = """
            {
              "search": [{
                "id": "Q123",
                "label": "Hanser",
                "description": "Chinese singer and voice actor",
                "aliases": ["憨色"],
                "match": {"type": "label", "text": "Hanser"}
              }]
            }
        """
        const val WIKIPEDIA_RESPONSE = """
            {
              "query": {
                "search": [{
                  "pageid": 456,
                  "title": "Hanser",
                  "snippet": "Chinese singer and voice actor"
                }]
              }
            }
        """
    }
}
