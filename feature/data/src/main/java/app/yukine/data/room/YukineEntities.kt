package app.yukine.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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

@Entity(
    tableName = "recording_play_history",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_recording_history_time", value = ["played_at"])]
)
data class RecordingPlayHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "representative_track_id") val representativeTrackId: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "play_count") val playCount: Int
)

@Entity(
    tableName = "recording_play_events",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_recording_events_time", value = ["played_at"]),
        Index(name = "idx_recording_events_recording_time", value = ["recording_id", "played_at"]),
        Index(
            name = "idx_recording_events_legacy_event",
            value = ["legacy_event_id"],
            unique = true
        )
    ]
)
data class RecordingPlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "source_id") val sourceId: Long?,
    @ColumnInfo(name = "track_id_snapshot") val trackIdSnapshot: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "legacy_event_id") val legacyEventId: Long? = null
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

@Entity(
    tableName = "playlist_recording_items",
    primaryKeys = ["playlist_id", "recording_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_playlist_recording_order", value = ["playlist_id", "sort_key"]),
        Index(name = "idx_playlist_recording_recording", value = ["recording_id"])
    ]
)
data class PlaylistRecordingItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "representative_track_id") val representativeTrackId: Long,
    @ColumnInfo(name = "sort_key") val sortKey: Long,
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
    tableName = "playback_queue_identities",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_queue_identity_recording", value = ["recording_id"])]
)
data class PlaybackQueueIdentityEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "preferred_source_id") val preferredSourceId: Long?
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

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_favorites_created", value = ["created_at"]),
        Index(name = "idx_favorites_sync_state", value = ["sync_state"])
    ]
)
data class RecordingFavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sync_state", defaultValue = "'LOCAL_ONLY'") val syncState: String = "LOCAL_ONLY"
)

@Entity(
    tableName = "works",
    indices = [
        Index(name = "idx_works_uuid", value = ["canonical_uuid"], unique = true),
        Index(name = "idx_works_title", value = ["normalized_title"]),
        Index(name = "idx_works_creator", value = ["primary_creator_id"])
    ]
)
data class CanonicalWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "canonical_uuid") val canonicalUuid: String,
    @ColumnInfo(name = "normalized_title") val normalizedTitle: String,
    @ColumnInfo(name = "primary_creator_id") val primaryCreatorId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "recordings",
    indices = [
        Index(name = "idx_recording_uuid", value = ["canonical_uuid"], unique = true),
        Index(name = "idx_recordings_work", value = ["work_id"]),
        Index(name = "idx_recordings_mbid", value = ["musicbrainz_recording_id"]),
        Index(name = "idx_recordings_isrc", value = ["isrc"]),
        Index(name = "idx_recordings_status", value = ["match_status"]),
        Index(name = "idx_recordings_active_source", value = ["active_source_id"])
    ]
)
data class CanonicalRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "canonical_uuid") val canonicalUuid: String,
    @ColumnInfo(name = "work_id") val workId: Long?,
    @ColumnInfo(name = "active_source_id") val activeSourceId: Long?,
    @ColumnInfo(name = "musicbrainz_recording_id", defaultValue = "''") val musicBrainzRecordingId: String,
    @ColumnInfo(name = "musicbrainz_work_id", defaultValue = "''") val musicBrainzWorkId: String,
    @ColumnInfo(defaultValue = "''") val title: String,
    @ColumnInfo(name = "primary_artist_display", defaultValue = "''") val primaryArtistDisplay: String,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long,
    @ColumnInfo(defaultValue = "''") val isrc: String,
    @ColumnInfo(name = "acoust_id", defaultValue = "''") val acoustId: String,
    @ColumnInfo(name = "match_status", defaultValue = "'UNRESOLVED'") val matchStatus: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "metadata_source", defaultValue = "''") val metadataSource: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "canonical_artists",
    indices = [
        Index(name = "idx_artist_uuid", value = ["artist_uuid"], unique = true),
        Index(name = "idx_canonical_artists_mbid", value = ["musicbrainz_artist_id"]),
        Index(name = "idx_canonical_artists_status", value = ["match_status"]),
        Index(name = "idx_canonical_artists_sort_name", value = ["sort_name"])
    ]
)
data class CanonicalArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "artist_uuid") val artistUuid: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "sort_name", defaultValue = "''") val sortName: String,
    @ColumnInfo(name = "artist_type", defaultValue = "'UNKNOWN'") val artistType: String,
    @ColumnInfo(name = "country_code", defaultValue = "''") val countryCode: String,
    @ColumnInfo(name = "musicbrainz_artist_id", defaultValue = "''") val musicBrainzArtistId: String,
    @ColumnInfo(name = "match_status", defaultValue = "'UNRESOLVED'") val matchStatus: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "metadata_source", defaultValue = "''") val metadataSource: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "artist_aliases",
    primaryKeys = ["artist_id", "normalized_alias", "locale"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_artist_aliases_normalized", value = ["normalized_alias"]),
        Index(name = "idx_artist_aliases_artist", value = ["artist_id"])
    ]
)
data class ArtistAliasEntity(
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val alias: String,
    @ColumnInfo(name = "normalized_alias") val normalizedAlias: String,
    @ColumnInfo(defaultValue = "''") val locale: String,
    @ColumnInfo(defaultValue = "''") val script: String,
    @ColumnInfo(name = "alias_type", defaultValue = "'ALIAS'") val aliasType: String,
    @ColumnInfo(defaultValue = "''") val source: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "verified_at", defaultValue = "0") val verifiedAt: Long
)

