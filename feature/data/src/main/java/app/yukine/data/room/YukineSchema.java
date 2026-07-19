package app.yukine.data.room;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Idempotent v14 schema normalization shared by every supported legacy migration. */
public final class YukineSchema {
    private YukineSchema() {
    }

    public static void normalizeV14(SupportSQLiteDatabase db) {
        createCoreTables(db);
        ensureTrackColumns(db);
        ensureColumn(db, "favorites", "created_at", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "play_history", "played_at", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "play_history", "play_count", "INTEGER NOT NULL DEFAULT 1");
        createPlayEvents(db);
        db.execSQL(
                "INSERT INTO play_events(track_id, played_at) "
                        + "SELECT track_id, played_at FROM play_history "
                        + "WHERE NOT EXISTS (SELECT 1 FROM play_events)"
        );
        createPlaylistTables(db);
        createSettings(db);
        createRemoteSources(db);
        createPlaybackQueue(db);
        ensurePlaybackQueueColumns(db);
        createStreamingTrackMatches(db);
        createLibraryExclusions(db);
        createIndexes(db);
    }

    public static void normalizeV15(SupportSQLiteDatabase db) {
        normalizeV14(db);
        requireNoNullPrimaryKey(db, "settings", "key");
        requireNoNullPrimaryKey(db, "library_exclusions", "source_key");
        db.execSQL("CREATE TABLE settings_room_v15 ("
                + "key TEXT NOT NULL PRIMARY KEY,"
                + "value TEXT NOT NULL"
                + ")");
        db.execSQL("INSERT INTO settings_room_v15(key, value) SELECT key, value FROM settings");
        db.execSQL("DROP TABLE settings");
        db.execSQL("ALTER TABLE settings_room_v15 RENAME TO settings");
        db.execSQL("CREATE TABLE library_exclusions_room_v15 ("
                + "source_key TEXT NOT NULL PRIMARY KEY,"
                + "content_uri TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("INSERT INTO library_exclusions_room_v15("
                + "source_key, content_uri, data_path, created_at) "
                + "SELECT source_key, content_uri, data_path, created_at FROM library_exclusions");
        db.execSQL("DROP TABLE library_exclusions");
        db.execSQL("ALTER TABLE library_exclusions_room_v15 RENAME TO library_exclusions");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_library_exclusions_created_at "
                + "ON library_exclusions (created_at DESC)");
    }

    public static void normalizeV16(SupportSQLiteDatabase db) {
        normalizeV15(db);
        createMusicIdentityTables(db);
        migrateLegacyStreamingMatches(db);
        seedOfflineTrackIdentities(db);
    }

