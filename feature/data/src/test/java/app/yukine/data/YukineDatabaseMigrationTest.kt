package app.yukine.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YukineDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun versionOneFixtureMigratesWithoutLosingLibraryFavoritesOrHistory() {
        val name = databaseName("v1")
        createVersionOneFixture(name)

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(15, sqlite.version)
        assertEquals("Legacy Track", stringValue(sqlite, "SELECT title FROM tracks WHERE id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites WHERE track_id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT play_count FROM play_history WHERE track_id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM play_events WHERE track_id = 901"))
        assertTrue(columnExists(sqlite, "tracks", "replay_gain_album_db"))
        assertTrue(columnExists(sqlite, "playback_queue", "codec"))
        database.close()
    }

    @Test
    fun versionFourteenFixturePreservesEveryUserStateDomain() {
        val name = databaseName("v14")
        val helper = EchoDatabaseHelper(context, name)
        val track = Track(
            1401L,
            "Migration Track",
            "Migration Artist",
            "Migration Album",
            180_000L,
            android.net.Uri.parse("file:///migration.flac"),
            "/music/migration.flac",
            14L,
            null
        )
        helper.upsertTracks(listOf(track))
        helper.setFavorite(track.id, true)
        helper.markPlayed(track.id)
        val playlistId = helper.createPlaylist("Migration Playlist")
        helper.addTrackToPlaylist(playlistId, track.id)
        helper.savePlaybackQueue(List(512) { track }, 311)
        helper.savePlaybackPosition(track.id, 54_321L)
        helper.saveThemeMode("dark")
        helper.saveStreamingTrackMatch("local-key", "netease", "provider-1401", track)
        helper.close()

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(15, sqlite.version)
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM tracks WHERE id = 1401"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites WHERE track_id = 1401"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM play_history WHERE track_id = 1401"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlists WHERE id = $playlistId"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = $playlistId"))
        assertEquals(512L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue"))
        assertEquals("311", stringValue(sqlite, "SELECT value FROM settings WHERE `key` = 'playback_queue_index'"))
        assertEquals("54321", stringValue(sqlite, "SELECT value FROM settings WHERE `key` = 'playback_position_ms'"))
        assertEquals("dark", stringValue(sqlite, "SELECT value FROM settings WHERE `key` = 'theme_mode'"))
        assertEquals(
            "provider-1401",
            stringValue(
                sqlite,
                "SELECT provider_track_id FROM streaming_track_matches " +
                    "WHERE local_key = 'local-key' AND provider = 'netease'"
            )
        )
        database.close()
    }

    @Test
    fun invalidNullLegacyPrimaryKeyAbortsAndLeavesOriginalDatabaseUntouched() {
        val name = databaseName("invalid")
        val helper = EchoDatabaseHelper(context, name)
        helper.writableDatabase.execSQL("INSERT INTO settings(`key`, value) VALUES (NULL, 'keep-me')")
        helper.close()

        val database = YukineDatabase.open(context, name)
        try {
            database.openHelper.writableDatabase
            fail("Expected migration to abort before losing the null-key row")
        } catch (expected: IllegalStateException) {
            database.close()
        }

        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        assertEquals(14, raw.version)
        raw.rawQuery("SELECT value FROM settings WHERE `key` IS NULL", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("keep-me", cursor.getString(0))
        }
        raw.close()
    }

    private fun createVersionOneFixture(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.execSQL(
            "CREATE TABLE tracks (" +
                "id INTEGER PRIMARY KEY,title TEXT NOT NULL,artist TEXT NOT NULL," +
                "album TEXT NOT NULL,duration_ms INTEGER NOT NULL,content_uri TEXT NOT NULL," +
                "data_path TEXT NOT NULL,album_id INTEGER NOT NULL,album_art_uri TEXT NOT NULL)"
        )
        database.execSQL("CREATE TABLE favorites (track_id INTEGER PRIMARY KEY)")
        database.execSQL(
            "CREATE TABLE play_history (track_id INTEGER PRIMARY KEY, played_at INTEGER NOT NULL)"
        )
        database.execSQL(
            "INSERT INTO tracks VALUES (901, 'Legacy Track', 'Legacy Artist', 'Legacy Album', " +
                "123000, 'file:///legacy.mp3', '/music/legacy.mp3', 90, '')"
        )
        database.execSQL("INSERT INTO favorites(track_id) VALUES (901)")
        database.execSQL("INSERT INTO play_history(track_id, played_at) VALUES (901, 1700000000000)")
        database.version = 1
        database.close()
    }

    private fun databaseName(suffix: String): String =
        "yukine-room-migration-$suffix.db".also {
            databaseNames += it
            context.deleteDatabase(it)
            File(context.getDatabasePath(it).path + "-wal").delete()
            File(context.getDatabasePath(it).path + "-shm").delete()
        }

    private fun longValue(database: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun stringValue(database: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun columnExists(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean = database.query("PRAGMA table_info($table)").use { cursor ->
        val index = cursor.getColumnIndexOrThrow("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(index) else null }
            .any(column::equals)
    }
}