@Entity(
    tableName = "artist_source_mappings",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_artist_source_artist", value = ["artist_id"]),
        Index(
            name = "idx_artist_source_provider_artist",
            value = ["provider", "provider_artist_id"],
            unique = true
        )
    ]
)
data class ArtistSourceMappingEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "mapping_id") val mappingId: Long?,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val provider: String,
    @ColumnInfo(name = "provider_artist_id") val providerArtistId: String,
    @ColumnInfo(name = "display_name", defaultValue = "''") val displayName: String,
    @ColumnInfo(defaultValue = "'UNRESOLVED'") val status: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "last_verified_at", defaultValue = "0") val lastVerifiedAt: Long
)

@Entity(
    tableName = "recording_artist_credits",
    primaryKeys = ["recording_id", "artist_id", "role", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_recording_credits_recording", value = ["recording_id", "position"]),
        Index(name = "idx_recording_credits_artist", value = ["artist_id"])
    ]
)
data class RecordingArtistCreditEntity(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val role: String,
    @ColumnInfo(defaultValue = "0") val position: Int,
    @ColumnInfo(name = "credited_name", defaultValue = "''") val creditedName: String,
    @ColumnInfo(name = "join_phrase", defaultValue = "''") val joinPhrase: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double
)

@Entity(
    tableName = "track_sources",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_track_source_recording", value = ["recording_id"]),
        Index(
            name = "idx_track_source_provider_track",
            value = ["provider", "provider_track_id"],
            unique = true
        ),
        Index(name = "idx_track_source_local_track", value = ["local_track_id"], unique = true),
        Index(name = "idx_track_source_verified", value = ["last_verified_at"]),
        Index(
            name = "idx_source_selection",
            value = ["recording_id", "playable", "quality_score"]
        )
    ]
)
data class TrackSourceMappingEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "source_id") val sourceId: Long?,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    val provider: String,
    @ColumnInfo(name = "provider_track_id") val providerTrackId: String,
    @ColumnInfo(name = "local_track_id") val localTrackId: Long?,
    @ColumnInfo(name = "data_path", defaultValue = "''") val dataPath: String,
    @ColumnInfo(defaultValue = "''") val title: String,
    @ColumnInfo(defaultValue = "''") val artist: String,
    @ColumnInfo(defaultValue = "''") val album: String,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long,
    @ColumnInfo(defaultValue = "''") val quality: String,
    @ColumnInfo(name = "quality_score", defaultValue = "0") val qualityScore: Int,
    @ColumnInfo(defaultValue = "1") val playable: Boolean,
    @ColumnInfo(name = "match_status", defaultValue = "'UNRESOLVED'") val matchStatus: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "last_successful_at", defaultValue = "0") val lastSuccessfulAt: Long,
    @ColumnInfo(name = "last_verified_at", defaultValue = "0") val lastVerifiedAt: Long,
    @ColumnInfo(name = "legacy_local_key", defaultValue = "''") val legacyLocalKey: String,
    @ColumnInfo(defaultValue = "''") val codec: String = "",
    @ColumnInfo(name = "bitrate_kbps", defaultValue = "0") val bitrateKbps: Int = 0,
    @ColumnInfo(name = "last_failure_at", defaultValue = "0") val lastFailureAt: Long = 0L,
    @ColumnInfo(name = "failure_reason", defaultValue = "''") val failureReason: String = "",
    @ColumnInfo(name = "failure_count", defaultValue = "0") val failureCount: Int = 0
)

