package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.room.YukineDatabase
import app.yukine.fingerprint.AudioFingerprintEvidence
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
class AudioFingerprintPersistenceTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "audio-fingerprint-persistence.db"
    private lateinit var database: YukineDatabase
    private lateinit var library: LibraryRepository
    private lateinit var repository: MusicLibraryRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        library = LibraryRepository(database)
        repository = MusicLibraryRepository(context, LocalPathParser, database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun staleFingerprintCannotOverwriteChangedContent() {
        library.upsertTracks(listOf(track("/music/original.flac")))
        val first = repository.loadPendingAudioFingerprintCandidates(4).single()

        assertTrue(repository.saveAudioFingerprint(
            first,
            AudioFingerprintEvidence("pcm-a", "chromaprint-a", 1, 30_000L)
        ))
        assertEquals(
            "chromaprint-a",
            database.musicIdentityDao().audioFeature(first.sourceId)?.chromaprint
        )

        library.upsertTracks(listOf(track("/music/replaced.flac")))
        val changed = repository.loadPendingAudioFingerprintCandidates(4).single()
        assertFalse(first.contentSignature == changed.contentSignature)
        assertEquals("", database.musicIdentityDao().audioFeature(changed.sourceId)?.chromaprint)

        assertFalse(repository.saveAudioFingerprint(
            first,
            AudioFingerprintEvidence("stale", "stale", 1, 30_000L)
        ))
        assertTrue(repository.saveAudioFingerprint(
            changed,
            AudioFingerprintEvidence("pcm-b", "chromaprint-b", 1, 30_000L)
        ))
        assertEquals(
            "chromaprint-b",
            database.musicIdentityDao().audioFeature(changed.sourceId)?.chromaprint
        )
    }

    @Test
    fun recentFailureBacksOffWithoutMarkingSourceUnplayable() {
        library.upsertTracks(listOf(track("/music/unreadable.flac")))
        val candidate = repository.loadPendingAudioFingerprintCandidates(4).single()

        assertTrue(repository.recordAudioFingerprintFailure(candidate, "decoder unavailable"))
        assertTrue(repository.loadPendingAudioFingerprintCandidates(4).isEmpty())
        val source = checkNotNull(database.musicIdentityDao().sourceForLocalTrack(candidate.track.id))
        assertTrue(source.playable)
        assertEquals(
            "FINGERPRINT_DECODER_UNAVAILABLE",
            database.musicIdentityDao().audioFeature(candidate.sourceId)?.lastError
        )
    }

    @Test
    fun webDavCandidateIsExplicitAndRevisionSafeWithoutEnteringLocalBatch() {
        val original = webDavTrack("https://dav.example/song.flac#echoRevision=first")
        library.upsertTracks(listOf(original))

        assertTrue(repository.loadPendingAudioFingerprintCandidates(4).isEmpty())
        val first = checkNotNull(repository.loadPendingWebDavAudioFingerprintCandidate(original.id))
        assertTrue(repository.saveAudioFingerprint(
            first,
            AudioFingerprintEvidence("", "cached-head-a", 1, 10_000L)
        ))
        assertNull(repository.loadPendingWebDavAudioFingerprintCandidate(original.id))

        val revised = webDavTrack("https://dav.example/song.flac#echoRevision=second")
        library.upsertTracks(listOf(revised))
        val changed = checkNotNull(repository.loadPendingWebDavAudioFingerprintCandidate(revised.id))

        assertEquals(first.sourceId, changed.sourceId)
        assertFalse(first.contentSignature == changed.contentSignature)
        assertEquals("", database.musicIdentityDao().audioFeature(changed.sourceId)?.chromaprint)
    }

    private fun track(path: String) = Track(
        901L,
        "Fingerprint",
        "Artist",
        "Album",
        180_000L,
        Uri.parse("content://media/external/audio/media/901"),
        path,
        9L,
        null
    )

    private fun webDavTrack(uri: String) = Track(
        902L,
        "WebDAV fingerprint",
        "Artist",
        "Album",
        180_000L,
        Uri.parse(uri),
        "webdav:9:/music/song.flac",
        9L,
        null
    )

    private object LocalPathParser : StreamingDataPathParser {
        override fun isStreamingTrack(dataPath: String) = false
        override fun providerName(dataPath: String): String? = null
        override fun providerTrackId(dataPath: String) = ""
    }
}
