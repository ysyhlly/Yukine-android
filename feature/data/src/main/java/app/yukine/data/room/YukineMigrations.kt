package app.yukine.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object YukineMigrations {
    const val TARGET_VERSION: Int = 25

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
            }
        }
    }.toTypedArray()

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