/** Cold, versioned audio evidence. No library or playback hot query joins this table. */
@Entity(
    tableName = "audio_features",
    foreignKeys = [
        ForeignKey(
            entity = TrackSourceMappingEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_audio_features_signature", value = ["content_signature"]),
        Index(name = "idx_audio_features_algorithm", value = ["algorithm_version"]),
        Index(name = "idx_audio_features_spec_state", value = ["audio_spec_state", "last_attempt_at"])
    ]
)
data class AudioFeatureEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "content_signature") val contentSignature: String,
    @ColumnInfo(name = "pcm_hash") val pcmHash: String,
    val chromaprint: String,
    @ColumnInfo(name = "recording_embedding") val recordingEmbedding: ByteArray?,
    @ColumnInfo(name = "work_embedding") val workEmbedding: ByteArray?,
    @ColumnInfo(name = "version_scores") val versionScores: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: Int,
    @ColumnInfo(name = "audio_spec_state") val audioSpecState: String,
    @ColumnInfo(name = "audio_spec_algorithm_version") val audioSpecAlgorithmVersion: Int,
    @ColumnInfo(name = "audio_spec_attempt_count") val audioSpecAttemptCount: Int,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long,
    @ColumnInfo(name = "last_error") val lastError: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * Versioned, immutable metadata features used to generate recording match candidates.
 *
 * The source row remains the authority. [metadataSignature] lets background ingestion skip all
 * normalization work when neither the metadata nor the feature algorithm changed.
 */
@Entity(
    tableName = "source_match_features",
    foreignKeys = [
        ForeignKey(
            entity = TrackSourceMappingEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            name = "idx_source_match_bucket",
            value = ["core_title", "normalized_artist", "duration_bucket"]
        ),
        Index(name = "idx_source_match_algorithm", value = ["algorithm_version"])
    ]
)
data class SourceMatchFeatureEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "normalized_title") val normalizedTitle: String,
    @ColumnInfo(name = "core_title") val coreTitle: String,
    @ColumnInfo(name = "normalized_artist") val normalizedArtist: String,
    @ColumnInfo(name = "normalized_album") val normalizedAlbum: String,
    @ColumnInfo(name = "version_type") val versionType: String,
    @ColumnInfo(name = "version_signature") val versionSignature: String,
    @ColumnInfo(name = "duration_bucket") val durationBucket: Long,
    @ColumnInfo(name = "title_tokens") val titleTokens: String,
    @ColumnInfo(name = "title_bigrams") val titleBigrams: String,
    @ColumnInfo(name = "title_trigrams") val titleTrigrams: String,
    @ColumnInfo(name = "metadata_signature") val metadataSignature: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "candidate_algorithm_version", defaultValue = "0")
    val candidateAlgorithmVersion: Int = 0,
    @ColumnInfo(name = "candidate_snapshot_signature", defaultValue = "''")
    val candidateSnapshotSignature: String = "",
    @ColumnInfo(name = "candidate_generated_at", defaultValue = "0")
    val candidateGeneratedAt: Long = 0L
)

