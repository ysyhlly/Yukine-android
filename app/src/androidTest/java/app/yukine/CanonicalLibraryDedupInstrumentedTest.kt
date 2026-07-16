package app.yukine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yukine.data.LibraryRepository
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalLibraryDedupInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "canonical_library_dedup_test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = YukineDatabase.open(context, databaseName)
        repository = LibraryRepository(database)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun roomRecordingIdsPublishOneRowPerRecordingAndKeepLiveSeparate() {
        val local = track(71L, "Song", "/music/song.flac", 180_000L)
        val webDav = track(72L, "Song", "webdav:9:/music/song.flac", 180_300L)
        val live = track(73L, "Song (Live)", "webdav:9:/music/song-live.flac", 196_000L)
        repository.upsertTracks(listOf(local, webDav, live))
        val rows = database.musicIdentityDao().trackRecordingIdentities()
        val recordingIds = rows.associate { it.localTrackId to it.recordingId }

        assertEquals(recordingIds.getValue(local.id), recordingIds.getValue(webDav.id))
        assertTrue(recordingIds.getValue(local.id) != recordingIds.getValue(live.id))

        val owner = LibraryDataStateOwner(scope, Dispatchers.IO)
        owner.bindRecordingIdentitySnapshotProvider {
            database.musicIdentityDao().trackRecordingIdentities()
                .associate { it.localTrackId to it.recordingId }
        }
        val published = CountDownLatch(1)
        owner.replaceLibraryAsync(repository.loadTracks(), emptySet(), null, Runnable {
            published.countDown()
        })

        assertTrue(published.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(local.id, live.id), owner.allTracks().map { it.id })
        assertEquals(
            setOf(local.id, webDav.id),
            owner.sourceCandidatesFor(owner.allTracks().first()).map { it.id }.toSet()
        )
    }

    private fun track(id: Long, title: String, dataPath: String, durationMs: Long) = Track(
        id,
        title,
        "Artist",
        "Album",
        durationMs,
        Uri.parse("content://test/$id"),
        dataPath,
        id,
        null
    )
}
