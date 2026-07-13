package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "history-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = HistoryRepository(database.historyDao())
        val track = Track(1L, "History", "Artist", "Album", 1L, Uri.EMPTY, "/history", 0L, null)
        database.libraryDao().upsertTracks(listOf(TrackEntityMapper.entity(track, 1L)))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun repeatedPlaybackUpdatesAggregateAndEventWindowsAtomically() {
        repository.markPlayed(1L)
        repository.markPlayed(1L)

        assertEquals(2, repository.loadRecentlyPlayed(10).single().playCount)
        assertEquals(2, repository.loadMostPlayed(10).single().playCount)
        assertEquals(2, repository.loadPlayedSince(0L, 10).single().playCount)
        assertEquals(1, repository.clear())
        assertEquals(emptyList<Any>(), repository.loadRecentlyPlayed(10))
        assertEquals(emptyList<Any>(), repository.loadPlayedSince(0L, 10))
    }
}
