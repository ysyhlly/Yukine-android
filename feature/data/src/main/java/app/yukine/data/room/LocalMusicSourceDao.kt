package app.yukine.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

data class LocalMusicSourceRow(
    @ColumnInfo(name = "source_id") val sourceId: String,
    val type: String,
    @ColumnInfo(name = "root_uri") val rootUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val status: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface LocalMusicSourceDao {
    @Query(
        "SELECT s.source_id, s.type, s.root_uri, s.display_name, s.status, " +
            "COUNT(m.track_id) AS track_count, s.added_at, s.last_scan_at, s.updated_at " +
            "FROM local_music_sources s LEFT JOIN local_music_source_tracks m " +
            "ON m.source_id = s.source_id WHERE s.type = 'FOLDER' " +
            "GROUP BY s.source_id ORDER BY s.added_at ASC, s.display_name COLLATE NOCASE"
    )
    fun loadFolderRows(): List<LocalMusicSourceRow>

    @Query("SELECT * FROM local_music_sources WHERE source_id = :sourceId LIMIT 1")
    fun loadSource(sourceId: String): LocalMusicSourceEntity?

    @Query(
        "SELECT * FROM local_music_sources WHERE type = 'FOLDER' " +
            "ORDER BY added_at ASC, display_name COLLATE NOCASE"
    )
    fun loadFolderSources(): List<LocalMusicSourceEntity>

    @Upsert
    fun putSource(source: LocalMusicSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMappings(mappings: List<LocalMusicSourceTrackEntity>)

    @Query("SELECT track_id FROM local_music_source_tracks WHERE source_id = :sourceId")
    fun loadTrackIds(sourceId: String): List<Long>

    @Query("SELECT COUNT(*) FROM local_music_source_tracks WHERE track_id = :trackId")
    fun sourceCountForTrack(trackId: Long): Int

    @Query("DELETE FROM local_music_source_tracks WHERE source_id = :sourceId")
    fun deleteMappings(sourceId: String): Int

    @Query("DELETE FROM local_music_sources WHERE source_id = :sourceId")
    fun deleteSource(sourceId: String): Int
}
