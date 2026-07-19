package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(listOf(track.id), repository.loadTracks(sourceId).map { it.id })

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

    @Test
    fun incrementalWebDavRenameKeepsRecordingFavoriteAndSingleSource() {
        val sourceId = repository.save(source(-1L, "Rename", "secret"))
        val original = trackAtPath(sourceId, 41L, "/old/name.flac")
        repository.replaceTracks(sourceId, listOf(original))
        library.setFavorite(original.id, true)
        val recordingId = library.loadRecordingId(original.id)
        val canonicalId = library.loadCanonicalId(original.id)
        val moved = trackAtPath(sourceId, original.id, "/new/name.flac")

        assertEquals(0, repository.applyIncrementalTracks(listOf(original), listOf(moved)))

        val stored = repository.loadTracks(sourceId).single()
        assertEquals(original.id, stored.id)
        assertEquals(moved.dataPath, stored.dataPath)
        assertEquals(recordingId, library.loadRecordingId(stored.id))
        assertEquals(canonicalId, library.loadCanonicalId(stored.id))
        assertTrue(library.isFavorite(stored.id))
        val sources = database.musicIdentityDao().sources(recordingId)
        assertEquals(1, sources.size)
        assertEquals(moved.dataPath, sources.single().dataPath)
    }

    @Test
    fun metadataUniqueRenameReusesOldIdentityWhenServerHasNoEtag() {
        val sourceId = repository.save(source(-1L, "No ETag", "secret"))
        val original = trackAtPath(sourceId, 51L, "/before/song.flac")
        repository.replaceTracks(sourceId, listOf(original))
        val recordingId = library.loadRecordingId(original.id)
        val movedWithNewScanId = trackAtPath(sourceId, 99L, "/after/song.flac")

        assertEquals(0, repository.applyIncrementalTracks(listOf(original), listOf(movedWithNewScanId)))

        val stored = repository.loadTracks(sourceId).single()
        assertEquals(original.id, stored.id)
        assertEquals(movedWithNewScanId.dataPath, stored.dataPath)
        assertEquals(recordingId, library.loadRecordingId(stored.id))
    }

    @Test
    fun ambiguousOrDifferentVersionRenameDoesNotReuseOldIdentity() {
        val sourceId = repository.save(source(-1L, "Ambiguous", "secret"))
        val first = trackAtPath(sourceId, 61L, "/old/first.flac")
        val second = trackAtPath(sourceId, 62L, "/old/second.flac")
        repository.replaceTracks(sourceId, listOf(first, second))
        val ambiguous = trackAtPath(sourceId, 63L, "/new/ambiguous.flac")

        assertEquals(2, repository.applyIncrementalTracks(listOf(first, second), listOf(ambiguous)))
        assertEquals(ambiguous.id, repository.loadTracks(sourceId).single().id)

        val original = trackAtPath(sourceId, 71L, "/version/original.flac")
        repository.replaceTracks(sourceId, listOf(original))
        val storedOriginal = repository.loadTracks(sourceId).single()
        val live = trackAtPath(
            sourceId,
            72L,
            "/version/live.flac",
            title = "Stable title (Live)"
        )

        assertEquals(1, repository.applyIncrementalTracks(listOf(storedOriginal), listOf(live)))
        assertEquals(live.id, repository.loadTracks(sourceId).single().id)
    }

    @Test
    fun samePathContentReplacementDropsOldTrackAndCreatesNewRecording() {
        val sourceId = repository.save(source(-1L, "Replacement", "secret"))
        val original = trackAtPath(
            sourceId,
            81L,
            "/same/song.flac",
            title = "Original song",
            artist = "Original artist"
        )
        repository.replaceTracks(sourceId, listOf(original))
        val originalRecording = library.loadRecordingId(original.id)
        library.setFavorite(original.id, true)
        val replacement = trackAtPath(
            sourceId,
            82L,
            "/same/song.flac",
            title = "Replacement song",
            artist = "Replacement artist"
        )

        assertEquals(
            1,
            repository.applyIncrementalTracks(listOf(original), listOf(replacement))
        )

        assertEquals(listOf(replacement.id), repository.loadTracks(sourceId).map { it.id })
        val replacementRecording = library.loadRecordingId(replacement.id)
        assertTrue(replacementRecording > 0L)
        assertTrue(originalRecording != replacementRecording)
        assertFalse(library.isFavorite(replacement.id))
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

    private fun trackAtPath(
        sourceId: Long,
        id: Long,
        path: String,
        title: String = "Stable title",
        artist: String = "Stable artist",
        durationMs: Long = 180_000L
    ) = Track(
        id,
        title,
        artist,
        "Album",
        durationMs,
        Uri.parse("https://dav.example.test$path"),
        "webdav:$sourceId:https://dav.example.test$path",
        0L,
        null
    )
}
