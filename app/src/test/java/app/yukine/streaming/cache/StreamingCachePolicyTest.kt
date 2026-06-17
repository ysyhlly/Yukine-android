package app.yukine.streaming.cache

import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCachePolicyTest {
    @Test
    fun defaultPolicyUsesLayeredTtlValues() {
        val policy = StreamingCachePolicy()

        assertEquals(
            StreamingCachePolicy.DEFAULT_SEARCH_TTL_MS,
            policy.ttlForSearch(searchRequest(), searchResult())
        )
        assertEquals(
            StreamingCachePolicy.DEFAULT_PLAYBACK_TTL_MS,
            policy.ttlForPlayback(playbackSource(expiresAtEpochMs = null), nowMs = 1_000L)
        )
        assertEquals(
            StreamingCachePolicy.DEFAULT_AUTH_METADATA_TTL_MS,
            policy.ttlForAuth(StreamingProviderName.NETEASE, StreamingAuthState())
        )
        assertEquals(0L, policy.ttlForSignedOutAuth())
    }

    @Test
    fun playbackSourceExpiryOverridesDefaultPlaybackTtl() {
        val policy = StreamingCachePolicy(defaultPlaybackTtlMs = 120_000L)

        assertEquals(
            30_000L,
            policy.ttlForPlayback(playbackSource(expiresAtEpochMs = 40_000L), nowMs = 10_000L)
        )
        assertEquals(
            0L,
            policy.ttlForPlayback(playbackSource(expiresAtEpochMs = 9_999L), nowMs = 10_000L)
        )
    }

    @Test
    fun policyCoercesNegativeTtlValues() {
        val policy = StreamingCachePolicy(
            searchTtlMs = -1L,
            defaultPlaybackTtlMs = -2L,
            authMetadataTtlMs = -3L
        )

        assertEquals(0L, policy.ttlForSearch(searchRequest(), searchResult()))
        assertEquals(0L, policy.ttlForPlayback(playbackSource(expiresAtEpochMs = null), nowMs = 10_000L))
        assertEquals(0L, policy.ttlForAuth(StreamingProviderName.NETEASE, StreamingAuthState()))
    }

    private fun searchRequest(): StreamingSearchRequest {
        return StreamingSearchRequest(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            mediaTypes = setOf(StreamingMediaType.TRACK),
            page = 1,
            pageSize = 20
        )
    }

    private fun searchResult(): StreamingSearchResult {
        return StreamingSearchResult(
            provider = StreamingProviderName.NETEASE,
            query = "echo",
            page = 1,
            pageSize = 20
        )
    }

    private fun playbackSource(expiresAtEpochMs: Long?): StreamingPlaybackSource {
        return StreamingPlaybackSource(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = "track-1",
            url = "https://stream.example.test/track-1.flac",
            expiresAtEpochMs = expiresAtEpochMs
        )
    }
}
