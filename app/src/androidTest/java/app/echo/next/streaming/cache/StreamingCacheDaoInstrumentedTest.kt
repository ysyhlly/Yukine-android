package app.echo.next.streaming.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingCacheDaoInstrumentedTest {
    private lateinit var database: StreamingCacheDatabase
    private lateinit var dao: StreamingCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StreamingCacheDatabase::class.java
        ).build()
        dao = database.streamingCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchCacheReturnsOnlyUnexpiredUniqueEntry() = runTest {
        dao.upsertSearch(
            StreamingSearchCacheEntity(
                provider = "netease",
                cacheKey = "netease:echo:track:1:20",
                query = "Echo",
                page = 1,
                pageSize = 20,
                payloadJson = "{\"tracks\":[\"old\"]}",
                createdAtMs = 1_000L,
                expiresAtMs = 2_000L
            )
        )
        dao.upsertSearch(
            StreamingSearchCacheEntity(
                provider = "netease",
                cacheKey = "netease:echo:track:1:20",
                query = "Echo",
                page = 1,
                pageSize = 20,
                payloadJson = "{\"tracks\":[\"new\"]}",
                createdAtMs = 1_500L,
                expiresAtMs = 3_000L
            )
        )

        assertEquals(
            "{\"tracks\":[\"new\"]}",
            dao.search("netease", "netease:echo:track:1:20", 2_500L)?.payloadJson
        )
        assertNull(dao.search("netease", "netease:echo:track:1:20", 3_000L))
    }

    @Test
    fun playbackCacheSeparatesQualityAndDeletesExpiredRows() = runTest {
        dao.upsertPlayback(
            StreamingPlaybackCacheEntity(
                provider = "qqmusic",
                providerTrackId = "track-1",
                quality = "standard",
                payloadJson = "{\"url\":\"standard\"}",
                createdAtMs = 1_000L,
                expiresAtMs = 5_000L
            )
        )
        dao.upsertPlayback(
            StreamingPlaybackCacheEntity(
                provider = "qqmusic",
                providerTrackId = "track-1",
                quality = "lossless",
                payloadJson = "{\"url\":\"lossless\"}",
                createdAtMs = 1_000L,
                expiresAtMs = 2_000L
            )
        )

        assertEquals(
            "{\"url\":\"standard\"}",
            dao.playback("qqmusic", "track-1", "standard", 2_500L)?.payloadJson
        )
        assertNull(dao.playback("qqmusic", "track-1", "lossless", 2_500L))
        assertEquals(1, dao.deleteExpiredPlayback(2_500L))
        assertNotNull(dao.playback("qqmusic", "track-1", "standard", 2_500L))
    }

    @Test
    fun authMetadataAllowsPermanentRowsAndClearsExpiredRows() = runTest {
        dao.upsertAuth(
            StreamingAuthMetadataEntity(
                provider = "spotify",
                payloadJson = "{\"connected\":true}",
                updatedAtMs = 1_000L,
                expiresAtMs = null
            )
        )
        dao.upsertAuth(
            StreamingAuthMetadataEntity(
                provider = "tidal",
                payloadJson = "{\"connected\":false}",
                updatedAtMs = 1_000L,
                expiresAtMs = 2_000L
            )
        )

        assertEquals("{\"connected\":true}", dao.auth("spotify", 10_000L)?.payloadJson)
        assertNull(dao.auth("tidal", 2_000L))
        assertEquals(1, dao.deleteExpiredAuth(2_000L))
        assertEquals(0, dao.deleteExpiredAuth(10_000L))
        assertEquals("{\"connected\":true}", dao.auth("spotify", 10_000L)?.payloadJson)
    }
}
