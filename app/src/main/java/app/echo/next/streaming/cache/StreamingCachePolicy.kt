package app.echo.next.streaming.cache

import app.echo.next.streaming.StreamingAuthState
import app.echo.next.streaming.StreamingPlaybackSource
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingSearchRequest
import app.echo.next.streaming.StreamingSearchResult

data class StreamingCachePolicy(
    val searchTtlMs: Long = DEFAULT_SEARCH_TTL_MS,
    val playlistTtlMs: Long = DEFAULT_PLAYLIST_TTL_MS,
    val defaultPlaybackTtlMs: Long = DEFAULT_PLAYBACK_TTL_MS,
    val authMetadataTtlMs: Long? = DEFAULT_AUTH_METADATA_TTL_MS
) {
    fun ttlForSearch(request: StreamingSearchRequest, result: StreamingSearchResult): Long {
        return searchTtlMs.coerceAtLeast(0L)
    }

    fun ttlForPlaylist(provider: StreamingProviderName, providerPlaylistId: String): Long {
        return playlistTtlMs.coerceAtLeast(0L)
    }

    fun ttlForPlayback(source: StreamingPlaybackSource, nowMs: Long): Long {
        return source.expiresAtEpochMs
            ?.minus(nowMs)
            ?.coerceAtLeast(0L)
            ?: defaultPlaybackTtlMs.coerceAtLeast(0L)
    }

    fun ttlForAuth(provider: StreamingProviderName, state: StreamingAuthState): Long? {
        return authMetadataTtlMs?.coerceAtLeast(0L)
    }

    fun ttlForSignedOutAuth(): Long {
        return 0L
    }

    companion object {
        const val DEFAULT_SEARCH_TTL_MS = 5 * 60 * 1000L
        const val DEFAULT_PLAYLIST_TTL_MS = 10 * 60 * 1000L
        const val DEFAULT_PLAYBACK_TTL_MS = 2 * 60 * 1000L
        const val DEFAULT_AUTH_METADATA_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
