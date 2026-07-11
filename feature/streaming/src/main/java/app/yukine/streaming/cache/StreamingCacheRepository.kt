package app.yukine.streaming.cache

import android.content.Context
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.streamingPlaybackCacheTrackId
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
        quality: StreamingAudioQuality,
        luoxueMusicInfoJson: String? = null
    ): String? {
        return dao.playback(
            provider.wireName,
            streamingPlaybackCacheTrackId(provider, providerTrackId, luoxueMusicInfoJson),
            quality.wireName,
            clock()
        )?.payloadJson
    }

    /**
     * 同步读取播放缓存（任意质量级别中最新的一条）。
     *
     * **此方法使用 `runBlocking(Dispatchers.IO)` 桥接**：调用方
     * [app.yukine.streaming.PersistentStreamingPlaybackHeaders] 的
     * `restoreForDataPath` / `restoredTrackFor` 实现同步接口，无法改为 suspend，
     * 而 Room 在主线程调用同步 DAO 时会硬抛 `IllegalStateException`。
     * `runBlocking(IO)` 把实际查询切到 IO 线程，满足 Room 的线程断言。
     *
     * 如未来接口整体协程化，应将本方法改为 suspend 并去掉 runBlocking。
     */
    fun cachedPlaybackBlocking(provider: StreamingProviderName, providerTrackId: String): String? {
        return runBlocking(Dispatchers.IO) {
            dao.playbackBlocking(provider.wireName, providerTrackId, clock())?.payloadJson
        }
    }

    fun cachedPlaybackBlocking(
        provider: StreamingProviderName,
        providerTrackId: String,
        luoxueMusicInfoJson: String?
    ): String? {
        return runBlocking(Dispatchers.IO) {
            dao.playbackBlocking(
                provider.wireName,
                streamingPlaybackCacheTrackId(provider, providerTrackId, luoxueMusicInfoJson),
                clock()
            )?.payloadJson
        }
    }

    suspend fun savePlayback(
        provider: StreamingProviderName,
        providerTrackId: String,
        quality: StreamingAudioQuality,
        payloadJson: String,
        ttlMs: Long,
        luoxueMusicInfoJson: String? = null
    ) {
        val now = clock()
        dao.upsertPlayback(
            StreamingPlaybackCacheEntity(
                provider = provider.wireName,
                providerTrackId = streamingPlaybackCacheTrackId(provider, providerTrackId, luoxueMusicInfoJson),
                quality = quality.wireName,
                payloadJson = payloadJson,
                createdAtMs = now,
                expiresAtMs = now + ttlMs.coerceAtLeast(0L)
            )
        )
    }

    suspend fun clearPlaybackForProvider(provider: StreamingProviderName): Int {
        return dao.deletePlaybackForProvider(provider.wireName)
    }

    suspend fun clearSearchAndPlaybackForProvider(provider: StreamingProviderName): Int {
        return dao.deleteSearchForProvider(provider.wireName) +
            dao.deletePlaybackForProvider(provider.wireName)
    }

    /**
     * Source-management UI calls this from its IO executor before refreshing LX provider state.
     * Keep the synchronous bridge local to that background boundary rather than teaching UI code
     * about Room or coroutines.
     */
    fun clearPlaybackForProviderBlocking(provider: StreamingProviderName): Int {
        return runBlocking(Dispatchers.IO) {
            clearPlaybackForProvider(provider)
        }
    }

    fun clearSearchAndPlaybackForProviderBlocking(provider: StreamingProviderName): Int {
        return runBlocking(Dispatchers.IO) {
            clearSearchAndPlaybackForProvider(provider)
        }
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
