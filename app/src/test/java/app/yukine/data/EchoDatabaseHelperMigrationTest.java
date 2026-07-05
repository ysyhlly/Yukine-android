package app.yukine.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import app.yukine.model.Track;

/**
 * Migration/幂等性基线测试。锁定 {@link EchoDatabaseHelper#onUpgrade} 的关键契约：
 * 不看 oldVersion、无条件执行全量 create/ensure/backfill，依赖 CREATE TABLE IF NOT
 * EXISTS 与 ensureColumn 的幂等性。本文件只测不改生产代码；若某次 schema 变更
 * 破坏了"升级后 == 新建"或"数据不丢"的契约，这里会先红。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class EchoDatabaseHelperMigrationTest {
    private static final String LEGACY_DB = "test-echo-migration.db";

    private EchoDatabaseHelper helper;

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
            helper = null;
        }
        Context context = ApplicationProvider.getApplicationContext();
        File dbFile = context.getDatabasePath(LEGACY_DB);
        if (dbFile != null) {
            dbFile.delete();
        }
    }

    @Test
    public void freshCreationBuildsCompleteSchema() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), LEGACY_DB);
        SQLiteDatabase db = helper.getWritableDatabase();

        Assert.assertTrue(tableExists(db, "tracks"));
        Assert.assertTrue(tableExists(db, "favorites"));
        Assert.assertTrue(tableExists(db, "play_history"));
        Assert.assertTrue(tableExists(db, "play_events"));
        Assert.assertTrue(tableExists(db, "playlists"));
        Assert.assertTrue(tableExists(db, "playlist_tracks"));
        Assert.assertTrue(tableExists(db, "settings"));
        Assert.assertTrue(tableExists(db, "remote_sources"));
        Assert.assertTrue(tableExists(db, "playback_queue"));
        Assert.assertTrue(tableExists(db, "streaming_track_matches"));

        Set<String> trackCols = columnsOf(db, "tracks");
        Assert.assertTrue("tracks 表应包含全部 17 列", trackCols.containsAll(Arrays.asList(
                "id", "title", "artist", "album", "duration_ms", "content_uri", "data_path",
                "album_id", "album_art_uri", "codec", "bitrate_kbps", "sample_rate_hz",
                "bits_per_sample", "channel_count", "replay_gain_track_db",
                "replay_gain_album_db", "updated_at")));
    }

    @Test
    public void onUpgradeIsIdempotentOnFullSchema() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), LEGACY_DB);
        SQLiteDatabase db = helper.getWritableDatabase();

        Set<String> tracksBefore = columnsOf(db, "tracks");
        Set<String> favoritesBefore = columnsOf(db, "favorites");
        Set<String> historyBefore = columnsOf(db, "play_history");

        // 全量 schema 上再跑一次 onUpgrade，模拟 v13 -> v13 重跑或重复打开。
        helper.onUpgrade(db, 13, 13);

        Assert.assertEquals("tracks 列集不应变化", tracksBefore, columnsOf(db, "tracks"));
        Assert.assertEquals("favorites 列集不应变化", favoritesBefore, columnsOf(db, "favorites"));
        Assert.assertEquals("play_history 列集不应变化", historyBefore, columnsOf(db, "play_history"));
    }

    @Test
    public void onUpgradePreservesTracksAndPlaylistsAcrossReRun() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), LEGACY_DB);
        SQLiteDatabase db = helper.getWritableDatabase();

        Track track = new Track(1L, "Title", "Artist", "Album", 120_000L, Uri.EMPTY, "/path/track.mp3");
        helper.upsertTracks(Arrays.asList(track));
        long playlistId = helper.createPlaylist("PL");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, 1L));

        helper.onUpgrade(db, 12, 13);

        Assert.assertEquals("track 不应丢失", 1, helper.loadTracks().size());
        Assert.assertEquals("playlist 不应丢失", 1, helper.loadPlaylists().size());
        Assert.assertEquals("playlist 关联不应丢失", 1, helper.loadPlaylistTracks(playlistId).size());
    }

    @Test
    public void backfillPlayEventsFromHistorySkipsWhenPlayEventsExist() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), LEGACY_DB);
        SQLiteDatabase db = helper.getWritableDatabase();

        // 模拟升级前 play_events 已有数据：onUpgrade 的 backfill 应早退，不重复插入。
        ContentValues cv = new ContentValues();
        cv.put("track_id", 7L);
        cv.put("played_at", 1000L);
        db.insert("play_events", null, cv);
        Assert.assertEquals(1L, playEventsCount(db));

        helper.onUpgrade(db, 12, 13);

        Assert.assertEquals("play_events 不应被 backfill 重复填充", 1L, playEventsCount(db));
    }

    @Test
    public void onUpgradeAddsMissingColumnsToLegacyTracksTable() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), LEGACY_DB);
        SQLiteDatabase db = helper.getWritableDatabase();

        // 模拟旧版最小 tracks 表：drop 重建为缺列版本（无 codec/replay_gain 等）。
        db.execSQL("DROP TABLE IF EXISTS tracks");
        db.execSQL("CREATE TABLE tracks ("
                + "id INTEGER PRIMARY KEY,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "content_uri TEXT NOT NULL DEFAULT '',"
                + "data_path TEXT NOT NULL DEFAULT '',"
                + "album_id INTEGER NOT NULL DEFAULT 0,"
                + "album_art_uri TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        Assert.assertFalse(columnsOf(db, "tracks").contains("codec"));

        helper.onUpgrade(db, 1, 13);

        Set<String> cols = columnsOf(db, "tracks");
        Assert.assertTrue("ensureTrackColumns 应补齐 codec", cols.contains("codec"));
        Assert.assertTrue("ensureTrackColumns 应补齐 bitrate_kbps", cols.contains("bitrate_kbps"));
        Assert.assertTrue("ensureTrackColumns 应补齐 sample_rate_hz", cols.contains("sample_rate_hz"));
        Assert.assertTrue("ensureTrackColumns 应补齐 bits_per_sample", cols.contains("bits_per_sample"));
        Assert.assertTrue("ensureTrackColumns 应补齐 channel_count", cols.contains("channel_count"));
        Assert.assertTrue("ensureTrackColumns 应补齐 replay_gain_track_db", cols.contains("replay_gain_track_db"));
        Assert.assertTrue("ensureTrackColumns 应补齐 replay_gain_album_db", cols.contains("replay_gain_album_db"));
    }

    @Test
    public void onUpgradeResultMatchesFreshCreationSchema() {
        // 全新创建的 schema 作为参照。
        Context context = ApplicationProvider.getApplicationContext();
        EchoDatabaseHelper freshHelper = new EchoDatabaseHelper(context, "test-echo-fresh-ref.db");
        SQLiteDatabase freshDb = freshHelper.getWritableDatabase();
        Set<String> freshTrackCols = columnsOf(freshDb, "tracks");
        Set<String> freshFavoriteCols = columnsOf(freshDb, "favorites");
        Set<String> freshHistoryCols = columnsOf(freshDb, "play_history");

        try {
            // 旧版最小 tracks 表升级后的 schema。
            helper = new EchoDatabaseHelper(context, LEGACY_DB);
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DROP TABLE IF EXISTS tracks");
            db.execSQL("CREATE TABLE tracks ("
                    + "id INTEGER PRIMARY KEY,"
                    + "title TEXT NOT NULL DEFAULT '',"
                    + "artist TEXT NOT NULL DEFAULT '',"
                    + "album TEXT NOT NULL DEFAULT '',"
                    + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                    + "content_uri TEXT NOT NULL DEFAULT '',"
                    + "data_path TEXT NOT NULL DEFAULT '',"
                    + "album_id INTEGER NOT NULL DEFAULT 0,"
                    + "album_art_uri TEXT NOT NULL DEFAULT '',"
                    + "updated_at INTEGER NOT NULL DEFAULT 0)");
            helper.onUpgrade(db, 1, 13);

            Assert.assertEquals("升级后 tracks 列集应与新建一致",
                    freshTrackCols, columnsOf(db, "tracks"));
            Assert.assertEquals("升级后 favorites 列集应与新建一致",
                    freshFavoriteCols, columnsOf(db, "favorites"));
            Assert.assertEquals("升级后 play_history 列集应与新建一致",
                    freshHistoryCols, columnsOf(db, "play_history"));
        } finally {
            freshHelper.close();
            File freshFile = context.getDatabasePath("test-echo-fresh-ref.db");
            if (freshFile != null) {
                freshFile.delete();
            }
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table})) {
            return c.moveToFirst();
        }
    }

    private static Set<String> columnsOf(SQLiteDatabase db, String table) {
        Set<String> cols = new HashSet<>();
        // PRAGMA 不支持 ? 占位，表名来自测试常量，无注入风险。
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) {
                cols.add(c.getString(c.getColumnIndexOrThrow("name")));
            }
        }
        return cols;
    }

    private static long playEventsCount(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM play_events", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }
}
