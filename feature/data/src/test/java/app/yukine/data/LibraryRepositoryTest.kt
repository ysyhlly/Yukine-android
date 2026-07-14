package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.PlayHistoryEntity
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
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
class LibraryRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "library-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var playback: PlaybackPersistenceRepository
    private lateinit var playlists: PlaylistRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = LibraryRepository(database)
        playback = PlaybackPersistenceRepository(database)
        playlists = PlaylistRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun deleteReconcilesEveryReferenceQueueIndexAndPositionAtomically() {
        val first = track(1L)
        val deleted = track(2L)
        val last = track(3L)
        repository.upsertTracks(listOf(first, deleted, last))
        repository.setFavorite(deleted.id, true)
        HistoryRepository(database.historyDao()).markPlayed(deleted.id)
        val playlistId = playlists.create("Delete")
        playlists.addTrack(playlistId, deleted.id)
        playback.saveQueue(listOf(first, deleted, last), 2)
        playback.savePosition(deleted.id, 44_000L)

        assertEquals(1, repository.deleteTrack(deleted.id))

        assertNull(database.libraryDao().loadTrack(deleted.id))
        assertFalse(repository.isFavorite(deleted.id))
        assertTrue(HistoryRepository(database.historyDao()).loadRecentlyPlayed(10).isEmpty())
        assertTrue(playlists.loadTracks(playlistId).isEmpty())
        assertEquals(listOf(first.id, last.id), playback.loadQueue().map { it.id })
        assertEquals(1, playback.loadQueueIndex())
        assertEquals(-1L, playback.loadPositionTrackId())
        assertEquals(0L, playback.loadPositionMs())
    }

    @Test
    fun replacementMergesReferencesAndCollapsesDuplicateQueueEntries() {
        val old = track(10L, "Old")
        val replacement = track(20L, "Replacement")
        val other = track(30L, "Other")
        repository.upsertTracks(listOf(old, replacement, other))
        repository.setFavorite(old.id, true)
        database.historyDao().upsertHistory(PlayHistoryEntity(old.id, 100L, 2))
        database.historyDao().upsertHistory(PlayHistoryEntity(replacement.id, 200L, 3))
        val playlistId = playlists.create("Merge")
        playlists.addTrack(playlistId, old.id)
        playlists.addTrack(playlistId, replacement.id)
        playback.saveQueue(listOf(old, replacement, other), 0)
        playback.savePosition(old.id, 9_000L)

        repository.replaceTrackAndMigrateReferences(old.id, replacement)

        assertNull(database.libraryDao().loadTrack(old.id))
        assertTrue(repository.isFavorite(replacement.id))
        assertEquals(5, database.historyDao().history(replacement.id)?.playCount)
        assertEquals(listOf(replacement.id), playlists.loadTracks(playlistId).map { it.id })
        assertEquals(listOf(replacement.id, other.id), playback.loadQueue().map { it.id })
        assertEquals(0, playback.loadQueueIndex())
        assertEquals(replacement.id, playback.loadPositionTrackId())
        assertEquals(9_000L, playback.loadPositionMs())
    }

    @Test
    fun scanReplacementPreservesNonScanSourcesAndHonorsExclusions() {
        val removedLocal = track(100L, "Removed", "/music/removed.flac")
        val retainedLocal = track(101L, "Retained", "/music/retained.flac")
        val document = track(102L, "Document", "document:tree/file")
        val stream = track(103L, "Stream", "stream:one")
        val webdav = track(104L, "WebDAV", "webdav:7:/one.flac")
        repository.upsertTracks(listOf(removedLocal, retainedLocal, document, stream, webdav))
        assertEquals(1, repository.hideTracks(listOf(retainedLocal)))

        repository.replaceScanManagedTracks(listOf(retainedLocal, track(105L, "New", "/music/new.flac")))

        assertEquals(
            setOf(document.id, stream.id, webdav.id, 105L),
            repository.loadTracks().map { it.id }.toSet()
        )
        assertEquals(1, repository.loadExclusions().size)
    }

    @Test
    fun audioSpecUpdatesLibraryAndQueueSnapshotTogether() {
        val original = track(200L)
        val enriched = Track(
            original.id,
            original.title,
            original.artist,
            original.album,
            original.durationMs,
            original.contentUri,
            original.dataPath,
            original.albumId,
            original.albumArtUri,
            "flac",
            900,
            96_000,
            24,
            2,
            -6.0f,
            -4.0f
        )
        database.libraryDao().upsertTracks(listOf(TrackEntityMapper.entity(original, 1L)))
        playback.saveQueue(listOf(original), 0)

        assertEquals(1, repository.updateAudioSpecs(listOf(enriched)))

        assertEquals("flac", repository.loadTracks().single().codec)
        assertEquals("flac", playback.loadQueue().single().codec)
        assertEquals(-6.0f, playback.loadQueue().single().replayGainTrackDb)
    }

    private fun track(
        id: Long,
        title: String = "Track $id",
        dataPath: String = "/music/$id.flac"
    ) = Track(
        id,
        title,
        "Artist",
        "Album",
        120_000L,
        Uri.parse("content://media/$id"),
        dataPath,
        id,
        null
    )
}
