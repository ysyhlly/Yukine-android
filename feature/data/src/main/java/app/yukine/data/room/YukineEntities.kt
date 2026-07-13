package app.yukine.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(name = "idx_tracks_data_path", value = ["data_path"]),
        Index(name = "idx_tracks_artist", value = ["artist"]),
        Index(name = "idx_tracks_album", value = ["album"])
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long?,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "data_path") val dataPath: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "album_art_uri") val albumArtUri: String,
    @ColumnInfo(defaultValue = "''") val codec: String,
    @ColumnInfo(name = "bitrate_kbps", defaultValue = "0") val bitrateKbps: Int,
    @ColumnInfo(name = "sample_rate_hz", defaultValue = "0") val sampleRateHz: Int,
    @ColumnInfo(name = "bits_per_sample", defaultValue = "0") val bitsPerSample: Int,
    @ColumnInfo(name = "channel_count", defaultValue = "0") val channelCount: Int,
    @ColumnInfo(name = "replay_gain_track_db", defaultValue = "0") val replayGainTrackDb: Double,
    @ColumnInfo(name = "replay_gain_album_db", defaultValue = "0") val replayGainAlbumDb: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long?,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "play_count", defaultValue = "1") val playCount: Int
)

@Entity(
    tableName = "play_events",
    indices = [
        Index(name = "idx_play_events_played_at", value = ["played_at"]),
        Index(name = "idx_play_events_track_time", value = ["track_id", "played_at"])
    ]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    indices = [
        Index(name = "idx_playlist_tracks_playlist", value = ["playlist_id", "position"])
    ]
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "remote_sources",
    indices = [Index(name = "idx_remote_sources_type", value = ["type", "updated_at"])]
)
data class RemoteSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    val type: String,
    val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(defaultValue = "''") val username: String,
    @ColumnInfo(defaultValue = "''") val password: String,
    @ColumnInfo(name = "root_path", defaultValue = "'/'") val rootPath: String,
    @ColumnInfo(name = "last_status", defaultValue = "''") val lastStatus: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "playback_queue",
    indices = [Index(name = "idx_playback_queue_track", value = ["track_id"])]
)
data class PlaybackQueueEntity(
    @PrimaryKey val position: Int?,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(defaultValue = "''") val title: String,
    @ColumnInfo(defaultValue = "''") val artist: String,
    @ColumnInfo(defaultValue = "''") val album: String,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long,
    @ColumnInfo(name = "content_uri", defaultValue = "''") val contentUri: String,
    @ColumnInfo(name = "data_path", defaultValue = "''") val dataPath: String,
    @ColumnInfo(name = "album_id", defaultValue = "0") val albumId: Long,
    @ColumnInfo(name = "album_art_uri", defaultValue = "''") val albumArtUri: String,
    @ColumnInfo(defaultValue = "''") val codec: String,
    @ColumnInfo(name = "bitrate_kbps", defaultValue = "0") val bitrateKbps: Int,
    @ColumnInfo(name = "sample_rate_hz", defaultValue = "0") val sampleRateHz: Int,
    @ColumnInfo(name = "bits_per_sample", defaultValue = "0") val bitsPerSample: Int,
    @ColumnInfo(name = "channel_count", defaultValue = "0") val channelCount: Int,
    @ColumnInfo(name = "replay_gain_track_db", defaultValue = "0") val replayGainTrackDb: Double,
    @ColumnInfo(name = "replay_gain_album_db", defaultValue = "0") val replayGainAlbumDb: Double
)

@Entity(
    tableName = "streaming_track_matches",
    primaryKeys = ["local_key", "provider"],
    indices = [
        Index(
            name = "idx_streaming_track_matches_provider_track",
            value = ["provider", "provider_track_id"]
        )
    ]
)
data class StreamingTrackMatchEntity(
    @ColumnInfo(name = "local_key") val localKey: String,
    val provider: String,
    @ColumnInfo(name = "provider_track_id") val providerTrackId: String,
    @ColumnInfo(defaultValue = "''") val title: String,
    @ColumnInfo(defaultValue = "''") val artist: String,
    @ColumnInfo(name = "data_path", defaultValue = "''") val dataPath: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "library_exclusions",
    indices = [
        Index(
            name = "idx_library_exclusions_created_at",
            value = ["created_at"],
            orders = [Index.Order.DESC]
        )
    ]
)
data class LibraryExclusionEntity(
    @PrimaryKey @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "content_uri", defaultValue = "''") val contentUri: String,
    @ColumnInfo(name = "data_path", defaultValue = "''") val dataPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
