package app.yukine.data.enrichment

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.BackgroundDatabaseTestRule
import app.yukine.data.RoomArtistIdentityRepository
import app.yukine.data.RoomAlbumIdentityRepository
import app.yukine.data.RoomIdentityCandidateRepository
import app.yukine.data.RoomIdentityJobRepository
import app.yukine.data.RoomRecordingIdentityRepository
import app.yukine.data.room.ArtistSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousAlbumCandidate
import app.yukine.identity.AnonymousAlbumMetadataProvider
import app.yukine.identity.AnonymousAlbumProviderResult
import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistAlias
import app.yukine.identity.AlbumAlias
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalAlbum
import app.yukine.identity.IdentityMatchStatus
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
import org.json.JSONObject

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
        confirmBaseIdentity(recording.recordingId, "musicbrainz", "mb-artist")
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
        assertEquals("mb-recording", recordings.canonicalForRecording(recording.recordingId)?.musicBrainzRecordingId)
    }

    @Test
    fun acoustIdAutoConfirmationPersistsIdentifiersWithoutCreatingSource() {
        val recording = recordings.ensureCanonicalForTrack(track(6L))
        val provider = fixedProvider(AnonymousProviderResult(listOf(
            AnonymousRecordingCandidate(
                provider = "acoustid",
                providerItemId = "acoust-result",
                title = "Song",
                artists = listOf(ProviderArtistCandidate("mb-artist", "Artist")),
                album = "Album",
                durationMs = 180_000L,
                recordingMbid = "recording-mbid",
                acoustId = "acoust-result",
                fingerprintVerified = true,
                providerScore = 0.99
            )
        )))

        val result = engine(provider).runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        val updated = checkNotNull(recordings.canonicalForRecording(recording.recordingId))
        assertEquals("recording-mbid", updated.musicBrainzRecordingId)
        assertEquals("acoust-result", updated.acoustId)
        assertEquals(1, recordings.confirmedSources(recording.recordingId).size)
        val identifiers = database.musicIdentityDao().identifiers(recording.recordingId)
        assertTrue(identifiers.isNotEmpty())
        identifiers.forEach { identifier ->
            assertEquals(1.0, identifier.confidence, 0.0)
        }
    }

    @Test
    fun autoConfirmedWinnerWritesTrustedCoverOnlyWhenLocalArtworkIsMissing() {
        val missing = recordings.ensureCanonicalForTrack(track(8L))
        val existing = recordings.ensureCanonicalForTrack(
            track(9L, Uri.parse("content://embedded/existing"))
        )
        confirmBaseIdentity(missing.recordingId, "musicbrainz", "mb-artist")
        val coverUrl = "https://coverartarchive.org/release/release-id/front-500"
        val provider = fixedProvider(AnonymousProviderResult(listOf(
            AnonymousRecordingCandidate(
                provider = "musicbrainz",
                providerItemId = "mb-cover",
                title = "Song",
                artists = listOf(ProviderArtistCandidate("mb-artist", "Artist")),
                album = "Album",
                durationMs = 180_000L,
                recordingMbid = "mb-cover",
                coverUrl = coverUrl
            )
        )))
        val engine = IdentityEnhancementEngine(
            recordings = recordings,
            artists = artists,
            candidates = candidates,
            jobs = jobs,
            providers = listOf(provider),
            missingCoverWriter = RoomMissingRecordingCoverWriter(database),
            now = { 100L }
        )

        engine.runReadyJobs(20)

        assertEquals(coverUrl, database.libraryDao().loadTrack(8L)?.albumArtUri)
        assertEquals(
            "content://embedded/existing",
            database.libraryDao().loadTrack(9L)?.albumArtUri
        )
        assertEquals(
            coverUrl,
            JSONObject(
                database.musicIdentityDao()
                .candidates(IdentityTargetType.RECORDING.name, missing.recordingId)
                .single()
                .evidenceJson
            ).optString("coverUrl")
        )
        assertTrue(existing.recordingId > 0L)
    }

    @Test
    fun manualMetadataConfirmationNeverCreatesPlayableTrackSource() {
        val recording = recordings.ensureCanonicalForTrack(track(7L))
        val provider = fixedProvider(AnonymousProviderResult(listOf(
            AnonymousRecordingCandidate(
                provider = "musicbrainz",
                providerItemId = "mbid-a",
                title = "Song",
                artists = listOf(ProviderArtistCandidate("artist-a", "Artist")),
                durationMs = 180_000L,
                recordingMbid = "mbid-a"
            ),
            AnonymousRecordingCandidate(
                provider = "musicbrainz",
                providerItemId = "mbid-b",
                title = "Song",
                artists = listOf(ProviderArtistCandidate("artist-b", "Artist")),
                durationMs = 180_000L,
                recordingMbid = "mbid-b"
            )
        )))
        engine(provider).runReadyJobs(20)
        val pending = candidates.pendingCandidates(IdentityTargetType.RECORDING, recording.recordingId)

        candidates.confirmCandidate(pending.first().candidateId)

        assertEquals(1, recordings.confirmedSources(recording.recordingId).size)
        assertTrue(recordings.canonicalForRecording(recording.recordingId)?.musicBrainzRecordingId?.isNotBlank() == true)
        assertTrue(database.musicIdentityDao().sources(recording.recordingId).none { it.provider == "musicbrainz" })
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

        assertTrue(result.succeeded >= 1)
        assertTrue(result.retried >= 1)
        val saved = candidates.pendingCandidates(IdentityTargetType.ARTIST, artistBefore.artistKey).single()
        assertEquals("artist-mbid", saved.providerItemId)
        assertEquals(artistBefore, artists.artistByKey(artistBefore.artistKey))
    }

    @Test
    fun exactHighConfidenceArtistCandidateFillsMissingProfileWithoutConfirmingIdentity() {
        val recording = recordings.ensureCanonicalForTrack(track(33L))
        val artistBefore = artists.creditsForRecording(recording.recordingId).single().let {
            checkNotNull(artists.artistByKey(it.artistKey))
        }
        val avatarUrl = "https://api.deezer.com/artist/74702872/image?size=big"
        val description = "A Japanese pop singer and lyricist."
        val artistProvider = object : AnonymousArtistMetadataProvider {
            override val providerName: String = "musicbrainz"
            override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>) =
                AnonymousArtistProviderResult(
                    listOf(
                        AnonymousArtistCandidate(
                            provider = "musicbrainz",
                            providerItemId = "8fb646cc-61e0-4fd6-8a72-1f5684cfba08",
                            displayName = artist.displayName,
                            artistMbid = "8fb646cc-61e0-4fd6-8a72-1f5684cfba08",
                            avatarUrl = avatarUrl,
                            providerScore = 1.0,
                            description = description
                        )
                    )
                )
        }

        val result = engine(
            fixedProvider(AnonymousProviderResult(emptyList())),
            artistProvider
        ).runReadyJobs(20)

        assertTrue(result.succeeded >= 2)
        assertEquals(avatarUrl, artists.artistByKey(artistBefore.artistKey)?.avatarUrl)
        assertEquals(description, artists.artistByKey(artistBefore.artistKey)?.description)
        val saved = candidates.pendingCandidates(IdentityTargetType.ARTIST, artistBefore.artistKey).single()
        assertEquals(app.yukine.identity.IdentityCandidateStatus.PENDING, saved.status)
        assertTrue(saved.evidenceJson.contains(description))
    }

    @Test
    fun terminalMissingAvatarJobIsRequeuedForTheVersionedRepair() {
        val recording = recordings.ensureCanonicalForTrack(track(34L))
        val artistKey = artists.creditsForRecording(recording.recordingId).single().artistKey
        val artistJob = jobs.readyJobs(100L, 100)
            .first { it.targetType == IdentityTargetType.ARTIST && it.targetId == artistKey }
        checkNotNull(jobs.claim(artistJob.jobId, 100L))
        jobs.markSucceeded(artistJob.jobId, 100L)

        val requeued = jobs.requeueMissingArtistAvatarJobs(200L)

        assertEquals(1, requeued)
        val repaired = jobs.readyJobs(200L, 100)
            .first { it.targetType == IdentityTargetType.ARTIST && it.targetId == artistKey }
        assertEquals("MISSING_ARTIST_AVATAR_V2", repaired.reason)
        assertEquals(0, repaired.attemptCount)
    }

    @Test
    fun multipleExactArtistCandidatesWithAvatarsSelectsHighestScored() {
        val recording = recordings.ensureCanonicalForTrack(track(36L))
        val artistBefore = artists.creditsForRecording(recording.recordingId).single().let {
            checkNotNull(artists.artistByKey(it.artistKey))
        }
        val lowerScoredAvatar = "https://provider-a.example.com/artist/low.jpg"
        val higherScoredAvatar = "https://provider-b.example.com/artist/high.jpg"
        val artistProvider = object : AnonymousArtistMetadataProvider {
            override val providerName: String = "musicbrainz"
            override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>) =
                AnonymousArtistProviderResult(
                    listOf(
                        AnonymousArtistCandidate(
                            provider = "musicbrainz",
                            providerItemId = "mbid-low",
                            displayName = artist.displayName,
                            artistMbid = "mbid-low",
                            avatarUrl = lowerScoredAvatar,
                            providerScore = 0.96
                        ),
                        AnonymousArtistCandidate(
                            provider = "netease",
                            providerItemId = "netease-high",
                            displayName = artist.displayName,
                            avatarUrl = higherScoredAvatar,
                            providerScore = 1.0
                        )
                    )
                )
        }

        val result = engine(
            fixedProvider(AnonymousProviderResult(emptyList())),
            artistProvider
        ).runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        val updated = checkNotNull(artists.artistByKey(artistBefore.artistKey))
        assertEquals(higherScoredAvatar, updated.avatarUrl)
    }

    @Test
    fun multipleExactCandidatesWithDescriptionsSelectsHighestScored() {
        val recording = recordings.ensureCanonicalForTrack(track(37L))
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
                            providerItemId = "mbid-desc-low",
                            displayName = artist.displayName,
                            artistMbid = "mbid-desc-low",
                            description = "Lower scored description.",
                            providerScore = 0.96
                        ),
                        AnonymousArtistCandidate(
                            provider = "netease",
                            providerItemId = "netease-desc-high",
                            displayName = artist.displayName,
                            description = "Higher scored description.",
                            providerScore = 1.0
                        )
                    )
                )
        }

        val result = engine(
            fixedProvider(AnonymousProviderResult(emptyList())),
            artistProvider
        ).runReadyJobs(20)

        assertTrue(result.succeeded >= 1)
        val updated = checkNotNull(artists.artistByKey(artistBefore.artistKey))
        assertEquals("Higher scored description.", updated.description)
    }

    @Test
    fun failedArtistJobIsRecoveredAfterCooldown() {
        val recording = recordings.ensureCanonicalForTrack(track(38L))
        val artistKey = artists.creditsForRecording(recording.recordingId).single().artistKey
        val artistJob = jobs.readyJobs(100L, 100)
            .first { it.targetType == IdentityTargetType.ARTIST && it.targetId == artistKey }
        checkNotNull(jobs.claim(artistJob.jobId, 100L))
        jobs.markFailed(artistJob.jobId, "Network error", 100L)

        // Before cooldown: no recovery
        val tooEarly = jobs.recoverFailedArtistJobs(
            100L + RoomIdentityJobRepository.FAILED_RECOVERY_COOLDOWN_MS - 1
        )
        assertEquals(0, tooEarly)

        // After cooldown: recovered
        val recovered = jobs.recoverFailedArtistJobs(
            100L + RoomIdentityJobRepository.FAILED_RECOVERY_COOLDOWN_MS + 1
        )
        assertEquals(1, recovered)
        val retryJob = jobs.readyJobs(
            100L + RoomIdentityJobRepository.FAILED_RECOVERY_COOLDOWN_MS + 1, 100
        ).first { it.targetType == IdentityTargetType.ARTIST && it.targetId == artistKey }
        assertEquals(0, retryJob.attemptCount)
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

    @Test
    fun albumJobsResolveCanonicalAlbumWithoutBeingMisroutedAsRecordings() {
        val recording = recordings.ensureCanonicalForTrack(track(40L))
        val dao = database.musicIdentityDao()
        val albumKey = checkNotNull(dao.sources(recording.recordingId).single().albumId)
        val artistKey = artists.creditsForRecording(recording.recordingId).single().artistKey
        val album = checkNotNull(dao.album(albumKey))
        dao.update(
            album.copy(
                albumArtistId = artistKey,
                releaseType = "Album",
                year = 2024
            )
        )
        val albumProvider = object : AnonymousAlbumMetadataProvider {
            override val providerName: String = "musicbrainz"

            override fun search(album: CanonicalAlbum, aliases: List<AlbumAlias>) =
                AnonymousAlbumProviderResult(
                    listOf(
                        AnonymousAlbumCandidate(
                            provider = "musicbrainz",
                            providerAlbumId = "release-group-mbid",
                            title = "Album",
                            aliases = setOf("专辑"),
                            artist = "Artist",
                            musicBrainzReleaseGroupId = "release-group-mbid",
                            musicBrainzReleaseId = "release-mbid",
                            releaseType = "Album",
                            year = 2024,
                            providerScore = 1.0
                        )
                    )
                )
        }
        val engine = IdentityEnhancementEngine(
            recordings = recordings,
            artists = artists,
            albums = RoomAlbumIdentityRepository(database),
            candidates = candidates,
            jobs = jobs,
            providers = listOf(fixedProvider(AnonymousProviderResult(emptyList()))),
            albumProviders = listOf(albumProvider),
            now = { 100L }
        )

        val result = engine.runReadyJobs(20)

        assertTrue(result.succeeded >= 2)
        val saved = dao.candidates(IdentityTargetType.ALBUM.name, albumKey).single()
        assertEquals(IdentityCandidateStatus.AUTO_CONFIRMED.name, saved.status)
        val resolved = checkNotNull(RoomAlbumIdentityRepository(database).albumByKey(albumKey))
        assertEquals(IdentityMatchStatus.CONFIRMED, resolved.matchStatus)
        assertEquals("release-group-mbid", resolved.musicBrainzReleaseGroupId)
        assertEquals("release-mbid", resolved.musicBrainzReleaseId)
        assertTrue(RoomAlbumIdentityRepository(database).aliases(albumKey).any { it.alias == "专辑" })
    }

    @Test
    fun albumConfirmationCannotStealStrongIdsFromAnotherCanonicalAlbum() {
        val firstRecording = recordings.ensureCanonicalForTrack(track(41L))
        val secondRecording = recordings.ensureCanonicalForTrack(
            track(42L, album = "Other Album")
        )
        val dao = database.musicIdentityDao()
        val firstAlbumKey = checkNotNull(
            dao.sources(firstRecording.recordingId).first { it.localTrackId == 41L }.albumId
        )
        val secondAlbumKey = checkNotNull(
            dao.sources(secondRecording.recordingId).first { it.localTrackId == 42L }.albumId
        )
        val repository = RoomAlbumIdentityRepository(database)
        val candidate = AnonymousAlbumCandidate(
            provider = "musicbrainz",
            providerAlbumId = "shared-provider-id",
            title = "Album",
            musicBrainzReleaseGroupId = "shared-release-group",
            musicBrainzReleaseId = "shared-release",
            providerScore = 1.0
        )
        repository.confirmCandidate(firstAlbumKey, candidate, 100L)

        val conflict = runCatching {
            repository.confirmCandidate(
                secondAlbumKey,
                candidate.copy(title = "Other Album"),
                101L
            )
        }.exceptionOrNull()

        assertTrue(conflict is IllegalArgumentException)
        assertEquals(firstAlbumKey, repository.albumForProvider("musicbrainz", "shared-provider-id")?.albumKey)
        assertEquals(firstAlbumKey, repository.albumForReleaseGroup("shared-release-group")?.albumKey)
        assertEquals(firstAlbumKey, repository.albumForRelease("shared-release")?.albumKey)
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

    private fun confirmBaseIdentity(
        recordingId: Long,
        provider: String,
        providerArtistId: String
    ) {
        val dao = database.musicIdentityDao()
        val artistId = checkNotNull(dao.primaryArtistId(recordingId))
        val artist = checkNotNull(dao.artist(artistId))
        dao.update(
            artist.copy(
                matchStatus = IdentityMatchStatus.CONFIRMED.name,
                confidence = 1.0,
                metadataSource = "TEST"
            )
        )
        dao.upsert(
            ArtistSourceMappingEntity(
                mappingId = null,
                artistId = artistId,
                provider = provider,
                providerArtistId = providerArtistId,
                displayName = artist.displayName,
                status = IdentityMatchStatus.CONFIRMED.name,
                confidence = 1.0,
                lastVerifiedAt = 100L
            )
        )
        assertTrue(checkNotNull(recordings.canonicalForRecording(recordingId)).canonicalWorkConfirmed)
    }

    private fun track(
        id: Long,
        albumArtUri: Uri? = null,
        album: String = "Album"
    ) = Track(
        id,
        "Song",
        "Artist",
        album,
        180_000L,
        Uri.parse("content://media/$id"),
        "/music/$id.flac",
        id,
        albumArtUri,
        "",
        0,
        0,
        0,
        0,
        0.0f,
        0.0f
    )
}
