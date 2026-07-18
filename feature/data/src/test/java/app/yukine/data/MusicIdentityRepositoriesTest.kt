package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.ArtistSourceMappingEntity
import app.yukine.data.room.ArtistAliasEntity
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.PlaybackQueueIdentityEntity
import app.yukine.data.room.RecordingFavoriteEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityJobStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.RecordingIdentifier
import app.yukine.model.Track
import app.yukine.model.TrackIdentityTags
import app.yukine.streaming.RecordingRelationship
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicIdentityRepositoriesTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "music-identity-repositories-test.db"
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
    fun ensureUsesLongHotKeysStableUuidAndConfirmedPhysicalSource() {
        val track = track(1L, "One", "Hanser")

        val first = recordings.ensureCanonicalForTrack(track)
        val second = recordings.ensureCanonicalForTrack(track(1L, "One rescanned", "hanser"))

        assertEquals(first.recordingId, second.recordingId)
        assertEquals(first.canonicalId, second.canonicalId)
        assertEquals(first.canonicalId, UUID.fromString(first.canonicalId).toString())
        val source = recordings.confirmedSources(first.recordingId).single()
        assertTrue(source.sourceId > 0L)
        assertEquals(first.recordingId, source.recordingId)
        assertEquals("local", source.provider)
        assertEquals(1.0, source.confidence, 0.0)
        assertEquals(first.recordingId, artists.creditsForRecording(first.recordingId).single().recordingId)
    }

    @Test
    fun localMoveWithStableMediaIdKeepsCanonicalIdentityAndUpdatesPhysicalPath() {
        val original = recordings.ensureCanonicalForTrack(
            track(2L, "Moved song", "Artist", dataPath = "/music/old/song.flac")
        )

        val moved = recordings.ensureCanonicalForTrack(
            track(2L, "Moved song", "Artist", dataPath = "/music/new/song.flac")
        )

        assertEquals(original.recordingId, moved.recordingId)
        assertEquals(original.canonicalId, moved.canonicalId)
        val source = recordings.confirmedSources(moved.recordingId).single()
        assertEquals(2L, source.localTrackId)
        assertEquals("/music/new/song.flac", source.dataPath)
    }

    @Test
    fun mergeMovesEveryIdentityDependentRowAndSplitCreatesNewStableUuid() {
        val source = recordings.ensureCanonicalForTrack(track(10L, "Song", "Same Artist"))
        val target = recordings.ensureCanonicalForTrack(track(11L, "Song candidate", "Same Artist"))
        recordings.attachIdentifier(
            source.recordingId,
            RecordingIdentifier(
                recordingId = source.recordingId,
                canonicalId = source.canonicalId,
                identifierType = "ISRC",
                identifierValue = "JP-ABC-12-34567",
                source = "TAG",
                confidence = 1.0,
                verifiedAt = 100L
            )
        )
        candidates.saveCandidate(
            candidate(
                targetType = IdentityTargetType.RECORDING,
                targetId = source.recordingId,
                provider = "luoxue",
                providerItemId = "wy:alternate"
            )
        )
        candidates.saveCandidate(
            candidate(
                targetType = IdentityTargetType.RECORDING,
                targetId = target.recordingId,
                provider = "local",
                providerItemId = "/music/10.flac"
            )
        )

        val merged = recordings.mergeRecordings(source.recordingId, target.recordingId)

        assertEquals(target.canonicalId, merged.canonicalId)
        assertNull(recordings.canonicalForRecording(source.recordingId))
        assertEquals(2, database.musicIdentityDao().sourceCount(target.recordingId))
        assertEquals(
            target.recordingId,
            database.musicIdentityDao().identifier("ISRC", "", "JPABC1234567")?.recordingId
        )
        assertEquals(
            target.recordingId,
            candidates.pendingCandidates(IdentityTargetType.RECORDING, target.recordingId)
                .single { it.providerItemId == "wy:alternate" }
                .targetId
        )
        assertTrue(
            candidates.pendingCandidates(IdentityTargetType.RECORDING, target.recordingId)
                .none { it.provider == "local" && it.providerItemId == "/music/10.flac" }
        )

        val sourceToSplit = checkNotNull(
            database.musicIdentityDao().sourceForLocalTrack(10L)?.sourceId
        )
        val split = recordings.splitSource(sourceToSplit)

        assertNotEquals(merged.recordingId, split.recordingId)
        assertNotEquals(merged.canonicalId, split.canonicalId)
        assertEquals(split.canonicalId, UUID.fromString(split.canonicalId).toString())
        assertEquals(1, database.musicIdentityDao().sourceCount(split.recordingId))
        assertTrue(artists.creditsForRecording(split.recordingId).isNotEmpty())
        val splitRelation = requireNotNull(
            RecordingRelationStore(database).relation(merged.recordingId, split.recordingId)
        )
        assertEquals(RecordingRelationship.CANNOT_LINK.name, splitRelation.relationType)
        assertTrue(splitRelation.locked)
        assertThrows(IllegalArgumentException::class.java) {
            recordings.mergeRecordings(split.recordingId, merged.recordingId)
        }
    }

    @Test
    fun mergeRejectsDifferentPrimaryArtistIdentities() {
        val first = recordings.ensureCanonicalForTrack(track(20L, "Same title", "Artist A"))
        val second = recordings.ensureCanonicalForTrack(track(21L, "Same title", "Artist B"))

        assertThrows(IllegalArgumentException::class.java) {
            recordings.mergeRecordings(first.recordingId, second.recordingId)
        }
    }

    @Test
    fun mergeAcceptsUnresolvedParentheticalArtistAliasAndCollapsesPrimaryCredit() {
        val source = recordings.ensureCanonicalForTrack(track(201L, "Re:Call", "霜月はるか"))
        val target = recordings.ensureCanonicalForTrack(track(202L, "Re:Call", "temporary artist identity"))
        val dao = database.musicIdentityDao()
        val sourceArtist = artists.creditsForRecording(source.recordingId).single()
        val targetArtist = artists.creditsForRecording(target.recordingId).single()
        assertNotEquals(sourceArtist.artistKey, targetArtist.artistKey)
        dao.update(
            requireNotNull(dao.artist(targetArtist.artistKey)).copy(
                displayName = "霜月遥 (霜月はるか)",
                sortName = "霜月遥 (霜月はるか)"
            )
        )
        dao.upsert(
            ArtistAliasEntity(
                artistId = targetArtist.artistKey,
                alias = "霜月遥 (霜月はるか)",
                normalizedAlias = "霜月遥 (霜月はるか)",
                locale = "",
                script = "",
                aliasType = "PRIMARY",
                source = "TEST_LEGACY",
                confidence = 0.75,
                verifiedAt = 1L
            )
        )
        RecordingRelationStore(database).upsert(
            listOf(
                RecordingRelationDraft(
                    leftRecordingId = source.recordingId,
                    rightRecordingId = target.recordingId,
                    relationship = RecordingRelationship.SAME_RECORDING,
                    sameRecordingProbability = 0.93,
                    sameWorkProbability = 0.95,
                    confidence = 0.93,
                    origin = "TEST_REEVALUATION",
                    algorithmVersion = Int.MAX_VALUE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )

        recordings.mergeRecordings(source.recordingId, target.recordingId)

        val credits = artists.creditsForRecording(target.recordingId)
        assertEquals(1, credits.size)
        assertEquals(targetArtist.artistKey, credits.single().artistKey)
    }

    @Test
    fun mergeRejectsDistinctConfirmedArtistsEvenWhenNamesLookLikeAliases() {
        val source = recordings.ensureCanonicalForTrack(track(203L, "Re:Call", "霜月はるか"))
        val target = recordings.ensureCanonicalForTrack(track(204L, "Re:Call", "temporary confirmed identity"))
        val dao = database.musicIdentityDao()
        val sourceArtistId = artists.creditsForRecording(source.recordingId).single().artistKey
        val targetArtistId = artists.creditsForRecording(target.recordingId).single().artistKey
        dao.update(requireNotNull(dao.artist(sourceArtistId)).copy(matchStatus = "CONFIRMED"))
        dao.update(
            requireNotNull(dao.artist(targetArtistId)).copy(
                displayName = "霜月遥 (霜月はるか)",
                sortName = "霜月遥 (霜月はるか)",
                matchStatus = "CONFIRMED"
            )
        )
        dao.upsert(
            ArtistAliasEntity(
                artistId = targetArtistId,
                alias = "霜月遥 (霜月はるか)",
                normalizedAlias = "霜月遥 (霜月はるか)",
                locale = "",
                script = "",
                aliasType = "PRIMARY",
                source = "TEST_CONFIRMED",
                confidence = 1.0,
                verifiedAt = 1L
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            recordings.mergeRecordings(source.recordingId, target.recordingId)
        }
    }

    @Test
    fun mergeFailureRollsBackMetadataSourcesAndBusinessReferences() {
        val source = recordings.ensureCanonicalForTrack(track(22L, "Rollback song", "Same Artist"))
        val target = recordings.ensureCanonicalForTrack(track(23L, "Rollback song candidate", "Same Artist"))
        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(source.recordingId, 10L))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER abort_test_recording_merge BEFORE DELETE ON recordings " +
                "WHEN OLD.id = ${source.recordingId} BEGIN " +
                "SELECT RAISE(ABORT, 'forced merge rollback'); END"
        )

        assertThrows(Exception::class.java) {
            recordings.mergeRecordings(source.recordingId, target.recordingId)
        }

        assertEquals(source.canonicalId, recordings.canonicalForRecording(source.recordingId)?.canonicalId)
        assertEquals(target.canonicalId, recordings.canonicalForRecording(target.recordingId)?.canonicalId)
        assertEquals(1, database.musicIdentityDao().sourceCount(source.recordingId))
        assertEquals(1, database.musicIdentityDao().sourceCount(target.recordingId))
        assertEquals(source.recordingId, database.musicIdentityDao().sourceForLocalTrack(22L)?.recordingId)
        assertEquals(target.recordingId, database.musicIdentityDao().sourceForLocalTrack(23L)?.recordingId)
        assertEquals(10L, database.libraryDao().recordingFavorite(source.recordingId)?.createdAt)
        assertNull(database.libraryDao().recordingFavorite(target.recordingId))
        assertTrue(database.musicIdentityDao().identityOperations(target.recordingId, 10).isEmpty())
    }

    @Test
    fun splitFailureRollsBackNewIdentitySourcesReferencesAndJobs() {
        val original = recordings.ensureCanonicalForTrack(track(24L, "Rollback split", "Same Artist"))
        val dao = database.musicIdentityDao()
        dao.upsert(
            TrackSourceMappingEntity(
                sourceId = null,
                recordingId = original.recordingId,
                provider = "webdav",
                providerTrackId = "rollback-live-24",
                localTrackId = null,
                dataPath = "https://example.invalid/rollback-live-24.flac",
                title = "Rollback split (Live)",
                artist = "Same Artist",
                album = "Album",
                durationMs = 120_000L,
                quality = "FLAC",
                qualityScore = 900,
                playable = true,
                matchStatus = "CONFIRMED",
                confidence = 1.0,
                lastSuccessfulAt = 100L,
                lastVerifiedAt = 100L,
                legacyLocalKey = ""
            )
        )
        val movedSourceId = checkNotNull(dao.source("webdav", "rollback-live-24")?.sourceId)
        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(original.recordingId, 10L))
        database.playbackPersistenceDao().insertQueueIdentities(
            listOf(PlaybackQueueIdentityEntity(0, original.recordingId, movedSourceId))
        )
        val beforeRecordingCount = database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM recordings")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        val beforeJobs = dao.readyJobs(Long.MAX_VALUE, 100).map { it.jobId }.toSet()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER abort_test_recording_split BEFORE UPDATE ON recordings " +
                "WHEN OLD.id = ${original.recordingId} BEGIN " +
                "SELECT RAISE(ABORT, 'forced split rollback'); END"
        )

        assertThrows(Exception::class.java) {
            recordings.splitSources(
                setOf(movedSourceId),
                RecordingSplitOptions(
                    favoriteDestination = RecordingSplitDestination.NEW_RECORDING,
                    queueDestination = RecordingSplitDestination.NEW_RECORDING
                )
            )
        }

        val afterRecordingCount = database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM recordings")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        assertEquals(beforeRecordingCount, afterRecordingCount)
        assertEquals(original.canonicalId, recordings.canonicalForRecording(original.recordingId)?.canonicalId)
        assertEquals(2, dao.sourceCount(original.recordingId))
        assertEquals(original.recordingId, dao.source("webdav", "rollback-live-24")?.recordingId)
        assertEquals(10L, database.libraryDao().recordingFavorite(original.recordingId)?.createdAt)
        val queue = database.playbackPersistenceDao().loadQueueIdentities().single()
        assertEquals(original.recordingId, queue.recordingId)
        assertEquals(movedSourceId, queue.preferredSourceId)
        assertEquals(beforeJobs, dao.readyJobs(Long.MAX_VALUE, 100).map { it.jobId }.toSet())
        assertTrue(dao.identityOperations(original.recordingId, 10).isEmpty())
    }

    @Test
    fun artistMergeMovesCreditsAndMappingSplitCreatesIndependentArtist() {
        val firstRecording = recordings.ensureCanonicalForTrack(track(30L, "First", "Artist A"))
        val secondRecording = recordings.ensureCanonicalForTrack(track(31L, "Second", "Artist B"))
        val firstArtist = artists.creditsForRecording(firstRecording.recordingId).single()
        val secondArtist = artists.creditsForRecording(secondRecording.recordingId).single()

        val merged = artists.mergeArtists(firstArtist.artistKey, secondArtist.artistKey)

        assertNull(artists.artistByKey(firstArtist.artistKey))
        assertEquals(
            merged.artistKey,
            artists.creditsForRecording(firstRecording.recordingId).single().artistKey
        )
        database.musicIdentityDao().upsert(
            ArtistSourceMappingEntity(
                mappingId = null,
                artistId = merged.artistKey,
                provider = "netease",
                providerArtistId = "artist-100",
                displayName = "Artist Alias",
                status = "CONFIRMED",
                confidence = 1.0,
                lastVerifiedAt = 100L
            )
        )
        val mappingId = checkNotNull(
            database.musicIdentityDao().artistMappings(merged.artistKey).single().mappingId
        )

        val split = artists.splitArtistMapping(mappingId)

        assertNotEquals(merged.artistKey, split.artistKey)
        assertNotEquals(merged.artistId, split.artistId)
        assertEquals(split.artistId, UUID.fromString(split.artistId).toString())
        assertEquals(
            split.artistKey,
            database.musicIdentityDao().artistMapping(mappingId)?.artistId
        )
    }

    @Test
    fun candidateConfirmationCreatesRecognizedButNotYetPlayableSource() {
        val recording = recordings.ensureCanonicalForTrack(track(40L, "Candidate", "Artist"))
        val candidate = candidate(
            targetType = IdentityTargetType.RECORDING,
            targetId = recording.recordingId,
            provider = "netease",
            providerItemId = "song-40"
        ).copy(isrc = "US-AAA-12-34567")
        candidates.saveCandidate(candidate)

        val confirmed = candidates.confirmCandidate(candidate.candidateId)

        assertEquals(IdentityCandidateStatus.USER_CONFIRMED, confirmed.status)
        val source = checkNotNull(database.musicIdentityDao().source("netease", "song-40"))
        assertEquals("CONFIRMED", source.matchStatus)
        assertTrue(!source.playable)
        assertEquals(0L, source.lastVerifiedAt)
        assertTrue(recordings.confirmedSources(recording.recordingId).none { it.sourceId == source.sourceId })
        assertEquals("USAAA1234567", recordings.canonicalForRecording(recording.recordingId)?.isrc)
    }

    @Test(timeout = 10_000L)
    fun candidateConfirmationReusesItsTransactionWhenProviderSourceHasAnotherOwner() {
        val owner = recordings.ensureCanonicalForTrack(track(41L, "Candidate", "Artist"))
        val candidateTarget = recordings.ensureCanonicalForTrack(track(42L, "Candidate version", "Artist"))
        val dao = database.musicIdentityDao()
        dao.upsert(
            TrackSourceMappingEntity(
                sourceId = null,
                recordingId = owner.recordingId,
                provider = "netease",
                providerTrackId = "owned-song-42",
                localTrackId = null,
                dataPath = "",
                title = "Candidate",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
                quality = "",
                qualityScore = 0,
                playable = false,
                matchStatus = "CONFIRMED",
                confidence = 1.0,
                lastSuccessfulAt = 0L,
                lastVerifiedAt = 0L,
                legacyLocalKey = ""
            )
        )
        val candidate = candidate(
            targetType = IdentityTargetType.RECORDING,
            targetId = candidateTarget.recordingId,
            provider = "netease",
            providerItemId = "owned-song-42"
        )
        candidates.saveCandidate(candidate)
        val mergedRecordingId = minOf(owner.recordingId, candidateTarget.recordingId)
        val removedRecordingId = maxOf(owner.recordingId, candidateTarget.recordingId)

        val confirmed = candidates.confirmCandidate(candidate.candidateId)

        assertEquals(IdentityCandidateStatus.USER_CONFIRMED, confirmed.status)
        assertEquals(mergedRecordingId, confirmed.targetId)
        assertNull(recordings.canonicalForRecording(removedRecordingId))
        assertEquals(
            mergedRecordingId,
            dao.source("netease", "owned-song-42")?.recordingId
        )
        assertTrue(
            candidates.pendingCandidates(IdentityTargetType.RECORDING, mergedRecordingId)
                .none { it.provider == "netease" && it.providerItemId == "owned-song-42" }
        )
        assertTrue(!database.inTransaction())
    }

    @Test
    fun jobClaimAndRetryAreAtomicAndPersistent() {
        recordings.ensureCanonicalForTrack(track(50L, "Job", "Artist"))
        val pending = jobs.readyJobs(Long.MAX_VALUE, 10).first()

        val claimed = jobs.claim(pending.jobId, Long.MAX_VALUE)

        assertEquals(IdentityJobStatus.RUNNING, claimed?.status)
        assertNull(jobs.claim(pending.jobId, Long.MAX_VALUE))
        jobs.markRetry(pending.jobId, Long.MAX_VALUE, "temporary", Long.MAX_VALUE - 1L)
        val retried = jobs.readyJobs(Long.MAX_VALUE, 10).first { it.jobId == pending.jobId }
        assertEquals(IdentityJobStatus.RETRY, retried.status)
        assertEquals(1, retried.attemptCount)
        assertEquals("temporary", retried.lastError)
    }

    @Test
    fun newerJobSupersedesSameTargetTerminalStatusWithoutUniqueConstraintFailure() {
        val recording = recordings.ensureCanonicalForTrack(track(51L, "Repeated job", "Artist"))
        val pending = jobs.readyJobs(Long.MAX_VALUE, 10).first {
            it.targetType == IdentityTargetType.RECORDING && it.targetId == recording.recordingId
        }
        val now = 1_700_000_000_000L
        database.musicIdentityDao().upsert(
            IdentityResolutionJobEntity(
                jobId = "older-succeeded-job",
                targetType = IdentityTargetType.RECORDING.name,
                targetId = recording.recordingId,
                priority = 0,
                reason = "OLDER_RUN",
                attemptCount = 0,
                nextAttemptAt = 0L,
                lastError = "",
                status = IdentityJobStatus.SUCCEEDED.name,
                createdAt = now - 1L,
                updatedAt = now - 1L
            )
        )
        assertEquals(IdentityJobStatus.RUNNING, jobs.claim(pending.jobId, Long.MAX_VALUE)?.status)

        jobs.markSucceeded(pending.jobId, now)

        assertNull(database.musicIdentityDao().job("older-succeeded-job"))
        assertEquals(
            IdentityJobStatus.SUCCEEDED.name,
            database.musicIdentityDao().job(pending.jobId)?.status
        )
    }

    @Test
    fun staleRunningJobIsRecoveredAsRetryAfterLeaseExpires() {
        recordings.ensureCanonicalForTrack(track(52L, "Stale job", "Artist"))
        val pending = jobs.readyJobs(1_000L, 10).first()
        assertEquals(IdentityJobStatus.RUNNING, jobs.claim(pending.jobId, 1_000L)?.status)

        val recovered = jobs.readyJobs(
            1_000L + RoomIdentityJobRepository.STALE_RUNNING_TIMEOUT_MS + 1L,
            10
        ).first { it.jobId == pending.jobId }

        assertEquals(IdentityJobStatus.RETRY, recovered.status)
        assertEquals(1, recovered.attemptCount)
        assertEquals("STALE_RUNNING_RECOVERED", recovered.lastError)
    }

    @Test
    fun embeddedTagsPersistWithoutReplacingCanonicalUuidAndConfirmArtistMapping() {
        val original = recordings.ensureCanonicalForTrack(track(60L, "Tagged", "Tagged Artist"))
        val tags = TrackIdentityTags(
            "123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174001",
            "JP-ABC-12-34567",
            "123e4567-e89b-12d3-a456-426614174002",
            listOf("123e4567-e89b-12d3-a456-426614174003")
        )

        LibraryRepository(database).upsertTracks(
            listOf(track(60L, "Tagged updated", "Tagged Artist", tags))
        )

        val updated = checkNotNull(recordings.canonicalForLocalTrack(60L))
        assertEquals(original.recordingId, updated.recordingId)
        assertEquals(original.canonicalId, updated.canonicalId)
        assertEquals(tags.recordingMusicBrainzId, updated.musicBrainzRecordingId)
        assertEquals(tags.workMusicBrainzId, updated.musicBrainzWorkId)
        assertEquals(tags.isrc, updated.isrc)
        assertEquals(tags.acoustId, updated.acoustId)
        assertEquals(4, database.musicIdentityDao().identifiers(updated.recordingId).size)
        val credit = artists.creditsForRecording(updated.recordingId).single()
        val artist = checkNotNull(artists.artistByKey(credit.artistKey))
        assertEquals(tags.artistMusicBrainzIds.single(), artist.musicBrainzArtistId)
        assertEquals(
            artist.artistKey,
            database.musicIdentityDao()
                .artistForProvider("musicbrainz", tags.artistMusicBrainzIds.single())
                ?.id
        )
    }

    @Test
    fun equalStrongTagMergesSameArtistButIsrcArtistConflictIsRejected() {
        val sameMbid = "223e4567-e89b-12d3-a456-426614174000"
        val first = track(
            70L,
            "Duplicate",
            "Same Artist",
            TrackIdentityTags(sameMbid, "", "", "", emptyList())
        )
        val second = track(
            71L,
            "Duplicate",
            "Same Artist",
            TrackIdentityTags(sameMbid, "", "", "", emptyList())
        )
        LibraryRepository(database).upsertTracks(listOf(first, second))

        val firstRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(70L))
        val secondRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(71L))
        assertEquals(firstRecording, secondRecording)
        assertEquals(2, database.musicIdentityDao().sourceCount(firstRecording))

        val conflictingIsrc = "USAAA1234567"
        val artistA = track(
            72L,
            "Conflict",
            "Artist A",
            TrackIdentityTags("", "", conflictingIsrc, "", emptyList())
        )
        val artistB = track(
            73L,
            "Conflict",
            "Artist B",
            TrackIdentityTags("", "", conflictingIsrc, "", emptyList())
        )
        LibraryRepository(database).upsertTracks(listOf(artistA, artistB))

        val artistARecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(72L))
        val artistBRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(73L))
        assertNotEquals(artistARecording, artistBRecording)
        assertTrue(
            database.musicIdentityDao().candidates("RECORDING", artistBRecording)
                .any { it.provider == "embedded_tag" && it.status == "REJECTED" }
        )
    }

    @Test
    fun equalStrongTagDoesNotMergeOriginalWithLiveVersion() {
        val sharedMbid = "323e4567-e89b-12d3-a456-426614174000"
        val original = track(
            74L,
            "Same Song",
            "Same Artist",
            TrackIdentityTags(sharedMbid, "", "", "", emptyList())
        )
        val live = track(
            75L,
            "Same Song (Live)",
            "Same Artist",
            TrackIdentityTags(sharedMbid, "", "", "", emptyList())
        )

        LibraryRepository(database).upsertTracks(listOf(original, live))

        val originalRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(original.id))
        val liveRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(live.id))
        assertNotEquals(originalRecording, liveRecording)
        assertTrue(
            database.musicIdentityDao().candidates("RECORDING", liveRecording)
                .any { it.provider == "embedded_tag" && it.status == "REJECTED" }
        )
    }

    private fun candidate(
        targetType: IdentityTargetType,
        targetId: Long,
        provider: String,
        providerItemId: String
    ) = IdentityCandidate(
        candidateId = UUID.randomUUID().toString(),
        targetType = targetType,
        targetId = targetId,
        provider = provider,
        providerItemId = providerItemId,
        title = "Candidate",
        artist = "Artist",
        album = "Album",
        durationMs = 120_000L,
        score = 0.95,
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun track(
        id: Long,
        title: String,
        artist: String,
        identityTags: TrackIdentityTags = TrackIdentityTags.EMPTY,
        dataPath: String = "/music/$id.flac"
    ) = Track(
        id,
        title,
        artist,
        "Album",
        120_000L,
        Uri.parse("content://media/$id"),
        dataPath,
        id,
        null,
        "",
        0,
        0,
        0,
        0,
        0.0f,
        0.0f,
        identityTags
    )
}
