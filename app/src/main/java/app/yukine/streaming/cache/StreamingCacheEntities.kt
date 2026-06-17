package app.yukine.streaming.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "streaming_search_cache",
    indices = [
        Index(value = ["provider", "cache_key"], unique = true),
        Index(value = ["expires_at_ms"])
    ]
)
data class StreamingSearchCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    val query: String,
    val page: Int,
    @ColumnInfo(name = "page_size")
    val pageSize: Int,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long
)

@Entity(
    tableName = "streaming_playlist_cache",
    indices = [
        Index(value = ["provider", "provider_playlist_id"], unique = true),
        Index(value = ["expires_at_ms"])
    ]
)
data class StreamingPlaylistCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    @ColumnInfo(name = "provider_playlist_id")
    val providerPlaylistId: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long
)

@Entity(
    tableName = "streaming_playback_cache",
    indices = [
        Index(value = ["provider", "provider_track_id", "quality"], unique = true),
        Index(value = ["expires_at_ms"])
    ]
)
data class StreamingPlaybackCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    @ColumnInfo(name = "provider_track_id")
    val providerTrackId: String,
    val quality: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long
)

@Entity(
    tableName = "streaming_auth_metadata",
    indices = [
        Index(value = ["provider"], unique = true),
        Index(value = ["expires_at_ms"])
    ]
)
data class StreamingAuthMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long?
)
