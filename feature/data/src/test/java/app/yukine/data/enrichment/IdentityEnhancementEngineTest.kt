package app.yukine.data.enrichment

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.BackgroundDatabaseTestRule
import app.yukine.data.RoomArtistIdentityRepository
import app.yukine.data.RoomIdentityCandidateRepository
import app.yukine.data.RoomIdentityJobRepository
import app.yukine.data.RoomRecordingIdentityRepository
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistAlias
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.model.Track
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
class IdentityEnhancementEngineTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "identity-enhancement-engine-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var recordings: RoomRecordingIdentityRepository
    private lateinit var artists: RoomArtistIdentityRepository
    private lateinit var candidates: RoomIdentityCandidateRepository
    private lateinit var jobs: RoomIdentityJobRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        recordings = RoomRecordingIdentityRepository(database)
        artists = RoomArtistIdentityRepository(database)
        candidates = RoomIdentityCandidateRepository(database)
        jobs = RoomIdentityJobRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun highConfidenceOnlineResultIsAutoConfirmedWithoutCreatingPlayableSource() {
        val recording = recordings.ensureCanonicalForTrack(track(1L))
        val canonicalUuid = recording.canonicalId
        val provider = fixedProvider(
            AnonymousProviderResult(
                listOf(
                    AnonymousRecordingCandidate(
                        provider = "musicbrainz",
                        providerItemId = "mb-recording",
                        title = "Song",
                        artists = listOf(ProviderArtistCandidate("mb-artist", "Artist")),
                        album = "Album",
                        durationMs = 180_000L,
                        recordingMbid = "mb-recording"
                    )
                )
            )
        )
        val engine = engine(provider)

        val result = engine.runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        assertEquals(1, result.candidatesSaved)
        val saved = database.musicIdentityDao()
            .candidates(IdentityTargetType.RECORDING.name, recording.recordingId)
            .single()
        assertEquals(IdentityCandidateStatus.AUTO_CONFIRMED.name, saved.status)
        assertTrue(saved.score >= 0.92)
        assertEquals(1, recordings.confirmedSources(recording.recordingId).size)
        assertEquals(canonicalUuid, recordings.canonicalForRecording(recording.recordingId)?.canonicalId)
    }

    @Test
    fun unavailableProvidersRetryPersistentRecordingJob() {
        val recording = recordings.ensureCanonicalForTrack(track(2L))
        val engine = engine(fixedProvider(AnonymousProviderResult(emptyList(), allEndpointsFailed = true)))

        val result = engine.runReadyJobs(20)

        assertTrue(result.retried >= 1)
        val job = database.musicIdentityDao().readyJobs(100L + 15L * 60L * 1_000L, 100)
            .first { it.targetType == "RECORDING" && it.targetId == recording.recordingId }
        assertEquals("RETRY", job.status)
        assertEquals(1, job.attemptCount)
    }

    @Test
    fun artistJobsCreateIsolatedCandidatesInsteadOfOverwritingCanonicalArtist() {
        val recording = recordings.ensureCanonicalForTrack(track(3L))
        val artistBefore = artists.creditsForRecording(recording.recordingId).single().let {
            checkNotNull(artists.artistByKey(it.artistKey))
        }
        val artistProvider = object : AnonymousArtistMetadataProvider {
            override val providerName: String = "musicbrainz"
            override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>) =
                AnonymousArtistProviderResult(
                    listOf(
                        AnonymousArtistCandidate(
                            provider = "musicbrainz",
                            providerItemId = "artist-mbid",
                            displayName = "Artist",
                            aliases = setOf("Artist Alias"),
                            countryCode = "CN",
                            artistMbid = "artist-mbid",
                            providerScore = 1.0
                        )
                    )
                )
        }
        val engine = engine(fixedProvider(AnonymousProviderResult(emptyList())), artistProvider)

        val result = engine.runReadyJobs(20)

        assertTrue(result.succeeded >= 2)
        val saved = candidates.pendingCandidates(IdentityTargetType.ARTIST, artistBefore.artistKey).single()
        assertEquals("artist-mbid", saved.providerItemId)
        assertEquals(artistBefore, artists.artistByKey(artistBefore.artistKey))
    }

    @Test
    fun authoritativeCandidateSkipsFallbackProviders() {
        val recording = recordings.ensureCanonicalForTrack(track(4L))
        val calls = mutableListOf<String>()
        val primary = namedProvider(
            "musicbrainz",
            AnonymousProviderResult(listOf(candidate("musicbrainz", "mb-4")))
        ) { calls += it }
        val fallback = namedProvider(
            "itunes",
            AnonymousProviderResult(listOf(candidate("itunes", "itunes-4")))
        ) { calls += it }
        val engine = engine(listOf(primary, fallback))

        val result = engine.runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        assertEquals(listOf("musicbrainz"), calls)
        assertEquals(
            listOf("musicbrainz"),
            database.musicIdentityDao().candidates("RECORDING", recording.recordingId).map { it.provider }
        )
    }

    @Test
    fun unavailableAuthoritativeProviderQueriesEveryConfiguredFallback() {
        val recording = recordings.ensureCanonicalForTrack(track(5L))
        val canonicalUuid = recording.canonicalId
        val calls = mutableListOf<String>()
        val primary = namedProvider(
            "musicbrainz",
            AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
        ) { calls += it }
        val netease = namedProvider(
            "netease",
            AnonymousProviderResult(listOf(candidate("netease", "netease-5")))
        ) { calls += it }
        val itunes = namedProvider(
            "itunes",
            AnonymousProviderResult(listOf(candidate("itunes", "itunes-5")))
        ) { calls += it }
        val engine = engine(listOf(primary, netease, itunes))

        val result = engine.runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        assertEquals(listOf("musicbrainz", "netease", "itunes"), calls)
        assertEquals(
            setOf("netease", "itunes"),
            database.musicIdentityDao().candidates("RECORDING", recording.recordingId)
                .map { it.provider }
                .toSet()
        )
        assertEquals(canonicalUuid, recordings.canonicalForRecording(recording.recordingId)?.canonicalId)
    }

    private fun engine(
        provider: AnonymousRecordingMetadataProvider,
        artistProvider: AnonymousArtistMetadataProvider? = null
    ) = IdentityEnhancementEngine(
        recordings = recordings,
        artists = artists,
        candidates = candidates,
        jobs = jobs,
        providers = listOf(provider),
        artistProviders = listOfNotNull(artistProvider),
        now = { 100L }
    )

    private fun engine(providers: List<AnonymousRecordingMetadataProvider>) = IdentityEnhancementEngine(
        recordings = recordings,
        artists = artists,
        candidates = candidates,
        jobs = jobs,
        providers = providers,
        now = { 100L }
    )

    private fun fixedProvider(result: AnonymousProviderResult) = object : AnonymousRecordingMetadataProvider {
        override val providerName: String = "musicbrainz"
        override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult = result
    }

    private fun namedProvider(
        name: String,
        result: AnonymousProviderResult,
        onCall: (String) -> Unit
    ) = object : AnonymousRecordingMetadataProvider {
        override val providerName: String = name
        override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult {
            onCall(name)
            return result
        }
    }

    private fun candidate(provider: String, id: String) = AnonymousRecordingCandidate(
        provider = provider,
        providerItemId = id,
        title = "Song",
        artists = listOf(ProviderArtistCandidate("$provider-artist", "Artist")),
        album = "Album",
        durationMs = 180_000L,
        providerScore = 0.95
    )

    private fun track(id: Long) = Track(
        id,
        "Song",
        "Artist",
        "Album",
        180_000L,
        Uri.parse("content://media/$id"),
        "/music/$id.flac",
        id,
        null,
        "",
        0,
        0,
        0,
        0,
        0.0f,
        0.0f
    )
}
