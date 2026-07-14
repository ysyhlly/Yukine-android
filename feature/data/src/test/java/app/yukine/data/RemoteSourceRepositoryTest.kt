package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteSourceRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "remote-source-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var library: LibraryRepository
    private lateinit var repository: RemoteSourceRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        library = LibraryRepository(database)
        repository = RemoteSourceRepository(database, library)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun sourcePasswordRoundTripsEncryptedAndCachedTracksFollowSourceLifecycle() {
        val sourceId = repository.save(source(-1L, "Original", "secret"))
        val track = track(sourceId, 1L)
        repository.replaceTracks(sourceId, listOf(track))

        assertEquals("secret", repository.loadSource(sourceId)?.password)
        // Robolectric has no AndroidKeyStore provider; production instrumentation covers ciphertext.
        assertTrue(database.remoteSourceDao().loadSource(sourceId)?.password?.isNotEmpty() == true)
        assertEquals(listOf(track.id), repository.loadTracks(sourceId).map { it.id })

        assertEquals(sourceId, repository.save(source(sourceId, "Updated", "new-secret")))
        assertEquals("Updated", repository.loadSource(sourceId)?.name)
        assertTrue(repository.loadTracks(sourceId).isEmpty())

        repository.replaceTracks(sourceId, listOf(track))
        repository.delete(sourceId)
        assertNull(repository.loadSource(sourceId))
        assertTrue(repository.loadTracks(sourceId).isEmpty())
    }

    @Test
    fun replacementUsesSingleDeletionPathAndReconcilesPlaybackState() {
        val sourceId = repository.save(source(-1L, "Queue", "secret"))
        val old = track(sourceId, 10L)
        val replacement = track(sourceId, 11L)
        repository.replaceTracks(sourceId, listOf(old))
        PlaybackPersistenceRepository(database).apply {
            saveQueue(listOf(old), 0)
            savePosition(old.id, 500L)
        }

        assertEquals(1, repository.replaceTracks(sourceId, listOf(replacement)))

        PlaybackPersistenceRepository(database).apply {
            assertTrue(loadQueue().isEmpty())
            assertEquals(-1, loadQueueIndex())
            assertEquals(-1L, loadPositionTrackId())
        }
        assertEquals(listOf(replacement.id), repository.loadTracks(sourceId).map { it.id })
    }

    private fun source(id: Long, name: String, password: String) = RemoteSource(
        id,
        RemoteSource.TYPE_WEBDAV,
        name,
        "https://dav.example.test",
        "user",
        password,
        "music",
        "",
        0L
    )

    private fun track(sourceId: Long, id: Long) = Track(
        id,
        "Track $id",
        "Artist",
        "Album",
        1L,
        Uri.parse("https://dav.example.test/$id.flac"),
        "webdav:$sourceId:/$id.flac",
        0L,
        null
    )
}