/** Bounded coarse candidates. Only these rows enter expensive complete-link V2 scoring. */
@Entity(
    tableName = "source_recording_candidates",
    primaryKeys = ["source_id", "candidate_recording_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackSourceMappingEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidate_recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_source_candidates_recording", value = ["candidate_recording_id"]),
        Index(name = "idx_source_candidates_rank", value = ["source_id", "coarse_score"]),
        Index(name = "idx_source_candidates_algorithm", value = ["algorithm_version"])
    ]
)
data class SourceRecordingCandidateEntity(
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "candidate_recording_id") val candidateRecordingId: Long,
    @ColumnInfo(name = "coarse_score") val coarseScore: Double,
    @ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @ColumnInfo(defaultValue = "'GENERATED'") val state: String = "GENERATED",
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * Durable relationship between two distinct canonical recordings.
 *
 * Callers must store the smaller recording ID on the left. [locked] rows originate from an
 * explicit user decision and must not be replaced by later background scoring.
 */
@Entity(
    tableName = "recording_relations",
    primaryKeys = ["left_recording_id", "right_recording_id"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["left_recording_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["right_recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_recording_relations_right", value = ["right_recording_id"]),
        Index(name = "idx_recording_relations_type", value = ["relation_type"]),
        Index(name = "idx_recording_relations_updated", value = ["updated_at"])
    ]
)
data class RecordingRelationEntity(
    @ColumnInfo(name = "left_recording_id") val leftRecordingId: Long,
    @ColumnInfo(name = "right_recording_id") val rightRecordingId: Long,
    @ColumnInfo(name = "relation_type") val relationType: String,
    @ColumnInfo(name = "same_recording_probability", defaultValue = "0")
    val sameRecordingProbability: Double = 0.0,
    @ColumnInfo(name = "same_work_probability", defaultValue = "0")
    val sameWorkProbability: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val confidence: Double = 0.0,
    @ColumnInfo(defaultValue = "'ALGORITHM'") val origin: String = "ALGORITHM",
    @ColumnInfo(name = "algorithm_version", defaultValue = "0") val algorithmVersion: Int = 0,
    @ColumnInfo(name = "evidence_json", defaultValue = "''") val evidenceJson: String = "",
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "recording_identifiers",
    primaryKeys = ["identifier_type", "namespace", "identifier_value"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_recording_identifiers_recording", value = ["recording_id"])]
)
data class RecordingIdentifierEntity(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "identifier_type") val identifierType: String,
    val namespace: String,
    @ColumnInfo(name = "identifier_value") val identifierValue: String,
    @ColumnInfo(defaultValue = "''") val source: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double,
    @ColumnInfo(name = "verified_at", defaultValue = "0") val verifiedAt: Long
)

@Entity(
    tableName = "recording_variants",
    primaryKeys = ["variant_group_id", "recording_id"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_recording_variants_recording", value = ["recording_id"])]
)
data class RecordingVariantEntity(
    @ColumnInfo(name = "variant_group_id") val variantGroupId: String,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "variant_type", defaultValue = "'UNKNOWN'") val variantType: String,
    @ColumnInfo(name = "display_name", defaultValue = "''") val displayName: String,
    @ColumnInfo(defaultValue = "0") val confidence: Double
)

@Entity(
    tableName = "identity_candidates",
    indices = [
        Index(name = "idx_identity_candidates_target", value = ["target_type", "target_id"]),
        Index(name = "idx_identity_candidates_status", value = ["status", "updated_at"]),
        Index(
            name = "idx_identity_candidates_provider_item",
            value = ["target_type", "target_id", "provider", "provider_item_id"],
            unique = true
        )
    ]
)
data class IdentityCandidateEntity(
    @PrimaryKey @ColumnInfo(name = "candidate_id") val candidateId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: Long,
    val provider: String,
    @ColumnInfo(name = "provider_item_id") val providerItemId: String,
    @ColumnInfo(defaultValue = "''") val title: String,
    @ColumnInfo(defaultValue = "''") val artist: String,
    @ColumnInfo(defaultValue = "''") val album: String,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long,
    @ColumnInfo(defaultValue = "''") val isrc: String,
    @ColumnInfo(name = "variant_type", defaultValue = "'UNKNOWN'") val variantType: String,
    @ColumnInfo(defaultValue = "0") val score: Double,
    @ColumnInfo(defaultValue = "'PENDING'") val status: String,
    @ColumnInfo(name = "evidence_json", defaultValue = "''") val evidenceJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "identity_resolution_jobs",
    indices = [
        Index(name = "idx_identity_jobs_target", value = ["target_type", "target_id"]),
        Index(
            name = "idx_identity_jobs_target_status",
            value = ["target_type", "target_id", "status"],
            unique = true
        ),
        Index(
            name = "idx_identity_jobs_ready",
            value = ["status", "next_attempt_at", "priority"]
        )
    ]
)
data class IdentityResolutionJobEntity(
    @PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: Long,
    @ColumnInfo(defaultValue = "0") val priority: Int,
    @ColumnInfo(defaultValue = "'NEW_TRACK'") val reason: String,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at", defaultValue = "0") val nextAttemptAt: Long,
    @ColumnInfo(name = "last_error", defaultValue = "''") val lastError: String,
    @ColumnInfo(defaultValue = "'PENDING'") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "provider_response_cache",
    primaryKeys = ["provider", "endpoint", "request_hash"],
    indices = [
        Index(name = "idx_provider_cache_expires", value = ["expires_at"]),
        Index(
            name = "idx_provider_cache_circuit",
            value = ["provider", "endpoint", "circuit_open_until"]
        )
    ]
)
data class ProviderResponseCacheEntity(
    val provider: String,
    @ColumnInfo(defaultValue = "''") val endpoint: String,
    @ColumnInfo(name = "request_hash") val requestHash: String,
    @ColumnInfo(name = "response_json", defaultValue = "''") val responseJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at", defaultValue = "0") val expiresAt: Long,
    @ColumnInfo(name = "failure_count", defaultValue = "0") val failureCount: Int,
    @ColumnInfo(name = "circuit_open_until", defaultValue = "0") val circuitOpenUntil: Long,
    @ColumnInfo(name = "last_error", defaultValue = "''") val lastError: String
)

@Entity(
    tableName = "lyric_bindings",
    primaryKeys = ["recording_id", "provider"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_lyric_bindings_provider", value = ["provider", "provider_lyric_id"])
    ]
)
data class LyricBindingEntity(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    val provider: String,
    @ColumnInfo(name = "provider_lyric_id") val providerLyricId: String,
    @ColumnInfo(defaultValue = "0") val synced: Boolean,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long,
    @ColumnInfo(defaultValue = "''") val checksum: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "identity_operations",
    indices = [
        Index(name = "idx_identity_operations_source", value = ["source_recording_id", "created_at"]),
        Index(name = "idx_identity_operations_target", value = ["target_recording_id", "created_at"]),
        Index(name = "idx_identity_operations_created", value = ["created_at"]),
        Index(name = "idx_identity_operations_reverted", value = ["reverted_at"])
    ]
)
data class IdentityOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "source_recording_id") val sourceRecordingId: Long?,
    @ColumnInfo(name = "target_recording_id") val targetRecordingId: Long?,
    @ColumnInfo(name = "before_payload", defaultValue = "''") val beforePayload: String,
    @ColumnInfo(name = "after_payload", defaultValue = "''") val afterPayload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "reverted_at") val revertedAt: Long?
)
