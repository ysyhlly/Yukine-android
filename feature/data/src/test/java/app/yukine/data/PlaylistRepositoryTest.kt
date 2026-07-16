package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import app.yukine.model.TrackIdentityTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

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

    @Test
    fun playlistStoresOneCanonicalRecordingAndSurvivesSourceRemoval() {
        val mbid = "123e4567-e89b-12d3-a456-426614174012"
        val webDav = identifiedTrack(31L, "webdav:1:/playlist.flac", mbid)
        val local = identifiedTrack(32L, "/music/playlist.flac", mbid)
        val library = LibraryRepository(database)
        library.upsertTracks(listOf(webDav, local))
        val playlist = repository.create("Canonical")

        assertTrue(repository.addTrack(playlist, webDav.id))
        assertFalse(repository.addTrack(playlist, local.id))
        assertEquals(listOf(local.id), repository.loadTracks(playlist).map { it.id })
        assertEquals(1, database.playlistDao().playlistRecordingRows(playlist).size)

        library.deleteTrack(local.id)

        assertEquals(listOf(webDav.id), repository.loadTracks(playlist).map { it.id })
        assertTrue(repository.removeTrack(playlist, webDav.id))
        assertTrue(repository.loadTracks(playlist).isEmpty())
    }

    @Test
    fun replaceTracksWritesCompletePlaylistAndDeduplicatesCanonicalSources() {
        val sharedMbid = "123e4567-e89b-12d3-a456-426614174099"
        val firstSource = identifiedTrack(41L, "webdav:1:/bulk.flac", sharedMbid)
        val secondSource = identifiedTrack(42L, "/music/bulk.flac", sharedMbid)
        val independent = track(43L, "streaming:independent")
        val library = LibraryRepository(database)
        library.upsertTracks(listOf(firstSource, secondSource, independent))
        val playlist = repository.create("Bulk")

        assertEquals(2, repository.replaceTracks(
            playlist,
            listOf(firstSource.id, secondSource.id, independent.id, independent.id)
        ))

        assertEquals(listOf(secondSource.id, independent.id), repository.loadTracks(playlist).map { it.id })
        assertEquals(2, database.playlistDao().playlistRecordingRows(playlist).size)
        assertEquals(2, database.playlistDao().playlistTrackRows(playlist).size)
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

    private fun identifiedTrack(id: Long, dataPath: String, mbid: String): Track {
        val base = track(id, dataPath)
        return Track(
            base.id,
            base.title,
            base.artist,
            base.album,
            base.durationMs,
            base.contentUri,
            base.dataPath,
            base.albumId,
            base.albumArtUri,
            base.codec,
            base.bitrateKbps,
            base.sampleRateHz,
            base.bitsPerSample,
            base.channelCount,
            base.replayGainTrackDb,
            base.replayGainAlbumDb,
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
    }
}
