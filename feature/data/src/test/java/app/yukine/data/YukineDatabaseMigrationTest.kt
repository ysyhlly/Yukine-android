package app.yukine.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.data.room.YukineMigrations
import app.yukine.data.room.YukineSchema
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
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
    fun roomEnablesWalForConcurrentLibraryReads() {
        val name = databaseName("journal")
        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertTrue(stringValue(sqlite, "PRAGMA journal_mode").equals("wal", ignoreCase = true))
        database.close()
    }

    @Test
    fun versionOneFixtureMigratesWithoutLosingLibraryFavoritesOrHistory() {
        val name = databaseName("v1")
        createVersionOneFixture(name)

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals("Legacy Track 1", stringValue(sqlite, "SELECT title FROM tracks WHERE id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites"))
        assertEquals("LOCAL_ONLY", stringValue(sqlite, "SELECT sync_state FROM favorites"))
        assertEquals(1L, longValue(sqlite, "SELECT play_count FROM play_history WHERE track_id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM play_events WHERE track_id = 901"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_history"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_events"))
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

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM tracks WHERE id = 1401"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites"))
        assertTrue(columnExists(sqlite, "favorites", "recording_id"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM play_history WHERE track_id = 1401"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlists WHERE id = $playlistId"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = $playlistId"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlist_recording_items WHERE playlist_id = $playlistId"))
        assertEquals(512L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue"))
        assertEquals(512L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue_identities"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_history"))
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

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals("Second", stringValue(sqlite, "SELECT title FROM tracks WHERE id = 802"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites"))
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

    @Test
    fun versionFifteenFixtureImportsLegacyPlatformMappingsAsUnverifiedSources() {
        val name = databaseName("v15-identity")
        createVersionFifteenIdentityFixture(name)

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM tracks"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites"))
        assertTrue(indexExists(sqlite, "idx_favorites_sync_state"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playlists WHERE id = 15"))
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = 15"))
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM playlist_recording_items WHERE playlist_id = 15"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM remote_sources WHERE id = 9"))
        assertEquals(4L, longValue(sqlite, "SELECT play_count FROM play_history WHERE track_id = 1501"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM play_events WHERE track_id = 1501"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue WHERE track_id = 1501"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue_identities"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_history"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_events"))
        assertEquals("offline", stringValue(sqlite, "SELECT value FROM settings WHERE `key` = 'identity_mode'"))
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM library_exclusions"))
        assertEquals(4L, longValue(sqlite, "SELECT COUNT(*) FROM streaming_track_matches"))
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM recordings"))
        assertEquals(5L, longValue(sqlite, "SELECT COUNT(*) FROM track_sources"))
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM identity_candidates"))
        assertEquals(0L, longValue(sqlite, "SELECT COUNT(*) FROM identity_operations"))
        assertTrue(columnExists(sqlite, "identity_operations", "before_payload"))
        assertTrue(columnExists(sqlite, "identity_operations", "reverted_at"))
        assertTrue(columnExists(sqlite, "track_sources", "failure_count"))
        assertEquals(0L, longValue(sqlite, "SELECT SUM(failure_count) FROM track_sources"))
        assertEquals(
            2L,
            longValue(sqlite, "SELECT COUNT(DISTINCT recording_id) FROM track_sources")
        )
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM tracks t WHERE " +
                    "(SELECT COUNT(*) FROM track_sources s WHERE s.local_track_id = t.id) != 1"
            )
        )
        assertEquals(
            1L,
            longValue(
                sqlite,
                "SELECT COUNT(DISTINCT recording_id) FROM track_sources " +
                    "WHERE data_path = '/music/identity.flac'"
            )
        )
        assertEquals(
            3L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM track_sources " +
                    "WHERE match_status = 'UNVERIFIED_LEGACY' AND confidence = 0"
            )
        )
        assertEquals(
            1L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM track_sources " +
                    "WHERE provider = 'netease' AND provider_track_id = 'netease-1501'"
            )
        )
        assertEquals(
            1L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM track_sources " +
                    "WHERE provider = 'qqmusic' AND provider_track_id = 'qq-1501'"
            )
        )
        assertEquals(
            1L,
            longValue(sqlite, "SELECT COUNT(*) FROM track_sources WHERE provider = 'luoxue'")
        )
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM track_sources WHERE provider_track_id IN " +
                    "('tx:lx-live-1501','kw:lx-remix-1501')"
            )
        )
        assertEquals(
            2L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM identity_candidates WHERE status = 'PENDING' " +
                    "AND provider = 'luoxue'"
            )
        )
        assertEquals(
            "LIVE",
            stringValue(
                sqlite,
                "SELECT variant_type FROM identity_candidates " +
                    "WHERE provider_item_id = 'tx:lx-live-1501'"
            )
        )
        assertEquals(
            "REMIX",
            stringValue(
                sqlite,
                "SELECT variant_type FROM identity_candidates " +
                "WHERE provider_item_id = 'kw:lx-remix-1501'"
            )
        )
        assertEquals(
            1L,
            longValue(sqlite, "SELECT COUNT(DISTINCT target_id) FROM identity_candidates")
        )
        assertEquals(1L, longValue(sqlite, "SELECT COUNT(*) FROM recording_identifiers"))
        assertEquals(
            "JPABC1234567",
            stringValue(
                sqlite,
                "SELECT identifier_value FROM recording_identifiers WHERE identifier_type = 'ISRC'"
            )
        )
        val canonicalId = stringValue(
            sqlite,
            "SELECT canonical_uuid FROM recordings " +
                "WHERE metadata_source = 'LEGACY_STREAMING_MATCH'"
        )
        assertEquals(36, canonicalId.length)
        assertEquals(canonicalId, UUID.fromString(canonicalId).toString())
        assertTrue(!canonicalId.startsWith("luoxue:"))
        sqlite.query("SELECT canonical_uuid FROM recordings").use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                assertEquals(value, UUID.fromString(value).toString())
            }
        }
        sqlite.query("SELECT artist_uuid FROM canonical_artists").use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                assertEquals(value, UUID.fromString(value).toString())
            }
        }
        assertEquals(
            "UNVERIFIED_LEGACY",
            stringValue(
                sqlite,
                "SELECT match_status FROM recordings " +
                    "WHERE metadata_source = 'LEGACY_STREAMING_MATCH'"
            )
        )
        assertEquals(3L, longValue(sqlite, "SELECT COUNT(*) FROM canonical_artists"))
        assertEquals(4L, longValue(sqlite, "SELECT COUNT(*) FROM recording_artist_credits"))
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM tracks t WHERE NOT EXISTS (" +
                    "SELECT 1 FROM track_sources s INNER JOIN recording_artist_credits c " +
                    "ON c.recording_id = s.recording_id WHERE s.local_track_id = t.id " +
                    "AND c.role IN ('PRIMARY','UNKNOWN'))"
            )
        )
        assertEquals(
            "FEATURED",
            stringValue(
                sqlite,
                "SELECT c.role FROM recording_artist_credits c " +
                    "INNER JOIN canonical_artists a ON a.id = c.artist_id " +
                    "WHERE a.display_name = 'Guest Artist'"
            )
        )
        assertEquals(
            5L,
            longValue(sqlite, "SELECT COUNT(*) FROM identity_resolution_jobs WHERE status = 'PENDING'")
        )
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM recordings " +
                    "WHERE active_source_id IS NULL AND metadata_source = 'LEGACY_STREAMING_MATCH'"
            )
        )
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM recordings r INNER JOIN track_sources s " +
                    "ON s.source_id = r.active_source_id WHERE s.recording_id != r.id"
            )
        )
        assertEquals(
            "local",
            stringValue(
                sqlite,
                "SELECT active.provider FROM track_sources local " +
                    "INNER JOIN recordings r ON r.id = local.recording_id " +
                    "INNER JOIN track_sources active ON active.source_id = r.active_source_id " +
                    "WHERE local.local_track_id = 1501"
            )
        )
        assertEquals(
            "webdav",
            stringValue(
                sqlite,
                "SELECT active.provider FROM track_sources local " +
                    "INNER JOIN recordings r ON r.id = local.recording_id " +
                    "INNER JOIN track_sources active ON active.source_id = r.active_source_id " +
                    "WHERE local.local_track_id = 1502"
            )
        )
        assertEquals("integer", stringValue(sqlite, "SELECT typeof(id) FROM recordings LIMIT 1"))
        assertEquals("integer", stringValue(sqlite, "SELECT typeof(source_id) FROM track_sources LIMIT 1"))
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        assertTrue(columnExists(sqlite, "provider_response_cache", "endpoint"))
        assertTrue(columnExists(sqlite, "provider_response_cache", "last_error"))
        assertTrue(indexExists(sqlite, "idx_recording_uuid"))
        assertTrue(indexExists(sqlite, "idx_source_selection"))
        database.close()
    }

    @Test
    fun everySupportedLegacyVersionHasAnAtomicRouteToCurrentSchema() {
        (1 until 20).forEach { startVersion ->
            val name = databaseName("route-v$startVersion")
            createMinimalLegacyFixture(name, startVersion)

            val database = YukineDatabase.open(context, name)
            val sqlite = database.openHelper.writableDatabase

            assertEquals(
                "startVersion=$startVersion",
                YukineMigrations.TARGET_VERSION,
                sqlite.version
            )
            assertEquals(
                "startVersion=$startVersion",
                "Legacy Track $startVersion",
                stringValue(sqlite, "SELECT title FROM tracks WHERE id = 901")
            )
            assertEquals("startVersion=$startVersion", 1L, longValue(sqlite, "SELECT COUNT(*) FROM favorites"))
            assertEquals("startVersion=$startVersion", 0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
            assertEquals("startVersion=$startVersion", "ok", stringValue(sqlite, "PRAGMA integrity_check"))
            database.close()
        }
    }

    @Test
    fun everyExportedV15ThroughV32SchemaMigratesAtomicallyToV33() {
        (15..32).forEach { startVersion ->
            val name = databaseName("exported-route-v$startVersion")
            createExportedSchemaFixture(name, startVersion)

            val database = YukineDatabase.open(context, name)
            val sqlite = database.openHelper.writableDatabase

            assertEquals(
                "startVersion=$startVersion",
                YukineMigrations.TARGET_VERSION,
                sqlite.version
            )
            assertTrue(columnExists(sqlite, "work_artist_credits", "work_id"))
            assertTrue(columnExists(sqlite, "work_identifiers", "identifier_value"))
            assertTrue(columnExists(sqlite, "identity_operations", "dedup_mode"))
            assertTrue(columnExists(sqlite, "identity_operations", "policy_version"))
            assertTrue(columnExists(sqlite, "identity_operations", "evaluation_batch"))
            assertTrue(columnExists(sqlite, "identity_operations", "rollback_status"))
            assertTrue(columnExists(sqlite, "identity_operations", "post_state_hash"))
            assertTrue(indexExists(sqlite, "idx_identity_operations_dedup_rollback"))
            assertTrue(indexExists(sqlite, "idx_source_candidates_global"))
            assertTrue(indexExists(sqlite, "idx_recording_relations_global_candidates"))
            assertEquals("startVersion=$startVersion", 0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
            assertEquals("startVersion=$startVersion", "ok", stringValue(sqlite, "PRAGMA integrity_check"))
            database.close()
        }
    }

    @Test
    fun version31BackfillsLegacyIdentifiersAndAddsTrustProvenance() {
        val name = databaseName("v31-v32-identity")
        createExportedSchemaFixture(name, 31)
        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL(
            "INSERT INTO works(id,canonical_uuid,normalized_title,created_at,updated_at) " +
                "VALUES(501,'00000000-0000-0000-0000-000000000501','test work',10,20)"
        )
        raw.execSQL(
            "INSERT INTO recordings(" +
                "id,canonical_uuid,work_id,musicbrainz_work_id,title,isrc,confidence,created_at,updated_at" +
                ") VALUES(" +
                "601,'00000000-0000-0000-0000-000000000601',501," +
                "'51cf9f61-7ce9-4a83-bb59-252d76f18b1b','Test Recording','US-AAA-26-00001'," +
                "0.9,10,20)"
        )
        raw.close()

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals("USAAA2600001", stringValue(
            sqlite,
            "SELECT identifier_value FROM recording_identifiers " +
                "WHERE recording_id=601 AND identifier_type='ISRC'"
        ))
        assertEquals("51cf9f61-7ce9-4a83-bb59-252d76f18b1b", stringValue(
            sqlite,
            "SELECT identifier_value FROM work_identifiers " +
                "WHERE work_id=501 AND identifier_type='MUSICBRAINZ_WORK_ID'"
        ))
        assertTrue(columnExists(sqlite, "source_match_features", "title_trust"))
        assertTrue(columnExists(sqlite, "source_match_features", "work_credit_trust"))
        assertTrue(columnExists(sqlite, "source_match_features", "evidence_provenance"))
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        database.close()
    }

    @Test
    fun v20BackfillIsCompleteAndIdempotent() {
        val name = databaseName("v20-repair")
        createPartialVersionTwentyCanonicalFixture(name)

        val database = YukineDatabase.open(context, name)
        val sqlite = database.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertTrue(columnExists(sqlite, "recording_play_events", "legacy_event_id"))
        assertTrue(indexExists(sqlite, "idx_recording_events_legacy_event"))
        assertEquals(3L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue_identities"))
        assertEquals(
            0L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM playback_queue q LEFT JOIN playback_queue_identities i " +
                    "ON i.position=q.position WHERE i.position IS NULL"
            )
        )
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_history"))
        assertEquals(5L, longValue(sqlite, "SELECT MAX(play_count) FROM recording_play_history"))
        assertEquals(4L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_events"))
        assertEquals(
            3L,
            longValue(
                sqlite,
                "SELECT COUNT(DISTINCT legacy_event_id) FROM recording_play_events " +
                    "WHERE legacy_event_id IS NOT NULL"
            )
        )
        assertEquals(
            1L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM recording_play_events WHERE legacy_event_id IS NULL"
            )
        )

        YukineSchema.normalizeV21(sqlite)

        assertEquals(3L, longValue(sqlite, "SELECT COUNT(*) FROM playback_queue_identities"))
        assertEquals(2L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_history"))
        assertEquals(5L, longValue(sqlite, "SELECT MAX(play_count) FROM recording_play_history"))
        assertEquals(4L, longValue(sqlite, "SELECT COUNT(*) FROM recording_play_events"))
        assertEquals(
            "CONFIRMED",
            stringValue(
                sqlite,
                "SELECT match_status FROM track_sources WHERE local_track_id=2103"
            )
        )
        assertEquals(
            "UNRESOLVED",
            stringValue(
                sqlite,
                "SELECT match_status FROM track_sources WHERE local_track_id=2104"
            )
        )
        assertEquals(
            1L,
            longValue(
                sqlite,
                "SELECT COUNT(*) FROM track_sources s INNER JOIN recordings r " +
                    "ON r.id=s.recording_id WHERE s.local_track_id=2104 " +
                    "AND r.active_source_id IS NULL"
            )
        )
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        database.close()
    }

    @Test
    fun versionTwentyOneAddsPersistedMatchFeaturesWithoutTouchingUserState() {
        val name = databaseName("v21-features")
        val current = YukineDatabase.open(context, name)
        current.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(`key`,value) VALUES('migration_v22_probe','keep-me')"
        )
        current.close()

        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL("DROP TABLE source_match_features")
        raw.version = 21
        raw.close()

        val migrated = YukineDatabase.open(context, name)
        val sqlite = migrated.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals(
            "keep-me",
            stringValue(sqlite, "SELECT value FROM settings WHERE `key`='migration_v22_probe'")
        )
        assertTrue(columnExists(sqlite, "source_match_features", "metadata_signature"))
        assertTrue(columnExists(sqlite, "source_match_features", "algorithm_version"))
        assertTrue(indexExists(sqlite, "idx_source_match_bucket"))
        assertTrue(indexExists(sqlite, "idx_source_match_algorithm"))
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        migrated.close()
    }

    @Test
    fun versionTwentyTwoAddsBoundedSourceCandidatesWithoutTouchingUserState() {
        val name = databaseName("v22-candidates")
        val current = YukineDatabase.open(context, name)
        current.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(`key`,value) VALUES('migration_v23_probe','keep-me')"
        )
        current.close()

        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL("DROP TABLE source_recording_candidates")
        raw.execSQL("ALTER TABLE source_match_features RENAME TO source_match_features_v23")
        raw.execSQL(
            "CREATE TABLE source_match_features (" +
                "source_id INTEGER NOT NULL,normalized_title TEXT NOT NULL," +
                "core_title TEXT NOT NULL,normalized_artist TEXT NOT NULL," +
                "normalized_album TEXT NOT NULL,version_type TEXT NOT NULL," +
                "version_signature TEXT NOT NULL,duration_bucket INTEGER NOT NULL," +
                "title_tokens TEXT NOT NULL,title_bigrams TEXT NOT NULL," +
                "title_trigrams TEXT NOT NULL,metadata_signature TEXT NOT NULL," +
                "algorithm_version INTEGER NOT NULL,updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(source_id),FOREIGN KEY(source_id) REFERENCES track_sources(source_id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        raw.execSQL(
            "INSERT INTO source_match_features(source_id,normalized_title,core_title," +
                "normalized_artist,normalized_album,version_type,version_signature," +
                "duration_bucket,title_tokens,title_bigrams,title_trigrams,metadata_signature," +
                "algorithm_version,updated_at) SELECT source_id,normalized_title,core_title," +
                "normalized_artist,normalized_album,version_type,version_signature," +
                "duration_bucket,title_tokens,title_bigrams,title_trigrams,metadata_signature," +
                "algorithm_version,updated_at FROM source_match_features_v23"
        )
        raw.execSQL("DROP TABLE source_match_features_v23")
        raw.execSQL(
            "CREATE INDEX idx_source_match_bucket ON source_match_features " +
                "(core_title,normalized_artist,duration_bucket)"
        )
        raw.execSQL(
            "CREATE INDEX idx_source_match_algorithm ON source_match_features (algorithm_version)"
        )
        raw.version = 22
        raw.close()

        val migrated = YukineDatabase.open(context, name)
        val sqlite = migrated.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals(
            "keep-me",
            stringValue(sqlite, "SELECT value FROM settings WHERE `key`='migration_v23_probe'")
        )
        assertTrue(columnExists(sqlite, "source_match_features", "candidate_algorithm_version"))
        assertTrue(columnExists(sqlite, "source_match_features", "candidate_snapshot_signature"))
        assertTrue(columnExists(sqlite, "source_match_features", "candidate_generated_at"))
        assertTrue(columnExists(sqlite, "source_recording_candidates", "coarse_score"))
        assertTrue(indexExists(sqlite, "idx_source_candidates_recording"))
        assertTrue(indexExists(sqlite, "idx_source_candidates_rank"))
        assertTrue(indexExists(sqlite, "idx_source_candidates_algorithm"))
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        migrated.close()
    }

    @Test
    fun v23ToV24AddsRecordingRelations() {
        val name = databaseName("v23-recording-relations")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).close()
        val current = YukineDatabase.open(context, name)
        current.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(`key`,value) VALUES('migration_v24_probe','keep-me')"
        )
        current.close()

        val raw = SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL("DROP TABLE recording_relations")
        raw.version = 23
        raw.close()

        val migrated = YukineDatabase.open(context, name)
        val sqlite = migrated.openHelper.writableDatabase

        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals(
            "keep-me",
            stringValue(sqlite, "SELECT value FROM settings WHERE `key`='migration_v24_probe'")
        )
        assertTrue(columnExists(sqlite, "recording_relations", "relation_type"))
        assertTrue(columnExists(sqlite, "recording_relations", "same_recording_probability"))
        assertTrue(columnExists(sqlite, "recording_relations", "same_work_probability"))
        assertTrue(columnExists(sqlite, "recording_relations", "locked"))
        assertTrue(indexExists(sqlite, "idx_recording_relations_right"))
        assertTrue(indexExists(sqlite, "idx_recording_relations_type"))
        assertTrue(indexExists(sqlite, "idx_recording_relations_updated"))
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        migrated.close()
    }

    @Test
    fun v24ToV25BackfillsWorksAndAddsColdAudioFeatures() {
        val name = databaseName("v24-work-audio-features")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).close()
        val current = YukineDatabase.open(context, name)
        val db = current.openHelper.writableDatabase
        db.execSQL("INSERT OR REPLACE INTO settings(`key`,value) VALUES('migration_v25_probe','keep-me')")
        db.execSQL(
            "INSERT INTO works(id,canonical_uuid,normalized_title,created_at,updated_at) " +
                "VALUES(250,'00000000-0000-4000-8000-000000000250','old work',1,1)"
        )
        db.execSQL(
            "INSERT INTO recordings(id,canonical_uuid,work_id,title,primary_artist_display," +
                "duration_ms,created_at,updated_at) VALUES(251," +
                "'00000000-0000-4000-8000-000000000251',250,'Migration Work'," +
                "'Migration Artist',180000,1700000000000,1700000000000)"
        )
        current.close()

        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("UPDATE recordings SET work_id=NULL")
        raw.execSQL("DROP TABLE audio_features")
        raw.execSQL("DROP TABLE works")
        raw.version = 24
        raw.close()

        val migrated = YukineDatabase.open(context, name)
        val sqlite = migrated.openHelper.writableDatabase
        assertEquals(YukineMigrations.TARGET_VERSION, sqlite.version)
        assertEquals("keep-me", stringValue(sqlite, "SELECT value FROM settings WHERE `key`='migration_v25_probe'"))
        assertTrue(columnExists(sqlite, "recordings", "work_id"))
        assertTrue(columnExists(sqlite, "audio_features", "content_signature"))
        assertTrue(columnExists(sqlite, "audio_features", "chromaprint"))
        assertTrue(columnExists(sqlite, "audio_features", "recording_embedding"))
        assertTrue(columnExists(sqlite, "audio_features", "work_embedding"))
        assertTrue(indexExists(sqlite, "idx_recordings_work"))
        assertTrue(indexExists(sqlite, "idx_works_uuid"))
        assertTrue(indexExists(sqlite, "idx_audio_features_spec_state"))
        assertEquals(1L, rowCount(sqlite, "SELECT * FROM works"))
        assertEquals(
            "00000000-0000-4000-8000-000000000251",
            stringValue(
                sqlite,
                "SELECT w.canonical_uuid FROM works w INNER JOIN recordings r ON r.work_id=w.id WHERE r.id=251"
            )
        )
        assertEquals(0L, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals("ok", stringValue(sqlite, "PRAGMA integrity_check"))
        migrated.close()
    }

    @Test
    fun v25ToV26AddsArtistAvatarAndRequeuesMissingAvatarEnrichment() {
        val name = databaseName("v25-artist-avatar")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(25) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE canonical_artists(" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                    "artist_uuid TEXT NOT NULL,display_name TEXT NOT NULL," +
                                    "sort_name TEXT NOT NULL DEFAULT ''," +
                                    "artist_type TEXT NOT NULL DEFAULT 'UNKNOWN'," +
                                    "country_code TEXT NOT NULL DEFAULT ''," +
                                    "musicbrainz_artist_id TEXT NOT NULL DEFAULT ''," +
                                    "match_status TEXT NOT NULL DEFAULT 'UNRESOLVED'," +
                                    "confidence REAL NOT NULL DEFAULT 0," +
                                    "metadata_source TEXT NOT NULL DEFAULT ''," +
                                    "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE identity_resolution_jobs(" +
                                    "job_id TEXT NOT NULL PRIMARY KEY,target_type TEXT NOT NULL," +
                                    "target_id INTEGER NOT NULL,priority INTEGER NOT NULL DEFAULT 0," +
                                    "reason TEXT NOT NULL DEFAULT 'NEW_TRACK'," +
                                    "attempt_count INTEGER NOT NULL DEFAULT 0," +
                                    "next_attempt_at INTEGER NOT NULL DEFAULT 0," +
                                    "last_error TEXT NOT NULL DEFAULT ''," +
                                    "status TEXT NOT NULL DEFAULT 'PENDING'," +
                                    "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO canonical_artists(id,artist_uuid,display_name,created_at,updated_at) " +
                "VALUES(2601,'00000000-0000-4000-8000-000000002601','Avatar Artist',1,1)"
        )
        db.execSQL(
            "INSERT INTO identity_resolution_jobs(job_id,target_type,target_id,status,created_at,updated_at) " +
                "VALUES('old-avatar-job','ARTIST',2601,'SUCCEEDED',1,1)"
        )
        YukineMigrations.normalizeV26(db)
        db.version = YukineMigrations.TARGET_VERSION

        assertEquals(YukineMigrations.TARGET_VERSION, db.version)
        assertTrue(columnExists(db, "canonical_artists", "avatar_url"))
        assertEquals("", stringValue(db, "SELECT avatar_url FROM canonical_artists WHERE id=2601"))
        assertEquals(
            1L,
            longValue(
                db,
                "SELECT COUNT(*) FROM identity_resolution_jobs WHERE target_type='ARTIST' " +
                    "AND target_id=2601 AND status='PENDING' AND reason='MISSING_ARTIST_AVATAR'"
            )
        )
        assertEquals("ok", stringValue(db, "PRAGMA integrity_check"))
        helper.close()
    }

    @Test
    fun v27ToV28AddsArtistDescriptionAndRequeuesMissingProfileEnrichment() {
        val name = databaseName("v27-artist-description")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(27) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE canonical_artists(" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                    "artist_uuid TEXT NOT NULL,display_name TEXT NOT NULL," +
                                    "sort_name TEXT NOT NULL DEFAULT ''," +
                                    "artist_type TEXT NOT NULL DEFAULT 'UNKNOWN'," +
                                    "country_code TEXT NOT NULL DEFAULT ''," +
                                    "musicbrainz_artist_id TEXT NOT NULL DEFAULT ''," +
                                    "avatar_url TEXT NOT NULL DEFAULT ''," +
                                    "match_status TEXT NOT NULL DEFAULT 'UNRESOLVED'," +
                                    "confidence REAL NOT NULL DEFAULT 0," +
                                    "metadata_source TEXT NOT NULL DEFAULT ''," +
                                    "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE identity_resolution_jobs(" +
                                    "job_id TEXT NOT NULL PRIMARY KEY,target_type TEXT NOT NULL," +
                                    "target_id INTEGER NOT NULL,priority INTEGER NOT NULL DEFAULT 0," +
                                    "reason TEXT NOT NULL DEFAULT 'NEW_TRACK'," +
                                    "attempt_count INTEGER NOT NULL DEFAULT 0," +
                                    "next_attempt_at INTEGER NOT NULL DEFAULT 0," +
                                    "last_error TEXT NOT NULL DEFAULT ''," +
                                    "status TEXT NOT NULL DEFAULT 'PENDING'," +
                                    "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO canonical_artists(id,artist_uuid,display_name,created_at,updated_at) " +
                "VALUES(2801,'00000000-0000-4000-8000-000000002801','Profile Artist',1,1)"
        )
        db.execSQL(
            "INSERT INTO identity_resolution_jobs(job_id,target_type,target_id,status,created_at,updated_at) " +
                "VALUES('old-profile-job','ARTIST',2801,'SUCCEEDED',1,1)"
        )

        YukineMigrations.normalizeV28(db)
        db.version = YukineMigrations.TARGET_VERSION

        assertEquals(YukineMigrations.TARGET_VERSION, db.version)
        assertTrue(columnExists(db, "canonical_artists", "description"))
        assertEquals("", stringValue(db, "SELECT description FROM canonical_artists WHERE id=2801"))
        assertEquals(
            1L,
            longValue(
                db,
                "SELECT COUNT(*) FROM identity_resolution_jobs WHERE target_type='ARTIST' " +
                    "AND target_id=2801 AND status='PENDING' " +
                    "AND reason='MISSING_ARTIST_DESCRIPTION'"
            )
        )
        assertEquals("ok", stringValue(db, "PRAGMA integrity_check"))
        helper.close()
    }

    @Test
    fun v28ToV29AddsContentSignatureWithoutChangingExistingSources() {
        val name = databaseName("v28-content-signature")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(28) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE track_sources(" +
                                    "source_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                    "provider TEXT NOT NULL," +
                                    "provider_track_id TEXT NOT NULL," +
                                    "failure_count INTEGER NOT NULL DEFAULT 0)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO track_sources(source_id,provider,provider_track_id) " +
                "VALUES(2901,'local','/music/existing.flac')"
        )

        YukineSchema.normalizeV29(db)
        YukineSchema.normalizeV29(db)
        db.version = 29

        assertEquals(29, db.version)
        assertTrue(columnExists(db, "track_sources", "content_signature"))
        assertEquals(
            "",
            stringValue(
                db,
                "SELECT content_signature FROM track_sources WHERE source_id=2901"
            )
        )
        assertEquals(1L, longValue(db, "SELECT COUNT(*) FROM track_sources"))
        assertEquals("ok", stringValue(db, "PRAGMA integrity_check"))
        helper.close()
    }

    @Test
    fun v29ToV30AddsExtendedMetadataAndEmbeddingColumnsIdempotently() {
        val name = databaseName("v29-recording-embedding")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(29) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE tracks(id INTEGER PRIMARY KEY,title TEXT NOT NULL)")
                            db.execSQL(
                                "CREATE TABLE playback_queue(" +
                                    "position INTEGER PRIMARY KEY,track_id INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE track_sources(" +
                                    "source_id INTEGER PRIMARY KEY,provider TEXT NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE source_match_features(" +
                                    "source_id INTEGER PRIMARY KEY,algorithm_version INTEGER NOT NULL)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase

        YukineMigrations.normalizeV30(db)
        YukineMigrations.normalizeV30(db)
        db.version = 30

        listOf("album_artist", "composer", "release_type", "year").forEach { column ->
            assertTrue(columnExists(db, "tracks", column))
            assertTrue(columnExists(db, "playback_queue", column))
            assertTrue(columnExists(db, "track_sources", column))
        }
        assertTrue(columnExists(db, "source_match_features", "metadata_vector"))
        assertTrue(columnExists(db, "source_match_features", "metadata_vector_version"))
        assertTrue(columnExists(db, "source_match_features", "metadata_sim_hash"))
        assertEquals("ok", stringValue(db, "PRAGMA integrity_check"))
        helper.close()
    }

    @Test
    fun v30ToV31CreatesCanonicalAlbumsAndBackfillsSourcesIdempotently() {
        val name = databaseName("v30-canonical-albums")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(30) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE canonical_artists(" +
                                    "id INTEGER PRIMARY KEY,display_name TEXT NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE recording_artist_credits(" +
                                    "recording_id INTEGER NOT NULL,artist_id INTEGER NOT NULL," +
                                    "role TEXT NOT NULL,position INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE track_sources(" +
                                    "source_id INTEGER PRIMARY KEY,recording_id INTEGER NOT NULL," +
                                    "album TEXT NOT NULL DEFAULT '',artist TEXT NOT NULL DEFAULT ''," +
                                    "album_artist TEXT NOT NULL DEFAULT ''," +
                                    "release_type TEXT NOT NULL DEFAULT '',year INTEGER NOT NULL DEFAULT 0)"
                            )
                            db.execSQL(
                                "INSERT INTO canonical_artists VALUES(10,'Artist'),(20,'Artist')"
                            )
                            db.execSQL(
                                "INSERT INTO recording_artist_credits VALUES" +
                                    "(1,10,'PRIMARY',0),(2,20,'PRIMARY',0)"
                            )
                            db.execSQL(
                                "INSERT INTO track_sources VALUES" +
                                    "(1,1,'Echo','Artist','','Album',2024)," +
                                    "(2,1,' Echo ','Artist','','Album',2024)," +
                                    "(3,1,'','Artist','','Album',2024)," +
                                    "(4,2,'Echo','Artist','','Album',2024)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase

        YukineMigrations.normalizeV31(db)
        YukineMigrations.normalizeV31(db)
        db.version = 31

        assertEquals(31, db.version)
        assertTrue(columnExists(db, "track_sources", "album_id"))
        assertTrue(indexExists(db, "idx_track_source_album"))
        assertTrue(indexExists(db, "idx_album_identity_key"))
        assertTrue(indexExists(db, "idx_album_source_provider_album"))
        assertEquals(2L, longValue(db, "SELECT COUNT(*) FROM canonical_albums"))
        assertEquals(2L, longValue(db, "SELECT COUNT(*) FROM album_aliases"))
        assertEquals(
            1L,
            longValue(
                db,
                "SELECT COUNT(DISTINCT album_id) FROM track_sources " +
                    "WHERE source_id IN (1,2) AND album_id IS NOT NULL"
            )
        )
        assertEquals(
            1L,
            longValue(db, "SELECT COUNT(*) FROM track_sources WHERE source_id=3 AND album_id IS NULL")
        )
        assertEquals(
            2L,
            longValue(
                db,
                "SELECT COUNT(DISTINCT album_id) FROM track_sources " +
                    "WHERE source_id IN (1,4) AND album_id IS NOT NULL"
            )
        )
        assertEquals("Echo", stringValue(db, "SELECT display_name FROM canonical_albums LIMIT 1"))
        assertEquals(
            "MIGRATION_V31",
            stringValue(db, "SELECT metadata_source FROM canonical_albums LIMIT 1")
        )
        assertEquals("ok", stringValue(db, "PRAGMA integrity_check"))
        helper.close()
    }

    private fun createVersionOneFixture(name: String) {
        createMinimalLegacyFixture(name, 1)
    }

    private fun createPartialVersionTwentyCanonicalFixture(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).close()
        val current = YukineDatabase.open(context, name)
        val db = current.openHelper.writableDatabase
        val firstPath = "streaming:qqmusic:shared-v20?legacy=1"
        val secondPath = "streaming:qqmusic:shared-v20?legacy=2"
        val missingPath = "/music/missing-v20.flac"
        listOf(
            2101L to firstPath,
            2102L to secondPath,
            2103L to missingPath,
            2104L to "streaming:netease:unconfirmed-v20"
        ).forEach { (trackId, dataPath) ->
            db.execSQL(
                "INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path," +
                    "album_id,album_art_uri,updated_at) VALUES(?,?,'Repair Artist','Repair Album'," +
                    "180000,'',?,0,'',1700000000000)",
                arrayOf<Any>(trackId, "Repair Track $trackId", dataPath)
            )
        }
        db.execSQL(
            "INSERT INTO recordings(id,canonical_uuid,title,primary_artist_display,duration_ms," +
                "created_at,updated_at) VALUES(210,'00000000-0000-4000-8000-000000000210'," +
                "'Repair Track','Repair Artist',180000,1700000000000,1700000000000)"
        )
        db.execSQL(
            "INSERT INTO track_sources(source_id,recording_id,provider,provider_track_id," +
                "local_track_id,data_path,title,artist,duration_ms,playable,match_status,confidence) " +
                "VALUES(211,210,'qqmusic','shared-v20',2102,?,'Repair Track','Repair Artist'," +
                "180000,1,'CONFIRMED',1)",
            arrayOf(secondPath)
        )
        db.execSQL(
            "INSERT INTO play_history(track_id,played_at,play_count) VALUES" +
                "(2101,1700000000100,2),(2102,1700000000200,3)," +
                "(2103,1700000000300,4)"
        )
        db.execSQL(
            "INSERT INTO play_events(id,track_id,played_at) VALUES" +
                "(11,2101,1700000000100),(12,2101,1700000000100)," +
                "(13,2103,1700000000300)"
        )
        db.execSQL(
            "INSERT INTO recording_play_history(recording_id,representative_track_id," +
                "played_at,play_count) VALUES(210,2102,1700000000100,2)"
        )
        db.execSQL(
            "INSERT INTO recording_play_events(id,recording_id,source_id,track_id_snapshot," +
                "played_at,legacy_event_id) VALUES" +
                "(21,210,211,2101,1700000000100,NULL)," +
                "(22,210,211,2102,1700000000999,NULL)"
        )
        db.execSQL(
            "INSERT INTO playback_queue(position,track_id,title,artist,data_path) VALUES" +
                "(0,2101,'Repair Track 2101','Repair Artist',?)," +
                "(1,2102,'Repair Track 2102','Repair Artist',?)," +
                "(2,2103,'Repair Track 2103','Repair Artist',?)",
            arrayOf(firstPath, secondPath, missingPath)
        )
        db.execSQL(
            "INSERT INTO playback_queue_identities(position,recording_id,preferred_source_id) " +
                "VALUES(0,210,NULL),(9,210,211)"
        )
        current.close()

        val raw = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("DROP INDEX IF EXISTS idx_recording_events_legacy_event")
        raw.execSQL("ALTER TABLE recording_play_events RENAME TO recording_play_events_v21")
        raw.execSQL(
            "CREATE TABLE recording_play_events (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "recording_id INTEGER NOT NULL,source_id INTEGER,track_id_snapshot INTEGER NOT NULL," +
                "played_at INTEGER NOT NULL,FOREIGN KEY(recording_id) REFERENCES recordings(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        raw.execSQL(
            "INSERT INTO recording_play_events(id,recording_id,source_id,track_id_snapshot,played_at) " +
                "SELECT id,recording_id,source_id,track_id_snapshot,played_at " +
                "FROM recording_play_events_v21"
        )
        raw.execSQL("DROP TABLE recording_play_events_v21")
        raw.execSQL(
            "CREATE INDEX idx_recording_events_time ON recording_play_events (played_at)"
        )
        raw.execSQL(
            "CREATE INDEX idx_recording_events_recording_time " +
                "ON recording_play_events (recording_id,played_at)"
        )
        raw.version = 20
        raw.close()
    }

    private fun createMinimalLegacyFixture(name: String, version: Int) {
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
            "INSERT INTO tracks VALUES (901, 'Legacy Track $version', 'Legacy Artist', 'Legacy Album', " +
                "123000, 'file:///legacy.mp3', '/music/legacy.mp3', 90, '')"
        )
        database.execSQL("INSERT INTO favorites(track_id) VALUES (901)")
        database.execSQL("INSERT INTO play_history(track_id, played_at) VALUES (901, 1700000000000)")
        database.version = version
        database.close()
    }

    private fun createExportedSchemaFixture(name: String, version: Int) {
        val relative = "schemas/app.yukine.data.room.YukineDatabase/$version.json"
        val schemaFile = sequenceOf(
            File(relative),
            File("feature/data/$relative")
        ).firstOrNull(File::isFile)
            ?: error("Missing exported Room schema $relative")
        val schema = JSONObject(schemaFile.readText(StandardCharsets.UTF_8)).getJSONObject("database")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.execSQL("PRAGMA foreign_keys=OFF")
        val entities = schema.getJSONArray("entities")
        repeat(entities.length()) { index ->
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            database.execSQL(
                entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            )
        }
        repeat(entities.length()) { index ->
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            entity.optJSONArray("indices")?.let { indices ->
                repeat(indices.length()) { indexPosition ->
                    database.execSQL(
                        indices.getJSONObject(indexPosition)
                            .getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                    )
                }
            }
        }
        schema.optJSONArray("views")?.let { views ->
            repeat(views.length()) { index ->
                database.execSQL(views.getJSONObject(index).getString("createSql"))
            }
        }
        schema.optJSONArray("setupQueries")?.let { queries ->
            repeat(queries.length()) { index ->
                database.execSQL(queries.getString(index))
            }
        }
        database.version = version
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

    private fun createVersionFifteenIdentityFixture(name: String) {
        val helper = createSqlFixtureHelper(name, 15, "/db-fixtures/echo-v15.sql")
        val db = helper.writableDatabase
        val dataPath = "/music/identity.flac"
        db.execSQL(
            "INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path," +
            "album_id,album_art_uri,updated_at) VALUES(1501,'Identity Song'," +
                "'Identity Artist feat. Guest Artist','Local Album',180000,'content://media/1501',?,1501,'',?)",
            arrayOf<Any>(dataPath, 1_700_000_000_000L)
        )
        db.execSQL(
            "INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path," +
            "album_id,album_art_uri,updated_at) VALUES(1502,'WebDAV Song'," +
                "'Identity Artist / WebDAV Artist','Remote Album',181000,'','webdav:9:/identity.flac',1502,'',?)",
            arrayOf(1_700_000_000_000L)
        )
        db.execSQL("INSERT INTO favorites(track_id,created_at) VALUES(1501,1700000000000)")
        db.execSQL(
            "INSERT INTO playlists(id,name,created_at,updated_at) " +
                "VALUES(15,'Migration Mix',1700000000000,1700000000000)"
        )
        db.execSQL(
            "INSERT INTO playlist_tracks(playlist_id,track_id,position,added_at) VALUES" +
                "(15,1501,0,1700000000000),(15,1502,1,1700000000000)"
        )
        db.execSQL(
            "INSERT INTO remote_sources(id,type,name,base_url,username,password,root_path," +
                "last_status,updated_at) VALUES(9,'webdav','Fixture DAV','https://dav.test'," +
                "'fixture','','/','ok',1700000000000)"
        )
        db.execSQL(
            "INSERT INTO play_history(track_id,played_at,play_count) " +
                "VALUES(1501,1700000000000,4)"
        )
        db.execSQL("INSERT INTO play_events(id,track_id,played_at) VALUES(7,1501,1700000000000)")
        db.execSQL(
            "INSERT INTO playback_queue(position,track_id,title,artist,album,duration_ms," +
                "content_uri,data_path,album_id,album_art_uri) VALUES(0,1501,'Identity Song'," +
                "'Identity Artist','Local Album',180000,'content://media/1501',?,1501,'')",
            arrayOf(dataPath)
        )
        db.execSQL("INSERT INTO settings(`key`,value) VALUES('identity_mode','offline')")
        db.execSQL(
            "INSERT INTO library_exclusions(source_key,content_uri,data_path,created_at) " +
                "VALUES('path:/music/hidden.flac','','/music/hidden.flac',1700000000000)"
        )
        val encodedLx = "__echo_source_match_v2__:{\"primary\":\"wy:lx-1501\"," +
            "\"candidates\":[" +
            "{\"id\":\"wy:lx-1501\",\"title\":\"Identity Song\"," +
            "\"artist\":\"Identity Artist\",\"durationMs\":180000," +
            "\"isrc\":\"JP-ABC-12-34567\"}," +
            "{\"id\":\"tx:lx-live-1501\",\"title\":\"Identity Song (Live)\"," +
            "\"artist\":\"Identity Artist\",\"durationMs\":196000}," +
            "{\"id\":\"kw:lx-remix-1501\",\"title\":\"Identity Song (Remix)\"," +
            "\"artist\":\"Identity Artist\",\"durationMs\":205000," +
            "\"isrc\":\"JP-REM-99-99999\"}]}"
        listOf(
            Triple("netease", "netease-1501", "meta:identity song|identity artist"),
            Triple("qqmusic", "qq-1501", "id:1501"),
            Triple("luoxue", encodedLx, "path:$dataPath")
        ).forEach { (provider, providerTrackId, localKey) ->
            db.execSQL(
                "INSERT INTO streaming_track_matches(local_key,provider,provider_track_id,title," +
                    "artist,data_path,updated_at) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any>(
                    localKey,
                    provider,
                    providerTrackId,
                    "Identity Song",
                    "Identity Artist",
                    dataPath,
                    1_700_000_000_000L
                )
            )
        }
        db.execSQL(
            "INSERT INTO streaming_track_matches(local_key,provider,provider_track_id,title," +
                "artist,data_path,updated_at) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any>(
                "path:/music/duplicate-platform-id.flac",
                "netease",
                "netease-1501",
                "Duplicate Legacy Group",
                "Identity Artist",
                "/music/duplicate-platform-id.flac",
                1_700_000_000_001L
            )
        )
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

    private fun rowCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String
    ): Long = database.query(sql).use { cursor ->
        var count = 0L
        while (cursor.moveToNext()) count++
        count
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

    private fun indexExists(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        index: String
    ): Boolean = database.query(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
        arrayOf(index)
    ).use { cursor ->
        cursor.moveToFirst() && cursor.getLong(0) == 1L
    }
}
