package app.yukine.data.enrichment

import app.yukine.identity.ProviderCachedResponse
import app.yukine.identity.ProviderCacheFreshness
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
class ItunesMetadataClientTest {
    @Test
    fun searchesAnonymousSongCandidatesWithoutInventingIsrc() {
        val cache = NoCache()
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        val client = ItunesMetadataClient(cache, MetadataHttpTransport { url, requestHeaders ->
            urls += url
            headers += requestHeaders
            MetadataHttpResponse(200, RESPONSE)
        }, applicationVersion = "1.0", contact = "maintainer@example.com", now = { 100L })

        val result = client.searchRecording("Song Name", "Artist Name")

        assertTrue(urls.single().contains("media=music&entity=song"))
        assertTrue(urls.single().contains("term=Song%20Name%20Artist%20Name"))
        assertEquals("123", result.candidates.single().providerItemId)
        assertEquals("456", result.candidates.single().artists.single().providerArtistId)
        assertEquals("", result.candidates.single().isrc)
        assertEquals("EchoAndroid/1.0 (maintainer@example.com)", headers.single()["User-Agent"])
        assertEquals(listOf("https://itunes.apple.com/"), cache.successes)
    }

    @Test
    fun staleCacheReturnsBeforeScheduledRefresh() {
        val cache = NoCache().apply {
            cached = ProviderCachedResponse(
                "itunes",
                "https://itunes.apple.com/",
                "hash",
                RESPONSE,
                1L,
                2L,
                ProviderCacheFreshness.STALE
            )
        }
        val scheduled = mutableListOf<() -> Unit>()
        var calls = 0
        val client = ItunesMetadataClient(
            cache,
            MetadataHttpTransport { _, _ ->
                calls++
                MetadataHttpResponse(200, RESPONSE)
            },
            now = { 100L },
            maxAttempts = 1,
            refreshScheduler = { scheduled += it }
        )

        val result = client.searchRecording("Song Name")

        assertEquals(0, calls)
        assertTrue(result.staleCache)
        scheduled.single().invoke()
        assertEquals(1, calls)
    }

    @Test
    fun retryableFailureUsesBoundedRetry() {
        val delays = mutableListOf<Long>()
        var calls = 0
        val client = ItunesMetadataClient(
            NoCache(),
            MetadataHttpTransport { _, _ ->
                calls++
                if (calls == 1) MetadataHttpResponse(503, "busy") else MetadataHttpResponse(200, RESPONSE)
            },
            now = { 100L },
            retryDelay = { delays += it },
            maxAttempts = 2,
            refreshScheduler = { }
        )

        val result = client.searchRecording("Song Name")

        assertEquals(2, calls)
        assertEquals(listOf(250L), delays)
        assertEquals("123", result.candidates.single().providerItemId)
    }

    @Test
    fun freshCacheAvoidsNetworkAndOpenCircuitFallsBackWithoutBlocking() {
        val freshCache = NoCache().apply {
            cached = ProviderCachedResponse(
                "itunes",
                "https://itunes.apple.com/",
                "hash",
                RESPONSE,
                1L,
                200L,
                ProviderCacheFreshness.FRESH
            )
        }
        var calls = 0
        val freshClient = ItunesMetadataClient(
            freshCache,
            MetadataHttpTransport { _, _ ->
                calls++
                MetadataHttpResponse(500, "")
            },
            now = { 100L }
        )

        val cached = freshClient.searchRecording("Song Name")

        assertEquals(0, calls)
        assertTrue(cached.fromCache)
        assertFalse(cached.staleCache)

        val offlineCache = NoCache().apply { allCircuitsOpen = true }
        val offlineClient = ItunesMetadataClient(
            offlineCache,
            MetadataHttpTransport { _, _ ->
                calls++
                MetadataHttpResponse(200, RESPONSE)
            },
            now = { 100L }
        )
        val offline = offlineClient.searchRecording("Song Name")

        assertEquals(0, calls)
        assertTrue(offline.candidates.isEmpty())
        assertTrue(offline.allEndpointsFailed)
    }

    private class NoCache : ProviderResponseCacheRepository {
        var cached: ProviderCachedResponse? = null
        var allCircuitsOpen: Boolean = false
        val successes = mutableListOf<String>()
        override fun response(provider: String, endpoint: String, requestHash: String, now: Long): ProviderCachedResponse? =
            cached?.copy(requestHash = requestHash)
        override fun endpointHealth(provider: String, endpoint: String) = ProviderEndpointHealth(
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
        ) { successes += endpoint }
        override fun recordFailure(provider: String, endpoint: String, error: String, now: Long) =
            ProviderEndpointHealth(provider, endpoint, failureCount = 1, lastError = error)
    }

    private companion object {
        const val RESPONSE = """
            {
              "resultCount": 1,
              "results": [{
                "wrapperType": "track",
                "kind": "song",
                "artistId": 456,
                "trackId": 123,
                "artistName": "Artist Name",
                "collectionName": "Album",
                "trackName": "Song Name",
                "trackTimeMillis": 180000
              }]
            }
        """
    }
}
