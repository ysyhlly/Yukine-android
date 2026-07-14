package app.yukine.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import java.io.File
import java.nio.charset.StandardCharsets
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
    fun roomDoesNotEnableWalForFileCopyBackupCompatibility() {
        val name = databaseName("journal")
        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertTrue(!stringValue(sqlite, "PRAGMA journal_mode").equals("wal", ignoreCase = true))
        database.close()
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
        val playlistId = 77L
        createVersionFourteenFixture(name, playlistId)

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
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM library_exclusions"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM remote_sources WHERE id = 14"))
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
    fun partialVersionEightFixturePreservesPlaylistOrderSettingsAndHistory() {
        val name = databaseName("v8-partial")
        createSqlFixture(name, 8, "/db-fixtures/echo-partial-v8.sql")

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(15, sqlite.version)
        assertEquals("Second", stringValue(sqlite, "SELECT title FROM tracks WHERE id = 802"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites WHERE track_id = 801"))
        assertEquals(4L, longValue(sqlite, "SELECT play_count FROM play_history WHERE track_id = 801"))
        assertEquals(
            802L,
            longValue(
                sqlite,
                "SELECT track_id FROM playlist_tracks WHERE playlist_id = 88 ORDER BY position LIMIT 1"
            )
        )
        assertEquals("zh-CN", stringValue(sqlite, "SELECT value FROM settings WHERE `key` = 'language_mode'"))
        assertTrue(columnExists(sqlite, "tracks", "replay_gain_album_db"))
        assertTrue(columnExists(sqlite, "playback_queue", "codec"))
        database.close()
    }

    @Test
    fun invalidNullLegacyPrimaryKeyAbortsAndLeavesOriginalDatabaseUntouched() {
        val name = databaseName("invalid")
        createVersionFourteenFixture(name, 77L, includeNullSetting = true)

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

    private fun createVersionFourteenFixture(
        name: String,
        playlistId: Long,
        includeNullSetting: Boolean = false
    ) {
        val helper = createSqlFixtureHelper(name, 14, "/db-fixtures/echo-v14.sql")
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path," +
                "album_id,album_art_uri,codec,bitrate_kbps,sample_rate_hz,bits_per_sample," +
                "channel_count,replay_gain_track_db,replay_gain_album_db,updated_at) " +
                "VALUES(1401,'Migration Track','Migration Artist','Migration Album',180000," +
                "'file:///migration.flac','/music/migration.flac',14,'','flac',900,96000," +
                "24,2,-6.0,-4.0,1700000000000)"
        )
        db.execSQL("INSERT INTO favorites(track_id,created_at) VALUES(1401,1700000000000)")
        db.execSQL("INSERT INTO play_history(track_id,played_at,play_count) VALUES(1401,1700000000000,3)")
        db.execSQL("INSERT INTO play_events(track_id,played_at) VALUES(1401,1700000000000)")
        db.execSQL(
            "INSERT INTO playlists(id,name,created_at,updated_at) " +
                "VALUES($playlistId,'Migration Playlist',1700000000000,1700000000000)"
        )
        db.execSQL(
            "INSERT INTO playlist_tracks(playlist_id,track_id,position,added_at) " +
                "VALUES($playlistId,1401,0,1700000000000)"
        )
        repeat(512) { position ->
            db.execSQL(
                "INSERT INTO playback_queue(position,track_id,title,artist,album,duration_ms," +
                    "content_uri,data_path,album_id,album_art_uri,codec,bitrate_kbps," +
                    "sample_rate_hz,bits_per_sample,channel_count,replay_gain_track_db," +
                    "replay_gain_album_db) VALUES($position,1401,'Migration Track'," +
                    "'Migration Artist','Migration Album',180000,'file:///migration.flac'," +
                    "'/music/migration.flac',14,'','flac',900,96000,24,2,-6.0,-4.0)"
            )
        }
        listOf(
            "playback_queue_index" to "311",
            "playback_position_track_id" to "1401",
            "playback_position_ms" to "54321",
            "theme_mode" to "dark"
        ).forEach { (key, value) ->
            db.execSQL("INSERT INTO settings(`key`,value) VALUES('$key','$value')")
        }
        db.execSQL(
            "INSERT INTO remote_sources(id,type,name,base_url,username,password,root_path," +
                "last_status,updated_at) VALUES(14,'webdav','Legacy DAV'," +
                "'https://dav.example.test','user','secret','music','ready',1700000000000)"
        )
        db.execSQL(
            "INSERT INTO streaming_track_matches(local_key,provider,provider_track_id,title," +
                "artist,data_path,updated_at) VALUES('local-key','netease','provider-1401'," +
                "'Migration Track','Migration Artist','/music/migration.flac',1700000000000)"
        )
        db.execSQL(
            "INSERT INTO library_exclusions(source_key,content_uri,data_path,created_at) " +
                "VALUES('uri:content://hidden','content://hidden','/music/hidden.flac',1700000000000)"
        )
        if (includeNullSetting) {
            db.execSQL("INSERT INTO settings(`key`, value) VALUES (NULL, 'keep-me')")
        }
        helper.close()
    }

    private fun createSqlFixture(name: String, version: Int, resourcePath: String) {
        createSqlFixtureHelper(name, version, resourcePath).close()
    }

    private fun createSqlFixtureHelper(
        name: String,
        version: Int,
        resourcePath: String
    ): SupportSQLiteOpenHelper {
        val schema = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Missing independent migration fixture: $resourcePath"
        }.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            schema.lineSequence()
                                .filterNot { it.trimStart().startsWith("--") }
                                .joinToString("\n")
                                .split(';')
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .forEach(db::execSQL)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        ).also { it.writableDatabase }
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
