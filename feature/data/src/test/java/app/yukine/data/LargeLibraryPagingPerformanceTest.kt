package app.yukine.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.TrackEntity
import app.yukine.data.room.YukineDatabase
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LargeLibraryPagingPerformanceTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "large-library-paging-performance.db"
    private lateinit var database: YukineDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = YukineDatabase.openForTest(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun tenThousandTrackCatalogLoadsDeterministicDeepPagesWithinBudget() {
        val tracks = (0 until TRACK_COUNT).map { index ->
            TrackEntity(
                id = index.toLong() + 1L,
                title = "Track ${index.toString().padStart(5, '0')}",
                artist = "Artist ${(index % 200).toString().padStart(3, '0')}",
                album = "Album ${(index % 500).toString().padStart(3, '0')}",
                durationMs = 180_000L,
                contentUri = "content://large/$index",
                dataPath = "/large/$index.flac",
                albumId = (index % 500).toLong(),
                albumArtUri = "",
                codec = "flac",
                bitrateKbps = 1_200,
                sampleRateHz = 96_000,
                bitsPerSample = 24,
                channelCount = 2,
                replayGainTrackDb = 0.0,
                replayGainAlbumDb = 0.0,
                updatedAt = index.toLong()
            )
        }
        database.runInTransaction {
            tracks.chunked(500).forEach(database.libraryDao()::upsertTracks)
        }

        page(0)
        var firstPage = emptyList<Long>()
        var deepPage = emptyList<Long>()
        val elapsedMs = measureTimeMillis {
            firstPage = page(0)
            deepPage = page(TRACK_COUNT - PAGE_SIZE)
        }

        assertEquals(PAGE_SIZE, firstPage.size)
        assertEquals(PAGE_SIZE, deepPage.size)
        assertTrue(firstPage.intersect(deepPage.toSet()).isEmpty())
        assertEquals(TRACK_COUNT.toLong(), scalarLong("SELECT COUNT(*) FROM tracks"))
        assertTrue("Two 128-row pages took ${elapsedMs}ms", elapsedMs < QUERY_BUDGET_MS)
    }

    private fun page(offset: Int): List<Long> = database.openHelper.readableDatabase.query(
        "SELECT id FROM tracks ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, " +
            "title COLLATE NOCASE, id LIMIT $PAGE_SIZE OFFSET $offset"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getLong(0))
        }
    }

    private fun scalarLong(sql: String): Long = database.openHelper.readableDatabase.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private companion object {
        const val TRACK_COUNT = 10_240
        const val PAGE_SIZE = 128
        const val QUERY_BUDGET_MS = 5_000L
    }
}
