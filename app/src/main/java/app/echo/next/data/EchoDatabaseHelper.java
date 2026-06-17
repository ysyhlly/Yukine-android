package app.echo.next.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.echo.next.model.Playlist;
import app.echo.next.model.RemoteSource;
import app.echo.next.model.Track;
import app.echo.next.model.TrackPlayRecord;

public final class EchoDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "echo_next.db";
    private static final int DATABASE_VERSION = 11;

    private static final String TABLE_TRACKS = "tracks";
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_HISTORY = "play_history";
    private static final String TABLE_PLAY_EVENTS = "play_events";
    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String TABLE_PLAYLIST_TRACKS = "playlist_tracks";
    private static final String TABLE_SETTINGS = "settings";
    private static final String TABLE_REMOTE_SOURCES = "remote_sources";
    private static final String TABLE_PLAYBACK_QUEUE = "playback_queue";
    private static final String TABLE_STREAMING_TRACK_MATCHES = "streaming_track_matches";
    private static final String SETTING_THEME_MODE = "theme_mode";
    private static final String SETTING_ACCENT_MODE = "accent_mode";
    private static final String SETTING_LANGUAGE_MODE = "language_mode";
    private static final String SETTING_PLAYBACK_SPEED = "playback_speed";
    private static final String SETTING_APP_VOLUME = "app_volume";
    private static final String SETTING_STREAMING_AUDIO_QUALITY = "streaming_audio_quality";
    private static final String SETTING_ONLINE_LYRICS = "online_lyrics";
    private static final String SETTING_CONCURRENT_PLAYBACK = "concurrent_playback";
    private static final String SETTING_LYRICS_OFFSET_MS = "lyrics_offset_ms";
    private static final String SETTING_PLAYBACK_QUEUE_INDEX = "playback_queue_index";
    private static final String SETTING_PLAYBACK_POSITION_TRACK_ID = "playback_position_track_id";
    private static final String SETTING_PLAYBACK_POSITION_MS = "playback_position_ms";
    private static final String SETTING_SHUFFLE_ENABLED = "shuffle_enabled";
    private static final String SETTING_REPEAT_MODE = "repeat_mode";
    private static final String SETTING_PLAYBACK_RESUME_REQUESTED = "playback_resume_requested";

    public EchoDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    EchoDatabaseHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCoreTables(db);
        createPlayEventTable(db);
        createPlaylistTables(db);
        createSettingsTable(db);
        createRemoteSourceTables(db);
        createPlaybackQueueTable(db);
        createStreamingTrackMatchTable(db);
        ensurePlaybackQueueColumns(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createCoreTables(db);
        ensureTrackColumns(db);
        ensureFavoriteColumns(db);
        ensureHistoryColumns(db);
        createPlayEventTable(db);
        backfillPlayEventsFromHistory(db);
        createPlaylistTables(db);
        createSettingsTable(db);
        createRemoteSourceTables(db);
        createPlaybackQueueTable(db);
        createStreamingTrackMatchTable(db);
        ensurePlaybackQueueColumns(db);
    }

    private void createCoreTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TRACKS + " ("
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
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FAVORITES + " ("
                + "track_id INTEGER PRIMARY KEY,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " ("
                + "track_id INTEGER PRIMARY KEY,"
                + "played_at INTEGER NOT NULL,"
                + "play_count INTEGER NOT NULL DEFAULT 1"
                + ")");
    }

    private void ensureTrackColumns(SQLiteDatabase db) {
        ensureColumn(db, TABLE_TRACKS, "title", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "artist", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "album", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "duration_ms", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "content_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "data_path", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "album_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "album_art_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "codec", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_TRACKS, "bitrate_kbps", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "sample_rate_hz", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "bits_per_sample", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "channel_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_TRACKS, "updated_at", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureFavoriteColumns(SQLiteDatabase db) {
        ensureColumn(db, TABLE_FAVORITES, "created_at", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureHistoryColumns(SQLiteDatabase db) {
        ensureColumn(db, TABLE_HISTORY, "played_at", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_HISTORY, "play_count", "INTEGER NOT NULL DEFAULT 1");
    }

    private void createPlayEventTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PLAY_EVENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "track_id INTEGER NOT NULL,"
                + "played_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_play_events_played_at "
                + "ON " + TABLE_PLAY_EVENTS + " (played_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_play_events_track_time "
                + "ON " + TABLE_PLAY_EVENTS + " (track_id, played_at)");
    }

    private void backfillPlayEventsFromHistory(SQLiteDatabase db) {
        try (Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAY_EVENTS, null)) {
            if (countCursor.moveToFirst() && countCursor.getLong(0) > 0L) {
                return;
            }
        }
        try (Cursor cursor = db.query(
                TABLE_HISTORY,
                new String[]{"track_id", "played_at"},
                null,
                null,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("track_id", cursor.getLong(0));
                values.put("played_at", cursor.getLong(1));
                db.insert(TABLE_PLAY_EVENTS, null, values);
            }
        }
    }

    private void ensureColumn(SQLiteDatabase db, String table, String column, String definition) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createPlaylistTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PLAYLISTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PLAYLIST_TRACKS + " ("
                + "playlist_id INTEGER NOT NULL,"
                + "track_id INTEGER NOT NULL,"
                + "position INTEGER NOT NULL,"
                + "added_at INTEGER NOT NULL,"
                + "PRIMARY KEY (playlist_id, track_id)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist "
                + "ON " + TABLE_PLAYLIST_TRACKS + " (playlist_id, position)");
    }

    private void createSettingsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SETTINGS + " ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT NOT NULL"
                + ")");
    }

    private void createRemoteSourceTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_REMOTE_SOURCES + " ("
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_remote_sources_type "
                + "ON " + TABLE_REMOTE_SOURCES + " (type, updated_at)");
    }

    private void createPlaybackQueueTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PLAYBACK_QUEUE + " ("
                + "position INTEGER PRIMARY KEY,"
                + "track_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "content_uri TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "album_id INTEGER NOT NULL DEFAULT 0,"
                + "album_art_uri TEXT NOT NULL DEFAULT ''"
                + ",codec TEXT NOT NULL DEFAULT ''"
                + ",bitrate_kbps INTEGER NOT NULL DEFAULT 0"
                + ",sample_rate_hz INTEGER NOT NULL DEFAULT 0"
                + ",bits_per_sample INTEGER NOT NULL DEFAULT 0"
                + ",channel_count INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_queue_track "
                + "ON " + TABLE_PLAYBACK_QUEUE + " (track_id)");
    }

    private void createStreamingTrackMatchTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STREAMING_TRACK_MATCHES + " ("
                + "local_key TEXT NOT NULL,"
                + "provider TEXT NOT NULL,"
                + "provider_track_id TEXT NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY (local_key, provider)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_streaming_track_matches_provider_track "
                + "ON " + TABLE_STREAMING_TRACK_MATCHES + " (provider, provider_track_id)");
    }

    private void ensurePlaybackQueueColumns(SQLiteDatabase db) {
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "title", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "artist", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "album", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "duration_ms", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "content_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "data_path", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "album_id", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "album_art_uri", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "codec", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "bitrate_kbps", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "sample_rate_hz", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "bits_per_sample", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, TABLE_PLAYBACK_QUEUE, "channel_count", "INTEGER NOT NULL DEFAULT 0");
    }

    public String loadThemeMode() {
        return loadSetting(SETTING_THEME_MODE, "system");
    }

    public void saveThemeMode(String mode) {
        saveSetting(SETTING_THEME_MODE, mode == null ? "system" : mode);
    }

    public String loadAccentMode() {
        return loadSetting(SETTING_ACCENT_MODE, "blue");
    }

    public void saveAccentMode(String mode) {
        saveSetting(SETTING_ACCENT_MODE, mode == null ? "blue" : mode);
    }

    public String loadLanguageMode() {
        return loadSetting(SETTING_LANGUAGE_MODE, "system");
    }

    public void saveLanguageMode(String mode) {
        saveSetting(SETTING_LANGUAGE_MODE, mode == null ? "system" : mode);
    }

    public float loadPlaybackSpeed() {
        String value = loadSetting(SETTING_PLAYBACK_SPEED, "1.0");
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    public void savePlaybackSpeed(float speed) {
        saveSetting(SETTING_PLAYBACK_SPEED, String.valueOf(speed));
    }

    public float loadAppVolume() {
        String value = loadSetting(SETTING_APP_VOLUME, "1.0");
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    public void saveAppVolume(float volume) {
        saveSetting(SETTING_APP_VOLUME, String.valueOf(volume));
    }

    public String loadStreamingAudioQuality() {
        return loadSetting(SETTING_STREAMING_AUDIO_QUALITY, "high");
    }

    public void saveStreamingAudioQuality(String quality) {
        saveSetting(SETTING_STREAMING_AUDIO_QUALITY, quality == null ? "high" : quality);
    }

    public boolean loadOnlineLyricsEnabled() {
        return "true".equals(loadSetting(SETTING_ONLINE_LYRICS, "false"));
    }

    public void saveOnlineLyricsEnabled(boolean enabled) {
        saveSetting(SETTING_ONLINE_LYRICS, enabled ? "true" : "false");
    }

    public boolean loadConcurrentPlaybackEnabled() {
        return "true".equals(loadSetting(SETTING_CONCURRENT_PLAYBACK, "false"));
    }

    public void saveConcurrentPlaybackEnabled(boolean enabled) {
        saveSetting(SETTING_CONCURRENT_PLAYBACK, enabled ? "true" : "false");
    }

    public long loadLyricsOffsetMs() {
        String value = loadSetting(SETTING_LYRICS_OFFSET_MS, "0");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public void saveLyricsOffsetMs(long offsetMs) {
        saveSetting(SETTING_LYRICS_OFFSET_MS, String.valueOf(offsetMs));
    }

    public boolean loadShuffleEnabled() {
        return "true".equals(loadSetting(SETTING_SHUFFLE_ENABLED, "false"));
    }

    public void saveShuffleEnabled(boolean enabled) {
        saveSetting(SETTING_SHUFFLE_ENABLED, enabled ? "true" : "false");
    }

    public int loadRepeatMode() {
        String value = loadSetting(SETTING_REPEAT_MODE, "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public void saveRepeatMode(int repeatMode) {
        saveSetting(SETTING_REPEAT_MODE, String.valueOf(repeatMode));
    }

    public boolean loadPlaybackResumeRequested() {
        return "true".equals(loadSetting(SETTING_PLAYBACK_RESUME_REQUESTED, "false"));
    }

    public void savePlaybackResumeRequested(boolean requested) {
        saveSetting(SETTING_PLAYBACK_RESUME_REQUESTED, requested ? "true" : "false");
    }

    private String loadSetting(String key, String fallback) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SETTINGS,
                new String[]{"value"},
                "key = ?",
                new String[]{key},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        }
    }

    private void saveSetting(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        saveSettingWithDatabase(db, key, value);
    }

    private void saveSettingWithDatabase(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceTracks(List<Track> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_TRACKS,
                    "data_path NOT LIKE ? AND data_path NOT LIKE ? AND data_path NOT LIKE ? AND data_path NOT LIKE ?",
                    new String[]{"document:%", "stream:%", "streaming:%", "webdav:%"});
            long now = System.currentTimeMillis();
            for (Track track : tracks) {
                db.insertWithOnConflict(TABLE_TRACKS, null, trackValues(track, now), SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void upsertTracks(List<Track> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Track track : tracks) {
                db.insertWithOnConflict(TABLE_TRACKS, null, trackValues(track, now), SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public String loadStreamingTrackMatch(String localKey, String provider) {
        if (localKey == null || localKey.isEmpty() || provider == null || provider.isEmpty()) {
            return "";
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_STREAMING_TRACK_MATCHES,
                new String[]{"provider_track_id"},
                "local_key = ? AND provider = ?",
                new String[]{localKey, provider},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    public void saveStreamingTrackMatch(String localKey, String provider, String providerTrackId, Track track) {
        if (localKey == null || localKey.isEmpty()
                || provider == null || provider.isEmpty()
                || providerTrackId == null || providerTrackId.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("local_key", localKey);
        values.put("provider", provider);
        values.put("provider_track_id", providerTrackId);
        values.put("title", track == null ? "" : track.title);
        values.put("artist", track == null ? "" : track.artist);
        values.put("data_path", track == null ? "" : track.dataPath);
        values.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE_STREAMING_TRACK_MATCHES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int updateAudioSpecs(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            int updated = 0;
            for (Track track : tracks) {
                if (track == null || !track.hasAudioSpec()) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("codec", track.codec);
                values.put("bitrate_kbps", track.bitrateKbps);
                values.put("sample_rate_hz", track.sampleRateHz);
                values.put("bits_per_sample", track.bitsPerSample);
                values.put("channel_count", track.channelCount);
                values.put("updated_at", now);
                updated += db.update(TABLE_TRACKS, values, "id = ?", new String[]{String.valueOf(track.id)});
                ContentValues queueValues = new ContentValues(values);
                queueValues.remove("updated_at");
                db.update(TABLE_PLAYBACK_QUEUE, queueValues, "track_id = ?", new String[]{String.valueOf(track.id)});
            }
            db.setTransactionSuccessful();
            return updated;
        } finally {
            db.endTransaction();
        }
    }

    public void replaceTrackAndMigrateReferences(long oldTrackId, Track replacement) {
        if (replacement == null) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            db.insertWithOnConflict(TABLE_TRACKS, null, trackValues(replacement, now), SQLiteDatabase.CONFLICT_REPLACE);
            long newTrackId = replacement.id;
            if (oldTrackId != newTrackId) {
                migrateFavoriteReference(db, oldTrackId, newTrackId);
                migrateHistoryReference(db, oldTrackId, newTrackId);
                migratePlayEventsReference(db, oldTrackId, newTrackId);
                migratePlaylistReferences(db, oldTrackId, newTrackId);
                migratePlaybackQueueReferences(db, oldTrackId, newTrackId);
                migratePlaybackPositionReference(db, oldTrackId, newTrackId);
                db.delete(TABLE_TRACKS, "id = ?", new String[]{String.valueOf(oldTrackId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int replaceRemoteSourceTracks(long sourceId, List<Track> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = deleteTracksWhere(db, "data_path LIKE ?", new String[]{"webdav:" + sourceId + ":%"});
            long now = System.currentTimeMillis();
            if (tracks != null) {
                for (Track track : tracks) {
                    db.insertWithOnConflict(TABLE_TRACKS, null, trackValues(track, now), SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues trackValues(Track track, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put("id", track.id);
        values.put("title", track.title);
        values.put("artist", track.artist);
        values.put("album", track.album);
        values.put("duration_ms", track.durationMs);
        values.put("content_uri", track.contentUri.toString());
        values.put("data_path", track.dataPath);
        values.put("album_id", track.albumId);
        values.put("album_art_uri", track.albumArtUriString());
        values.put("codec", track.codec);
        values.put("bitrate_kbps", track.bitrateKbps);
        values.put("sample_rate_hz", track.sampleRateHz);
        values.put("bits_per_sample", track.bitsPerSample);
        values.put("channel_count", track.channelCount);
        values.put("updated_at", updatedAt);
        return values;
    }

    private ContentValues playbackQueueValues(Track track, int position) {
        ContentValues values = playbackQueueTrackValues(track);
        values.put("position", position);
        return values;
    }

    private ContentValues playbackQueueTrackValues(Track track) {
        ContentValues values = new ContentValues();
        values.put("track_id", track.id);
        values.put("title", track.title);
        values.put("artist", track.artist);
        values.put("album", track.album);
        values.put("duration_ms", track.durationMs);
        values.put("content_uri", track.contentUri.toString());
        values.put("data_path", track.dataPath);
        values.put("album_id", track.albumId);
        values.put("album_art_uri", track.albumArtUriString());
        values.put("codec", track.codec);
        values.put("bitrate_kbps", track.bitrateKbps);
        values.put("sample_rate_hz", track.sampleRateHz);
        values.put("bits_per_sample", track.bitsPerSample);
        values.put("channel_count", track.channelCount);
        return values;
    }

    private Track readPlaybackQueueTrack(Cursor cursor) {
        long id = cursor.getLong(0);
        String title = cursor.getString(1);
        String dataPath = cursor.getString(6);
        if (title == null || title.isEmpty() || dataPath == null || dataPath.isEmpty()) {
            if (cursor.isNull(14)) {
                return null;
            }
            return new Track(
                    id,
                    cursor.getString(14),
                    cursor.getString(15),
                    cursor.getString(16),
                    cursor.getLong(17),
                    Uri.parse(cursor.getString(18)),
                    cursor.getString(19),
                    cursor.getLong(20),
                    uriOrNull(cursor.getString(21)),
                    cursor.getString(22),
                    cursor.getInt(23),
                    cursor.getInt(24),
                    cursor.getInt(25),
                    cursor.getInt(26)
            );
        }
        return new Track(
                id,
                title,
                cursor.getString(2),
                cursor.getString(3),
                cursor.getLong(4),
                Uri.parse(cursor.getString(5)),
                dataPath,
                cursor.getLong(7),
                uriOrNull(cursor.getString(8)),
                cursor.getString(9),
                cursor.getInt(10),
                cursor.getInt(11),
                cursor.getInt(12),
                cursor.getInt(13)
        );
    }

    public List<Track> loadTracks() {
        ArrayList<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_TRACKS,
                trackProjection(),
                null,
                null,
                null,
                null,
                "artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                tracks.add(new Track(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        Uri.parse(cursor.getString(5)),
                        cursor.getString(6),
                        cursor.getLong(7),
                        uriOrNull(cursor.getString(8)),
                        cursor.getString(9),
                        cursor.getInt(10),
                        cursor.getInt(11),
                        cursor.getInt(12),
                        cursor.getInt(13)
                ));
            }
        }
        return tracks;
    }

    public List<Track> loadRemoteSourceTracks(long sourceId) {
        ArrayList<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_TRACKS,
                trackProjection(),
                "data_path LIKE ?",
                new String[]{"webdav:" + sourceId + ":%"},
                null,
                null,
                "artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                tracks.add(new Track(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        Uri.parse(cursor.getString(5)),
                        cursor.getString(6),
                        cursor.getLong(7),
                        uriOrNull(cursor.getString(8)),
                        cursor.getString(9),
                        cursor.getInt(10),
                        cursor.getInt(11),
                        cursor.getInt(12),
                        cursor.getInt(13)
                ));
            }
        }
        return tracks;
    }

    public void savePlaybackQueue(List<Track> tracks, int currentIndex) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_PLAYBACK_QUEUE, null, null);
            if (tracks != null) {
                for (int i = 0; i < tracks.size(); i++) {
                    db.insertWithOnConflict(TABLE_PLAYBACK_QUEUE, null, playbackQueueValues(tracks.get(i), i), SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            saveSettingWithDatabase(db, SETTING_PLAYBACK_QUEUE_INDEX, String.valueOf(currentIndex));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Track> loadPlaybackQueueTracks() {
        return loadPlaybackQueueTrackSnapshots(getReadableDatabase());
    }

    private ArrayList<Track> loadPlaybackQueueTrackSnapshots(SQLiteDatabase db) {
        ArrayList<Track> tracks = new ArrayList<>();
        String sql = "SELECT q.track_id, q.title, q.artist, q.album, q.duration_ms, q.content_uri, "
                + "q.data_path, q.album_id, q.album_art_uri, q.codec, q.bitrate_kbps, q.sample_rate_hz, "
                + "q.bits_per_sample, q.channel_count, "
                + "t.title, t.artist, t.album, t.duration_ms, t.content_uri, t.data_path, t.album_id, "
                + "t.album_art_uri, t.codec, t.bitrate_kbps, t.sample_rate_hz, t.bits_per_sample, t.channel_count "
                + "FROM " + TABLE_PLAYBACK_QUEUE + " q "
                + "LEFT JOIN " + TABLE_TRACKS + " t ON t.id = q.track_id "
                + "ORDER BY q.position ASC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                Track track = readPlaybackQueueTrack(cursor);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }
        return tracks;
    }

    public int loadPlaybackQueueIndex() {
        String value = loadSetting(SETTING_PLAYBACK_QUEUE_INDEX, "-1");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public long loadPlaybackPositionTrackId() {
        String value = loadSetting(SETTING_PLAYBACK_POSITION_TRACK_ID, "-1");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    public long loadPlaybackPositionMs() {
        String value = loadSetting(SETTING_PLAYBACK_POSITION_MS, "0");
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public void savePlaybackPosition(long trackId, long positionMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            saveSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_TRACK_ID, String.valueOf(trackId));
            saveSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_MS, String.valueOf(Math.max(0L, positionMs)));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public long saveRemoteSource(RemoteSource source) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = remoteSourceValues(source, now);
        if (source.id > 0L) {
            int updated = db.update(TABLE_REMOTE_SOURCES, values, "id = ?", new String[]{String.valueOf(source.id)});
            if (updated > 0) {
                return source.id;
            }
        }
        return db.insertWithOnConflict(TABLE_REMOTE_SOURCES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateRemoteSourceStatus(long sourceId, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("last_status", status == null ? "" : status);
        values.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_REMOTE_SOURCES, values, "id = ?", new String[]{String.valueOf(sourceId)});
    }

    public List<RemoteSource> loadRemoteSources() {
        ArrayList<RemoteSource> sources = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_REMOTE_SOURCES,
                new String[]{"id", "type", "name", "base_url", "username", "password", "root_path", "last_status", "updated_at"},
                null,
                null,
                null,
                null,
                "updated_at DESC, name COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                sources.add(readRemoteSource(cursor));
            }
        }
        return sources;
    }

    public RemoteSource loadRemoteSource(long sourceId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_REMOTE_SOURCES,
                new String[]{"id", "type", "name", "base_url", "username", "password", "root_path", "last_status", "updated_at"},
                "id = ?",
                new String[]{String.valueOf(sourceId)},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? readRemoteSource(cursor) : null;
        }
    }

    public void deleteRemoteSource(long sourceId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            deleteTracksWhere(db, "data_path LIKE ?", new String[]{"webdav:" + sourceId + ":%"});
            db.delete(TABLE_REMOTE_SOURCES, "id = ?", new String[]{String.valueOf(sourceId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int deleteRemoteSourceTracks(long sourceId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = deleteTracksWhere(db, "data_path LIKE ?", new String[]{"webdav:" + sourceId + ":%"});
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    public int deleteStreamTracks() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = deleteTracksWhere(db, "data_path LIKE ?", new String[]{"stream:%"});
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    public int deleteTrack(long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = deleteTracksWhere(db, "id = ?", new String[]{String.valueOf(trackId)});
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    private int deleteTracksWhere(SQLiteDatabase db, String whereClause, String[] whereArgs) {
        ArrayList<Long> trackIds = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_TRACKS,
                new String[]{"id"},
                whereClause,
                whereArgs,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                trackIds.add(cursor.getLong(0));
            }
        }
        ArrayList<Long> queuedTrackIds = loadPlaybackQueueTrackIds(db);
        HashSet<Long> deletedTrackIds = new HashSet<>(trackIds);
        for (Long trackId : trackIds) {
            String[] args = new String[]{String.valueOf(trackId)};
            db.delete(TABLE_FAVORITES, "track_id = ?", args);
            db.delete(TABLE_HISTORY, "track_id = ?", args);
            db.delete(TABLE_PLAYLIST_TRACKS, "track_id = ?", args);
            db.delete(TABLE_PLAYBACK_QUEUE, "track_id = ?", args);
        }
        int removed = db.delete(TABLE_TRACKS, whereClause, whereArgs);
        if (removed > 0) {
            reconcilePlaybackStateAfterTrackDelete(db, deletedTrackIds, queuedTrackIds);
        }
        return removed;
    }

    private ArrayList<Long> loadPlaybackQueueTrackIds(SQLiteDatabase db) {
        ArrayList<Long> trackIds = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_PLAYBACK_QUEUE,
                new String[]{"track_id"},
                null,
                null,
                null,
                null,
                "position ASC"
        )) {
            while (cursor.moveToNext()) {
                trackIds.add(cursor.getLong(0));
            }
        }
        return trackIds;
    }

    private void reconcilePlaybackStateAfterTrackDelete(
            SQLiteDatabase db,
            Set<Long> deletedTrackIds,
            ArrayList<Long> queuedTrackIdsBeforeDelete
    ) {
        if (deletedTrackIds == null || deletedTrackIds.isEmpty()) {
            return;
        }
        clearDeletedPlaybackPosition(db, deletedTrackIds);
        int currentIndex = parsePlaybackQueueIndex(loadSettingWithDatabase(db, SETTING_PLAYBACK_QUEUE_INDEX, "-1"));
        int removedBeforeCurrent = 0;
        if (currentIndex >= 0) {
            for (int i = 0; i < queuedTrackIdsBeforeDelete.size() && i < currentIndex; i++) {
                if (deletedTrackIds.contains(queuedTrackIdsBeforeDelete.get(i))) {
                    removedBeforeCurrent++;
                }
            }
        }
        int remainingCount = compactPlaybackQueue(db);
        int newCurrentIndex = -1;
        if (remainingCount > 0 && currentIndex >= 0) {
            newCurrentIndex = Math.min(Math.max(currentIndex - removedBeforeCurrent, 0), remainingCount - 1);
        }
        saveSettingWithDatabase(db, SETTING_PLAYBACK_QUEUE_INDEX, String.valueOf(newCurrentIndex));
    }

    private void clearDeletedPlaybackPosition(SQLiteDatabase db, Set<Long> deletedTrackIds) {
        long savedTrackId;
        try {
            savedTrackId = Long.parseLong(loadSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_TRACK_ID, "-1"));
        } catch (NumberFormatException ignored) {
            savedTrackId = -1L;
        }
        if (deletedTrackIds.contains(savedTrackId)) {
            saveSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_TRACK_ID, "-1");
            saveSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_MS, "0");
        }
    }

    private int compactPlaybackQueue(SQLiteDatabase db) {
        ArrayList<Track> orderedTracks = loadPlaybackQueueTrackSnapshots(db);
        db.delete(TABLE_PLAYBACK_QUEUE, null, null);
        for (int i = 0; i < orderedTracks.size(); i++) {
            db.insertWithOnConflict(TABLE_PLAYBACK_QUEUE, null, playbackQueueValues(orderedTracks.get(i), i), SQLiteDatabase.CONFLICT_REPLACE);
        }
        return orderedTracks.size();
    }

    private void migrateFavoriteReference(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        if (!rowExists(db, TABLE_FAVORITES, "track_id = ?", new String[]{String.valueOf(oldTrackId)})) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("track_id", newTrackId);
        values.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.delete(TABLE_FAVORITES, "track_id = ?", new String[]{String.valueOf(oldTrackId)});
    }

    private void migrateHistoryReference(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        long playedAt = 0L;
        int playCount = 0;
        boolean hasOld = false;
        try (Cursor cursor = db.query(
                TABLE_HISTORY,
                new String[]{"played_at", "play_count"},
                "track_id = ?",
                new String[]{String.valueOf(oldTrackId)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                hasOld = true;
                playedAt = cursor.getLong(0);
                playCount = cursor.getInt(1);
            }
        }
        if (!hasOld) {
            return;
        }
        try (Cursor cursor = db.query(
                TABLE_HISTORY,
                new String[]{"played_at", "play_count"},
                "track_id = ?",
                new String[]{String.valueOf(newTrackId)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                playedAt = Math.max(playedAt, cursor.getLong(0));
                playCount += cursor.getInt(1);
            }
        }
        ContentValues values = new ContentValues();
        values.put("track_id", newTrackId);
        values.put("played_at", playedAt);
        values.put("play_count", Math.max(playCount, 1));
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.delete(TABLE_HISTORY, "track_id = ?", new String[]{String.valueOf(oldTrackId)});
    }

    private void migratePlayEventsReference(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        ContentValues values = new ContentValues();
        values.put("track_id", newTrackId);
        db.update(TABLE_PLAY_EVENTS, values, "track_id = ?", new String[]{String.valueOf(oldTrackId)});
    }

    private void migratePlaylistReferences(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        ArrayList<Long> playlistIds = new ArrayList<>();
        ArrayList<Integer> positions = new ArrayList<>();
        ArrayList<Long> addedTimes = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_PLAYLIST_TRACKS,
                new String[]{"playlist_id", "position", "added_at"},
                "track_id = ?",
                new String[]{String.valueOf(oldTrackId)},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                playlistIds.add(cursor.getLong(0));
                positions.add(cursor.getInt(1));
                addedTimes.add(cursor.getLong(2));
            }
        }
        for (int i = 0; i < playlistIds.size(); i++) {
            long playlistId = playlistIds.get(i);
            if (!rowExists(db, TABLE_PLAYLIST_TRACKS, "playlist_id = ? AND track_id = ?",
                    new String[]{String.valueOf(playlistId), String.valueOf(newTrackId)})) {
                ContentValues values = new ContentValues();
                values.put("playlist_id", playlistId);
                values.put("track_id", newTrackId);
                values.put("position", positions.get(i));
                values.put("added_at", addedTimes.get(i));
                db.insertWithOnConflict(TABLE_PLAYLIST_TRACKS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            touchPlaylist(db, playlistId, System.currentTimeMillis());
        }
        db.delete(TABLE_PLAYLIST_TRACKS, "track_id = ?", new String[]{String.valueOf(oldTrackId)});
    }

    private void migratePlaybackQueueReferences(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        boolean hasOldTrack = rowExists(db, TABLE_PLAYBACK_QUEUE, "track_id = ?",
                new String[]{String.valueOf(oldTrackId)});
        if (!hasOldTrack) {
            return;
        }
        boolean hasNewTrack = rowExists(db, TABLE_PLAYBACK_QUEUE, "track_id = ?",
                new String[]{String.valueOf(newTrackId)});
        if (hasNewTrack) {
            collapsePlaybackQueueReferences(db, oldTrackId, newTrackId);
            return;
        }
        Track replacement = loadTrackById(db, newTrackId);
        ContentValues values = replacement == null ? new ContentValues() : playbackQueueTrackValues(replacement);
        values.put("track_id", newTrackId);
        db.update(TABLE_PLAYBACK_QUEUE, values, "track_id = ?", new String[]{String.valueOf(oldTrackId)});
    }

    private void collapsePlaybackQueueReferences(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        ArrayList<Track> orderedTracks = loadPlaybackQueueTrackSnapshots(db);
        Track replacement = loadTrackById(db, newTrackId);
        int currentIndex = parsePlaybackQueueIndex(loadSettingWithDatabase(db, SETTING_PLAYBACK_QUEUE_INDEX, "-1"));
        int preferredIndex = -1;
        if (currentIndex >= 0 && currentIndex < orderedTracks.size()) {
            long currentTrackId = orderedTracks.get(currentIndex).id;
            if (currentTrackId == oldTrackId || currentTrackId == newTrackId) {
                preferredIndex = currentIndex;
            }
        }
        if (preferredIndex < 0) {
            for (int i = 0; i < orderedTracks.size(); i++) {
                long trackId = orderedTracks.get(i).id;
                if (trackId == oldTrackId || trackId == newTrackId) {
                    preferredIndex = i;
                    break;
                }
            }
        }
        if (preferredIndex < 0) {
            return;
        }

        db.delete(TABLE_PLAYBACK_QUEUE, null, null);
        int newCurrentIndex = -1;
        int position = 0;
        for (int i = 0; i < orderedTracks.size(); i++) {
            Track track = orderedTracks.get(i);
            long trackId = track.id;
            boolean isOldTrack = trackId == oldTrackId;
            boolean isNewTrack = trackId == newTrackId;
            if (isOldTrack || isNewTrack) {
                if (i != preferredIndex) {
                    continue;
                }
                track = replacement == null ? track : replacement;
            }
            boolean currentIsMergedTrack = currentIndex >= 0
                    && currentIndex < orderedTracks.size()
                    && (orderedTracks.get(currentIndex).id == oldTrackId
                    || orderedTracks.get(currentIndex).id == newTrackId);
            if (i == currentIndex || i == preferredIndex && currentIsMergedTrack) {
                newCurrentIndex = position;
            }
            db.insertWithOnConflict(TABLE_PLAYBACK_QUEUE, null, playbackQueueValues(track, position), SQLiteDatabase.CONFLICT_REPLACE);
            position++;
        }
        if (newCurrentIndex < 0 && currentIndex >= 0 && position > 0) {
            newCurrentIndex = Math.min(Math.max(currentIndex, 0), position - 1);
        }
        saveSettingWithDatabase(db, SETTING_PLAYBACK_QUEUE_INDEX, String.valueOf(newCurrentIndex));
    }

    private int parsePlaybackQueueIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void migratePlaybackPositionReference(SQLiteDatabase db, long oldTrackId, long newTrackId) {
        String current = loadSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_TRACK_ID, "-1");
        if (String.valueOf(oldTrackId).equals(current)) {
            saveSettingWithDatabase(db, SETTING_PLAYBACK_POSITION_TRACK_ID, String.valueOf(newTrackId));
        }
    }

    private boolean rowExists(SQLiteDatabase db, String table, String whereClause, String[] whereArgs) {
        try (Cursor cursor = db.query(
                table,
                new String[]{"1"},
                whereClause,
                whereArgs,
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private String loadSettingWithDatabase(SQLiteDatabase db, String key, String fallback) {
        try (Cursor cursor = db.query(
                TABLE_SETTINGS,
                new String[]{"value"},
                "key = ?",
                new String[]{key},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        }
    }

    private ContentValues remoteSourceValues(RemoteSource source, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put("type", source.type);
        values.put("name", source.name);
        values.put("base_url", source.baseUrl);
        values.put("username", source.username);
        values.put("password", source.password);
        values.put("root_path", source.rootPath);
        values.put("last_status", source.lastStatus);
        values.put("updated_at", updatedAt);
        return values;
    }

    private Uri uriOrNull(String value) {
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    private RemoteSource readRemoteSource(Cursor cursor) {
        return new RemoteSource(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                cursor.getString(7),
                cursor.getLong(8)
        );
    }

    public void markPlayed(long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            int updated = db.update(
                    TABLE_HISTORY,
                    historyValues(now, -1),
                    "track_id = ?",
                    new String[]{String.valueOf(trackId)}
            );
            if (updated == 0) {
                ContentValues values = new ContentValues();
                values.put("track_id", trackId);
                values.put("played_at", now);
                values.put("play_count", 1);
                db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } else {
                db.execSQL("UPDATE " + TABLE_HISTORY + " SET play_count = play_count + 1 WHERE track_id = ?", new Object[]{trackId});
            }
            ContentValues eventValues = new ContentValues();
            eventValues.put("track_id", trackId);
            eventValues.put("played_at", now);
            db.insert(TABLE_PLAY_EVENTS, null, eventValues);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        ArrayList<TrackPlayRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT "
                + trackColumns("t") + ", h.played_at, h.play_count "
                + "FROM " + TABLE_HISTORY + " h "
                + "JOIN " + TABLE_TRACKS + " t ON t.id = h.track_id "
                + "ORDER BY h.played_at DESC "
                + "LIMIT ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(Math.max(limit, 1))})) {
            while (cursor.moveToNext()) {
                records.add(new TrackPlayRecord(readTrack(cursor, 0), cursor.getLong(14), cursor.getInt(15)));
            }
        }
        return records;
    }

    public List<TrackPlayRecord> loadMostPlayed(int limit) {
        ArrayList<TrackPlayRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT "
                + trackColumns("t") + ", h.played_at, h.play_count "
                + "FROM " + TABLE_HISTORY + " h "
                + "JOIN " + TABLE_TRACKS + " t ON t.id = h.track_id "
                + "ORDER BY h.play_count DESC, h.played_at DESC "
                + "LIMIT ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(Math.max(limit, 1))})) {
            while (cursor.moveToNext()) {
                records.add(new TrackPlayRecord(readTrack(cursor, 0), cursor.getLong(14), cursor.getInt(15)));
            }
        }
        return records;
    }

    public List<TrackPlayRecord> loadPlayedSince(long startMs, int limit) {
        ArrayList<TrackPlayRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT "
                + trackColumns("t") + ", MAX(e.played_at) AS played_at, COUNT(*) AS play_count "
                + "FROM " + TABLE_PLAY_EVENTS + " e "
                + "JOIN " + TABLE_TRACKS + " t ON t.id = e.track_id "
                + "WHERE e.played_at >= ? "
                + "GROUP BY t.id "
                + "ORDER BY played_at DESC "
                + "LIMIT ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(Math.max(0L, startMs)),
                String.valueOf(Math.max(limit, 1))
        })) {
            while (cursor.moveToNext()) {
                records.add(new TrackPlayRecord(readTrack(cursor, 0), cursor.getLong(14), cursor.getInt(15)));
            }
        }
        return records;
    }

    public int clearPlayHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = db.delete(TABLE_HISTORY, null, null);
            db.delete(TABLE_PLAY_EVENTS, null, null);
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues historyValues(long playedAt, int playCount) {
        ContentValues values = new ContentValues();
        values.put("played_at", playedAt);
        if (playCount >= 0) {
            values.put("play_count", playCount);
        }
        return values;
    }

    public void setFavorite(long trackId, boolean favorite) {
        SQLiteDatabase db = getWritableDatabase();
        if (favorite) {
            ContentValues values = new ContentValues();
            values.put("track_id", trackId);
            values.put("created_at", System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            db.delete(TABLE_FAVORITES, "track_id = ?", new String[]{String.valueOf(trackId)});
        }
    }

    public boolean isFavorite(long trackId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FAVORITES,
                new String[]{"track_id"},
                "track_id = ?",
                new String[]{String.valueOf(trackId)},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst();
        }
    }

    public Set<Long> loadFavoriteIds() {
        HashSet<Long> ids = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FAVORITES,
                new String[]{"track_id"},
                null,
                null,
                null,
                null,
                "created_at DESC"
        )) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }

    public List<Track> loadFavoriteTracks() {
        ArrayList<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT " + trackColumns("t") + " "
                + "FROM " + TABLE_FAVORITES + " f "
                + "JOIN " + TABLE_TRACKS + " t ON t.id = f.track_id "
                + "ORDER BY f.created_at DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                tracks.add(readTrack(cursor, 0));
            }
        }
        return tracks;
    }

    public long createPlaylist(String name) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", cleanPlaylistName(name));
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insertWithOnConflict(TABLE_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public boolean renamePlaylist(long playlistId, String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", cleanPlaylistName(name));
        values.put("updated_at", System.currentTimeMillis());
        int updated = db.updateWithOnConflict(
                TABLE_PLAYLISTS,
                values,
                "id = ?",
                new String[]{String.valueOf(playlistId)},
                SQLiteDatabase.CONFLICT_IGNORE
        );
        return updated > 0;
    }

    public boolean deletePlaylist(long playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(
                    TABLE_PLAYLIST_TRACKS,
                    "playlist_id = ?",
                    new String[]{String.valueOf(playlistId)}
            );
            int deleted = db.delete(
                    TABLE_PLAYLISTS,
                    "id = ?",
                    new String[]{String.valueOf(playlistId)}
            );
            db.setTransactionSuccessful();
            return deleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public List<Playlist> loadPlaylists() {
        ArrayList<Playlist> playlists = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT p.id, p.name, COUNT(pt.track_id) AS track_count, p.created_at, p.updated_at "
                + "FROM " + TABLE_PLAYLISTS + " p "
                + "LEFT JOIN " + TABLE_PLAYLIST_TRACKS + " pt ON pt.playlist_id = p.id "
                + "GROUP BY p.id, p.name, p.created_at, p.updated_at "
                + "ORDER BY p.updated_at DESC, p.name COLLATE NOCASE ASC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                playlists.add(new Playlist(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getLong(3),
                        cursor.getLong(4)
                ));
            }
        }
        return playlists;
    }

    public boolean addTrackToPlaylist(long playlistId, long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        int position = nextPlaylistPosition(db, playlistId);
        ContentValues values = new ContentValues();
        values.put("playlist_id", playlistId);
        values.put("track_id", trackId);
        values.put("position", position);
        values.put("added_at", now);
        long inserted = db.insertWithOnConflict(TABLE_PLAYLIST_TRACKS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (inserted != -1L) {
            touchPlaylist(db, playlistId, now);
            return true;
        }
        return false;
    }

    public void removeTrackFromPlaylist(long playlistId, long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        int removed = db.delete(
                TABLE_PLAYLIST_TRACKS,
                "playlist_id = ? AND track_id = ?",
                new String[]{String.valueOf(playlistId), String.valueOf(trackId)}
        );
        if (removed > 0) {
            touchPlaylist(db, playlistId, System.currentTimeMillis());
        }
    }

    public void clearPlaylistTracks(long playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(
                TABLE_PLAYLIST_TRACKS,
                "playlist_id = ?",
                new String[]{String.valueOf(playlistId)}
        );
        touchPlaylist(db, playlistId, System.currentTimeMillis());
    }

    public boolean movePlaylistTrack(long playlistId, long trackId, int direction) {
        if (direction == 0) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int currentPosition = playlistTrackPosition(db, playlistId, trackId);
            if (currentPosition < 0) {
                return false;
            }
            String operator = direction < 0 ? "<" : ">";
            String order = direction < 0 ? "DESC" : "ASC";
            String sql = "SELECT track_id, position FROM " + TABLE_PLAYLIST_TRACKS
                    + " WHERE playlist_id = ? AND position " + operator + " ?"
                    + " ORDER BY position " + order + " LIMIT 1";
            long neighborTrackId = -1L;
            int neighborPosition = -1;
            try (Cursor cursor = db.rawQuery(sql, new String[]{
                    String.valueOf(playlistId),
                    String.valueOf(currentPosition)
            })) {
                if (cursor.moveToFirst()) {
                    neighborTrackId = cursor.getLong(0);
                    neighborPosition = cursor.getInt(1);
                }
            }
            if (neighborTrackId < 0L || neighborPosition < 0) {
                return false;
            }
            updatePlaylistTrackPosition(db, playlistId, trackId, neighborPosition);
            updatePlaylistTrackPosition(db, playlistId, neighborTrackId, currentPosition);
            touchPlaylist(db, playlistId, System.currentTimeMillis());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public boolean movePlaylistTrackAt(long playlistId, int trackIndex, int direction) {
        int neighborIndex = trackIndex + direction;
        if (trackIndex < 0 || neighborIndex < 0 || direction == 0) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ArrayList<Long> trackIds = new ArrayList<>();
            ArrayList<Integer> positions = new ArrayList<>();
            String sql = "SELECT track_id, position FROM " + TABLE_PLAYLIST_TRACKS
                    + " WHERE playlist_id = ?"
                    + " ORDER BY position ASC, added_at ASC";
            try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(playlistId)})) {
                while (cursor.moveToNext()) {
                    trackIds.add(cursor.getLong(0));
                    positions.add(cursor.getInt(1));
                }
            }
            if (trackIndex >= trackIds.size() || neighborIndex >= trackIds.size()) {
                return false;
            }
            long currentTrackId = trackIds.get(trackIndex);
            long neighborTrackId = trackIds.get(neighborIndex);
            int currentPosition = positions.get(trackIndex);
            int neighborPosition = positions.get(neighborIndex);
            updatePlaylistTrackPosition(db, playlistId, currentTrackId, neighborPosition);
            updatePlaylistTrackPosition(db, playlistId, neighborTrackId, currentPosition);
            touchPlaylist(db, playlistId, System.currentTimeMillis());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public List<Track> loadPlaylistTracks(long playlistId) {
        ArrayList<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT " + trackColumns("t") + " "
                + "FROM " + TABLE_PLAYLIST_TRACKS + " pt "
                + "JOIN " + TABLE_TRACKS + " t ON t.id = pt.track_id "
                + "WHERE pt.playlist_id = ? "
                + "ORDER BY pt.position ASC, pt.added_at ASC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(playlistId)})) {
            while (cursor.moveToNext()) {
                tracks.add(readTrack(cursor, 0));
            }
        }
        return tracks;
    }

    private int playlistTrackPosition(SQLiteDatabase db, long playlistId, long trackId) {
        String sql = "SELECT position FROM " + TABLE_PLAYLIST_TRACKS
                + " WHERE playlist_id = ? AND track_id = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(playlistId),
                String.valueOf(trackId)
        })) {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private void updatePlaylistTrackPosition(SQLiteDatabase db, long playlistId, long trackId, int position) {
        ContentValues values = new ContentValues();
        values.put("position", position);
        db.update(
                TABLE_PLAYLIST_TRACKS,
                values,
                "playlist_id = ? AND track_id = ?",
                new String[]{String.valueOf(playlistId), String.valueOf(trackId)}
        );
    }

    private int nextPlaylistPosition(SQLiteDatabase db, long playlistId) {
        String sql = "SELECT COALESCE(MAX(position), -1) + 1 FROM " + TABLE_PLAYLIST_TRACKS
                + " WHERE playlist_id = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(playlistId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private void touchPlaylist(SQLiteDatabase db, long playlistId, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put("updated_at", updatedAt);
        db.update(TABLE_PLAYLISTS, values, "id = ?", new String[]{String.valueOf(playlistId)});
    }

    private String cleanPlaylistName(String name) {
        if (name == null) {
            return "未命名播放列表";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "未命名播放列表" : trimmed;
    }

    private String trackColumns(String alias) {
        return alias + ".id, "
                + alias + ".title, "
                + alias + ".artist, "
                + alias + ".album, "
                + alias + ".duration_ms, "
                + alias + ".content_uri, "
                + alias + ".data_path, "
                + alias + ".album_id, "
                + alias + ".album_art_uri, "
                + alias + ".codec, "
                + alias + ".bitrate_kbps, "
                + alias + ".sample_rate_hz, "
                + alias + ".bits_per_sample, "
                + alias + ".channel_count";
    }

    private String[] trackProjection() {
        return new String[]{
                "id",
                "title",
                "artist",
                "album",
                "duration_ms",
                "content_uri",
                "data_path",
                "album_id",
                "album_art_uri",
                "codec",
                "bitrate_kbps",
                "sample_rate_hz",
                "bits_per_sample",
                "channel_count"
        };
    }

    private Track loadTrackById(SQLiteDatabase db, long trackId) {
        String sql = "SELECT " + trackColumns("t") + " "
                + "FROM " + TABLE_TRACKS + " t "
                + "WHERE t.id = ? "
                + "LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(trackId)})) {
            return cursor.moveToFirst() ? readTrack(cursor, 0) : null;
        }
    }

    private Track readTrack(Cursor cursor, int offset) {
        return new Track(
                cursor.getLong(offset),
                cursor.getString(offset + 1),
                cursor.getString(offset + 2),
                cursor.getString(offset + 3),
                cursor.getLong(offset + 4),
                Uri.parse(cursor.getString(offset + 5)),
                cursor.getString(offset + 6),
                cursor.getLong(offset + 7),
                uriOrNull(cursor.getString(offset + 8)),
                cursor.getString(offset + 9),
                cursor.getInt(offset + 10),
                cursor.getInt(offset + 11),
                cursor.getInt(offset + 12),
                cursor.getInt(offset + 13)
        );
    }
}
