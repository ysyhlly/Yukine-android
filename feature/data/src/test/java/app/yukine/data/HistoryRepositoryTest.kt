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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "history-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = HistoryRepository(database)
        val track = Track(1L, "History", "Artist", "Album", 1L, Uri.EMPTY, "/history", 0L, null)
        LibraryRepository(database).upsertTracks(listOf(track))
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
        val canonicalEvents = database.historyDao().recordingEvents(
            checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(1L))
        )
        assertEquals(2, canonicalEvents.size)
        assertTrue(canonicalEvents.all { it.legacyEventId != null })
        assertEquals(2, canonicalEvents.mapNotNull { it.legacyEventId }.distinct().size)
        assertEquals(1, repository.clear())
        assertEquals(emptyList<Any>(), repository.loadRecentlyPlayed(10))
        assertEquals(emptyList<Any>(), repository.loadPlayedSince(0L, 10))
    }

    @Test
    fun historyAggregatesDifferentSourcesByCanonicalRecording() {
        val mbid = "123e4567-e89b-12d3-a456-426614174010"
        val webDav = identifiedTrack(11L, "webdav:1:/history.flac", mbid)
        val local = identifiedTrack(12L, "/music/history.flac", mbid)
        LibraryRepository(database).upsertTracks(listOf(webDav, local))

        repository.markPlayed(webDav.id)
        repository.markPlayed(local.id)

        val record = repository.loadRecentlyPlayed(10).single()
        assertEquals(local.id, record.track.id)
        assertEquals(2, record.playCount)
        assertEquals(2, repository.loadPlayedSince(0L, 10).single().playCount)
    }

    private fun identifiedTrack(id: Long, dataPath: String, mbid: String): Track {
        val base = Track(id, "Canonical history", "Artist", "Album", 120_000L, Uri.EMPTY, dataPath)
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