    public static void normalizeV17(SupportSQLiteDatabase db) {
        normalizeV16(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_favorites ("
                + "recording_id INTEGER NOT NULL PRIMARY KEY,"
                + "created_at INTEGER NOT NULL,"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_favorites_created "
                + "ON recording_favorites (created_at)");
        db.execSQL("INSERT OR IGNORE INTO recording_favorites(recording_id,created_at) "
                + "SELECT s.recording_id,MIN(f.created_at) FROM favorites f "
                + "INNER JOIN track_sources s ON s.local_track_id=f.track_id "
                + "GROUP BY s.recording_id");
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_play_history ("
                + "recording_id INTEGER NOT NULL PRIMARY KEY,"
                + "representative_track_id INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL,"
                + "play_count INTEGER NOT NULL,"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_history_time "
                + "ON recording_play_history (played_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_play_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "recording_id INTEGER NOT NULL,"
                + "source_id INTEGER,"
                + "track_id_snapshot INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL,"
                + "legacy_event_id INTEGER,"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_events_time "
                + "ON recording_play_events (played_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_events_recording_time "
                + "ON recording_play_events (recording_id,played_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS playback_queue_identities ("
                + "position INTEGER NOT NULL PRIMARY KEY,"
                + "recording_id INTEGER NOT NULL,"
                + "preferred_source_id INTEGER,"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_queue_identity_recording "
                + "ON playback_queue_identities (recording_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_recording_items ("
                + "playlist_id INTEGER NOT NULL,"
                + "recording_id INTEGER NOT NULL,"
                + "representative_track_id INTEGER NOT NULL,"
                + "sort_key INTEGER NOT NULL,"
                + "added_at INTEGER NOT NULL,"
                + "PRIMARY KEY(playlist_id,recording_id),"
                + "FOREIGN KEY(playlist_id) REFERENCES playlists(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE,"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_recording_order "
                + "ON playlist_recording_items (playlist_id,sort_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_recording_recording "
                + "ON playlist_recording_items (recording_id)");
        db.execSQL("INSERT OR IGNORE INTO recording_play_history("
                + "recording_id,representative_track_id,played_at,play_count) "
                + "SELECT s.recording_id,MIN(h.track_id),MAX(h.played_at),SUM(h.play_count) "
                + "FROM play_history h INNER JOIN track_sources s ON s.local_track_id=h.track_id "
                + "GROUP BY s.recording_id");
        db.execSQL("INSERT INTO recording_play_events("
                + "recording_id,source_id,track_id_snapshot,played_at,legacy_event_id) "
                + "SELECT s.recording_id,s.source_id,e.track_id,e.played_at,e.id FROM play_events e "
                + "INNER JOIN track_sources s ON s.local_track_id=e.track_id "
                + "WHERE NOT EXISTS (SELECT 1 FROM recording_play_events)");
        db.execSQL("INSERT OR REPLACE INTO playback_queue_identities("
                + "position,recording_id,preferred_source_id) "
                + "SELECT q.position,s.recording_id,s.source_id FROM playback_queue q "
                + "INNER JOIN track_sources s ON s.local_track_id=q.track_id");
        db.execSQL("INSERT OR IGNORE INTO playlist_recording_items("
                + "playlist_id,recording_id,representative_track_id,sort_key,added_at) "
                + "SELECT pt.playlist_id,s.recording_id,pt.track_id,(pt.position + 1) * 1024,pt.added_at "
                + "FROM playlist_tracks pt INNER JOIN track_sources s ON s.local_track_id=pt.track_id "
                + "ORDER BY pt.playlist_id,pt.position,pt.added_at");
    }

    public static void normalizeV18(SupportSQLiteDatabase db) {
        normalizeV17(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS identity_operations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "operation_type TEXT NOT NULL,"
                + "source_recording_id INTEGER,"
                + "target_recording_id INTEGER,"
                + "before_payload TEXT NOT NULL DEFAULT '',"
                + "after_payload TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "reverted_at INTEGER"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_operations_source "
                + "ON identity_operations (source_recording_id,created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_operations_target "
                + "ON identity_operations (target_recording_id,created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_operations_created "
                + "ON identity_operations (created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_operations_reverted "
                + "ON identity_operations (reverted_at)");
    }

    public static void normalizeV19(SupportSQLiteDatabase db) {
        normalizeV18(db);
        ensureColumn(db, "track_sources", "codec", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "track_sources", "bitrate_kbps", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "track_sources", "last_failure_at", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "track_sources", "failure_reason", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "track_sources", "failure_count", "INTEGER NOT NULL DEFAULT 0");
    }

    public static void normalizeV20(SupportSQLiteDatabase db) {
        normalizeV19(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites_room_v20 ("
                + "recording_id INTEGER NOT NULL PRIMARY KEY,"
                + "created_at INTEGER NOT NULL,"
                + "sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY',"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("INSERT OR IGNORE INTO favorites_room_v20(recording_id,created_at,sync_state) "
                + "SELECT recording_id,created_at,'LOCAL_ONLY' FROM recording_favorites");
        db.execSQL("INSERT OR IGNORE INTO favorites_room_v20(recording_id,created_at,sync_state) "
                + "SELECT s.recording_id,MIN(f.created_at),'LOCAL_ONLY' FROM favorites f "
                + "INNER JOIN track_sources s ON s.local_track_id=f.track_id "
                + "GROUP BY s.recording_id");
        db.execSQL("DROP TABLE favorites");
        db.execSQL("DROP TABLE recording_favorites");
        db.execSQL("ALTER TABLE favorites_room_v20 RENAME TO favorites");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_created ON favorites (created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_sync_state ON favorites (sync_state)");
    }

    /** Repairs canonical business references without replaying the track-keyed v20 conversion. */
    public static void normalizeV21(SupportSQLiteDatabase db) {
        seedMissingTrackIdentities(db);
        ensureColumn(db, "recording_play_events", "legacy_event_id", "INTEGER");
        repairCanonicalBusinessReferences(db);
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_recording_events_legacy_event "
                + "ON recording_play_events (legacy_event_id)");
    }

    public static void normalizeV29(SupportSQLiteDatabase db) {
        ensureColumn(db, "track_sources", "content_signature", "TEXT NOT NULL DEFAULT ''");
    }

    private static void seedMissingTrackIdentities(SupportSQLiteDatabase db) {
        ArrayList<MissingTrackIdentity> missing = new ArrayList<>();
        try (Cursor cursor = db.query(
                "SELECT t.id,t.title,t.artist,t.album,t.duration_ms,t.content_uri,"
                        + "t.data_path,t.codec,t.updated_at FROM tracks t "
                        + "LEFT JOIN track_sources s ON s.local_track_id=t.id "
                        + "WHERE s.source_id IS NULL ORDER BY t.id"
        )) {
            while (cursor.moveToNext()) {
                missing.add(new MissingTrackIdentity(
                        cursor.getLong(0),
                        clean(cursor.getString(1)),
                        clean(cursor.getString(2)),
                        clean(cursor.getString(3)),
                        Math.max(0L, cursor.getLong(4)),
                        clean(cursor.getString(5)),
                        clean(cursor.getString(6)),
                        clean(cursor.getString(7)),
                        cursor.getLong(8)
                ));
            }
        }
        for (MissingTrackIdentity track : missing) {
            TrackSourceIdentity identity = TrackSourceIdentity.from(
                    track.trackId,
                    track.contentUri,
                    track.dataPath
            );
            if (sourceForProvider(db, identity.provider, identity.providerTrackId) != null) {
                continue;
            }
            long recordingId = recordingForDataPath(db, track.dataPath);
            if (recordingId <= 0L) {
                recordingId = insertOfflineRecording(
                        db,
                        track.title,
                        track.artist,
                        track.durationMs,
                        track.updatedAt
                );
            }
            insertOfflineTrackSource(
                    db,
                    recordingId,
                    identity,
                    track.trackId,
                    track.dataPath,
                    track.title,
                    track.artist,
                    track.album,
                    track.durationMs,
                    track.quality
            );
            ensureArtistIdentities(db, recordingId, track.artist, track.updatedAt);
            enqueueIdentityJob(db, "RECORDING", recordingId, "MISSING_SOURCE_REPAIR", track.updatedAt);
        }
    }

    private static void repairCanonicalBusinessReferences(SupportSQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) {
            db.beginTransaction();
        }
        try {
            db.execSQL("DROP TABLE IF EXISTS canonical_track_reference_v21");
            db.execSQL("CREATE TABLE canonical_track_reference_v21 ("
                    + "track_id INTEGER NOT NULL PRIMARY KEY,"
                    + "recording_id INTEGER NOT NULL,"
                    + "source_id INTEGER NOT NULL"
                    + ")");
            try {
                db.execSQL("INSERT OR REPLACE INTO canonical_track_reference_v21("
                        + "track_id,recording_id,source_id) "
                        + "SELECT t.id,s.recording_id,s.source_id FROM tracks t "
                        + "INNER JOIN track_sources s ON s.local_track_id=t.id");
                fillProviderIdentityReferences(db);
                requireMappedLegacyReferences(db);
                repairQueueIdentities(db);
                repairRecordingHistory(db);
                repairRecordingEvents(db);
            } finally {
                db.execSQL("DROP TABLE IF EXISTS canonical_track_reference_v21");
            }
            if (ownsTransaction) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (ownsTransaction) {
                db.endTransaction();
            }
        }
    }

    private static void fillProviderIdentityReferences(SupportSQLiteDatabase db) {
        ArrayList<TrackReferenceSeed> missing = new ArrayList<>();
        try (Cursor cursor = db.query(
                "SELECT t.id,t.content_uri,t.data_path FROM tracks t "
                        + "LEFT JOIN canonical_track_reference_v21 r ON r.track_id=t.id "
                        + "WHERE r.track_id IS NULL ORDER BY t.id"
        )) {
            while (cursor.moveToNext()) {
                missing.add(new TrackReferenceSeed(
                        cursor.getLong(0),
                        clean(cursor.getString(1)),
                        clean(cursor.getString(2))
                ));
            }
        }
        for (TrackReferenceSeed track : missing) {
            TrackSourceIdentity identity = TrackSourceIdentity.from(
                    track.trackId,
                    track.contentUri,
                    track.dataPath
            );
            ExistingTrackSource source = sourceForProvider(
                    db,
                    identity.provider,
                    identity.providerTrackId
            );
            if (source != null) {
                db.execSQL(
                        "INSERT OR REPLACE INTO canonical_track_reference_v21("
                                + "track_id,recording_id,source_id) VALUES(?,?,?)",
                        new Object[]{track.trackId, source.recordingId, source.sourceId}
                );
            }
        }
    }

    private static void requireMappedLegacyReferences(SupportSQLiteDatabase db) {
        try (Cursor cursor = db.query(
                "SELECT COUNT(*) FROM ("
                        + "SELECT q.track_id FROM playback_queue q "
                        + "INNER JOIN tracks t ON t.id=q.track_id "
                        + "LEFT JOIN canonical_track_reference_v21 r ON r.track_id=q.track_id "
                        + "WHERE r.track_id IS NULL "
                        + "UNION ALL "
                        + "SELECT h.track_id FROM play_history h "
                        + "INNER JOIN tracks t ON t.id=h.track_id "
                        + "LEFT JOIN canonical_track_reference_v21 r ON r.track_id=h.track_id "
                        + "WHERE r.track_id IS NULL "
                        + "UNION ALL "
                        + "SELECT e.track_id FROM play_events e "
                        + "INNER JOIN tracks t ON t.id=e.track_id "
                        + "LEFT JOIN canonical_track_reference_v21 r ON r.track_id=e.track_id "
                        + "WHERE r.track_id IS NULL"
                        + ")"
        )) {
            if (cursor.moveToFirst() && cursor.getLong(0) > 0L) {
                throw new IllegalStateException(
                        "Cannot migrate canonical references: persisted tracks have no source identity"
                );
            }
        }
    }

    private static void repairQueueIdentities(SupportSQLiteDatabase db) {
        db.execSQL("DELETE FROM playback_queue_identities "
                + "WHERE position NOT IN (SELECT position FROM playback_queue)");
        db.execSQL("INSERT OR REPLACE INTO playback_queue_identities("
                + "position,recording_id,preferred_source_id) "
                + "SELECT q.position,r.recording_id,r.source_id FROM playback_queue q "
                + "INNER JOIN canonical_track_reference_v21 r ON r.track_id=q.track_id");
    }

    private static void repairRecordingHistory(SupportSQLiteDatabase db) {
        db.execSQL("INSERT OR REPLACE INTO recording_play_history("
                + "recording_id,representative_track_id,played_at,play_count) "
                + "SELECT legacy.recording_id,"
                + "CASE WHEN current.recording_id IS NULL THEN legacy.representative_track_id "
                + "ELSE current.representative_track_id END,"
                + "CASE WHEN current.recording_id IS NULL THEN legacy.played_at "
                + "ELSE MAX(current.played_at,legacy.played_at) END,"
                + "CASE WHEN current.recording_id IS NULL THEN legacy.play_count "
                + "ELSE MAX(current.play_count,legacy.play_count) END "
                + "FROM (SELECT r.recording_id,MIN(h.track_id) AS representative_track_id,"
                + "MAX(h.played_at) AS played_at,SUM(h.play_count) AS play_count "
                + "FROM play_history h INNER JOIN canonical_track_reference_v21 r "
                + "ON r.track_id=h.track_id GROUP BY r.recording_id) legacy "
                + "LEFT JOIN recording_play_history current "
                + "ON current.recording_id=legacy.recording_id");
    }

    private static void repairRecordingEvents(SupportSQLiteDatabase db) {
        ArrayList<LegacyPlayEventReference> events = new ArrayList<>();
        try (Cursor cursor = db.query(
                "SELECT e.id,r.recording_id,r.source_id,e.track_id,e.played_at "
                        + "FROM play_events e INNER JOIN canonical_track_reference_v21 r "
                        + "ON r.track_id=e.track_id ORDER BY e.id"
        )) {
            while (cursor.moveToNext()) {
                events.add(new LegacyPlayEventReference(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getLong(4)
                ));
            }
        }
        for (LegacyPlayEventReference event : events) {
            if (canonicalEventForLegacy(db, event.legacyEventId) > 0L) {
                continue;
            }
            long existingEventId = unmatchedCanonicalEvent(db, event);
            if (existingEventId > 0L) {
                db.execSQL(
                        "UPDATE recording_play_events SET legacy_event_id=?,"
                                + "source_id=COALESCE(source_id,?) WHERE id=?",
                        new Object[]{event.legacyEventId, event.sourceId, existingEventId}
                );
            } else {
                db.execSQL(
                        "INSERT INTO recording_play_events("
                                + "recording_id,source_id,track_id_snapshot,played_at,legacy_event_id) "
                                + "VALUES(?,?,?,?,?)",
                        new Object[]{
                                event.recordingId,
                                event.sourceId,
                                event.trackId,
                                event.playedAt,
                                event.legacyEventId
                        }
                );
            }
        }
    }

    private static long canonicalEventForLegacy(
            SupportSQLiteDatabase db,
            long legacyEventId
    ) {
        try (Cursor cursor = db.query(
                "SELECT id FROM recording_play_events WHERE legacy_event_id=? LIMIT 1",
                new Object[]{legacyEventId}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static long unmatchedCanonicalEvent(
            SupportSQLiteDatabase db,
            LegacyPlayEventReference event
    ) {
        try (Cursor cursor = db.query(
                "SELECT id FROM recording_play_events WHERE legacy_event_id IS NULL "
                        + "AND recording_id=? AND track_id_snapshot=? AND played_at=? "
                        + "ORDER BY id LIMIT 1",
                new Object[]{event.recordingId, event.trackId, event.playedAt}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public static void recreateEmptyPlaylistsWithExactUniqueConstraint(SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS playlists");
        createPlaylists(db);
    }

    private static void createCoreTables(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tracks ("
                + "id INTEGER PRIMARY KEY,"
                + "title TEXT NOT NULL,"
                + "artist TEXT NOT NULL,"
                + "album TEXT NOT NULL,"
                + "duration_ms INTEGER NOT NULL,"
                + "content_uri TEXT NOT NULL,"
                + "data_path TEXT NOT NULL,"
                + "album_id INTEGER NOT NULL,"
                + "album_art_uri TEXT NOT NULL,"
                + "codec TEXT NOT NULL DEFAULT '',"
                + "bitrate_kbps INTEGER NOT NULL DEFAULT 0,"
                + "sample_rate_hz INTEGER NOT NULL DEFAULT 0,"
                + "bits_per_sample INTEGER NOT NULL DEFAULT 0,"
                + "channel_count INTEGER NOT NULL DEFAULT 0,"
                + "replay_gain_track_db REAL NOT NULL DEFAULT 0,"
                + "replay_gain_album_db REAL NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites ("
                + "track_id INTEGER PRIMARY KEY,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS play_history ("
                + "track_id INTEGER PRIMARY KEY,"
                + "played_at INTEGER NOT NULL,"
                + "play_count INTEGER NOT NULL DEFAULT 1"
                + ")");
    }

    private static void ensureTrackColumns(SupportSQLiteDatabase db) {
        ensureColumn(db, "tracks", "title", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "artist", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "album", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "duration_ms", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "content_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "data_path", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "album_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "album_art_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "codec", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "tracks", "bitrate_kbps", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "sample_rate_hz", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "bits_per_sample", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "channel_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "replay_gain_track_db", "REAL NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "replay_gain_album_db", "REAL NOT NULL DEFAULT 0");
        ensureColumn(db, "tracks", "updated_at", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void createPlayEvents(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS play_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "track_id INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL"
                + ")");
    }

    private static void createPlaylistTables(SupportSQLiteDatabase db) {
        createPlaylists(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_tracks ("
                + "playlist_id INTEGER NOT NULL,"
                + "track_id INTEGER NOT NULL,"
                + "position INTEGER NOT NULL,"
                + "added_at INTEGER NOT NULL,"
                + "PRIMARY KEY (playlist_id, track_id)"
                + ")");
    }

    private static void createPlaylists(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");
    }

    private static void createSettings(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }

    private static void createRemoteSources(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS remote_sources ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "type TEXT NOT NULL,"
                + "name TEXT NOT NULL,"
                + "base_url TEXT NOT NULL,"
                + "username TEXT NOT NULL DEFAULT '',"
                + "password TEXT NOT NULL DEFAULT '',"
                + "root_path TEXT NOT NULL DEFAULT '/',"
                + "last_status TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL"
                + ")");
    }

    private static void createPlaybackQueue(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS playback_queue ("
                + "position INTEGER PRIMARY KEY,"
                + "track_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "content_uri TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "album_id INTEGER NOT NULL DEFAULT 0,"
                + "album_art_uri TEXT NOT NULL DEFAULT '',"
                + "codec TEXT NOT NULL DEFAULT '',"
                + "bitrate_kbps INTEGER NOT NULL DEFAULT 0,"
                + "sample_rate_hz INTEGER NOT NULL DEFAULT 0,"
                + "bits_per_sample INTEGER NOT NULL DEFAULT 0,"
                + "channel_count INTEGER NOT NULL DEFAULT 0,"
                + "replay_gain_track_db REAL NOT NULL DEFAULT 0,"
                + "replay_gain_album_db REAL NOT NULL DEFAULT 0"
                + ")");
    }

    private static void ensurePlaybackQueueColumns(SupportSQLiteDatabase db) {
        ensureColumn(db, "playback_queue", "title", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "artist", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "album", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "duration_ms", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "content_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "data_path", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "album_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "album_art_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "codec", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, "playback_queue", "bitrate_kbps", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "sample_rate_hz", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "bits_per_sample", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "channel_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "replay_gain_track_db", "REAL NOT NULL DEFAULT 0");
        ensureColumn(db, "playback_queue", "replay_gain_album_db", "REAL NOT NULL DEFAULT 0");
    }

    private static void createStreamingTrackMatches(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS streaming_track_matches ("
                + "local_key TEXT NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_track_id TEXT NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY (local_key, provider)"
                + ")");
    }

    private static void createLibraryExclusions(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS library_exclusions ("
                + "source_key TEXT PRIMARY KEY,"
                + "content_uri TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL"
                + ")");
    }

    private static void createIndexes(SupportSQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_play_events_played_at ON play_events (played_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_play_events_track_time ON play_events (track_id, played_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist ON playlist_tracks (playlist_id, position)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_remote_sources_type ON remote_sources (type, updated_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_queue_track ON playback_queue (track_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_streaming_track_matches_provider_track "
                + "ON streaming_track_matches (provider, provider_track_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_library_exclusions_created_at "
                + "ON library_exclusions (created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracks_data_path ON tracks (data_path)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks (artist)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks (album)");
    }

    private static void createMusicIdentityTables(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recordings ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "canonical_uuid TEXT NOT NULL,"
                + "active_source_id INTEGER,"
                + "musicbrainz_recording_id TEXT NOT NULL DEFAULT '',"
                + "musicbrainz_work_id TEXT NOT NULL DEFAULT '',"
                + "title TEXT NOT NULL DEFAULT '',"
                + "primary_artist_display TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "isrc TEXT NOT NULL DEFAULT '',"
                + "acoust_id TEXT NOT NULL DEFAULT '',"
                + "match_status TEXT NOT NULL DEFAULT 'UNRESOLVED',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "metadata_source TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS canonical_artists ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "artist_uuid TEXT NOT NULL,"
                + "display_name TEXT NOT NULL,"
                + "sort_name TEXT NOT NULL DEFAULT '',"
                + "artist_type TEXT NOT NULL DEFAULT 'UNKNOWN',"
                + "country_code TEXT NOT NULL DEFAULT '',"
                + "musicbrainz_artist_id TEXT NOT NULL DEFAULT '',"
                + "avatar_url TEXT NOT NULL DEFAULT '',"
                + "match_status TEXT NOT NULL DEFAULT 'UNRESOLVED',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "metadata_source TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "description TEXT NOT NULL DEFAULT ''"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS artist_aliases ("
                + "artist_id INTEGER NOT NULL,"
                + "alias TEXT NOT NULL,"
                + "normalized_alias TEXT NOT NULL,"
                + "locale TEXT NOT NULL DEFAULT '',"
                + "script TEXT NOT NULL DEFAULT '',"
                + "alias_type TEXT NOT NULL DEFAULT 'ALIAS',"
                + "source TEXT NOT NULL DEFAULT '',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "verified_at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(artist_id,normalized_alias,locale),"
                + "FOREIGN KEY(artist_id) REFERENCES canonical_artists(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS artist_source_mappings ("
                + "mapping_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "artist_id INTEGER NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_artist_id TEXT NOT NULL,"
                + "display_name TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT 'UNRESOLVED',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "last_verified_at INTEGER NOT NULL DEFAULT 0,"
                + "FOREIGN KEY(artist_id) REFERENCES canonical_artists(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_artist_credits ("
                + "recording_id INTEGER NOT NULL,"
                + "artist_id INTEGER NOT NULL,"
                + "role TEXT NOT NULL DEFAULT 'UNKNOWN',"
                + "position INTEGER NOT NULL DEFAULT 0,"
                + "credited_name TEXT NOT NULL DEFAULT '',"
                + "join_phrase TEXT NOT NULL DEFAULT '',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(recording_id,artist_id,role,position),"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE,"
                + "FOREIGN KEY(artist_id) REFERENCES canonical_artists(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS track_sources ("
                + "source_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "recording_id INTEGER NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_track_id TEXT NOT NULL,"
                + "local_track_id INTEGER,"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "quality TEXT NOT NULL DEFAULT '',"
                + "quality_score INTEGER NOT NULL DEFAULT 0,"
                + "playable INTEGER NOT NULL DEFAULT 1,"
                + "match_status TEXT NOT NULL DEFAULT 'UNRESOLVED',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "last_successful_at INTEGER NOT NULL DEFAULT 0,"
                + "last_verified_at INTEGER NOT NULL DEFAULT 0,"
                + "legacy_local_key TEXT NOT NULL DEFAULT '',"
                + "content_signature TEXT NOT NULL DEFAULT '',"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_identifiers ("
                + "recording_id INTEGER NOT NULL,"
                + "identifier_type TEXT NOT NULL,"
                + "namespace TEXT NOT NULL,"
                + "identifier_value TEXT NOT NULL,"
                + "source TEXT NOT NULL DEFAULT '',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "verified_at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(identifier_type, namespace, identifier_value),"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS recording_variants ("
                + "variant_group_id TEXT NOT NULL,"
                + "recording_id INTEGER NOT NULL,"
                + "variant_type TEXT NOT NULL DEFAULT 'UNKNOWN',"
                + "display_name TEXT NOT NULL DEFAULT '',"
                + "confidence REAL NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(variant_group_id,recording_id),"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS identity_candidates ("
                + "candidate_id TEXT NOT NULL,"
                + "target_type TEXT NOT NULL,"
                + "target_id INTEGER NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_item_id TEXT NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "isrc TEXT NOT NULL DEFAULT '',"
                + "variant_type TEXT NOT NULL DEFAULT 'UNKNOWN',"
                + "score REAL NOT NULL DEFAULT 0,"
                + "status TEXT NOT NULL DEFAULT 'PENDING',"
                + "evidence_json TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY(candidate_id)"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS identity_resolution_jobs ("
                + "job_id TEXT NOT NULL,"
                + "target_type TEXT NOT NULL,"
                + "target_id INTEGER NOT NULL,"
                + "priority INTEGER NOT NULL DEFAULT 0,"
                + "reason TEXT NOT NULL DEFAULT 'NEW_TRACK',"
                + "attempt_count INTEGER NOT NULL DEFAULT 0,"
                + "next_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT 'PENDING',"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY(job_id)"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS provider_response_cache ("
                + "provider TEXT NOT NULL,"
                + "endpoint TEXT NOT NULL DEFAULT '',"
                + "request_hash TEXT NOT NULL,"
                + "response_json TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "expires_at INTEGER NOT NULL DEFAULT 0,"
                + "failure_count INTEGER NOT NULL DEFAULT 0,"
                + "circuit_open_until INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT NOT NULL DEFAULT '',"
                + "PRIMARY KEY(provider,endpoint,request_hash)"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS lyric_bindings ("
                + "recording_id INTEGER NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_lyric_id TEXT NOT NULL,"
                + "synced INTEGER NOT NULL DEFAULT 0,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "checksum TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY(recording_id,provider),"
                + "FOREIGN KEY(recording_id) REFERENCES recordings(id) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE"
                + ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_recording_uuid "
                + "ON recordings (canonical_uuid)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recordings_mbid "
                + "ON recordings (musicbrainz_recording_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recordings_isrc ON recordings (isrc)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recordings_status ON recordings (match_status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recordings_active_source "
                + "ON recordings (active_source_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_artist_uuid "
                + "ON canonical_artists (artist_uuid)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_canonical_artists_mbid "
                + "ON canonical_artists (musicbrainz_artist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_canonical_artists_status "
                + "ON canonical_artists (match_status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_canonical_artists_sort_name "
                + "ON canonical_artists (sort_name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_artist_aliases_normalized "
                + "ON artist_aliases (normalized_alias)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_artist_aliases_artist "
                + "ON artist_aliases (artist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_artist_source_artist "
                + "ON artist_source_mappings (artist_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_artist_source_provider_artist "
                + "ON artist_source_mappings (provider, provider_artist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_credits_recording "
                + "ON recording_artist_credits (recording_id, position)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_credits_artist "
                + "ON recording_artist_credits (artist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_track_source_recording "
                + "ON track_sources (recording_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_track_source_provider_track "
                + "ON track_sources (provider, provider_track_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_track_source_local_track "
                + "ON track_sources (local_track_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_track_source_verified "
                + "ON track_sources (last_verified_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_source_selection "
                + "ON track_sources (recording_id, playable, quality_score)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_identifiers_recording "
                + "ON recording_identifiers (recording_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recording_variants_recording "
                + "ON recording_variants (recording_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_candidates_target "
                + "ON identity_candidates (target_type, target_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_candidates_status "
                + "ON identity_candidates (status, updated_at)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_candidates_provider_item "
                + "ON identity_candidates (target_type, target_id, provider, provider_item_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_jobs_target "
                + "ON identity_resolution_jobs (target_type, target_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_jobs_target_status "
                + "ON identity_resolution_jobs (target_type, target_id, status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_jobs_ready "
                + "ON identity_resolution_jobs (status, next_attempt_at, priority)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_provider_cache_expires "
                + "ON provider_response_cache (expires_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_provider_cache_circuit "
                + "ON provider_response_cache (provider, endpoint, circuit_open_until)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_lyric_bindings_provider "
                + "ON lyric_bindings (provider, provider_lyric_id)");
    }

    /** Imports platform sources as unverified evidence while retaining the legacy table verbatim. */
    private static void migrateLegacyStreamingMatches(SupportSQLiteDatabase db) {
        if (tableCount(db, "recordings") > 0L) {
            return;
        }
        Map<String, Long> recordingIds = new LinkedHashMap<>();
        try (Cursor cursor = db.query(
                "SELECT local_key, provider, provider_track_id, title, artist, data_path, updated_at "
                        + "FROM streaming_track_matches ORDER BY updated_at, local_key, provider"
        )) {
            int localKeyIndex = cursor.getColumnIndexOrThrow("local_key");
            int providerIndex = cursor.getColumnIndexOrThrow("provider");
            int providerTrackIdIndex = cursor.getColumnIndexOrThrow("provider_track_id");
            int titleIndex = cursor.getColumnIndexOrThrow("title");
            int artistIndex = cursor.getColumnIndexOrThrow("artist");
            int dataPathIndex = cursor.getColumnIndexOrThrow("data_path");
            int updatedAtIndex = cursor.getColumnIndexOrThrow("updated_at");
            while (cursor.moveToNext()) {
                String localKey = clean(cursor.getString(localKeyIndex));
                String provider = clean(cursor.getString(providerIndex)).toLowerCase(Locale.ROOT);
                String rawProviderTrackId = clean(cursor.getString(providerTrackIdIndex));
                String title = clean(cursor.getString(titleIndex));
                String artist = clean(cursor.getString(artistIndex));
                String dataPath = clean(cursor.getString(dataPathIndex));
                long updatedAt = cursor.getLong(updatedAtIndex);
                LegacyMatch match = legacyMatch(
                        provider,
                        rawProviderTrackId,
                        title,
                        artist
                );
                if (match == null || match.primary == null) {
                    continue;
                }
                String groupKey = legacyGroupKey(localKey, title, artist, dataPath);
                Long recordingId = recordingIds.get(groupKey);
                if (recordingId == null) {
                    ExistingTrackSource existingSource = sourceForProvider(
                            db,
                            provider,
                            match.primary.providerTrackId
                    );
                    recordingId = existingSource == null
                            ? insertLegacyRecording(db, title, artist, updatedAt)
                            : existingSource.recordingId;
                    recordingIds.put(groupKey, recordingId);
                }
                insertLegacySource(
                        db,
                        recordingId,
                        provider,
                        match.primary,
                        dataPath,
                        localKey,
                        updatedAt
                );
                if (!match.primary.isrc.isEmpty()) {
                    insertLegacyIsrc(db, recordingId, match.primary.isrc, provider, updatedAt);
                }
                for (LegacySource candidate : match.candidates) {
                    insertLegacyCandidate(db, recordingId, provider, candidate, updatedAt);
                }
            }
        }
    }

    /** Gives every persisted track a stable UUID before any optional online enrichment runs. */
    private static void seedOfflineTrackIdentities(SupportSQLiteDatabase db) {
        try (Cursor cursor = db.query(
                "SELECT id,title,artist,album,duration_ms,content_uri,data_path,codec,updated_at "
                        + "FROM tracks ORDER BY id"
        )) {
            int idIndex = cursor.getColumnIndexOrThrow("id");
            int titleIndex = cursor.getColumnIndexOrThrow("title");
            int artistIndex = cursor.getColumnIndexOrThrow("artist");
            int albumIndex = cursor.getColumnIndexOrThrow("album");
            int durationIndex = cursor.getColumnIndexOrThrow("duration_ms");
            int contentUriIndex = cursor.getColumnIndexOrThrow("content_uri");
            int dataPathIndex = cursor.getColumnIndexOrThrow("data_path");
            int codecIndex = cursor.getColumnIndexOrThrow("codec");
            int updatedAtIndex = cursor.getColumnIndexOrThrow("updated_at");
            while (cursor.moveToNext()) {
                long trackId = cursor.getLong(idIndex);
                String title = clean(cursor.getString(titleIndex));
                String artist = clean(cursor.getString(artistIndex));
                String album = clean(cursor.getString(albumIndex));
                long durationMs = Math.max(0L, cursor.getLong(durationIndex));
                String contentUri = clean(cursor.getString(contentUriIndex));
                String dataPath = clean(cursor.getString(dataPathIndex));
                String quality = clean(cursor.getString(codecIndex));
                long updatedAt = cursor.getLong(updatedAtIndex);
                TrackSourceIdentity identity = TrackSourceIdentity.from(
                        trackId,
                        contentUri,
                        dataPath
                );
                ExistingTrackSource existing = sourceForProvider(
                        db,
                        identity.provider,
                        identity.providerTrackId
                );
                if (existing != null) {
                    attachTrackToSource(
                            db,
                            existing.sourceId,
                            trackId,
                            dataPath,
                            title,
                            artist,
                            album,
                            durationMs,
                            quality
                    );
                    refreshActiveSource(db, existing.recordingId);
                    ensureArtistIdentities(db, existing.recordingId, artist, updatedAt);
                    enqueueIdentityJob(db, "RECORDING", existing.recordingId, "NEW_TRACK", updatedAt);
                    continue;
                }
                long recordingId = recordingForDataPath(db, dataPath);
                if (recordingId <= 0L) {
                    recordingId = insertOfflineRecording(
                            db,
                            title,
                            artist,
                            durationMs,
                            updatedAt
                    );
                }
                insertOfflineTrackSource(
                        db,
                        recordingId,
                        identity,
                        trackId,
                        dataPath,
                        title,
                        artist,
                        album,
                        durationMs,
                        quality
                );
                ensureArtistIdentities(db, recordingId, artist, updatedAt);
                enqueueIdentityJob(db, "RECORDING", recordingId, "NEW_TRACK", updatedAt);
            }
        }
    }

    private static ExistingTrackSource sourceForProvider(
            SupportSQLiteDatabase db,
            String provider,
            String providerTrackId
    ) {
        try (Cursor cursor = db.query(
                "SELECT source_id,recording_id FROM track_sources "
                        + "WHERE provider = ? AND provider_track_id = ? LIMIT 1",
                new Object[]{provider, providerTrackId}
        )) {
            return cursor.moveToFirst()
                    ? new ExistingTrackSource(cursor.getLong(0), cursor.getLong(1))
                    : null;
        }
    }

    private static long recordingForDataPath(SupportSQLiteDatabase db, String dataPath) {
        if (dataPath.isEmpty()) {
            return 0L;
        }
        try (Cursor cursor = db.query(
                "SELECT recording_id FROM track_sources "
                        + "WHERE data_path = ? ORDER BY last_verified_at DESC LIMIT 1",
                new Object[]{dataPath}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static void attachTrackToSource(
            SupportSQLiteDatabase db,
            long sourceId,
            long trackId,
            String dataPath,
            String title,
            String artist,
            String album,
            long durationMs,
            String quality
    ) {
        db.execSQL(
                "UPDATE track_sources SET local_track_id=?,data_path=?,title=?,artist=?,"
                        + "album=?,duration_ms=?,quality=CASE WHEN ?='' THEN quality ELSE ? END,"
                        + "playable=1,match_status='CONFIRMED',confidence=1 WHERE source_id=?",
                new Object[]{
                        trackId,
                        dataPath,
                        title,
                        artist,
                        album,
                        durationMs,
                        quality,
                        quality,
                        sourceId
                }
        );
    }

    private static long insertOfflineRecording(
            SupportSQLiteDatabase db,
            String title,
            String artist,
            long durationMs,
            long updatedAt
    ) {
        String canonicalUuid = UUID.randomUUID().toString();
        db.execSQL(
                "INSERT INTO recordings("
                        + "canonical_uuid,active_source_id,title,primary_artist_display,duration_ms,"
                        + "match_status,confidence,metadata_source,created_at,updated_at) "
                        + "VALUES(?,NULL,?,?,?,?,?,?,?,?)",
                new Object[]{
                        canonicalUuid,
                        title,
                        artist,
                        durationMs,
                        "UNRESOLVED",
                        0.0d,
                        "LOCAL_CATALOG",
                        updatedAt,
                        updatedAt
                }
        );
        return recordingIdForUuid(db, canonicalUuid);
    }

    private static void insertOfflineTrackSource(
            SupportSQLiteDatabase db,
            long recordingId,
            TrackSourceIdentity identity,
            long trackId,
            String dataPath,
            String title,
            String artist,
            String album,
            long durationMs,
            String quality
    ) {
        boolean confirmed = isPhysicalProvider(identity.provider);
        db.execSQL(
                "INSERT OR IGNORE INTO track_sources("
                        + "recording_id,provider,provider_track_id,local_track_id,data_path,"
                        + "title,artist,album,duration_ms,quality,quality_score,playable,match_status,confidence,"
                        + "last_successful_at,last_verified_at,legacy_local_key) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,1,?,?,0,0,'')",
                new Object[]{
                        recordingId,
                        identity.provider,
                        identity.providerTrackId,
                        trackId,
                        dataPath,
                        title,
                        artist,
                        album,
                        durationMs,
                        quality,
                        qualityScore(quality),
                        confirmed ? "CONFIRMED" : "UNRESOLVED",
                        confirmed ? 1.0d : 0.0d
                }
        );
        ExistingTrackSource inserted = sourceForProvider(
                db,
                identity.provider,
                identity.providerTrackId
        );
        if (inserted != null) {
            refreshActiveSource(db, inserted.recordingId);
        }
    }

    private static long insertLegacyRecording(
            SupportSQLiteDatabase db,
            String title,
            String artist,
            long updatedAt
    ) {
        String canonicalUuid = UUID.randomUUID().toString();
        db.execSQL(
                "INSERT INTO recordings("
                        + "canonical_uuid,active_source_id,title,primary_artist_display,match_status,"
                        + "confidence,metadata_source,created_at,updated_at) "
                        + "VALUES(?,NULL,?,?,?,?,?,?,?)",
                new Object[]{
                        canonicalUuid,
                        title,
                        artist,
                        "UNVERIFIED_LEGACY",
                        0.0d,
                        "LEGACY_STREAMING_MATCH",
                        updatedAt,
                        updatedAt
                }
        );
        return recordingIdForUuid(db, canonicalUuid);
    }

    private static void insertLegacySource(
            SupportSQLiteDatabase db,
            long recordingId,
            String provider,
            LegacySource source,
            String dataPath,
            String localKey,
            long updatedAt
    ) {
        db.execSQL(
                "INSERT OR IGNORE INTO track_sources("
                        + "recording_id,provider,provider_track_id,local_track_id,data_path,"
                        + "title,artist,album,duration_ms,quality,quality_score,playable,match_status,confidence,"
                        + "last_successful_at,last_verified_at,legacy_local_key) "
                        + "VALUES(?,?,?,NULL,?,?,?,?,?,'',0,1,?,0,0,?,?)",
                new Object[]{
                        recordingId,
                        provider,
                        source.providerTrackId,
                        dataPath,
                        source.title,
                        source.artist,
                        source.album,
                        source.durationMs,
                        "UNVERIFIED_LEGACY",
                        updatedAt,
                        localKey
                }
        );
        ExistingTrackSource inserted = sourceForProvider(db, provider, source.providerTrackId);
        if (inserted != null) {
            refreshActiveSource(db, inserted.recordingId);
        }
    }

    private static void insertLegacyIsrc(
            SupportSQLiteDatabase db,
            long recordingId,
            String isrc,
            String provider,
            long updatedAt
    ) {
        String normalized = isrc.replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        db.execSQL(
                "INSERT OR IGNORE INTO recording_identifiers("
                        + "recording_id,identifier_type,namespace,identifier_value,source,confidence,verified_at) "
                        + "VALUES(?,'ISRC','',?,?,0,?)",
                new Object[]{recordingId, normalized, "LEGACY_" + provider.toUpperCase(Locale.ROOT), updatedAt}
        );
    }

    private static void insertLegacyCandidate(
            SupportSQLiteDatabase db,
            long recordingId,
            String provider,
            LegacySource candidate,
            long updatedAt
    ) {
        db.execSQL(
                "INSERT OR IGNORE INTO identity_candidates("
                        + "candidate_id,target_type,target_id,provider,provider_item_id,title,artist,"
                        + "album,duration_ms,isrc,variant_type,score,status,evidence_json,created_at,updated_at) "
                        + "VALUES(?,'RECORDING',?,?,?,?,?,?,?,?,?,0,'PENDING',?,?,?)",
                new Object[]{
                        UUID.randomUUID().toString(),
                        recordingId,
                        provider,
                        candidate.providerTrackId,
                        candidate.title,
                        candidate.artist,
                        candidate.album,
                        candidate.durationMs,
                        normalizeIsrc(candidate.isrc),
                        candidate.variantType,
                        candidate.evidenceJson,
                        updatedAt,
                        updatedAt
                }
        );
    }

    private static void ensureArtistIdentities(
            SupportSQLiteDatabase db,
            long recordingId,
            String rawArtist,
            long updatedAt
    ) {
        if (recordingCreditCount(db, recordingId) > 0L) {
            return;
        }
        for (ArtistCreditParser.Credit credit : ArtistCreditParser.parse(rawArtist)) {
            String normalized = ArtistCreditParser.normalizeAlias(credit.name);
            long artistId = artistIdForAlias(db, normalized);
            if (artistId <= 0L) {
                artistId = insertOfflineArtist(db, credit.name, normalized, updatedAt);
            }
            db.execSQL(
                    "INSERT OR IGNORE INTO recording_artist_credits("
                            + "recording_id,artist_id,role,position,credited_name,join_phrase,confidence) "
                            + "VALUES(?,?,?,?,?,?,0)",
                    new Object[]{
                            recordingId,
                            artistId,
                            credit.role,
                            credit.position,
                            credit.name,
                            credit.joinPhrase
                    }
            );
            enqueueIdentityJob(db, "ARTIST", artistId, "NEW_TRACK", updatedAt);
        }
    }

    private static long insertOfflineArtist(
            SupportSQLiteDatabase db,
            String displayName,
            String normalizedAlias,
            long updatedAt
    ) {
        String artistUuid = UUID.randomUUID().toString();
        db.execSQL(
                "INSERT INTO canonical_artists("
                        + "artist_uuid,display_name,sort_name,artist_type,country_code,"
                        + "musicbrainz_artist_id,match_status,confidence,metadata_source,created_at,updated_at) "
                        + "VALUES(?,?,?,'UNKNOWN','','','UNRESOLVED',0,'LOCAL_CATALOG',?,?)",
                new Object[]{artistUuid, displayName, normalizedAlias, updatedAt, updatedAt}
        );
        long artistId = artistIdForUuid(db, artistUuid);
        db.execSQL(
                "INSERT OR IGNORE INTO artist_aliases("
                        + "artist_id,alias,normalized_alias,locale,script,alias_type,source,confidence,verified_at) "
                        + "VALUES(?,?,?,'','','PRIMARY','LOCAL_CATALOG',0,0)",
                new Object[]{artistId, displayName, normalizedAlias}
        );
        return artistId;
    }

    private static long artistIdForAlias(SupportSQLiteDatabase db, String normalizedAlias) {
        try (Cursor cursor = db.query(
                "SELECT DISTINCT artist_id FROM artist_aliases WHERE normalized_alias = ? "
                        + "ORDER BY artist_id LIMIT 2",
                new Object[]{normalizedAlias}
        )) {
            if (!cursor.moveToFirst()) {
                return 0L;
            }
            long artistId = cursor.getLong(0);
            return cursor.moveToNext() ? 0L : artistId;
        }
    }

    private static long recordingCreditCount(SupportSQLiteDatabase db, long recordingId) {
        try (Cursor cursor = db.query(
                "SELECT COUNT(*) FROM recording_artist_credits WHERE recording_id = ?",
                new Object[]{recordingId}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static void enqueueIdentityJob(
            SupportSQLiteDatabase db,
            String targetType,
            long targetId,
            String reason,
            long updatedAt
    ) {
        db.execSQL(
                "INSERT OR IGNORE INTO identity_resolution_jobs("
                        + "job_id,target_type,target_id,priority,reason,attempt_count,next_attempt_at,"
                        + "last_error,status,created_at,updated_at) "
                        + "VALUES(?,?,?,0,?,0,0,'','PENDING',?,?)",
                new Object[]{UUID.randomUUID().toString(), targetType, targetId, reason, updatedAt, updatedAt}
        );
    }

    private static void refreshActiveSource(SupportSQLiteDatabase db, long recordingId) {
        db.execSQL(
                "UPDATE recordings SET active_source_id = ("
                        + "SELECT source_id FROM track_sources WHERE recording_id = ? AND playable = 1 "
                        + "AND match_status = 'CONFIRMED' "
                        + "AND (provider IN ('local','document','webdav') OR last_verified_at > 0 "
                        + "OR last_successful_at > 0) "
                        + "ORDER BY quality_score DESC,last_successful_at DESC,CASE provider "
                        + "WHEN 'local' THEN 600 WHEN 'webdav' THEN 500 "
                        + "WHEN 'document' THEN 450 WHEN 'stream' THEN 400 WHEN 'netease' THEN 300 "
                        + "WHEN 'qqmusic' THEN 250 WHEN 'luoxue' THEN 200 ELSE 100 END DESC, "
                        + "source_id LIMIT 1) WHERE id = ?",
                new Object[]{recordingId, recordingId}
        );
    }

    private static long recordingIdForUuid(SupportSQLiteDatabase db, String canonicalUuid) {
        try (Cursor cursor = db.query(
                "SELECT id FROM recordings WHERE canonical_uuid = ? LIMIT 1",
                new Object[]{canonicalUuid}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static long artistIdForUuid(SupportSQLiteDatabase db, String artistUuid) {
        try (Cursor cursor = db.query(
                "SELECT id FROM canonical_artists WHERE artist_uuid = ? LIMIT 1",
                new Object[]{artistUuid}
        )) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static int qualityScore(String quality) {
        String value = clean(quality).toLowerCase(Locale.ROOT);
        if (containsAny(value, "flac", "alac", "wav", "ape", "lossless")) {
            return 100;
        }
        if (containsAny(value, "high", "320", "256")) {
            return 70;
        }
        if (containsAny(value, "standard", "192", "128", "aac", "mp3", "ogg")) {
            return 40;
        }
        return 0;
    }

    private static boolean isPhysicalProvider(String provider) {
        return "local".equals(provider)
                || "webdav".equals(provider)
                || "document".equals(provider);
    }

    private static LegacyMatch legacyMatch(
            String provider,
            String rawProviderTrackId,
            String fallbackTitle,
            String fallbackArtist
    ) {
        if (rawProviderTrackId.isEmpty() || rawProviderTrackId.startsWith("__echo_no_source")) {
            return null;
        }
        if ("luoxue".equals(provider) && rawProviderTrackId.startsWith("__echo_source_match_v")) {
            try {
                JSONObject root = new JSONObject(rawProviderTrackId.substring(rawProviderTrackId.indexOf(':') + 1));
                String primaryId = clean(root.optString("primary"));
                if (primaryId.isEmpty()) {
                    return null;
                }
                ArrayList<LegacySource> parsed = new ArrayList<>();
                JSONArray candidates = root.optJSONArray("candidates");
                if (candidates != null) {
                    for (int index = 0; index < Math.min(candidates.length(), 12); index++) {
                        JSONObject candidate = candidates.optJSONObject(index);
                        if (candidate == null) {
                            continue;
                        }
                        LegacySource source = legacySource(candidate, fallbackTitle, fallbackArtist);
                        if (source != null && !containsSource(parsed, source.providerTrackId)) {
                            parsed.add(source);
                        }
                    }
                }
                LegacySource primary = null;
                ArrayList<LegacySource> alternates = new ArrayList<>();
                for (LegacySource source : parsed) {
                    if (source.providerTrackId.equals(primaryId)) {
                        primary = source;
                    } else if (alternates.size() < 11) {
                        alternates.add(source);
                    }
                }
                if (primary == null) {
                    primary = new LegacySource(
                            primaryId,
                            fallbackTitle,
                            fallbackArtist,
                            "",
                            0L,
                            "",
                            "UNKNOWN",
                            ""
                    );
                }
                return new LegacyMatch(primary, alternates);
            } catch (Exception ignored) {
                // The untouched legacy row remains the source of truth; malformed markers are not sources.
                return null;
            }
        }
        return new LegacyMatch(
                new LegacySource(
                        rawProviderTrackId,
                        fallbackTitle,
                        fallbackArtist,
                        "",
                        0L,
                        "",
                        "UNKNOWN",
                        ""
                ),
                new ArrayList<>()
        );
    }

    private static LegacySource legacySource(
            JSONObject candidate,
            String fallbackTitle,
            String fallbackArtist
    ) {
        String providerTrackId = clean(candidate.optString("id"));
        if (providerTrackId.isEmpty()) {
            return null;
        }
        String title = clean(candidate.optString("title"));
        String artist = clean(candidate.optString("artist"));
        String album = clean(candidate.optString("album"));
        return new LegacySource(
                providerTrackId,
                title.isEmpty() ? fallbackTitle : title,
                artist.isEmpty() ? fallbackArtist : artist,
                album,
                Math.max(0L, candidate.optLong("durationMs", 0L)),
                clean(candidate.optString("isrc")),
                variantType(title + " " + album),
                candidate.toString()
        );
    }

    private static boolean containsSource(List<LegacySource> sources, String providerTrackId) {
        for (LegacySource source : sources) {
            if (source.providerTrackId.equals(providerTrackId)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeIsrc(String value) {
        return clean(value).replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static String variantType(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "instrumental", "伴奏", "纯音乐", "純音樂", "インスト", "karaoke")) {
            return "INSTRUMENTAL";
        }
        if (containsAny(normalized, "remix", "リミックス", "重混", "混音", " remix", " mix")) {
            return "REMIX";
        }
        if (containsAny(normalized, "live", "现场", "現場", "演唱会", "演唱會", "ライブ")) {
            return "LIVE";
        }
        if (containsAny(normalized, "acoustic", "不插电", "不插電", "アコースティック")) {
            return "ACOUSTIC";
        }
        if (containsAny(normalized, "remaster", "重制", "重製", "リマスター")) {
            return "REMASTER";
        }
        if (containsAny(normalized, "demo", "デモ")) {
            return "DEMO";
        }
        if (containsAny(normalized, "cover", "翻唱", "カバー")) {
            return "COVER";
        }
        return "UNKNOWN";
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String legacyGroupKey(
            String localKey,
            String title,
            String artist,
            String dataPath
    ) {
        if (!dataPath.isEmpty()) {
            return "path:" + dataPath;
        }
        if (!title.isEmpty() || !artist.isEmpty()) {
            return "meta:" + title.toLowerCase(Locale.ROOT) + "|" + artist.toLowerCase(Locale.ROOT);
        }
        return "legacy:" + localKey;
    }

    private static long tableCount(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM " + table)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class LegacySource {
        final String providerTrackId;
        final String title;
        final String artist;
        final String album;
        final long durationMs;
        final String isrc;
        final String variantType;
        final String evidenceJson;

        LegacySource(
                String providerTrackId,
                String title,
                String artist,
                String album,
                long durationMs,
                String isrc,
                String variantType,
                String evidenceJson
        ) {
            this.providerTrackId = providerTrackId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
            this.isrc = isrc;
            this.variantType = variantType;
            this.evidenceJson = evidenceJson;
        }
    }

    private static final class LegacyMatch {
        final LegacySource primary;
        final List<LegacySource> candidates;

        LegacyMatch(LegacySource primary, List<LegacySource> candidates) {
            this.primary = primary;
            this.candidates = candidates;
        }
    }

    private static final class ExistingTrackSource {
        final long sourceId;
        final long recordingId;

        ExistingTrackSource(long sourceId, long recordingId) {
            this.sourceId = sourceId;
            this.recordingId = recordingId;
        }
    }

    private static final class TrackReferenceSeed {
        final long trackId;
        final String contentUri;
        final String dataPath;

        TrackReferenceSeed(long trackId, String contentUri, String dataPath) {
            this.trackId = trackId;
            this.contentUri = contentUri;
            this.dataPath = dataPath;
        }
    }

    private static final class MissingTrackIdentity {
        final long trackId;
        final String title;
        final String artist;
        final String album;
        final long durationMs;
        final String contentUri;
        final String dataPath;
        final String quality;
        final long updatedAt;

        MissingTrackIdentity(
                long trackId,
                String title,
                String artist,
                String album,
                long durationMs,
                String contentUri,
                String dataPath,
                String quality,
                long updatedAt
        ) {
            this.trackId = trackId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
            this.contentUri = contentUri;
            this.dataPath = dataPath;
            this.quality = quality;
            this.updatedAt = updatedAt;
        }
    }

    private static final class LegacyPlayEventReference {
        final long legacyEventId;
        final long recordingId;
        final long sourceId;
        final long trackId;
        final long playedAt;

        LegacyPlayEventReference(
                long legacyEventId,
                long recordingId,
                long sourceId,
                long trackId,
                long playedAt
        ) {
            this.legacyEventId = legacyEventId;
            this.recordingId = recordingId;
            this.sourceId = sourceId;
            this.trackId = trackId;
            this.playedAt = playedAt;
        }
    }

    private static void ensureColumn(
            SupportSQLiteDatabase db,
            String table,
            String column,
            String definition
    ) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean columnExists(SupportSQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.query("PRAGMA table_info(" + table + ")")) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireNoNullPrimaryKey(
            SupportSQLiteDatabase db,
            String table,
            String column
    ) {
        try (Cursor cursor = db.query(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NULL"
        )) {
            if (cursor.moveToFirst() && cursor.getLong(0) > 0L) {
                throw new IllegalStateException(
                        "Cannot migrate " + table + ": null primary key would be lost"
                );
            }
        }
    }
}
