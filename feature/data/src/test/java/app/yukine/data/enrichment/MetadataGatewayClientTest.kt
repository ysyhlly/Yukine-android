package app.yukine.data.enrichment

import app.yukine.identity.CanonicalRecording
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

    private class FakeCache : ProviderResponseCacheRepository {
        var successes = 0
        var lastError = ""
        override fun response(provider: String, endpoint: String, requestHash: String, now: Long): ProviderCachedResponse? = null
        override fun endpointHealth(provider: String, endpoint: String) = ProviderEndpointHealth(provider, endpoint)
        override fun saveSuccess(provider: String, endpoint: String, requestHash: String, responseJson: String, now: Long, ttlMs: Long) { successes++ }
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
              "acoustId":"acoust-id","fingerprintVerified":true,"score":0.99
            }]}
        """
    }
}
