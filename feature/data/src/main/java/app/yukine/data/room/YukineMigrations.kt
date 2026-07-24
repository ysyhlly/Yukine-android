package app.yukine.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object YukineMigrations {
    const val TARGET_VERSION: Int = 36

    val all: Array<Migration> = (1 until TARGET_VERSION).map { startVersion ->
        object : Migration(startVersion, TARGET_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (startVersion < 20) {
                    YukineSchema.normalizeV20(db)
                }
                YukineSchema.normalizeV21(db)
                normalizeV22(db)
                normalizeV23(db)
                normalizeV24(db)
                normalizeV25(db)
                normalizeV26(db)
                normalizeV27(db)
                normalizeV28(db)
                YukineSchema.normalizeV29(db)
                normalizeV30(db)
                normalizeV31(db)
                normalizeV32(db)
                normalizeV33(db)
                normalizeV34(db)
                normalizeV35(db)
                normalizeV36(db)
            }
        }
    }.toTypedArray()

    internal fun normalizeV36(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `local_music_sources` (" +
                "`source_id` TEXT NOT NULL," +
                "`type` TEXT NOT NULL," +
                "`root_uri` TEXT NOT NULL," +
                "`display_name` TEXT NOT NULL," +
                "`status` TEXT NOT NULL," +
                "`added_at` INTEGER NOT NULL," +
                "`last_scan_at` INTEGER NOT NULL," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`source_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_local_music_sources_type` ON " +
                "`local_music_sources` (`type`, `updated_at`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_local_music_sources_type_root_uri` ON " +
                "`local_music_sources` (`type`, `root_uri`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `local_music_source_tracks` (" +
                "`source_id` TEXT NOT NULL," +
                "`track_id` INTEGER NOT NULL," +
                "`document_uri` TEXT NOT NULL," +
                "`last_seen_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`source_id`, `track_id`)," +
                "FOREIGN KEY(`source_id`) REFERENCES `local_music_sources`(`source_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE," +
                "FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_local_music_source_tracks_track` ON " +
                "`local_music_source_tracks` (`track_id`)"
        )
    }

    private fun normalizeV22(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `source_match_features` (" +
                "`source_id` INTEGER NOT NULL," +
                "`normalized_title` TEXT NOT NULL," +
                "`core_title` TEXT NOT NULL," +
                "`normalized_artist` TEXT NOT NULL," +
                "`normalized_album` TEXT NOT NULL," +
                "`version_type` TEXT NOT NULL," +
                "`version_signature` TEXT NOT NULL," +
                "`duration_bucket` INTEGER NOT NULL," +
                "`title_tokens` TEXT NOT NULL," +
                "`title_bigrams` TEXT NOT NULL," +
                "`title_trigrams` TEXT NOT NULL," +
                "`metadata_signature` TEXT NOT NULL," +
                "`algorithm_version` INTEGER NOT NULL," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`source_id`)," +
                "FOREIGN KEY(`source_id`) REFERENCES `track_sources`(`source_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_match_bucket` ON " +
                "`source_match_features` (`core_title`, `normalized_artist`, `duration_bucket`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_match_algorithm` ON " +
                "`source_match_features` (`algorithm_version`)"
        )
    }

    private fun normalizeV23(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "source_match_features", "candidate_algorithm_version")) {
            db.execSQL(
                "ALTER TABLE `source_match_features` ADD COLUMN " +
                    "`candidate_algorithm_version` INTEGER NOT NULL DEFAULT 0"
            )
        }
        if (!columnExists(db, "source_match_features", "candidate_snapshot_signature")) {
            db.execSQL(
                "ALTER TABLE `source_match_features` ADD COLUMN " +
                    "`candidate_snapshot_signature` TEXT NOT NULL DEFAULT ''"
            )
        }
        if (!columnExists(db, "source_match_features", "candidate_generated_at")) {
            db.execSQL(
                "ALTER TABLE `source_match_features` ADD COLUMN " +
                    "`candidate_generated_at` INTEGER NOT NULL DEFAULT 0"
            )
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `source_recording_candidates` (" +
                "`source_id` INTEGER NOT NULL," +
                "`candidate_recording_id` INTEGER NOT NULL," +
                "`coarse_score` REAL NOT NULL," +
                "`evidence_json` TEXT NOT NULL," +
                "`state` TEXT NOT NULL DEFAULT 'GENERATED'," +
                "`algorithm_version` INTEGER NOT NULL," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`source_id`, `candidate_recording_id`)," +
                "FOREIGN KEY(`source_id`) REFERENCES `track_sources`(`source_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE," +
                "FOREIGN KEY(`candidate_recording_id`) REFERENCES `recordings`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_candidates_recording` ON " +
                "`source_recording_candidates` (`candidate_recording_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_candidates_rank` ON " +
                "`source_recording_candidates` (`source_id`, `coarse_score`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_candidates_algorithm` ON " +
                "`source_recording_candidates` (`algorithm_version`)"
        )
    }

    private fun normalizeV24(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recording_relations` (" +
                "`left_recording_id` INTEGER NOT NULL," +
                "`right_recording_id` INTEGER NOT NULL," +
                "`relation_type` TEXT NOT NULL," +
                "`same_recording_probability` REAL NOT NULL DEFAULT 0," +
                "`same_work_probability` REAL NOT NULL DEFAULT 0," +
                "`confidence` REAL NOT NULL DEFAULT 0," +
                "`origin` TEXT NOT NULL DEFAULT 'ALGORITHM'," +
                "`algorithm_version` INTEGER NOT NULL DEFAULT 0," +
                "`evidence_json` TEXT NOT NULL DEFAULT ''," +
                "`locked` INTEGER NOT NULL DEFAULT 0," +
                "`created_at` INTEGER NOT NULL," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`left_recording_id`, `right_recording_id`)," +
                "FOREIGN KEY(`left_recording_id`) REFERENCES `recordings`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE," +
                "FOREIGN KEY(`right_recording_id`) REFERENCES `recordings`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_recording_relations_right` ON " +
                "`recording_relations` (`right_recording_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_recording_relations_type` ON " +
                "`recording_relations` (`relation_type`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_recording_relations_updated` ON " +
                "`recording_relations` (`updated_at`)"
        )
    }

    private fun normalizeV25(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `works` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                "`canonical_uuid` TEXT NOT NULL," +
                "`normalized_title` TEXT NOT NULL," +
                "`primary_creator_id` INTEGER," +
                "`created_at` INTEGER NOT NULL," +
                "`updated_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_works_uuid` ON `works` (`canonical_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_works_title` ON `works` (`normalized_title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_works_creator` ON `works` (`primary_creator_id`)")
        if (!columnExists(db, "recordings", "work_id")) {
            db.execSQL("ALTER TABLE `recordings` ADD COLUMN `work_id` INTEGER")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_recordings_work` ON `recordings` (`work_id`)")
        db.execSQL(
            "INSERT OR IGNORE INTO `works`(" +
                "`canonical_uuid`,`normalized_title`,`primary_creator_id`,`created_at`,`updated_at`) " +
                "SELECT r.`canonical_uuid`,LOWER(TRIM(r.`title`))," +
                "(SELECT c.`artist_id` FROM `recording_artist_credits` c " +
                "WHERE c.`recording_id`=r.`id` AND c.`role`='PRIMARY' " +
                "ORDER BY c.`position`,c.`artist_id` LIMIT 1),r.`created_at`,r.`updated_at` " +
                "FROM `recordings` r"
        )
        db.execSQL(
            "UPDATE `recordings` SET `work_id`=(SELECT w.`id` FROM `works` w " +
                "WHERE w.`canonical_uuid`=`recordings`.`canonical_uuid` LIMIT 1) " +
                "WHERE `work_id` IS NULL"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audio_features` (" +
                "`source_id` INTEGER NOT NULL," +
                "`content_signature` TEXT NOT NULL," +
                "`pcm_hash` TEXT NOT NULL," +
                "`chromaprint` TEXT NOT NULL," +
                "`recording_embedding` BLOB," +
                "`work_embedding` BLOB," +
                "`version_scores` TEXT NOT NULL," +
                "`algorithm_version` INTEGER NOT NULL," +
                "`audio_spec_state` TEXT NOT NULL," +
                "`audio_spec_algorithm_version` INTEGER NOT NULL," +
                "`audio_spec_attempt_count` INTEGER NOT NULL," +
                "`last_attempt_at` INTEGER NOT NULL," +
                "`last_error` TEXT NOT NULL," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`source_id`)," +
                "FOREIGN KEY(`source_id`) REFERENCES `track_sources`(`source_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_audio_features_signature` ON " +
                "`audio_features` (`content_signature`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_audio_features_algorithm` ON " +
                "`audio_features` (`algorithm_version`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_audio_features_spec_state` ON " +
                "`audio_features` (`audio_spec_state`,`last_attempt_at`)"
        )
    }

    internal fun normalizeV26(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "canonical_artists", "avatar_url")) {
            db.execSQL(
                "ALTER TABLE `canonical_artists` ADD COLUMN " +
                    "`avatar_url` TEXT NOT NULL DEFAULT ''"
            )
        }
        // Existing artist jobs may have succeeded before avatar enrichment existed. Requeue each
        // missing artist exactly once during this schema upgrade; normal track ingestion owns new
        // jobs after that, so artists without a Wikimedia image are not retried every six hours.
        db.execSQL(
            "DELETE FROM `identity_resolution_jobs` WHERE `target_type`='ARTIST' " +
                "AND `target_id` IN (SELECT `id` FROM `canonical_artists` WHERE `avatar_url`='')"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `identity_resolution_jobs`(" +
                "`job_id`,`target_type`,`target_id`,`priority`,`reason`,`attempt_count`," +
                "`next_attempt_at`,`last_error`,`status`,`created_at`,`updated_at`) " +
                "SELECT lower(hex(randomblob(16))),'ARTIST',`id`,60,'MISSING_ARTIST_AVATAR'," +
                "0,0,'','PENDING',CAST(strftime('%s','now') AS INTEGER)*1000," +
                "CAST(strftime('%s','now') AS INTEGER)*1000 " +
                "FROM `canonical_artists` WHERE `avatar_url`=''"
        )
    }

    internal fun normalizeV27(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_lyrics` (" +
                "`identity_key` TEXT NOT NULL," +
                "`recording_id` INTEGER," +
                "`provider` TEXT NOT NULL DEFAULT ''," +
                "`provider_track_id` TEXT NOT NULL DEFAULT ''," +
                "`source_name` TEXT NOT NULL DEFAULT ''," +
                "`format` TEXT NOT NULL DEFAULT ''," +
                "`document_json` TEXT NOT NULL," +
                "`checksum` TEXT NOT NULL DEFAULT ''," +
                "`updated_at` INTEGER NOT NULL," +
                "PRIMARY KEY(`identity_key`)," +
                "FOREIGN KEY(`recording_id`) REFERENCES `recordings`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_custom_lyrics_recording` ON " +
                "`custom_lyrics` (`recording_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_custom_lyrics_provider_track` ON " +
                "`custom_lyrics` (`provider`, `provider_track_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_custom_lyrics_updated` ON " +
                "`custom_lyrics` (`updated_at`)"
        )
    }

    internal fun normalizeV28(db: SupportSQLiteDatabase) {
        if (!columnExists(db, "canonical_artists", "description")) {
            db.execSQL(
                "ALTER TABLE `canonical_artists` ADD COLUMN " +
                    "`description` TEXT NOT NULL DEFAULT ''"
            )
        }
        // Artist jobs completed before profile descriptions existed need one new lookup. The
        // enhancement engine treats a missing remote description as a valid result, so this
        // migration schedules one pass without creating a permanent retry loop.
        db.execSQL(
            "DELETE FROM `identity_resolution_jobs` WHERE `target_type`='ARTIST' " +
                "AND `target_id` IN (SELECT `id` FROM `canonical_artists` WHERE `description`='')"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `identity_resolution_jobs`(" +
                "`job_id`,`target_type`,`target_id`,`priority`,`reason`,`attempt_count`," +
                "`next_attempt_at`,`last_error`,`status`,`created_at`,`updated_at`) " +
                "SELECT lower(hex(randomblob(16))),'ARTIST',`id`,70,'MISSING_ARTIST_DESCRIPTION'," +
                "0,0,'','PENDING',CAST(strftime('%s','now') AS INTEGER)*1000," +
                "CAST(strftime('%s','now') AS INTEGER)*1000 " +
                "FROM `canonical_artists` WHERE `description`=''"
        )
    }

    internal fun normalizeV30(db: SupportSQLiteDatabase) {
        addTextColumn(db, "tracks", "album_artist")
        addTextColumn(db, "tracks", "composer")
        addTextColumn(db, "tracks", "release_type")
        addIntegerColumn(db, "tracks", "year")
        addTextColumn(db, "playback_queue", "album_artist")
        addTextColumn(db, "playback_queue", "composer")
        addTextColumn(db, "playback_queue", "release_type")
        addIntegerColumn(db, "playback_queue", "year")
        addTextColumn(db, "track_sources", "album_artist")
        addTextColumn(db, "track_sources", "composer")
        addTextColumn(db, "track_sources", "release_type")
        addIntegerColumn(db, "track_sources", "year")
        if (!columnExists(db, "source_match_features", "metadata_vector")) {
            db.execSQL("ALTER TABLE `source_match_features` ADD COLUMN `metadata_vector` BLOB")
        }
        if (!columnExists(db, "source_match_features", "metadata_vector_version")) {
            db.execSQL(
                "ALTER TABLE `source_match_features` ADD COLUMN " +
                    "`metadata_vector_version` INTEGER NOT NULL DEFAULT 0"
            )
        }
        if (!columnExists(db, "source_match_features", "metadata_sim_hash")) {
            db.execSQL("ALTER TABLE `source_match_features` ADD COLUMN `metadata_sim_hash` INTEGER")
        }
    }

    internal fun normalizeV31(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `canonical_albums` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`album_uuid` TEXT NOT NULL, `identity_key` TEXT NOT NULL, " +
                "`display_name` TEXT NOT NULL, `sort_name` TEXT NOT NULL DEFAULT '', " +
                "`album_artist_id` INTEGER, " +
                "`musicbrainz_release_group_id` TEXT NOT NULL DEFAULT '', " +
                "`musicbrainz_release_id` TEXT NOT NULL DEFAULT '', " +
                "`release_type` TEXT NOT NULL DEFAULT '', `year` INTEGER NOT NULL DEFAULT 0, " +
                "`match_status` TEXT NOT NULL DEFAULT 'UNRESOLVED', " +
                "`confidence` REAL NOT NULL DEFAULT 0, " +
                "`metadata_source` TEXT NOT NULL DEFAULT '', " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`album_artist_id`) REFERENCES `canonical_artists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_aliases` (" +
                "`album_id` INTEGER NOT NULL, `alias` TEXT NOT NULL, " +
                "`normalized_alias` TEXT NOT NULL, `locale` TEXT NOT NULL DEFAULT '', " +
                "`alias_type` TEXT NOT NULL DEFAULT 'ALIAS', `source` TEXT NOT NULL DEFAULT '', " +
                "`confidence` REAL NOT NULL DEFAULT 0, `verified_at` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`album_id`, `normalized_alias`, `locale`), " +
                "FOREIGN KEY(`album_id`) REFERENCES `canonical_albums`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_source_mappings` (" +
                "`mapping_id` INTEGER PRIMARY KEY AUTOINCREMENT, `album_id` INTEGER NOT NULL, " +
                "`provider` TEXT NOT NULL, `provider_album_id` TEXT NOT NULL, " +
                "`display_name` TEXT NOT NULL DEFAULT '', " +
                "`musicbrainz_release_group_id` TEXT NOT NULL DEFAULT '', " +
                "`musicbrainz_release_id` TEXT NOT NULL DEFAULT '', " +
                "`status` TEXT NOT NULL DEFAULT 'UNRESOLVED', " +
                "`confidence` REAL NOT NULL DEFAULT 0, `last_verified_at` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`album_id`) REFERENCES `canonical_albums`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        if (!columnExists(db, "track_sources", "album_id")) {
            db.execSQL(
                "ALTER TABLE `track_sources` ADD COLUMN `album_id` INTEGER " +
                    "REFERENCES `canonical_albums`(`id`) ON DELETE SET NULL"
            )
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_album_uuid` ON `canonical_albums` (`album_uuid`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_album_identity_key` " +
                "ON `canonical_albums` (`identity_key`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_album_artist` ON `canonical_albums` (`album_artist_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_album_mbid_release_group` " +
                "ON `canonical_albums` (`musicbrainz_release_group_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_album_mbid_release` " +
                "ON `canonical_albums` (`musicbrainz_release_id`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_album_status` ON `canonical_albums` (`match_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_album_aliases_normalized` " +
                "ON `album_aliases` (`normalized_alias`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_album_aliases_album` ON `album_aliases` (`album_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_album_source_album` " +
                "ON `album_source_mappings` (`album_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_album_source_provider_album` " +
                "ON `album_source_mappings` (`provider`, `provider_album_id`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_track_source_album` ON `track_sources` (`album_id`)")
        backfillCanonicalAlbums(db)
    }

    internal fun normalizeV32(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_artist_credits` (" +
                "`work_id` INTEGER NOT NULL, `artist_id` INTEGER NOT NULL, " +
                "`role` TEXT NOT NULL DEFAULT 'UNKNOWN', `position` INTEGER NOT NULL DEFAULT 0, " +
                "`credited_name` TEXT NOT NULL DEFAULT '', `source` TEXT NOT NULL DEFAULT '', " +
                "`confidence` REAL NOT NULL DEFAULT 0, `verified_at` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`work_id`,`artist_id`,`role`,`position`), " +
                "FOREIGN KEY(`work_id`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`artist_id`) REFERENCES `canonical_artists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_identifiers` (" +
                "`work_id` INTEGER NOT NULL, `identifier_type` TEXT NOT NULL, " +
                "`namespace` TEXT NOT NULL, `identifier_value` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL DEFAULT '', `confidence` REAL NOT NULL DEFAULT 0, " +
                "`verified_at` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`identifier_type`,`namespace`,`identifier_value`), " +
                "FOREIGN KEY(`work_id`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_work_artist_credits_work` " +
                "ON `work_artist_credits` (`work_id`,`position`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_work_artist_credits_artist` " +
                "ON `work_artist_credits` (`artist_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_work_identifiers_work` " +
                "ON `work_identifiers` (`work_id`)"
        )
        addRealColumn(db, "source_match_features", "title_trust", 0.7)
        addRealColumn(db, "source_match_features", "artist_trust", 0.7)
        addRealColumn(db, "source_match_features", "version_trust", 0.7)
        addRealColumn(db, "source_match_features", "identifier_trust", 0.2)
        addRealColumn(db, "source_match_features", "work_credit_trust", 0.2)
        addTextColumn(db, "source_match_features", "evidence_provenance")
        db.execSQL(
            "INSERT OR IGNORE INTO recording_identifiers(" +
                "recording_id,identifier_type,namespace,identifier_value,source,confidence,verified_at) " +
                "SELECT id,'ISRC','',upper(replace(replace(trim(isrc),'-',''),' ',''))," +
                "'MIGRATION_V32',confidence,updated_at FROM recordings " +
                "WHERE trim(isrc) != ''"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO work_identifiers(" +
                "work_id,identifier_type,namespace,identifier_value,source,confidence,verified_at) " +
                "SELECT work_id,'MUSICBRAINZ_WORK_ID','',lower(trim(musicbrainz_work_id))," +
                "'MIGRATION_V32',confidence,updated_at FROM recordings " +
                "WHERE work_id IS NOT NULL AND trim(musicbrainz_work_id) != ''"
        )
    }

    internal fun normalizeV33(db: SupportSQLiteDatabase) {
        addTextColumn(db, "identity_operations", "dedup_mode")
        addIntegerColumn(db, "identity_operations", "policy_version")
        addTextColumn(db, "identity_operations", "evaluation_batch")
        if (!columnExists(db, "identity_operations", "rollback_status")) {
            db.execSQL(
                "ALTER TABLE `identity_operations` ADD COLUMN " +
                    "`rollback_status` TEXT NOT NULL DEFAULT 'NONE'"
            )
        }
        addTextColumn(db, "identity_operations", "post_state_hash")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_source_candidates_global` ON " +
                "`source_recording_candidates` (`state`,`coarse_score`,`updated_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_identity_operations_dedup_rollback` ON " +
                "`identity_operations` (`dedup_mode`,`rollback_status`,`created_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_recording_relations_global_candidates` ON " +
                "`recording_relations` " +
                "(`locked`,`relation_type`,`same_recording_probability`,`updated_at`)"
        )
    }

    private fun backfillCanonicalAlbums(db: SupportSQLiteDatabase) {
        val albumArtistId =
            "COALESCE((SELECT c.artist_id FROM recording_artist_credits c " +
                "WHERE c.recording_id=track_sources.recording_id " +
                "AND c.role IN ('PRIMARY','UNKNOWN') " +
                "ORDER BY CASE c.role WHEN 'PRIMARY' THEN 0 ELSE 1 END,c.position,c.artist_id LIMIT 1),0)"
        val artistIdentity =
            "CASE WHEN $albumArtistId > 0 THEN 'id:' || $albumArtistId " +
                "ELSE 'name:' || lower(trim(CASE WHEN track_sources.album_artist != '' " +
                "THEN track_sources.album_artist ELSE track_sources.artist END)) END"
        val identityKey =
            "lower(trim(track_sources.album)) || '|' || $artistIdentity || '|' || " +
                "track_sources.year || '|' || lower(trim(track_sources.release_type))"
        db.execSQL(
            "INSERT OR IGNORE INTO canonical_albums(" +
                "album_uuid, identity_key, display_name, sort_name, album_artist_id, " +
                "musicbrainz_release_group_id, musicbrainz_release_id, release_type, year, " +
                "match_status, confidence, metadata_source, created_at, updated_at) " +
                "SELECT lower(hex(randomblob(16))), $identityKey, max(trim(album)), " +
                "lower(trim(album)), NULLIF($albumArtistId,0), '', '', max(release_type), max(year), " +
                "'UNRESOLVED', 0, 'MIGRATION_V31', 0, 0 FROM track_sources " +
                "WHERE trim(album) != '' GROUP BY $identityKey"
        )
        db.execSQL(
            "UPDATE track_sources SET album_id=(" +
                "SELECT id FROM canonical_albums WHERE identity_key=$identityKey LIMIT 1" +
                ") WHERE album_id IS NULL AND trim(album) != ''"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO album_aliases(" +
                "album_id, alias, normalized_alias, locale, alias_type, source, confidence, verified_at) " +
                "SELECT album_id, album, lower(trim(album)), '', 'PRIMARY', 'MIGRATION_V31', 0, 0 " +
                "FROM track_sources WHERE album_id IS NOT NULL AND trim(album) != ''"
        )
    }

    private fun addTextColumn(db: SupportSQLiteDatabase, table: String, column: String) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun normalizeV34(db: SupportSQLiteDatabase) {
        addTextColumn(db, "tracks", "genre")
        addIntegerColumn(db, "tracks", "disc_number")
        addIntegerColumn(db, "tracks", "track_number")
        addIntegerColumn(db, "tracks", "bpm")
        addTextColumn(db, "tracks", "lyrics")
        addTextColumn(db, "playback_queue", "genre")
        addIntegerColumn(db, "playback_queue", "disc_number")
        addIntegerColumn(db, "playback_queue", "track_number")
        addIntegerColumn(db, "playback_queue", "bpm")
        addTextColumn(db, "playback_queue", "lyrics")
    }

    private fun normalizeV35(db: SupportSQLiteDatabase) {
        addIntegerColumn(db, "remote_sources", "allow_insecure_tls")
    }

    private fun addIntegerColumn(db: SupportSQLiteDatabase, table: String, column: String) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun addRealColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        defaultValue: Double
    ) {
        if (!columnExists(db, table, column)) {
            db.execSQL(
                "ALTER TABLE `$table` ADD COLUMN `$column` REAL NOT NULL DEFAULT $defaultValue"
            )
        }
    }

    private fun columnExists(
        db: SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return@use true
        }
        false
    }
}
