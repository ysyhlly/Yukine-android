package app.yukine.data.room

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.ColumnInfo
import androidx.room.Update
import androidx.room.Upsert
import app.yukine.streaming.PlaybackSourceSelectionEvaluator
import app.yukine.streaming.PlaybackSourceSelectionFeatures

data class PlaylistRecordingTrackRow(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @Embedded val track: TrackEntity
)

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

    @Query(
        "SELECT * FROM tracks WHERE data_path NOT LIKE 'document:%' " +
            "AND data_path NOT LIKE 'stream:%' AND data_path NOT LIKE 'streaming:%' " +
            "AND data_path NOT LIKE 'webdav:%'"
    )
    fun loadScanManagedTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    fun loadTracksByIds(trackIds: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun loadTrack(trackId: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTracks(tracks: List<TrackEntity>)

    @Query(
        "UPDATE tracks SET album_art_uri = :coverUrl, updated_at = :updatedAt " +
            "WHERE id = :trackId AND TRIM(album_art_uri) = ''"
    )
    fun updateAlbumArtIfMissing(trackId: Long, coverUrl: String, updatedAt: Long): Int

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

    @Query("UPDATE tracks SET updated_at = :attemptedAt WHERE id = :trackId")
    fun touchAudioSpecAttempt(trackId: Long, attemptedAt: Long): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE data_path = :dataPath")
    fun trackCountByDataPath(dataPath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putRecordingFavorite(favorite: RecordingFavoriteEntity)

    @Query("SELECT * FROM favorites WHERE recording_id = :recordingId LIMIT 1")
    fun recordingFavorite(recordingId: Long): RecordingFavoriteEntity?

    @Query("DELETE FROM favorites WHERE recording_id = :recordingId")
    fun deleteRecordingFavorite(recordingId: Long): Int

    @Query("UPDATE favorites SET sync_state = :syncState WHERE recording_id = :recordingId")
    fun updateRecordingFavoriteSyncState(recordingId: Long, syncState: String): Int

    @Query(
        "SELECT COUNT(*) FROM favorites f INNER JOIN track_sources s " +
            "ON s.recording_id = f.recording_id WHERE s.local_track_id = :trackId"
    )
    fun recordingFavoriteCountForTrack(trackId: Long): Int

    @Query(
        "SELECT s.local_track_id FROM favorites f INNER JOIN track_sources s " +
            "ON s.recording_id = f.recording_id WHERE s.local_track_id IS NOT NULL"
    )
    fun loadRecordingFavoriteTrackIds(): List<Long>

    @Query(
        "SELECT t.* FROM favorites f " +
            "INNER JOIN track_sources s ON s.recording_id = f.recording_id " +
            "INNER JOIN tracks t ON t.id = s.local_track_id " +
            "WHERE s.source_id = (SELECT s2.source_id FROM track_sources s2 " +
            "WHERE s2.recording_id = f.recording_id AND s2.local_track_id IS NOT NULL " +
            "ORDER BY CASE " +
            "WHEN s2.data_path LIKE 'document:%' THEN 0 " +
            "WHEN s2.data_path NOT LIKE 'webdav:%' AND s2.data_path NOT LIKE 'streaming:%' " +
            "AND s2.data_path NOT LIKE 'stream:%' THEN 0 " +
            "WHEN s2.data_path LIKE 'webdav:%' THEN 1 " +
            "WHEN s2.data_path LIKE '%:netease:%' THEN 2 " +
            "WHEN s2.data_path LIKE '%:qqmusic:%' THEN 3 " +
            "WHEN s2.data_path LIKE '%:luoxue:%' THEN 4 ELSE 5 END, " +
            "s2.last_successful_at DESC, s2.source_id LIMIT 1) " +
            "ORDER BY f.created_at DESC"
    )
    fun loadRecordingFavoriteTracks(): List<TrackEntity>

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

    @Query("DELETE FROM tracks")
    fun deleteAllTracks(): Int

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    fun deleteTracksByIds(trackIds: List<Long>): Int
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, CASE WHEN EXISTS (SELECT 1 FROM playlist_recording_items pri " +
            "WHERE pri.playlist_id = p.id) THEN (SELECT COUNT(*) FROM playlist_recording_items pri " +
            "WHERE pri.playlist_id = p.id) ELSE (SELECT COUNT(*) FROM playlist_tracks pt " +
            "WHERE pt.playlist_id = p.id) END AS track_count, p.created_at, p.updated_at " +
            "FROM playlists p " +
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

    @Query("DELETE FROM playlist_recording_items WHERE playlist_id = :playlistId")
    fun deletePlaylistRecordingItems(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistTrack(track: PlaylistTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistRecordingItem(item: PlaylistRecordingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPlaylistRecordingItems(items: List<PlaylistRecordingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPlaylistRecordingItem(item: PlaylistRecordingItemEntity)

    @Query("SELECT * FROM playlist_recording_items WHERE recording_id = :recordingId")
    fun playlistRecordingReferences(recordingId: Long): List<PlaylistRecordingItemEntity>

    @Query(
        "SELECT * FROM playlist_recording_items WHERE playlist_id = :playlistId " +
            "AND recording_id = :recordingId LIMIT 1"
    )
    fun playlistRecordingItem(playlistId: Long, recordingId: Long): PlaylistRecordingItemEntity?

    @Query(
        "SELECT COALESCE(MAX(sort_key), 0) + 1024 FROM playlist_recording_items " +
            "WHERE playlist_id = :playlistId"
    )
    fun nextRecordingSortKey(playlistId: Long): Long

    @Query(
        "SELECT * FROM playlist_recording_items WHERE playlist_id = :playlistId " +
            "ORDER BY sort_key, added_at, recording_id"
    )
    fun playlistRecordingRows(playlistId: Long): List<PlaylistRecordingItemEntity>

    @Query(
        "UPDATE playlist_recording_items SET sort_key = :sortKey " +
            "WHERE playlist_id = :playlistId AND recording_id = :recordingId"
    )
    fun updateRecordingSortKey(playlistId: Long, recordingId: Long, sortKey: Long): Int

    @Query(
        "DELETE FROM playlist_recording_items WHERE playlist_id = :playlistId " +
            "AND recording_id = :recordingId"
    )
    fun removePlaylistRecordingItem(playlistId: Long, recordingId: Long): Int

    @Query(
        "DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id IN (" +
            "SELECT local_track_id FROM track_sources WHERE recording_id = :recordingId " +
            "AND local_track_id IS NOT NULL)"
    )
    fun removeLegacyPlaylistRecordingSources(playlistId: Long, recordingId: Long): Int

    @Query(
        "SELECT pri.recording_id AS recording_id, t.* FROM playlist_recording_items pri " +
            "JOIN tracks t ON t.id = COALESCE((" +
            "SELECT s.local_track_id FROM track_sources s JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.recording_id = pri.recording_id AND s.local_track_id IS NOT NULL AND s.playable = 1 " +
            "ORDER BY CASE WHEN s.source_id = r.active_source_id THEN 1000 WHEN s.provider = 'local' THEN 600 " +
            "WHEN s.provider = 'webdav' THEN 500 WHEN s.provider = 'document' THEN 450 " +
            "WHEN s.provider = 'stream' THEN 400 WHEN s.provider = 'netease' THEN 300 " +
            "WHEN s.provider = 'qqmusic' THEN 250 WHEN s.provider = 'luoxue' THEN 200 ELSE 100 END DESC, " +
            "s.quality_score DESC, s.source_id LIMIT 1), pri.representative_track_id) " +
            "WHERE pri.playlist_id = :playlistId ORDER BY pri.sort_key, pri.added_at, pri.recording_id"
    )
    fun playlistRecordingTracks(playlistId: Long): List<PlaylistRecordingTrackRow>

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
            "LEFT JOIN track_sources favorite_source ON favorite_source.local_track_id = t.id " +
            "LEFT JOIN favorites recording_favorite " +
            "ON recording_favorite.recording_id = favorite_source.recording_id " +
            "LEFT JOIN play_history h ON h.track_id = t.id " +
            "LEFT JOIN play_events e ON e.track_id = t.id " +
            "LEFT JOIN playback_queue q ON q.track_id = t.id " +
            "WHERE pt.playlist_id = :playlistId AND other.track_id IS NULL " +
            "AND recording_favorite.recording_id IS NULL " +
            "AND h.track_id IS NULL AND e.track_id IS NULL " +
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRecordingHistory(history: RecordingPlayHistoryEntity)

    @Insert
    fun insertRecordingEvent(event: RecordingPlayEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRecordingEvents(events: List<RecordingPlayEventEntity>)

    @Query(
        "UPDATE recording_play_history SET representative_track_id = :trackId, " +
            "played_at = :playedAt, play_count = play_count + 1 WHERE recording_id = :recordingId"
    )
    fun incrementRecordingHistory(recordingId: Long, trackId: Long, playedAt: Long): Int

    @Query("SELECT * FROM recording_play_history WHERE recording_id = :recordingId LIMIT 1")
    fun recordingHistory(recordingId: Long): RecordingPlayHistoryEntity?

    @Query("SELECT COUNT(*) FROM recording_play_events WHERE recording_id = :recordingId")
    fun recordingEventCount(recordingId: Long): Int

    @Query("SELECT * FROM recording_play_events WHERE recording_id = :recordingId ORDER BY id")
    fun recordingEvents(recordingId: Long): List<RecordingPlayEventEntity>

    @Query("DELETE FROM recording_play_history WHERE recording_id = :recordingId")
    fun deleteRecordingHistory(recordingId: Long): Int

    @Query("UPDATE recording_play_events SET recording_id = :targetId WHERE recording_id = :sourceId")
    fun moveRecordingEvents(sourceId: Long, targetId: Long): Int

    @Query(
        "UPDATE recording_play_events SET source_id = NULL WHERE recording_id = :recordingId " +
            "AND source_id IN (:sourceIds)"
    )
    fun clearRecordingEventSources(recordingId: Long, sourceIds: List<Long>): Int

    @Query("DELETE FROM recording_play_events WHERE recording_id IN (:recordingIds)")
    fun deleteRecordingEvents(recordingIds: List<Long>): Int

    @Query(
        "UPDATE play_history SET played_at = :playedAt, play_count = play_count + 1 " +
            "WHERE track_id = :trackId"
    )
    fun incrementHistory(trackId: Long, playedAt: Long): Int

    @Transaction
    fun recordPlay(trackId: Long, playedAt: Long): Long {
        if (incrementHistory(trackId, playedAt) == 0) {
            upsertHistory(PlayHistoryEntity(trackId, playedAt, 1))
        }
        return insertEvent(PlayEventEntity(null, trackId, playedAt))
    }

    @Transaction
    fun recordCanonicalPlay(trackId: Long, recordingId: Long, sourceId: Long?, playedAt: Long) {
        val legacyEventId = recordPlay(trackId, playedAt)
        if (recordingId <= 0L) return
        if (incrementRecordingHistory(recordingId, trackId, playedAt) == 0) {
            upsertRecordingHistory(RecordingPlayHistoryEntity(recordingId, trackId, playedAt, 1))
        }
        insertRecordingEvent(
            RecordingPlayEventEntity(
                null,
                recordingId,
                sourceId,
                trackId,
                playedAt,
                legacyEventId
            )
        )
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

    @Query(
        "SELECT t.*, h.played_at AS record_played_at, h.play_count AS record_play_count " +
            "FROM recording_play_history h JOIN tracks t ON t.id = COALESCE((" +
            "SELECT s.local_track_id FROM track_sources s JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.recording_id = h.recording_id AND s.local_track_id IS NOT NULL AND s.playable = 1 " +
            "ORDER BY CASE WHEN s.source_id = r.active_source_id THEN 1000 WHEN s.provider = 'local' THEN 600 " +
            "WHEN s.provider = 'webdav' THEN 500 WHEN s.provider = 'document' THEN 450 " +
            "WHEN s.provider = 'stream' THEN 400 WHEN s.provider = 'netease' THEN 300 " +
            "WHEN s.provider = 'qqmusic' THEN 250 WHEN s.provider = 'luoxue' THEN 200 ELSE 100 END DESC, " +
            "s.quality_score DESC, s.source_id LIMIT 1), h.representative_track_id) " +
            "ORDER BY h.played_at DESC LIMIT :limit"
    )
    fun canonicalRecentlyPlayed(limit: Int): List<TrackPlayRecordRow>

    @Query(
        "SELECT t.*, h.played_at AS record_played_at, h.play_count AS record_play_count " +
            "FROM recording_play_history h JOIN tracks t ON t.id = COALESCE((" +
            "SELECT s.local_track_id FROM track_sources s JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.recording_id = h.recording_id AND s.local_track_id IS NOT NULL AND s.playable = 1 " +
            "ORDER BY CASE WHEN s.source_id = r.active_source_id THEN 1000 ELSE 0 END DESC, " +
            "s.quality_score DESC, s.source_id LIMIT 1), h.representative_track_id) " +
            "ORDER BY h.play_count DESC, h.played_at DESC LIMIT :limit"
    )
    fun canonicalMostPlayed(limit: Int): List<TrackPlayRecordRow>

    @Query(
        "SELECT t.*, MAX(e.played_at) AS record_played_at, COUNT(*) AS record_play_count " +
            "FROM recording_play_events e JOIN tracks t ON t.id = COALESCE((" +
            "SELECT s.local_track_id FROM track_sources s JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.recording_id = e.recording_id AND s.local_track_id IS NOT NULL AND s.playable = 1 " +
            "ORDER BY CASE WHEN s.source_id = r.active_source_id THEN 1000 ELSE 0 END DESC, " +
            "s.quality_score DESC, s.source_id LIMIT 1), e.track_id_snapshot) " +
            "WHERE e.played_at >= :startMs GROUP BY e.recording_id " +
            "ORDER BY record_played_at DESC LIMIT :limit"
    )
    fun canonicalPlayedSince(startMs: Long, limit: Int): List<TrackPlayRecordRow>

    @Query("DELETE FROM play_history")
    fun clearHistoryRows(): Int

    @Query("DELETE FROM play_events")
    fun clearEvents(): Int

    @Query("DELETE FROM recording_play_history")
    fun clearRecordingHistoryRows(): Int

    @Query("DELETE FROM recording_play_events")
    fun clearRecordingEvents(): Int

    @Transaction
    fun clearHistory(): Int {
        val removed = clearHistoryRows()
        clearEvents()
        clearRecordingHistoryRows()
        clearRecordingEvents()
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

    @Query("SELECT * FROM playback_queue_identities ORDER BY position")
    fun loadQueueIdentities(): List<PlaybackQueueIdentityEntity>

    @Query("SELECT * FROM playback_queue_identities WHERE recording_id IN (:recordingIds) ORDER BY position")
    fun queueIdentities(recordingIds: List<Long>): List<PlaybackQueueIdentityEntity>

    @Query("DELETE FROM playback_queue")
    fun clearQueue(): Int

    @Query("DELETE FROM playback_queue_identities")
    fun clearQueueIdentities(): Int

    @Query("DELETE FROM playback_queue_identities WHERE recording_id IN (:recordingIds)")
    fun deleteQueueIdentities(recordingIds: List<Long>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQueue(tracks: List<PlaybackQueueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQueueIdentities(values: List<PlaybackQueueIdentityEntity>)

    @Query(
        "UPDATE playback_queue_identities SET recording_id = :targetId " +
            "WHERE recording_id = :sourceId"
    )
    fun moveQueueRecording(sourceId: Long, targetId: Long): Int

    @Query("SELECT COUNT(*) FROM playback_queue_identities WHERE recording_id = :recordingId")
    fun queueRecordingCount(recordingId: Long): Int

    @Query(
        "UPDATE playback_queue_identities SET preferred_source_id = NULL " +
            "WHERE recording_id = :recordingId AND preferred_source_id IN (:sourceIds)"
    )
    fun clearQueuePreferredSources(recordingId: Long, sourceIds: List<Long>): Int

    @Query(
        "UPDATE playback_queue_identities SET preferred_source_id = NULL " +
            "WHERE recording_id = :recordingId AND preferred_source_id IS NOT NULL " +
            "AND preferred_source_id NOT IN (:sourceIds)"
    )
    fun clearQueuePreferredSourcesExcept(recordingId: Long, sourceIds: List<Long>): Int

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
    fun replaceQueue(
        tracks: List<PlaybackQueueEntity>,
        identities: List<PlaybackQueueIdentityEntity> = emptyList()
    ) {
        clearQueueIdentities()
        clearQueue()
        insertQueue(tracks)
        insertQueueIdentities(identities)
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

data class GlobalDedupCandidateRow(
    @ColumnInfo(name = "left_recording_id") val leftRecordingId: Long,
    @ColumnInfo(name = "right_recording_id") val rightRecordingId: Long,
    @ColumnInfo(name = "left_title") val leftTitle: String,
    @ColumnInfo(name = "left_artist") val leftArtist: String,
    @ColumnInfo(name = "right_title") val rightTitle: String,
    @ColumnInfo(name = "right_artist") val rightArtist: String,
    @ColumnInfo(name = "same_recording_probability") val sameRecordingProbability: Double,
    @ColumnInfo(name = "runner_up_probability") val runnerUpProbability: Double,
    @ColumnInfo(name = "relation_type") val relationType: String,
    @ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

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

    @Query("SELECT * FROM streaming_track_matches")
    fun allMatches(): List<StreamingTrackMatchEntity>

    @Query("DELETE FROM streaming_track_matches WHERE provider = :provider")
    fun deleteProvider(provider: String): Int
}

@Dao
interface MusicIdentityDao {
    @Query(
        "SELECT rel.left_recording_id, rel.right_recording_id, " +
            "left_recording.title AS left_title, " +
            "left_recording.primary_artist_display AS left_artist, " +
            "right_recording.title AS right_title, " +
            "right_recording.primary_artist_display AS right_artist, " +
            "rel.same_recording_probability, " +
            "COALESCE((SELECT MAX(competing.same_recording_probability) " +
            "FROM recording_relations competing " +
            "WHERE competing.locked = 0 " +
            "AND competing.relation_type IN ('SAME_RECORDING','UNKNOWN') " +
            "AND (competing.left_recording_id IN " +
            "(rel.left_recording_id, rel.right_recording_id) " +
            "OR competing.right_recording_id IN " +
            "(rel.left_recording_id, rel.right_recording_id)) " +
            "AND NOT (competing.left_recording_id = rel.left_recording_id " +
            "AND competing.right_recording_id = rel.right_recording_id)), 0) " +
            "AS runner_up_probability, " +
            "rel.relation_type, rel.evidence_json, rel.updated_at " +
            "FROM recording_relations rel " +
            "JOIN recordings left_recording ON left_recording.id = rel.left_recording_id " +
            "JOIN recordings right_recording ON right_recording.id = rel.right_recording_id " +
            "WHERE rel.locked = 0 " +
            "AND rel.relation_type IN ('SAME_RECORDING','UNKNOWN') " +
            "ORDER BY rel.same_recording_probability DESC, rel.updated_at DESC, " +
            "rel.left_recording_id, rel.right_recording_id LIMIT :limit OFFSET :offset"
    )
    fun globalDedupCandidates(limit: Int, offset: Int): List<GlobalDedupCandidateRow>

    @Query(
        "SELECT rel.left_recording_id, rel.right_recording_id, " +
            "left_recording.title AS left_title, " +
            "left_recording.primary_artist_display AS left_artist, " +
            "right_recording.title AS right_title, " +
            "right_recording.primary_artist_display AS right_artist, " +
            "rel.same_recording_probability, " +
            "COALESCE((SELECT MAX(competing.same_recording_probability) " +
            "FROM recording_relations competing " +
            "WHERE competing.locked = 0 " +
            "AND competing.relation_type IN ('SAME_RECORDING','UNKNOWN') " +
            "AND (competing.left_recording_id IN " +
            "(rel.left_recording_id, rel.right_recording_id) " +
            "OR competing.right_recording_id IN " +
            "(rel.left_recording_id, rel.right_recording_id)) " +
            "AND NOT (competing.left_recording_id = rel.left_recording_id " +
            "AND competing.right_recording_id = rel.right_recording_id)), 0) " +
            "AS runner_up_probability, " +
            "rel.relation_type, rel.evidence_json, rel.updated_at " +
            "FROM recording_relations rel " +
            "JOIN recordings left_recording ON left_recording.id = rel.left_recording_id " +
            "JOIN recordings right_recording ON right_recording.id = rel.right_recording_id " +
            "WHERE rel.left_recording_id = :leftRecordingId " +
            "AND rel.right_recording_id = :rightRecordingId " +
            "AND rel.locked = 0 " +
            "AND rel.relation_type IN ('SAME_RECORDING','UNKNOWN') LIMIT 1"
    )
    fun globalDedupCandidate(
        leftRecordingId: Long,
        rightRecordingId: Long
    ): GlobalDedupCandidateRow?

    @Query(
        "SELECT COUNT(*) FROM recording_relations " +
            "WHERE locked = 0 AND relation_type IN ('SAME_RECORDING','UNKNOWN')"
    )
    fun globalDedupCandidateCount(): Int

    @Query(
        "SELECT COUNT(*) FROM identity_operations " +
            "WHERE dedup_mode = 'AGGRESSIVE' AND rollback_status = 'REVIEW_REQUIRED' " +
            "AND reverted_at IS NULL"
    )
    fun dedupRollbackReviewRequiredCount(): Int
    @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
    fun work(workId: Long): CanonicalWorkEntity?

    @Query("SELECT * FROM works WHERE canonical_uuid = :canonicalUuid LIMIT 1")
    fun work(canonicalUuid: String): CanonicalWorkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertWork(work: CanonicalWorkEntity): Long

    @Upsert
    fun upsert(work: CanonicalWorkEntity)

    @Query(
        "SELECT * FROM works WHERE normalized_title = :normalizedTitle " +
            "AND primary_creator_id = :primaryCreatorId ORDER BY created_at, id LIMIT 1"
    )
    fun workForIdentity(normalizedTitle: String, primaryCreatorId: Long): CanonicalWorkEntity?

    @Query(
        "SELECT * FROM work_artist_credits WHERE work_id = :workId " +
            "ORDER BY position, role, artist_id"
    )
    fun workCredits(workId: Long): List<WorkArtistCreditEntity>

    @Query(
        "SELECT * FROM work_identifiers WHERE work_id = :workId " +
            "ORDER BY identifier_type, namespace, identifier_value"
    )
    fun workIdentifiers(workId: Long): List<WorkIdentifierEntity>

    @Query(
        "SELECT * FROM work_identifiers WHERE identifier_type = :type " +
            "AND namespace = :namespace AND identifier_value = :value LIMIT 1"
    )
    fun workIdentifier(type: String, namespace: String, value: String): WorkIdentifierEntity?

    @Query(
        "SELECT artist_id FROM recording_artist_credits WHERE recording_id = :recordingId " +
            "AND role = 'PRIMARY' ORDER BY position, artist_id LIMIT 1"
    )
    fun primaryArtistId(recordingId: Long): Long?

    @Query("UPDATE recordings SET work_id = :workId, updated_at = :updatedAt WHERE id = :recordingId")
    fun updateRecordingWork(recordingId: Long, workId: Long, updatedAt: Long): Int

    @Query(
        "UPDATE recordings SET work_id = :targetWorkId, updated_at = :updatedAt " +
            "WHERE work_id = :sourceWorkId"
    )
    fun moveRecordingsToWork(sourceWorkId: Long, targetWorkId: Long, updatedAt: Long): Int

    @Query(
        "UPDATE works SET normalized_title = :normalizedTitle, updated_at = :updatedAt " +
            "WHERE id = :workId"
    )
    fun updateWorkTitle(workId: Long, normalizedTitle: String, updatedAt: Long): Int

    @Query(
        "UPDATE works SET primary_creator_id=(SELECT c.artist_id FROM recording_artist_credits c " +
            "INNER JOIN recordings r ON r.id=c.recording_id WHERE r.work_id=works.id " +
            "AND c.role='PRIMARY' ORDER BY c.position,c.artist_id LIMIT 1), updated_at=:updatedAt " +
            "WHERE id=(SELECT work_id FROM recordings WHERE id=:recordingId LIMIT 1) " +
            "AND primary_creator_id IS NULL"
    )
    fun refreshWorkPrimaryCreator(recordingId: Long, updatedAt: Long): Int

    @Query("DELETE FROM works WHERE id NOT IN (SELECT work_id FROM recordings WHERE work_id IS NOT NULL)")
    fun deleteOrphanWorks(): Int

    @Query(
        "SELECT s.local_track_id AS localTrackId, CASE WHEN " +
            "(SELECT COUNT(*) FROM track_sources grouped " +
            "WHERE grouped.recording_id = r.id) > 1 OR " +
            "EXISTS (SELECT 1 FROM recording_identifiers i WHERE i.recording_id = r.id) OR " +
            "EXISTS (SELECT 1 FROM recording_variants v WHERE v.recording_id = r.id) OR " +
            "r.match_status = 'CONFIRMED' " +
            "THEN r.canonical_uuid ELSE '' END AS mergeIdentity " +
            "FROM track_sources s INNER JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.local_track_id IS NOT NULL"
    )
    fun trackMergeIdentities(): List<TrackMergeIdentityRow>

    /** Integer-only library hot-path identity; every persisted source belongs to one recording. */
    @Query(
        "SELECT local_track_id AS localTrackId, recording_id AS recordingId " +
            "FROM track_sources WHERE local_track_id IS NOT NULL"
    )
    fun trackRecordingIdentities(): List<TrackRecordingIdentityRow>

    @Query(
        "SELECT s.local_track_id AS localTrackId, a.id AS artistKey, " +
            "a.artist_uuid AS artistId, a.display_name AS displayName, " +
            "c.credited_name AS creditedName, a.avatar_url AS avatarUrl, " +
            "c.role AS role, c.position AS position " +
            "FROM track_sources s INNER JOIN recording_artist_credits c " +
            "ON c.recording_id = s.recording_id INNER JOIN canonical_artists a ON a.id = c.artist_id " +
            "WHERE s.local_track_id IS NOT NULL AND c.role IN ('PRIMARY','FEATURED','PERFORMER') " +
            "ORDER BY s.local_track_id, c.position, c.role"
    )
    fun trackArtistIdentities(): List<TrackArtistIdentityRow>

    @Query(
        "SELECT local.local_track_id AS localTrackId, candidate.provider AS provider, " +
            "'' AS providerTrackId, candidate.evidence_json AS evidenceJson, " +
            "1 AS storedCandidate, 0 AS statusRank, candidate.score AS confidence, " +
            "candidate.updated_at AS recency " +
            "FROM track_sources local INNER JOIN identity_candidates candidate " +
            "ON candidate.target_type = 'RECORDING' " +
            "AND candidate.target_id = local.recording_id " +
            "AND candidate.provider_item_id = '__stored_match__' " +
            "WHERE local.local_track_id IS NOT NULL AND candidate.status = 'PENDING' " +
            "UNION ALL " +
            "SELECT local.local_track_id AS localTrackId, source.provider AS provider, " +
            "source.provider_track_id AS providerTrackId, '' AS evidenceJson, " +
            "0 AS storedCandidate, CASE source.match_status " +
            "WHEN 'CONFIRMED' THEN 3 WHEN 'CANDIDATE' THEN 2 ELSE 1 END AS statusRank, " +
            "source.confidence AS confidence, source.last_verified_at AS recency " +
            "FROM track_sources local INNER JOIN track_sources source " +
            "ON source.recording_id = local.recording_id " +
            "WHERE local.local_track_id IS NOT NULL " +
            "ORDER BY localTrackId, provider, storedCandidate DESC, statusRank DESC, " +
            "confidence DESC, recency DESC"
    )
    fun trackStreamingMatches(): List<TrackStreamingMatchRow>

    @Query("SELECT * FROM recordings WHERE canonical_uuid = :canonicalUuid LIMIT 1")
    fun canonicalRecording(canonicalUuid: String): CanonicalRecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :recordingId LIMIT 1")
    fun recording(recordingId: Long): CanonicalRecordingEntity?

    @Query("SELECT * FROM recordings WHERE id IN (:recordingIds)")
    fun recordings(recordingIds: List<Long>): List<CanonicalRecordingEntity>

    @Query("SELECT id FROM recordings WHERE id IN (:recordingIds)")
    fun existingRecordingIds(recordingIds: List<Long>): List<Long>

    @Query("SELECT COUNT(*) FROM recordings")
    fun recordingCount(): Int

    @Query("SELECT id FROM recordings WHERE id > :afterId ORDER BY id LIMIT :limit")
    fun recordingIdsAfter(afterId: Long, limit: Int): List<Long>

    @Query(
        "SELECT r.id AS recordingId, r.canonical_uuid AS canonicalUuid, r.title AS title, " +
            "r.primary_artist_display AS primaryArtistDisplay, r.duration_ms AS durationMs, " +
            "(SELECT COUNT(*) FROM track_sources s WHERE s.recording_id = r.id) AS sourceCount, " +
            "COALESCE((SELECT GROUP_CONCAT(DISTINCT v.variant_type) FROM recording_variants v " +
            "WHERE v.recording_id = r.id), '') AS variantTypes " +
            "FROM recordings r WHERE r.id != :excludedRecordingId AND (" +
            ":query = '' OR r.title LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "r.primary_artist_display LIKE '%' || :query || '%' COLLATE NOCASE OR EXISTS (" +
            "SELECT 1 FROM track_sources s WHERE s.recording_id = r.id AND (" +
            "s.title LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "s.artist LIKE '%' || :query || '%' COLLATE NOCASE))) " +
            "ORDER BY CASE WHEN LOWER(r.title) = LOWER(:query) THEN 0 " +
            "WHEN r.title LIKE :query || '%' COLLATE NOCASE THEN 1 ELSE 2 END, " +
            "r.updated_at DESC, r.id LIMIT :limit"
    )
    fun searchRecordings(
        query: String,
        excludedRecordingId: Long,
        limit: Int
    ): List<RecordingMergeSearchRow>

    @Query(
        "SELECT r.* FROM recordings r INNER JOIN track_sources s ON s.recording_id = r.id " +
            "WHERE s.provider = :provider AND s.provider_track_id = :providerTrackId LIMIT 1"
    )
    fun recordingForProvider(provider: String, providerTrackId: String): CanonicalRecordingEntity?

    @Query(
        "SELECT r.canonical_uuid FROM recordings r INNER JOIN track_sources s " +
            "ON s.recording_id = r.id WHERE s.local_track_id = :localTrackId LIMIT 1"
    )
    fun canonicalIdForLocalTrack(localTrackId: Long): String?

    @Query("SELECT recording_id FROM track_sources WHERE local_track_id = :localTrackId LIMIT 1")
    fun recordingIdForLocalTrack(localTrackId: Long): Long?

    @Query(
        "SELECT * FROM track_sources " +
            "WHERE local_track_id = :localTrackId LIMIT 1"
    )
    fun sourceForLocalTrack(localTrackId: Long): TrackSourceMappingEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE id = :localTrackId)")
    fun localTrackExists(localTrackId: Long): Boolean

    @Query("SELECT * FROM track_sources WHERE local_track_id IN (:localTrackIds)")
    fun sourcesForLocalTracks(localTrackIds: List<Long>): List<TrackSourceMappingEntity>

    @Query("SELECT * FROM audio_features WHERE source_id IN (:sourceIds)")
    fun audioFeatures(sourceIds: List<Long>): List<AudioFeatureEntity>

    @Query("SELECT * FROM audio_features WHERE source_id = :sourceId LIMIT 1")
    fun audioFeature(sourceId: Long): AudioFeatureEntity?

    @Upsert
    fun upsertAudioFeatures(features: List<AudioFeatureEntity>)

    @Query(
        "SELECT ts.* FROM track_sources ts " +
            "JOIN tracks t ON t.id = ts.local_track_id " +
            "LEFT JOIN audio_features af ON af.source_id = ts.source_id " +
            "WHERE ts.local_track_id IS NOT NULL AND (" +
            "af.source_id IS NULL OR af.chromaprint = '' OR af.algorithm_version < :algorithmVersion " +
            "OR af.updated_at < t.updated_at) " +
            "AND (af.source_id IS NULL OR NOT (af.chromaprint = '' " +
            "AND af.algorithm_version >= :algorithmVersion " +
            "AND af.last_error LIKE 'FINGERPRINT_%' AND af.last_attempt_at > :retryAfter)) " +
            "ORDER BY COALESCE(af.last_attempt_at, 0), ts.source_id LIMIT :limit"
    )
    fun sourcesNeedingAudioFingerprint(
        algorithmVersion: Int,
        retryAfter: Long,
        limit: Int
    ): List<TrackSourceMappingEntity>

    @Query(
        "UPDATE audio_features SET pcm_hash = :pcmHash, chromaprint = :chromaprint, " +
            "algorithm_version = :algorithmVersion, last_attempt_at = :updatedAt, " +
            "last_error = '', updated_at = :updatedAt " +
            "WHERE source_id = :sourceId AND content_signature = :contentSignature"
    )
    fun updateAudioFingerprintIfCurrent(
        sourceId: Long,
        contentSignature: String,
        pcmHash: String,
        chromaprint: String,
        algorithmVersion: Int,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE audio_features SET algorithm_version = MAX(algorithm_version, :algorithmVersion), " +
            "last_attempt_at = :attemptedAt, last_error = :errorCode, updated_at = :attemptedAt " +
            "WHERE source_id = :sourceId AND content_signature = :contentSignature"
    )
    fun recordAudioFingerprintFailureIfCurrent(
        sourceId: Long,
        contentSignature: String,
        algorithmVersion: Int,
        errorCode: String,
        attemptedAt: Long
    ): Int

    @Query(
        "UPDATE audio_features SET updated_at = :updatedAt " +
            "WHERE source_id = :sourceId AND content_signature = :contentSignature"
    )
    fun touchAudioFeatureIfCurrent(
        sourceId: Long,
        contentSignature: String,
        updatedAt: Long
    ): Int

    @Query(
        "SELECT * FROM track_sources " +
            "WHERE provider = :provider AND provider_track_id = :providerTrackId LIMIT 1"
    )
    fun source(provider: String, providerTrackId: String): TrackSourceMappingEntity?

    @Query(
        "SELECT * FROM track_sources WHERE data_path = :dataPath " +
            "ORDER BY CASE WHEN local_track_id IS NULL THEN 1 ELSE 0 END, " +
            "last_verified_at DESC LIMIT 1"
    )
    fun sourceForDataPath(dataPath: String): TrackSourceMappingEntity?

    @Query("SELECT * FROM track_sources WHERE recording_id = :recordingId")
    fun sources(recordingId: Long): List<TrackSourceMappingEntity>

    @Query("SELECT * FROM track_sources WHERE provider = :provider ORDER BY recording_id, source_id")
    fun sourcesForProvider(provider: String): List<TrackSourceMappingEntity>

    @Query("SELECT * FROM track_sources WHERE source_id > :afterSourceId ORDER BY source_id LIMIT :limit")
    fun sourcesAfter(afterSourceId: Long, limit: Int): List<TrackSourceMappingEntity>

    @Query(
        "UPDATE track_sources SET provider = :normalizedProvider " +
            "WHERE source_id = :sourceId AND provider = :expectedProvider"
    )
    fun normalizeSourceProvider(
        sourceId: Long,
        expectedProvider: String,
        normalizedProvider: String
    ): Int

    @Query("DELETE FROM track_sources WHERE provider = :provider")
    fun deleteSourcesForProvider(provider: String): Int

    @Query("DELETE FROM identity_candidates WHERE provider = :provider")
    fun deleteCandidatesForProvider(provider: String): Int

    @Query(
        "SELECT COUNT(*) FROM identity_candidates WHERE target_type = 'RECORDING' " +
            "AND status = 'PENDING'"
    )
    fun pendingRecordingCandidateCount(): Int

    @Query(
        "SELECT t.* FROM tracks t LEFT JOIN track_sources s ON s.local_track_id = t.id " +
            "WHERE s.source_id IS NULL ORDER BY t.id"
    )
    fun tracksWithoutIdentitySource(): List<TrackEntity>

    @Query(
        "SELECT t.* FROM tracks t LEFT JOIN track_sources s ON s.local_track_id = t.id " +
            "WHERE s.source_id IS NULL ORDER BY t.id LIMIT :limit"
    )
    fun tracksWithoutIdentitySource(limit: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM track_sources WHERE local_track_id IS NOT NULL " +
            "AND provider IN ('local', 'document', 'webdav') " +
            "ORDER BY recording_id, source_id"
    )
    fun physicalSources(): List<TrackSourceMappingEntity>

    @Query(
        "SELECT * FROM track_sources WHERE match_status = 'CONFIRMED' " +
            "AND provider IN ('local', 'document', 'webdav', 'netease', 'qqmusic') " +
            "ORDER BY recording_id, source_id"
    )
    fun identityAnchorSources(): List<TrackSourceMappingEntity>

    @Query(
        "SELECT DISTINCT s.local_track_id FROM track_sources s " +
            "LEFT JOIN source_match_features f ON f.source_id = s.source_id " +
        "WHERE s.match_status != 'REJECTED' AND s.local_track_id IS NOT NULL AND (" +
            "f.source_id IS NULL OR f.algorithm_version != :featureAlgorithmVersion OR " +
            "f.candidate_algorithm_version != :candidateAlgorithmVersion) " +
            "ORDER BY s.local_track_id"
    )
    fun pendingLibraryIdentityTrackIds(
        featureAlgorithmVersion: Int,
        candidateAlgorithmVersion: Int
    ): List<Long>

    @Query(
        "SELECT * FROM track_sources WHERE match_status != 'REJECTED' " +
            "ORDER BY recording_id, source_id"
    )
    fun matchFeatureSources(): List<TrackSourceMappingEntity>

    @Query("SELECT * FROM source_match_features")
    fun sourceMatchFeatures(): List<SourceMatchFeatureEntity>

    @Query(
        "SELECT * FROM source_recording_candidates " +
            "ORDER BY source_id, coarse_score DESC, candidate_recording_id"
    )
    fun sourceRecordingCandidates(): List<SourceRecordingCandidateEntity>

    @Query(
        "SELECT * FROM source_recording_candidates WHERE " +
            "candidate_recording_id IN (:recordingIds) OR source_id IN (:sourceIds)"
    )
    fun sourceRecordingCandidates(
        recordingIds: List<Long>,
        sourceIds: List<Long>
    ): List<SourceRecordingCandidateEntity>

    @Query(
        "SELECT * FROM recording_relations WHERE left_recording_id = :leftRecordingId " +
            "AND right_recording_id = :rightRecordingId LIMIT 1"
    )
    fun recordingRelation(
        leftRecordingId: Long,
        rightRecordingId: Long
    ): RecordingRelationEntity?

    @Query(
        "SELECT * FROM recording_relations WHERE left_recording_id = :recordingId " +
            "OR right_recording_id = :recordingId ORDER BY updated_at DESC, " +
            "left_recording_id, right_recording_id"
    )
    fun recordingRelations(recordingId: Long): List<RecordingRelationEntity>

    @Query(
        "SELECT * FROM recording_relations WHERE left_recording_id IN (:recordingIds) " +
            "OR right_recording_id IN (:recordingIds) ORDER BY left_recording_id, right_recording_id"
    )
    fun recordingRelations(recordingIds: List<Long>): List<RecordingRelationEntity>

    @Query(
        "SELECT s.* FROM track_sources s INNER JOIN recordings r ON r.id = s.recording_id " +
            "WHERE s.recording_id = :recordingId ORDER BY " +
            "CASE WHEN s.source_id = r.active_source_id THEN 1 ELSE 0 END DESC, " +
            "CASE s.match_status WHEN 'CONFIRMED' THEN 3 WHEN 'CANDIDATE' THEN 2 ELSE 1 END DESC, " +
            "s.playable DESC, CASE s.provider WHEN 'local' THEN 600 WHEN 'webdav' THEN 500 " +
            "WHEN 'document' THEN 450 WHEN 'stream' THEN 400 WHEN 'netease' THEN 300 " +
            "WHEN 'qqmusic' THEN 250 WHEN 'luoxue' THEN 200 ELSE 100 END DESC, " +
            "s.quality_score DESC, s.last_successful_at DESC, s.source_id LIMIT :limit OFFSET :offset"
    )
    fun sourcePage(recordingId: Long, limit: Int, offset: Int): List<TrackSourceMappingEntity>

    @Query(
        "SELECT * FROM track_sources WHERE recording_id = :recordingId AND provider = :provider " +
            "AND playable = 1 AND match_status != 'REJECTED' " +
            "ORDER BY CASE match_status WHEN 'CONFIRMED' THEN 3 " +
            "WHEN 'CANDIDATE' THEN 2 ELSE 1 END DESC, confidence DESC, last_verified_at DESC LIMIT 1"
    )
    fun bestProviderSource(recordingId: Long, provider: String): TrackSourceMappingEntity?

    @Query(
        "SELECT * FROM track_sources WHERE recording_id = :recordingId AND provider = :provider " +
            "AND match_status != 'REJECTED' " +
            "ORDER BY CASE match_status WHEN 'CONFIRMED' THEN 3 WHEN 'CANDIDATE' THEN 2 ELSE 1 END DESC, " +
            "confidence DESC, last_verified_at DESC, source_id LIMIT 1"
    )
    fun bestProviderMatch(recordingId: Long, provider: String): TrackSourceMappingEntity?

    @Query("SELECT * FROM track_sources WHERE source_id = :sourceId LIMIT 1")
    fun source(sourceId: Long): TrackSourceMappingEntity?

    @Query(
        "UPDATE track_sources SET match_status = 'CONFIRMED', confidence = 1.0 " +
            "WHERE source_id = :sourceId"
    )
    fun confirmDirectProviderSource(sourceId: Long): Int

    @Query(
        "UPDATE track_sources SET match_status = 'REJECTED', playable = 0 " +
            "WHERE source_id = :sourceId AND match_status != 'CONFIRMED'"
    )
    fun rejectUnconfirmedProviderSource(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM track_sources WHERE recording_id = :recordingId")
    fun sourceCount(recordingId: Long): Int

    @Query(
        "SELECT s.* FROM recordings r INNER JOIN track_sources s ON s.source_id = r.active_source_id " +
            "WHERE r.id = :recordingId AND s.recording_id = r.id LIMIT 1"
    )
    fun activeSource(recordingId: Long): TrackSourceMappingEntity?

    @Query(
        "SELECT * FROM identity_candidates WHERE target_type = :targetType " +
            "AND target_id = :targetId ORDER BY score DESC, updated_at DESC"
    )
    fun candidates(targetType: String, targetId: Long): List<IdentityCandidateEntity>

    @Query(
        "DELETE FROM identity_candidates WHERE target_type = 'RECORDING' AND status = 'PENDING' " +
            "AND EXISTS (SELECT 1 FROM track_sources s " +
            "WHERE s.recording_id = identity_candidates.target_id " +
            "AND s.provider = identity_candidates.provider " +
            "AND s.provider_track_id = identity_candidates.provider_item_id)"
    )
    fun deleteSelfOwnedPendingRecordingCandidates(): Int

    @Query(
        "SELECT * FROM identity_candidates WHERE target_type = :targetType AND target_id = :targetId " +
            "AND status = :status AND provider_item_id != '__stored_match__' " +
            "ORDER BY score DESC, updated_at DESC LIMIT :limit OFFSET :offset"
    )
    fun candidatePage(
        targetType: String,
        targetId: Long,
        status: String,
        limit: Int,
        offset: Int
    ): List<IdentityCandidateEntity>

    @Query(
        "SELECT COUNT(*) FROM identity_candidates WHERE target_type = :targetType " +
            "AND target_id = :targetId AND status = :status " +
            "AND provider_item_id != '__stored_match__'"
    )
    fun candidateCount(targetType: String, targetId: Long, status: String): Int

    @Query("SELECT * FROM identity_candidates WHERE candidate_id = :candidateId LIMIT 1")
    fun candidate(candidateId: String): IdentityCandidateEntity?

    @Query(
        "SELECT * FROM identity_candidates WHERE target_type = :targetType AND target_id = :targetId " +
            "AND provider = :provider AND provider_item_id = :providerItemId LIMIT 1"
    )
    fun candidate(
        targetType: String,
        targetId: Long,
        provider: String,
        providerItemId: String
    ): IdentityCandidateEntity?

    @Query("SELECT * FROM canonical_artists WHERE artist_uuid = :artistUuid LIMIT 1")
    fun canonicalArtist(artistUuid: String): CanonicalArtistEntity?

    @Query("SELECT * FROM canonical_artists WHERE id = :artistId LIMIT 1")
    fun artist(artistId: Long): CanonicalArtistEntity?

    @Query(
        "SELECT * FROM canonical_artists WHERE avatar_url = '' " +
            "ORDER BY id LIMIT :limit"
    )
    fun artistsMissingAvatar(limit: Int): List<CanonicalArtistEntity>

    @Query(
        "SELECT * FROM canonical_artists WHERE id != :excludedArtistId " +
            "ORDER BY display_name COLLATE NOCASE, id LIMIT :limit"
    )
    fun otherArtists(excludedArtistId: Long, limit: Int): List<CanonicalArtistEntity>

    @Query(
        "SELECT a.* FROM canonical_artists a INNER JOIN artist_source_mappings m " +
        "ON m.artist_id = a.id WHERE m.provider = :provider " +
            "AND m.provider_artist_id = :providerArtistId " +
            "AND m.status = 'CONFIRMED' LIMIT 1"
    )
    fun artistForProvider(provider: String, providerArtistId: String): CanonicalArtistEntity?

    @Query(
        "SELECT a.* FROM canonical_artists a INNER JOIN artist_aliases x " +
        "ON x.artist_id = a.id WHERE x.normalized_alias = :normalizedAlias " +
            "ORDER BY a.created_at, a.id"
    )
    fun artistsForNormalizedAlias(normalizedAlias: String): List<CanonicalArtistEntity>

    @Query(
        "SELECT * FROM artist_aliases WHERE artist_id = :artistId " +
            "ORDER BY confidence DESC, alias"
    )
    fun aliases(artistId: Long): List<ArtistAliasEntity>

    @Query("SELECT * FROM artist_source_mappings WHERE artist_id = :artistId")
    fun artistMappings(artistId: Long): List<ArtistSourceMappingEntity>

    @Query("SELECT * FROM artist_source_mappings WHERE mapping_id = :mappingId LIMIT 1")
    fun artistMapping(mappingId: Long): ArtistSourceMappingEntity?

    @Query("SELECT * FROM canonical_albums WHERE id = :albumId LIMIT 1")
    fun album(albumId: Long): CanonicalAlbumEntity?

    @Query("SELECT * FROM canonical_albums WHERE album_uuid = :albumUuid LIMIT 1")
    fun canonicalAlbum(albumUuid: String): CanonicalAlbumEntity?

    @Query("SELECT * FROM canonical_albums WHERE identity_key = :identityKey LIMIT 1")
    fun albumForIdentity(identityKey: String): CanonicalAlbumEntity?

    @Query(
        "SELECT a.* FROM canonical_albums a INNER JOIN album_source_mappings m " +
            "ON m.album_id = a.id WHERE m.provider = :provider " +
            "AND m.provider_album_id = :providerAlbumId LIMIT 1"
    )
    fun albumForProvider(provider: String, providerAlbumId: String): CanonicalAlbumEntity?

    @Query(
        "SELECT * FROM canonical_albums WHERE musicbrainz_release_group_id = :releaseGroupMbid " +
            "AND musicbrainz_release_group_id != '' LIMIT 1"
    )
    fun albumForReleaseGroup(releaseGroupMbid: String): CanonicalAlbumEntity?

    @Query(
        "SELECT * FROM canonical_albums WHERE musicbrainz_release_id = :releaseMbid " +
            "AND musicbrainz_release_id != '' LIMIT 1"
    )
    fun albumForRelease(releaseMbid: String): CanonicalAlbumEntity?

    @Query(
        "SELECT a.* FROM canonical_albums a INNER JOIN album_aliases x ON x.album_id = a.id " +
            "WHERE x.normalized_alias = :normalizedAlias ORDER BY a.created_at, a.id"
    )
    fun albumsForNormalizedAlias(normalizedAlias: String): List<CanonicalAlbumEntity>

    @Query(
        "SELECT * FROM album_aliases WHERE album_id = :albumId " +
            "ORDER BY confidence DESC, alias"
    )
    fun albumAliases(albumId: Long): List<AlbumAliasEntity>

    @Query("SELECT * FROM album_source_mappings WHERE album_id = :albumId")
    fun albumMappings(albumId: Long): List<AlbumSourceMappingEntity>

    @Query(
        "SELECT * FROM recording_artist_credits WHERE recording_id = :recordingId " +
            "ORDER BY position, role, artist_id"
    )
    fun credits(recordingId: Long): List<RecordingArtistCreditEntity>

    @Query(
        "SELECT * FROM recording_artist_credits WHERE artist_id = :artistId " +
            "ORDER BY recording_id, position, role"
    )
    fun creditsForArtist(artistId: Long): List<RecordingArtistCreditEntity>

    @Query("SELECT COUNT(*) FROM recording_artist_credits WHERE recording_id = :recordingId")
    fun creditCount(recordingId: Long): Int

    @Query(
        "SELECT * FROM recording_identifiers WHERE identifier_type = :type " +
            "AND namespace = :namespace AND identifier_value = :value LIMIT 1"
    )
    fun identifier(type: String, namespace: String, value: String): RecordingIdentifierEntity?

    @Query("SELECT * FROM recording_identifiers WHERE recording_id = :recordingId")
    fun identifiers(recordingId: Long): List<RecordingIdentifierEntity>

    @Query("SELECT * FROM recording_variants WHERE recording_id = :recordingId")
    fun variants(recordingId: Long): List<RecordingVariantEntity>

    @Query("SELECT * FROM lyric_bindings WHERE recording_id = :recordingId ORDER BY updated_at DESC")
    fun lyricBindings(recordingId: Long): List<LyricBindingEntity>

    @Query("SELECT * FROM custom_lyrics WHERE identity_key = :identityKey LIMIT 1")
    fun customLyrics(identityKey: String): CustomLyricsEntity?

    @Query("SELECT * FROM custom_lyrics WHERE recording_id = :recordingId ORDER BY updated_at DESC")
    fun customLyricsForRecording(recordingId: Long): List<CustomLyricsEntity>

    @Query(
        "SELECT * FROM custom_lyrics WHERE provider = :provider " +
            "AND provider_track_id = :providerTrackId ORDER BY updated_at DESC LIMIT 1"
    )
    fun customLyricsForProvider(provider: String, providerTrackId: String): CustomLyricsEntity?

    @Query("SELECT * FROM identity_resolution_jobs WHERE job_id = :jobId LIMIT 1")
    fun job(jobId: String): IdentityResolutionJobEntity?

    @Query(
        "SELECT * FROM identity_resolution_jobs WHERE status IN ('PENDING','RETRY') " +
            "AND next_attempt_at <= :now ORDER BY priority DESC, created_at, job_id LIMIT :limit"
    )
    fun readyJobs(now: Long, limit: Int): List<IdentityResolutionJobEntity>

    @Query(
        "DELETE FROM identity_resolution_jobs WHERE status = 'RETRY' AND EXISTS (" +
            "SELECT 1 FROM identity_resolution_jobs running " +
            "WHERE running.status = 'RUNNING' AND running.updated_at <= :staleBefore " +
            "AND running.target_type = identity_resolution_jobs.target_type " +
            "AND running.target_id = identity_resolution_jobs.target_id)"
    )
    fun deleteRetryPeersForStaleRunningJobs(staleBefore: Long): Int

    @Query(
        "UPDATE identity_resolution_jobs SET status = 'RETRY', " +
            "attempt_count = attempt_count + 1, next_attempt_at = :now, " +
            "last_error = 'STALE_RUNNING_RECOVERED', updated_at = :now " +
            "WHERE status = 'RUNNING' AND updated_at <= :staleBefore"
    )
    fun recoverStaleRunningJobs(staleBefore: Long, now: Long): Int

    @Query(
        "UPDATE identity_resolution_jobs SET status = 'RETRY', next_attempt_at = :now, " +
            "attempt_count = 0, last_error = '', updated_at = :now " +
            "WHERE status = 'FAILED' AND target_type = 'ARTIST' AND updated_at <= :failedBefore"
    )
    fun recoverExpiredFailedArtistJobs(failedBefore: Long, now: Long): Int

    @Query(
        "SELECT * FROM identity_resolution_jobs WHERE target_type = :targetType " +
            "AND target_id = :targetId ORDER BY created_at, job_id"
    )
    fun jobs(targetType: String, targetId: Long): List<IdentityResolutionJobEntity>

    @Query(
        "SELECT * FROM identity_operations WHERE (" +
            "source_recording_id = :recordingId OR target_recording_id = :recordingId) " +
            "AND operation_type != 'MANUAL_MATCH_DECISION' " +
            "ORDER BY created_at DESC, id DESC LIMIT :limit"
    )
    fun identityOperations(recordingId: Long, limit: Int): List<IdentityOperationEntity>

    @Query(
        "SELECT * FROM identity_operations WHERE operation_type = :operationType " +
            "AND reverted_at IS NULL ORDER BY created_at, id LIMIT :limit"
    )
    fun identityOperationsByType(operationType: String, limit: Int): List<IdentityOperationEntity>

    @Query(
        "SELECT COUNT(*) FROM identity_operations WHERE reverted_at IS NULL " +
            "AND operation_type = 'SPLIT_RECORDING' " +
            "AND (source_recording_id = :recordingId OR target_recording_id = :recordingId)"
    )
    fun activeSplitOperationCount(recordingId: Long): Int

    @Query("SELECT * FROM identity_operations WHERE id = :operationId LIMIT 1")
    fun identityOperation(operationId: Long): IdentityOperationEntity?

    @Query(
        "SELECT * FROM identity_operations WHERE dedup_mode = 'AGGRESSIVE' " +
            "AND rollback_status = 'ELIGIBLE' AND reverted_at IS NULL " +
            "ORDER BY id DESC LIMIT :limit"
    )
    fun aggressiveDedupRollbackOperations(limit: Int): List<IdentityOperationEntity>

    @Query("UPDATE identity_operations SET rollback_status = :status WHERE id = :operationId")
    fun updateIdentityOperationRollbackStatus(operationId: Long, status: String): Int

    @Query(
        "SELECT COUNT(*) FROM identity_operations WHERE id > :operationId AND (" +
            "source_recording_id IN (:recordingIds) OR target_recording_id IN (:recordingIds)) " +
            "AND operation_type != 'MANUAL_MATCH_DECISION'"
    )
    fun newerIdentityOperationCount(operationId: Long, recordingIds: List<Long>): Int

    @Query(
        "SELECT * FROM provider_response_cache WHERE provider = :provider " +
            "AND endpoint = :endpoint AND request_hash = :requestHash LIMIT 1"
    )
    fun providerCache(provider: String, endpoint: String, requestHash: String): ProviderResponseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(recording: CanonicalRecordingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: CanonicalArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: CanonicalAlbumEntity): Long

    @Update
    fun update(recording: CanonicalRecordingEntity): Int

    @Update
    fun update(artist: CanonicalArtistEntity): Int

    @Update
    fun update(album: CanonicalAlbumEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(alias: ArtistAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(mapping: ArtistSourceMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(alias: AlbumAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(mapping: AlbumSourceMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(credit: RecordingArtistCreditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(credit: WorkArtistCreditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(identifier: WorkIdentifierEntity)

    @Upsert
    fun upsert(source: TrackSourceMappingEntity)

    @Query("UPDATE track_sources SET recording_id = :recordingId WHERE source_id = :sourceId")
    fun moveSourceToRecording(sourceId: Long, recordingId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSourceMatchFeatures(features: List<SourceMatchFeatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSourceRecordingCandidates(candidates: List<SourceRecordingCandidateEntity>)

    @Query(
        "UPDATE source_recording_candidates SET evidence_json = :evidenceJson, " +
            "updated_at = :updatedAt WHERE source_id = :sourceId " +
            "AND candidate_recording_id = :candidateRecordingId AND state = 'SHADOW'"
    )
    fun updateShadowCandidateEvidence(
        sourceId: Long,
        candidateRecordingId: Long,
        evidenceJson: String,
        updatedAt: Long
    ): Int

    @Upsert
    fun upsertRecordingRelation(relation: RecordingRelationEntity)

    @Upsert
    fun upsertRecordingRelations(relations: List<RecordingRelationEntity>)

    @Query("DELETE FROM source_recording_candidates")
    fun clearSourceRecordingCandidates(): Int

    @Query(
        "DELETE FROM source_recording_candidates WHERE " +
            "candidate_recording_id IN (:recordingIds) OR source_id IN (:sourceIds)"
    )
    fun deleteSourceRecordingCandidates(
        recordingIds: List<Long>,
        sourceIds: List<Long>
    ): Int

    @Query(
        "UPDATE source_match_features SET candidate_algorithm_version = 0, " +
            "candidate_snapshot_signature = '', candidate_generated_at = 0 " +
            "WHERE source_id IN (:sourceIds)"
    )
    fun invalidateSourceCandidateGeneration(sourceIds: List<Long>): Int

    @Query(
        "DELETE FROM recording_relations WHERE left_recording_id = :recordingId " +
            "OR right_recording_id = :recordingId"
    )
    fun deleteRecordingRelations(recordingId: Long): Int

    @Query(
        "UPDATE source_match_features SET candidate_algorithm_version = :algorithmVersion, " +
            "candidate_snapshot_signature = :snapshotSignature, " +
            "candidate_generated_at = :generatedAt WHERE source_id IN (:sourceIds)"
    )
    fun markSourceCandidateGeneration(
        sourceIds: List<Long>,
        algorithmVersion: Int,
        snapshotSignature: String,
        generatedAt: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertIdentifier(identifier: RecordingIdentifierEntity)

    @Update
    fun updateIdentifier(identifier: RecordingIdentifierEntity): Int

    @Query(
        "UPDATE recording_identifiers SET recording_id = :targetRecordingId " +
            "WHERE recording_id = :sourceRecordingId"
    )
    fun moveIdentifiersForConfirmedMerge(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): Int

    /**
     * A globally stable identifier must never change recording ownership as a side effect of
     * SQLite REPLACE. Callers must explicitly merge recordings before moving an identifier.
     */
    @Transaction
    fun upsert(identifier: RecordingIdentifierEntity) {
        val existing = identifier(
            identifier.identifierType,
            identifier.namespace,
            identifier.identifierValue
        )
        when {
            existing == null -> insertIdentifier(identifier)
            existing.recordingId != identifier.recordingId -> {
                throw IllegalArgumentException(
                    "Recording identifier already belongs to recording ${existing.recordingId}"
                )
            }
            updateIdentifier(identifier) != 1 -> {
                throw IllegalStateException("Recording identifier disappeared during update")
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(variant: RecordingVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(candidate: IdentityCandidateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(job: IdentityResolutionJobEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertJob(job: IdentityResolutionJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(cache: ProviderResponseCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(binding: LyricBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(customLyrics: CustomLyricsEntity)

    @Insert
    fun insert(operation: IdentityOperationEntity): Long

    @Query("DELETE FROM track_sources WHERE source_id = :sourceId")
    fun deleteSource(sourceId: Long): Int

    @Query(
        "DELETE FROM recording_artist_credits WHERE recording_id = :recordingId " +
            "AND artist_id = :artistId AND role = :role AND position = :position"
    )
    fun deleteCredit(recordingId: Long, artistId: Long, role: String, position: Int): Int

    @Query("DELETE FROM recording_artist_credits WHERE recording_id = :recordingId")
    fun deleteCredits(recordingId: Long): Int

    @Query("DELETE FROM work_artist_credits WHERE work_id = :workId")
    fun deleteWorkCredits(workId: Long): Int

    @Query("DELETE FROM work_identifiers WHERE work_id = :workId")
    fun deleteWorkIdentifiers(workId: Long): Int

    @Query("DELETE FROM recording_identifiers WHERE recording_id = :recordingId")
    fun deleteIdentifiers(recordingId: Long): Int

    @Query("DELETE FROM artist_aliases WHERE artist_id = :artistId")
    fun deleteAliases(artistId: Long): Int

    @Query("DELETE FROM artist_source_mappings WHERE mapping_id = :mappingId")
    fun deleteArtistMapping(mappingId: Long): Int

    @Query("DELETE FROM identity_candidates WHERE candidate_id = :candidateId")
    fun deleteCandidate(candidateId: String): Int

    @Query("DELETE FROM identity_candidates WHERE target_type = :targetType AND target_id = :targetId")
    fun deleteCandidates(targetType: String, targetId: Long): Int

    @Query("DELETE FROM identity_resolution_jobs WHERE target_type = :targetType AND target_id = :targetId")
    fun deleteJobs(targetType: String, targetId: Long): Int

    @Query("DELETE FROM recording_variants WHERE recording_id = :recordingId")
    fun deleteVariants(recordingId: Long): Int

    @Query("DELETE FROM lyric_bindings WHERE recording_id = :recordingId")
    fun deleteLyricBindings(recordingId: Long): Int

    @Query("DELETE FROM custom_lyrics WHERE identity_key = :identityKey")
    fun deleteCustomLyrics(identityKey: String): Int

    @Query("DELETE FROM custom_lyrics WHERE recording_id = :recordingId")
    fun deleteCustomLyricsForRecording(recordingId: Long): Int

    @Query(
        "DELETE FROM custom_lyrics WHERE provider = :provider " +
            "AND provider_track_id = :providerTrackId"
    )
    fun deleteCustomLyricsForProvider(provider: String, providerTrackId: String): Int

    @Query(
        "UPDATE identity_operations SET reverted_at = :revertedAt " +
            "WHERE id = :operationId AND reverted_at IS NULL"
    )
    fun markIdentityOperationReverted(operationId: Long, revertedAt: Long): Int

    @Query("DELETE FROM recordings WHERE id = :recordingId")
    fun deleteRecording(recordingId: Long): Int

    @Query("DELETE FROM canonical_artists WHERE id = :artistId")
    fun deleteArtist(artistId: Long): Int

    @Query(
        "UPDATE identity_candidates SET status = :status, updated_at = :updatedAt " +
            "WHERE candidate_id = :candidateId"
    )
    fun updateCandidateStatus(candidateId: String, status: String, updatedAt: Long): Int

    @Query(
        "UPDATE identity_resolution_jobs SET status = 'RUNNING', updated_at = :now " +
            "WHERE job_id = :jobId AND status IN ('PENDING','RETRY') AND next_attempt_at <= :now"
    )
    fun claimJob(jobId: String, now: Long): Int

    @Query(
        "UPDATE identity_resolution_jobs SET status = :status, attempt_count = :attemptCount, " +
            "next_attempt_at = :nextAttemptAt, last_error = :lastError, updated_at = :updatedAt " +
            "WHERE job_id = :jobId"
    )
    fun updateJobStatus(
        jobId: String,
        status: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        lastError: String,
        updatedAt: Long
    ): Int

    @Query(
        "DELETE FROM identity_resolution_jobs WHERE target_type = :targetType " +
            "AND target_id = :targetId AND status = :status AND job_id != :jobId"
    )
    fun deleteJobStatusPeers(
        targetType: String,
        targetId: Long,
        status: String,
        jobId: String
    ): Int

    @Query(
        "UPDATE track_sources SET local_track_id = NULL WHERE local_track_id IS NOT NULL " +
            "AND local_track_id NOT IN (SELECT id FROM tracks) " +
            "AND recording_id IN (SELECT recording_id FROM favorites)"
    )
    fun detachFavoriteSourcesForMissingTracks(): Int

    @Query(
        "DELETE FROM track_sources WHERE local_track_id IS NOT NULL " +
            "AND local_track_id NOT IN (SELECT id FROM tracks) " +
            "AND recording_id NOT IN (SELECT recording_id FROM favorites)"
    )
    fun deleteSourcesForMissingTracks(): Int

    @Query(
        "DELETE FROM recordings WHERE id NOT IN (SELECT recording_id FROM track_sources) " +
            "AND id NOT IN (SELECT recording_id FROM favorites)"
    )
    fun deleteOrphanCanonicalRecordings(): Int

    @Query(
        "DELETE FROM canonical_artists WHERE " +
            "id NOT IN (SELECT artist_id FROM recording_artist_credits) " +
            "AND id NOT IN (SELECT artist_id FROM artist_source_mappings)"
    )
    fun deleteOrphanArtists(): Int

    @Query(
        "DELETE FROM identity_candidates WHERE " +
            "(target_type = 'RECORDING' AND target_id NOT IN (SELECT id FROM recordings)) OR " +
            "(target_type = 'ARTIST' AND target_id NOT IN (SELECT id FROM canonical_artists))"
    )
    fun deleteDanglingCandidates(): Int

    @Query(
        "DELETE FROM identity_resolution_jobs WHERE " +
            "(target_type = 'RECORDING' AND target_id NOT IN (SELECT id FROM recordings)) OR " +
            "(target_type = 'ARTIST' AND target_id NOT IN (SELECT id FROM canonical_artists))"
    )
    fun deleteDanglingJobs(): Int

    @Query(
        "UPDATE recordings SET active_source_id = NULL WHERE active_source_id IS NOT NULL " +
            "AND active_source_id NOT IN (SELECT source_id FROM track_sources)"
    )
    fun clearMissingActiveSources(): Int

    @Query(
        "SELECT * FROM track_sources WHERE recording_id = :recordingId " +
            "AND playable = 1 AND match_status = 'CONFIRMED' " +
            "AND provider IN ('local', 'document', 'webdav', 'netease') " +
            "AND (provider IN ('local', 'document', 'webdav') OR last_verified_at > 0 OR last_successful_at > 0)"
    )
    fun eligibleActiveSources(recordingId: Long): List<TrackSourceMappingEntity>

    @Query("UPDATE recordings SET active_source_id = :sourceId WHERE id = :recordingId")
    fun updateActiveSource(recordingId: Long, sourceId: Long?): Int

    @Transaction
    fun refreshActiveSource(recordingId: Long): Int {
        val winner = PlaybackSourceSelectionEvaluator.rank(
            eligibleActiveSources(recordingId).mapNotNull { source ->
                val sourceId = source.sourceId ?: return@mapNotNull null
                PlaybackSourceSelectionFeatures(
                    sourceId = sourceId,
                    provider = source.provider,
                    playable = source.playable,
                    confirmed = source.matchStatus == "CONFIRMED",
                    qualityScore = source.qualityScore,
                    lastSuccessfulAt = source.lastSuccessfulAt,
                    lastVerifiedAt = source.lastVerifiedAt,
                    failureCount = source.failureCount
                )
            }
        ).firstOrNull()?.source?.sourceId
        return updateActiveSource(recordingId, winner)
    }

    @Query(
        "UPDATE track_sources SET playable = 0, last_verified_at = :verifiedAt " +
            "WHERE source_id = :sourceId"
    )
    fun markSourceUnavailable(sourceId: Long, verifiedAt: Long): Int

    @Query(
        "UPDATE track_sources SET playable = 1, last_verified_at = :verifiedAt, " +
            "last_successful_at = :verifiedAt, last_failure_at = 0, failure_reason = '', " +
            "failure_count = 0, codec = CASE WHEN :codec = '' THEN codec ELSE :codec END, " +
            "bitrate_kbps = CASE WHEN :bitrateKbps <= 0 THEN bitrate_kbps ELSE :bitrateKbps END " +
            "WHERE source_id = :sourceId"
    )
    fun markSourceVerifiedSuccess(
        sourceId: Long,
        verifiedAt: Long,
        codec: String,
        bitrateKbps: Int
    ): Int

    @Query(
        "UPDATE track_sources SET playable = 0, last_verified_at = :failedAt, " +
            "last_failure_at = :failedAt, failure_reason = :reason, " +
            "failure_count = failure_count + 1 WHERE source_id = :sourceId"
    )
    fun markSourceVerificationFailure(sourceId: Long, failedAt: Long, reason: String): Int

    @Query(
        "UPDATE track_sources SET playable = CASE WHEN :disableSource = 1 THEN 0 ELSE playable END, " +
            "last_failure_at = :failedAt, failure_reason = :reason, " +
            "failure_count = CASE WHEN :resetCount = 1 THEN 1 ELSE failure_count + 1 END " +
            "WHERE source_id = :sourceId"
    )
    fun recordSourcePlaybackFailure(
        sourceId: Long,
        failedAt: Long,
        reason: String,
        resetCount: Boolean,
        disableSource: Boolean
    ): Int

    @Query(
        "UPDATE recordings SET active_source_id = :sourceId WHERE id = :recordingId AND EXISTS (" +
            "SELECT 1 FROM track_sources WHERE source_id = :sourceId AND recording_id = :recordingId " +
            "AND playable = 1 AND match_status = 'CONFIRMED' " +
            "AND provider IN ('local', 'document', 'webdav', 'netease') " +
            "AND (provider IN ('local', 'document', 'webdav') OR last_verified_at > 0 OR last_successful_at > 0))"
    )
    fun setActiveSource(recordingId: Long, sourceId: Long): Int
}

data class TrackArtistIdentityRow(
    val localTrackId: Long,
    val artistKey: Long,
    val artistId: String,
    val displayName: String,
    val creditedName: String,
    val avatarUrl: String,
    val role: String,
    val position: Int
)

data class TrackMergeIdentityRow(
    val localTrackId: Long,
    val mergeIdentity: String
)

data class TrackRecordingIdentityRow(
    val localTrackId: Long,
    val recordingId: Long
)

data class TrackStreamingMatchRow(
    val localTrackId: Long,
    val provider: String,
    val providerTrackId: String,
    val evidenceJson: String,
    val storedCandidate: Int,
    val statusRank: Int,
    val confidence: Double,
    val recency: Long
)

data class RecordingMergeSearchRow(
    val recordingId: Long,
    val canonicalUuid: String,
    val title: String,
    val primaryArtistDisplay: String,
    val durationMs: Long,
    val sourceCount: Int,
    val variantTypes: String
)
