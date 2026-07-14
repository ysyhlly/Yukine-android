package app.yukine.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        PlayEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SettingEntity::class,
        RemoteSourceEntity::class,
        PlaybackQueueEntity::class,
        StreamingTrackMatchEntity::class,
        LibraryExclusionEntity::class
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
            builder(context.applicationContext, databaseName).build()

        private fun build(context: Context, databaseName: String): YukineDatabase =
            builder(context, databaseName).build()

        private fun builder(
            context: Context,
            databaseName: String
        ): RoomDatabase.Builder<YukineDatabase> =
            Room.databaseBuilder(context, YukineDatabase::class.java, databaseName)
                // BackupManager copies a single live database snapshot. Keep rollback journaling
                // explicit until export is moved to a database-owned online-backup API.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
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
