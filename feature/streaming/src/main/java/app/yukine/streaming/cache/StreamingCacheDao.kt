package app.yukine.streaming.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StreamingCacheDao {
    @Query(
        "SELECT * FROM streaming_search_cache " +
            "WHERE provider = :provider AND cache_key = :cacheKey AND expires_at_ms > :nowMs LIMIT 1"
    )
    suspend fun search(provider: String, cacheKey: String, nowMs: Long): StreamingSearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(entity: StreamingSearchCacheEntity)

    @Query("DELETE FROM streaming_search_cache WHERE provider = :provider")
    suspend fun deleteSearchForProvider(provider: String): Int

    @Query(
        "SELECT * FROM streaming_playlist_cache " +
            "WHERE provider = :provider AND provider_playlist_id = :providerPlaylistId AND expires_at_ms > :nowMs LIMIT 1"
    )
    suspend fun playlist(provider: String, providerPlaylistId: String, nowMs: Long): StreamingPlaylistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(entity: StreamingPlaylistCacheEntity)

    @Query(
        "SELECT * FROM streaming_playback_cache " +
            "WHERE provider = :provider AND provider_track_id = :providerTrackId AND quality = :quality AND expires_at_ms > :nowMs LIMIT 1"
    )
    suspend fun playback(provider: String, providerTrackId: String, quality: String, nowMs: Long): StreamingPlaybackCacheEntity?

    @Query(
        "SELECT * FROM streaming_playback_cache " +
            "WHERE provider = :provider AND provider_track_id = :providerTrackId AND expires_at_ms > :nowMs " +
            "ORDER BY CASE quality WHEN 'lossless' THEN 0 WHEN 'hires' THEN 1 WHEN 'high' THEN 2 ELSE 3 END, expires_at_ms DESC LIMIT 1"
    )
    fun playbackBlocking(provider: String, providerTrackId: String, nowMs: Long): StreamingPlaybackCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayback(entity: StreamingPlaybackCacheEntity)

    @Query("DELETE FROM streaming_playback_cache WHERE provider = :provider")
    suspend fun deletePlaybackForProvider(provider: String): Int

    @Query(
        "SELECT * FROM streaming_auth_metadata " +
            "WHERE provider = :provider AND (expires_at_ms IS NULL OR expires_at_ms > :nowMs) LIMIT 1"
    )
    suspend fun auth(provider: String, nowMs: Long): StreamingAuthMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuth(entity: StreamingAuthMetadataEntity)

    @Query("DELETE FROM streaming_search_cache WHERE expires_at_ms <= :nowMs")
    suspend fun deleteExpiredSearch(nowMs: Long): Int

    @Query("DELETE FROM streaming_playlist_cache WHERE expires_at_ms <= :nowMs")
    suspend fun deleteExpiredPlaylists(nowMs: Long): Int

    @Query("DELETE FROM streaming_playback_cache WHERE expires_at_ms <= :nowMs")
    suspend fun deleteExpiredPlayback(nowMs: Long): Int

    @Query("DELETE FROM streaming_auth_metadata WHERE expires_at_ms IS NOT NULL AND expires_at_ms <= :nowMs")
    suspend fun deleteExpiredAuth(nowMs: Long): Int
}
