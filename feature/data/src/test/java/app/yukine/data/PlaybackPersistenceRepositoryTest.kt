package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackPersistenceRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "playback-persistence-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: PlaybackPersistenceRepository

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = YukineDatabase.openForTest(context, databaseName)
        repository = PlaybackPersistenceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun queueAndPositionRoundTripPreservesLargeQueueAndIndex() {
        val tracks = List(1_024) { index -> track(index.toLong() + 1L) }

        repository.saveQueue(tracks, 777)
        repository.savePosition(tracks[777].id, 98_765L)

        assertEquals(1_024, repository.loadQueue().size)
        assertEquals(777, repository.loadQueueIndex())
        assertEquals(tracks[777].id, repository.loadPositionTrackId())
        assertEquals(98_765L, repository.loadPositionMs())
        assertEquals(tracks.first().id, repository.loadQueue().first().id)
        assertEquals(tracks.last().id, repository.loadQueue().last().id)
    }

    @Test
    fun invalidReplacementDoesNotEraseExistingQueue() {
        repository.saveQueue(listOf(track(1L)), 0)

        try {
            @Suppress("UNCHECKED_CAST")
            repository.saveQueue(listOf(track(2L), null) as List<Track>, 1)
            fail("Expected invalid queue entry to abort")
        } catch (_: NullPointerException) {
        }

        assertEquals(listOf(1L), repository.loadQueue().map(Track::id))
        assertEquals(0, repository.loadQueueIndex())
    }

    private fun track(id: Long) = Track(
        id,
        "Track $id",
        "Artist",
        "Album",
        120_000L,
        Uri.parse("file:///track-$id.flac"),
        "/music/track-$id.flac",
        id,
        null,
        "flac",
        900,
        96_000,
        24,
        2,
        -4.5f,
        -3.0f
    )
}
