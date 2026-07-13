package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "playlist-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = PlaylistRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun playlistOrderMovesAndDuplicateNameIsRejected() {
        val tracks = listOf(track(1L, "streaming:a"), track(2L, "streaming:b"))
        database.libraryDao().upsertTracks(tracks.map { TrackEntityMapper.entity(it, 1L) })
        val playlist = repository.create("Order")

        assertTrue(repository.addTrack(playlist, 1L))
        assertTrue(repository.addTrack(playlist, 2L))
        assertFalse(repository.addTrack(playlist, 2L))
        assertEquals(listOf(1L, 2L), repository.loadTracks(playlist).map { it.id })
        assertTrue(repository.moveAt(playlist, 1, -1))
        assertEquals(listOf(2L, 1L), repository.loadTracks(playlist).map { it.id })
        assertEquals(-1L, repository.create("Order"))
    }

    @Test
    fun deletingPlaylistRemovesOnlyUnreferencedAndUnqueuedStreamingPlaceholders() {
        val shared = track(11L, "streaming:shared")
        val orphan = track(12L, "streaming:orphan")
        val queued = track(13L, "streaming:queued")
        database.libraryDao().upsertTracks(
            listOf(shared, orphan, queued).map { TrackEntityMapper.entity(it, 1L) }
        )
        val first = repository.create("First")
        val second = repository.create("Second")
        repository.addTrack(first, shared.id)
        repository.addTrack(first, orphan.id)
        repository.addTrack(first, queued.id)
        repository.addTrack(second, shared.id)
        PlaybackPersistenceRepository(database).saveQueue(listOf(queued), 0)

        assertTrue(repository.delete(first))

        assertEquals(listOf(shared.id), repository.loadTracks(second).map { it.id })
        assertEquals(setOf(shared.id, queued.id), database.libraryDao().loadTracks().mapNotNull { it.id }.toSet())
        assertEquals(listOf(queued.id), PlaybackPersistenceRepository(database).loadQueue().map { it.id })
    }

    private fun track(id: Long, dataPath: String) = Track(
        id,
        "Track $id",
        "Artist",
        "Album",
        1L,
        Uri.parse("https://example.com/$id"),
        dataPath,
        0L,
        null
    )
}
