package app.yukine.streaming.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StreamingSearchCacheEntity::class,
        StreamingPlaylistCacheEntity::class,
        StreamingPlaybackCacheEntity::class,
        StreamingAuthMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class StreamingCacheDatabase : RoomDatabase() {
    abstract fun streamingCacheDao(): StreamingCacheDao

    companion object {
        private const val DATABASE_NAME = "echo_streaming_cache.db"

        @Volatile
        private var instance: StreamingCacheDatabase? = null

        fun getInstance(context: Context): StreamingCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StreamingCacheDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
