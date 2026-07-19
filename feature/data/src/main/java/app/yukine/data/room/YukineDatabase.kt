package app.yukine.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        RecordingFavoriteEntity::class,
        PlayHistoryEntity::class,
        PlayEventEntity::class,
        RecordingPlayHistoryEntity::class,
        RecordingPlayEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaylistRecordingItemEntity::class,
        SettingEntity::class,
        RemoteSourceEntity::class,
        PlaybackQueueEntity::class,
        PlaybackQueueIdentityEntity::class,
        StreamingTrackMatchEntity::class,
        LibraryExclusionEntity::class,
        CanonicalWorkEntity::class,
        CanonicalRecordingEntity::class,
        CanonicalArtistEntity::class,
        ArtistAliasEntity::class,
        ArtistSourceMappingEntity::class,
        CanonicalAlbumEntity::class,
        AlbumAliasEntity::class,
        AlbumSourceMappingEntity::class,
        RecordingArtistCreditEntity::class,
        TrackSourceMappingEntity::class,
        AudioFeatureEntity::class,
        SourceMatchFeatureEntity::class,
        SourceRecordingCandidateEntity::class,
        RecordingRelationEntity::class,
        RecordingIdentifierEntity::class,
        RecordingVariantEntity::class,
        IdentityCandidateEntity::class,
        IdentityResolutionJobEntity::class,
        ProviderResponseCacheEntity::class,
        LyricBindingEntity::class,
        CustomLyricsEntity::class,
        IdentityOperationEntity::class
    ],
    version = YukineMigrations.TARGET_VERSION,
    exportSchema = true
)
abstract class YukineDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun playbackPersistenceDao(): PlaybackPersistenceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun remoteSourceDao(): RemoteSourceDao
    abstract fun streamingTrackMatchDao(): StreamingTrackMatchDao
    abstract fun musicIdentityDao(): MusicIdentityDao

    companion object {
        const val DATABASE_NAME: String = "echo_next.db"

        @Volatile
        private var instance: YukineDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): YukineDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext, DATABASE_NAME).also { instance = it }
        }

        @JvmStatic
        fun open(context: Context, databaseName: String): YukineDatabase =
            build(context.applicationContext, databaseName)

        /** Closes the process singleton between isolated instrumentation cases. */
        @JvmStatic
        fun resetInstanceForTest() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        internal fun openForTest(context: Context, databaseName: String): YukineDatabase =
            builder(context.applicationContext, databaseName)
                // Robolectric's legacy sqlite4java backend cannot create WAL sidecars from the
                // background-thread rule used by repository tests. Production entry points retain
                // WRITE_AHEAD_LOGGING; this override is isolated to local JVM fixtures.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()

        private fun build(context: Context, databaseName: String): YukineDatabase =
            builder(context, databaseName).build()

        private fun builder(
            context: Context,
            databaseName: String
        ): RoomDatabase.Builder<YukineDatabase> =
            Room.databaseBuilder(context, YukineDatabase::class.java, databaseName)
                // Identity reconciliation and playlist sync both read this database in parallel.
                // WAL keeps those readers off the single rollback-journal connection while the
                // backup archive preserves the database together with its WAL sidecar files.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*YukineMigrations.all)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Room has no table-level UNIQUE annotation. Rebuild the still-empty
                            // table so new installs retain the exact v14 `name TEXT NOT NULL UNIQUE`
                            // schema used by SQLiteOpenHelper releases.
                            YukineSchema.recreateEmptyPlaylistsWithExactUniqueConstraint(db)
                        }
                    }
                )
    }
}
