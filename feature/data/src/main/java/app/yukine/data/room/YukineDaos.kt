package app.yukine.data.room

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.ColumnInfo
import androidx.room.Update

@Dao
interface LibraryDao {
    @Query(
        "SELECT * FROM tracks " +
            "ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, title COLLATE NOCASE"
    )
    fun loadTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY updated_at DESC LIMIT :limit")
    fun loadRecentlyAdded(limit: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE id NOT IN (" +
            "SELECT DISTINCT track_id FROM play_events WHERE played_at > :playedAfter" +
            ") ORDER BY updated_at ASC LIMIT :limit"
    )
    fun loadLongUnplayed(playedAfter: Long, limit: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE codec = '' AND bitrate_kbps = 0 " +
            "AND sample_rate_hz = 0 AND bits_per_sample = 0 AND channel_count = 0 " +
            "AND data_path NOT LIKE 'stream:%' AND data_path NOT LIKE 'streaming:%' " +
            "AND data_path NOT LIKE 'webdav:%' ORDER BY updated_at ASC, id ASC LIMIT :limit"
    )
    fun loadTracksNeedingAudioSpecs(limit: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE data_path LIKE :dataPathPattern " +
            "ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, title COLLATE NOCASE"
    )
    fun loadTracksByDataPathPattern(dataPathPattern: String): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE data_path LIKE :dataPathPattern")
    fun loadTrackIdsByDataPathPattern(dataPathPattern: String): List<Long>

    @Query(
        "SELECT id FROM tracks WHERE data_path NOT LIKE 'document:%' " +
            "AND data_path NOT LIKE 'stream:%' AND data_path NOT LIKE 'streaming:%' " +
            "AND data_path NOT LIKE 'webdav:%'"
    )
    fun loadScanManagedTrackIds(): List<Long>

    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    fun loadTracksByIds(trackIds: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun loadTrack(trackId: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTracks(tracks: List<TrackEntity>)

    @Query(
        "UPDATE tracks SET codec = :codec, bitrate_kbps = :bitrateKbps, " +
            "sample_rate_hz = :sampleRateHz, bits_per_sample = :bitsPerSample, " +
            "channel_count = :channelCount, replay_gain_track_db = :replayGainTrackDb, " +
            "replay_gain_album_db = :replayGainAlbumDb, updated_at = :updatedAt " +
            "WHERE id = :trackId"
    )
    fun updateAudioSpecs(
        trackId: Long,
        codec: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        bitsPerSample: Int,
        channelCount: Int,
        replayGainTrackDb: Double,
        replayGainAlbumDb: Double,
        updatedAt: Long
    ): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE data_path = :dataPath")
    fun trackCountByDataPath(dataPath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE track_id = :trackId")
    fun deleteFavorite(trackId: Long): Int

    @Query("SELECT COUNT(*) FROM favorites WHERE track_id = :trackId")
    fun favoriteCount(trackId: Long): Int

    @Query("SELECT track_id FROM favorites")
    fun loadFavoriteIds(): List<Long>

    @Query(
        "SELECT t.* FROM favorites f JOIN tracks t ON t.id = f.track_id " +
            "ORDER BY f.created_at DESC"
    )
    fun loadFavoriteTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putExclusion(exclusion: LibraryExclusionEntity)

    @Query("SELECT * FROM library_exclusions ORDER BY created_at DESC")
    fun loadExclusions(): List<LibraryExclusionEntity>

    @Query("SELECT source_key FROM library_exclusions")
    fun loadExclusionKeys(): List<String>

    @Query("DELETE FROM library_exclusions WHERE source_key = :sourceKey")
    fun deleteExclusion(sourceKey: String): Int

    @Query("DELETE FROM library_exclusions")
    fun deleteAllExclusions(): Int

    @Query("DELETE FROM favorites WHERE track_id IN (:trackIds)")
    fun deleteFavoritesByTrackIds(trackIds: List<Long>): Int

    @Query("DELETE FROM tracks")
    fun deleteAllTracks(): Int

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    fun deleteTracksByIds(trackIds: List<Long>): Int
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, COUNT(pt.track_id) AS track_count, p.created_at, p.updated_at " +
            "FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id " +
            "GROUP BY p.id, p.name, p.created_at, p.updated_at " +
            "ORDER BY p.updated_at DESC, p.name COLLATE NOCASE ASC"
    )
    fun loadPlaylistRows(): List<PlaylistRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT COUNT(*) FROM playlists WHERE id = :playlistId")
    fun playlistExists(playlistId: Long): Int

    @Query(
        "UPDATE OR IGNORE playlists SET name = :name, updated_at = :updatedAt " +
            "WHERE id = :playlistId"
    )
    fun renamePlaylist(playlistId: Long, name: String, updatedAt: Long): Int

    @Query("UPDATE playlists SET updated_at = :updatedAt WHERE id = :playlistId")
    fun touchPlaylist(playlistId: Long, updatedAt: Long): Int

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @Transaction
    fun replacePlaylistTracks(playlistId: Long, tracks: List<PlaylistTrackEntity>) {
        deletePlaylistTracks(playlistId)
        insertPlaylistTracks(tracks)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun deletePlaylistTracks(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    fun removePlaylistTrack(playlistId: Long, trackId: Long): Int

    @Query(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks " +
            "WHERE playlist_id = :playlistId"
    )
    fun nextPosition(playlistId: Long): Int

    @Query(
        "SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId " +
            "ORDER BY position ASC, added_at ASC"
    )
    fun playlistTrackRows(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_tracks WHERE track_id = :trackId")
    fun playlistReferences(trackId: Long): List<PlaylistTrackEntity>

    @Query(
        "UPDATE playlist_tracks SET position = :position " +
            "WHERE playlist_id = :playlistId AND track_id = :trackId"
    )
    fun updateTrackPosition(playlistId: Long, trackId: Long, position: Int): Int

    @Query("DELETE FROM playlist_tracks WHERE track_id = :trackId")
    fun deleteTrackReferences(trackId: Long): Int

    @Query("DELETE FROM playlist_tracks WHERE track_id IN (:trackIds)")
    fun deleteTrackReferences(trackIds: List<Long>): Int

    @Query(
        "SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position ASC, pt.added_at ASC"
    )
    fun playlistTracks(playlistId: Long): List<TrackEntity>

    @Query(
        "SELECT DISTINCT t.id FROM playlist_tracks pt " +
            "JOIN tracks t ON t.id = pt.track_id " +
            "LEFT JOIN playlist_tracks other " +
            "ON other.track_id = pt.track_id AND other.playlist_id != pt.playlist_id " +
            "LEFT JOIN favorites f ON f.track_id = t.id " +
            "LEFT JOIN play_history h ON h.track_id = t.id " +
            "LEFT JOIN play_events e ON e.track_id = t.id " +
            "LEFT JOIN playback_queue q ON q.track_id = t.id " +
            "WHERE pt.playlist_id = :playlistId AND other.track_id IS NULL " +
            "AND f.track_id IS NULL AND h.track_id IS NULL AND e.track_id IS NULL " +
            "AND q.track_id IS NULL AND (t.data_path LIKE 'document:%' " +
            "OR t.data_path LIKE 'playlist-local:%' OR t.data_path LIKE 'streaming:%')"
    )
    fun orphanedPlaceholderTrackIds(playlistId: Long): List<Long>
}

data class PlaylistRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertHistory(history: PlayHistoryEntity)

    @Insert
    fun insertEvent(event: PlayEventEntity): Long

    @Query(
        "UPDATE play_history SET played_at = :playedAt, play_count = play_count + 1 " +
            "WHERE track_id = :trackId"
    )
    fun incrementHistory(trackId: Long, playedAt: Long): Int

    @Transaction
    fun recordPlay(trackId: Long, playedAt: Long) {
        if (incrementHistory(trackId, playedAt) == 0) {
            upsertHistory(PlayHistoryEntity(trackId, playedAt, 1))
        }
        insertEvent(PlayEventEntity(null, trackId, playedAt))
    }

    @Query("SELECT * FROM play_history WHERE track_id = :trackId LIMIT 1")
    fun history(trackId: Long): PlayHistoryEntity?

    @Query("DELETE FROM play_history WHERE track_id = :trackId")
    fun deleteHistory(trackId: Long): Int

    @Query("DELETE FROM play_history WHERE track_id IN (:trackIds)")
    fun deleteHistory(trackIds: List<Long>): Int

    @Query("UPDATE play_events SET track_id = :newTrackId WHERE track_id = :oldTrackId")
    fun migrateEvents(oldTrackId: Long, newTrackId: Long): Int

    @Query("DELETE FROM play_events WHERE track_id IN (:trackIds)")
    fun deleteEvents(trackIds: List<Long>): Int

    @Query(
        "SELECT t.*, h.played_at AS record_played_at, h.play_count AS record_play_count " +
            "FROM play_history h JOIN tracks t ON t.id = h.track_id " +
            "ORDER BY h.played_at DESC LIMIT :limit"
    )
    fun recentlyPlayed(limit: Int): List<TrackPlayRecordRow>

    @Query(
        "SELECT t.*, h.played_at AS record_played_at, h.play_count AS record_play_count " +
            "FROM play_history h JOIN tracks t ON t.id = h.track_id " +
            "ORDER BY h.play_count DESC, h.played_at DESC LIMIT :limit"
    )
    fun mostPlayed(limit: Int): List<TrackPlayRecordRow>

    @Query(
        "SELECT t.*, MAX(e.played_at) AS record_played_at, COUNT(*) AS record_play_count " +
            "FROM play_events e JOIN tracks t ON t.id = e.track_id " +
            "WHERE e.played_at >= :startMs GROUP BY t.id " +
            "ORDER BY record_played_at DESC LIMIT :limit"
    )
    fun playedSince(startMs: Long, limit: Int): List<TrackPlayRecordRow>

    @Query("DELETE FROM play_history")
    fun clearHistoryRows(): Int

    @Query("DELETE FROM play_events")
    fun clearEvents(): Int

    @Transaction
    fun clearHistory(): Int {
        val removed = clearHistoryRows()
        clearEvents()
        return removed
    }
}

data class TrackPlayRecordRow(
    @Embedded val track: TrackEntity,
    @ColumnInfo(name = "record_played_at") val playedAt: Long,
    @ColumnInfo(name = "record_play_count") val playCount: Int
)

@Dao
interface PlaybackPersistenceDao {
    @Query("SELECT * FROM playback_queue ORDER BY position")
    fun loadQueue(): List<PlaybackQueueEntity>

    @Query("DELETE FROM playback_queue")
    fun clearQueue(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQueue(tracks: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue WHERE track_id IN (:trackIds)")
    fun deleteTracks(trackIds: List<Long>): Int

    @Query(
        "UPDATE playback_queue SET codec = :codec, bitrate_kbps = :bitrateKbps, " +
            "sample_rate_hz = :sampleRateHz, bits_per_sample = :bitsPerSample, " +
            "channel_count = :channelCount, replay_gain_track_db = :replayGainTrackDb, " +
            "replay_gain_album_db = :replayGainAlbumDb WHERE track_id = :trackId"
    )
    fun updateAudioSpecs(
        trackId: Long,
        codec: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        bitsPerSample: Int,
        channelCount: Int,
        replayGainTrackDb: Double,
        replayGainAlbumDb: Double
    ): Int

    @Transaction
    fun replaceQueue(tracks: List<PlaybackQueueEntity>) {
        clearQueue()
        insertQueue(tracks)
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    fun value(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(settings: List<SettingEntity>)

    @Query("DELETE FROM settings WHERE `key` = :key")
    fun delete(key: String): Int
}

@Dao
interface RemoteSourceDao {
    @Query("SELECT * FROM remote_sources ORDER BY updated_at DESC, name COLLATE NOCASE")
    fun loadSources(): List<RemoteSourceEntity>

    @Query("SELECT * FROM remote_sources WHERE id = :sourceId LIMIT 1")
    fun loadSource(sourceId: Long): RemoteSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(source: RemoteSourceEntity): Long

    @Update
    fun update(source: RemoteSourceEntity): Int

    @Query(
        "UPDATE remote_sources SET last_status = :status, updated_at = :updatedAt " +
            "WHERE id = :sourceId"
    )
    fun updateStatus(sourceId: Long, status: String, updatedAt: Long): Int

    @Query("DELETE FROM remote_sources WHERE id = :sourceId")
    fun delete(sourceId: Long): Int
}

@Dao
interface StreamingTrackMatchDao {
    @Query(
        "SELECT * FROM streaming_track_matches " +
            "WHERE local_key = :localKey AND provider = :provider LIMIT 1"
    )
    fun match(localKey: String, provider: String): StreamingTrackMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(match: StreamingTrackMatchEntity)
}
