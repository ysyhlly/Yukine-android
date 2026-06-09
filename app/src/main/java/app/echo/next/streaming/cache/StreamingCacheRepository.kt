package app.echo.next.streaming.cache

import android.content.Context
import app.echo.next.streaming.StreamingAudioQuality
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class StreamingCacheRepository(
    private val dao: StreamingCacheDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun cachedSearch(request: StreamingSearchRequest): String? {
        return dao.search(request.provider.wireName, searchCacheKey(request), clock())?.payloadJson
    }

    suspend fun saveSearch(request: StreamingSearchRequest, payloadJson: String, ttlMs: Long) {
        val now = clock()
        dao.upsertSearch(
            StreamingSearchCacheEntity(
                provider = request.provider.wireName,
                cacheKey = searchCacheKey(request),
                query = request.query,
                page = request.page,
                pageSize = request.pageSize,
                payloadJson = payloadJson,
                createdAtMs = now,
                expiresAtMs = now + ttlMs.coerceAtLeast(0L)
            )
        )
    }

    suspend fun cachedPlaylist(provider: StreamingProviderName, providerPlaylistId: String): String? {
        return dao.playlist(provider.wireName, providerPlaylistId, clock())?.payloadJson
    }

    suspend fun savePlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        payloadJson: String,
        ttlMs: Long
    ) {
        val now = clock()
        dao.upsertPlaylist(
            StreamingPlaylistCacheEntity(
                provider = provider.wireName,
                providerPlaylistId = providerPlaylistId,
                payloadJson = payloadJson,
                createdAtMs = now,
                expiresAtMs = now + ttlMs.coerceAtLeast(0L)
            )
        )
    }

    suspend fun cachedPlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality
    ): String? {
        return dao.playback(provider.wireName, providerTrackId, quality.wireName, clock())?.payloadJson
    }

    fun cachedPlaybackBlocking(provider: StreamingProviderName, providerTrackId: String): String? {
        return runBlocking(Dispatchers.IO) {
            dao.playbackBlocking(provider.wireName, providerTrackId, clock())?.payloadJson
        }
    }

    suspend fun savePlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality,
        payloadJson: String,
        ttlMs: Long
    ) {
        val now = clock()
        dao.upsertPlayback(
            StreamingPlaybackCacheEntity(
                provider = provider.wireName,
                providerTrackId = providerTrackId,
                quality = quality.wireName,
                payloadJson = payloadJson,
                createdAtMs = now,
                expiresAtMs = now + ttlMs.coerceAtLeast(0L)
            )
        )
    }

    suspend fun cachedAuth(provider: StreamingProviderName): String? {
        return dao.auth(provider.wireName, clock())?.payloadJson
    }

    suspend fun saveAuth(provider: StreamingProviderName, payloadJson: String, ttlMs: Long?) {
        val now = clock()
        dao.upsertAuth(
            StreamingAuthMetadataEntity(
                provider = provider.wireName,
                payloadJson = payloadJson,
                updatedAtMs = now,
                expiresAtMs = ttlMs?.let { now + it.coerceAtLeast(0L) }
            )
        )
    }

    suspend fun clearExpired(): Int {
        val now = clock()
        return dao.deleteExpiredSearch(now) +
            dao.deleteExpiredPlaylists(now) +
            dao.deleteExpiredPlayback(now) +
            dao.deleteExpiredAuth(now)
    }

    private fun searchCacheKey(request: StreamingSearchRequest): String {
        val mediaTypes = request.mediaTypes.map { it.wireName }.sorted().joinToString(",")
        return listOf(
            request.provider.wireName,
            request.query.trim().lowercase(),
            mediaTypes,
            request.page.coerceAtLeast(1).toString(),
            request.pageSize.coerceIn(1, 50).toString()
        ).joinToString(":")
    }

    companion object {
        fun create(context: Context): StreamingCacheRepository {
            return StreamingCacheRepository(
                StreamingCacheDatabase.getInstance(context).streamingCacheDao()
            )
        }
    }
}
