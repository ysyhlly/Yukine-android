package app.yukine.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE, artist COLLATE NOCASE")
    fun loadTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    fun deleteAllTracks(): Int
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updated_at DESC, id DESC")
    fun loadPlaylists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Transaction
    fun replacePlaylistTracks(playlistId: Long, tracks: List<PlaylistTrackEntity>) {
        deletePlaylistTracks(playlistId)
        insertPlaylistTracks(tracks)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun deletePlaylistTracks(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertHistory(history: PlayHistoryEntity)

    @Insert
    fun insertEvent(event: PlayEventEntity): Long

    @Transaction
    fun recordPlay(history: PlayHistoryEntity, event: PlayEventEntity) {
        upsertHistory(history)
        insertEvent(event)
    }
}

@Dao
interface PlaybackPersistenceDao {
    @Query("SELECT * FROM playback_queue ORDER BY position")
    fun loadQueue(): List<PlaybackQueueEntity>

    @Query("DELETE FROM playback_queue")
    fun clearQueue(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQueue(tracks: List<PlaybackQueueEntity>)

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
    @Query("SELECT * FROM remote_sources ORDER BY updated_at DESC, id DESC")
    fun loadSources(): List<RemoteSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(source: RemoteSourceEntity): Long
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
