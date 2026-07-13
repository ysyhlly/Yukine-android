package app.yukine.data.room;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

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
