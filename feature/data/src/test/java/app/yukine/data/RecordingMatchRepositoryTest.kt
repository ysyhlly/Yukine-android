package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.PlaybackQueueIdentityEntity
import app.yukine.data.room.CanonicalWorkEntity
import app.yukine.data.room.PlaylistEntity
import app.yukine.data.room.PlaylistRecordingItemEntity
import app.yukine.data.room.RecordingFavoriteEntity
import app.yukine.data.room.RecordingPlayEventEntity
import app.yukine.data.room.RecordingPlayHistoryEntity
import app.yukine.data.room.RecordingVariantEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.RecordingIdentifier
import app.yukine.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordingMatchRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "recording-match-repository-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var recordings: RoomRecordingIdentityRepository
    private lateinit var candidates: RoomIdentityCandidateRepository
    private lateinit var repository: RecordingMatchRepository

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = YukineDatabase.openForTest(context, databaseName)
        recordings = RoomRecordingIdentityRepository(database)
        candidates = RoomIdentityCandidateRepository(database)
        repository = RecordingMatchRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun snapshotAndCandidateActionsRemainOfflineAndDoNotPromoteUnverifiedSource() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(
                71L,
                "Managed song",
                "Managed artist",
                "Album",
                180_000L,
                Uri.parse("file:///music/managed.flac"),
                "/music/managed.flac"
            )
        )
        val lower = candidate(recording.recordingId, "qqmusic", "qq-71", 0.61)
        val higher = candidate(recording.recordingId, "netease", "wy-71", 0.88)
        candidates.saveCandidate(lower)
        candidates.saveCandidate(higher)

        val before = requireNotNull(repository.snapshotForLocalTrack(71L))

        assertEquals(recording.recordingId, before.recording.recordingId)
        assertEquals(recording.canonicalId, before.recording.canonicalId)
        assertEquals("Managed song", before.track.title)
        assertEquals("local", before.activeSource?.provider)
        assertEquals(listOf("wy-71", "qq-71"), before.pendingCandidates.map { it.providerItemId })
        assertEquals(1, before.sourceTotal)
        assertEquals(2, before.candidateTotal)

        repository.confirmCandidate(higher.candidateId)
        val afterConfirm = requireNotNull(repository.snapshotForLocalTrack(71L))
        val confirmedSource = requireNotNull(database.musicIdentityDao().source("netease", "wy-71"))

        assertFalse(confirmedSource.playable)
        assertEquals("local", afterConfirm.activeSource?.provider)
        assertEquals(listOf("qq-71"), afterConfirm.pendingCandidates.map { it.providerItemId })
        assertTrue(afterConfirm.sources.any { it.provider == "netease" && !it.playable })

        repository.rejectCandidate(lower.candidateId)

        val completed = requireNotNull(repository.snapshotForLocalTrack(71L))
        assertTrue(completed.pendingCandidates.isEmpty())
        assertEquals(
            listOf("REJECT_CANDIDATE", "CONFIRM_CANDIDATE"),
            completed.recentOperations.map { it.operationType }
        )
        database.musicIdentityDao().identityOperations(recording.recordingId, 10).forEach { operation ->
            assertFalse(operation.beforePayload.contains("data_path", ignoreCase = true))
            assertFalse(operation.beforePayload.contains("cookie", ignoreCase = true))
            assertFalse(operation.afterPayload.contains("authorization", ignoreCase = true))
        }
    }

    @Test
    fun liveRemixAndCoverHardConflictsCannotBeConfirmedEvenByTheManualUiPath() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(72L, "Original", "Artist", "Album", 180_000L, Uri.EMPTY, "file:72")
        )
        listOf("LIVE", "REMIX", "COVER").forEach { variantType ->
            val providerItemId = "${variantType.lowercase()}-72"
            val conflict = candidate(recording.recordingId, "netease", providerItemId, 0.99).copy(
                variantType = variantType,
                evidenceJson = """{"hardConflict":true,"reasons":["variant:conflict"]}"""
            )
            candidates.saveCandidate(conflict)

            assertThrows(IllegalArgumentException::class.java) {
                repository.confirmCandidate(conflict.candidateId)
            }

            assertNull(database.musicIdentityDao().source("netease", providerItemId))
            assertEquals(
                IdentityCandidateStatus.PENDING.name,
                database.musicIdentityDao().candidate(conflict.candidateId)?.status
            )
        }
        assertEquals(3, repository.snapshotForLocalTrack(72L)?.candidateTotal)
    }

    @Test
    fun batchRejectOnlyRejectsPendingCandidatesWithExplicitHardConflicts() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(721L, "Original", "Artist", "Album", 180_000L, Uri.EMPTY, "file:721")
        )
        val live = candidate(recording.recordingId, "netease", "live-721", 0.99).copy(
            evidenceJson = """{"hardConflict":true,"reasons":["variant:conflict"]}"""
        )
        val artistConflict = candidate(recording.recordingId, "qqmusic", "artist-721", 0.98).copy(
            evidenceJson = """{"hardConflict":true,"reasons":["artist:conflict"]}"""
        )
        val uncertain = candidate(recording.recordingId, "luoxue", "uncertain-721", 0.40)
        candidates.saveCandidate(live)
        candidates.saveCandidate(artistConflict)
        candidates.saveCandidate(uncertain)

        assertEquals(2, repository.rejectObviousMismatches(recording.recordingId))

        assertEquals(IdentityCandidateStatus.REJECTED.name, database.musicIdentityDao().candidate(live.candidateId)?.status)
        assertEquals(
            IdentityCandidateStatus.REJECTED.name,
            database.musicIdentityDao().candidate(artistConflict.candidateId)?.status
        )
        assertEquals(
            IdentityCandidateStatus.PENDING.name,
            database.musicIdentityDao().candidate(uncertain.candidateId)?.status
        )
        assertEquals(
            listOf("REJECT_CANDIDATE", "REJECT_CANDIDATE"),
            database.musicIdentityDao().identityOperations(recording.recordingId, 10).map { it.operationType }
        )
    }

    @Test
    fun alternateVersionLeavesSourceAndActiveSourceUntouchedAndQueuesExplicitRefresh() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(73L, "Original", "Artist", "Album", 180_000L, Uri.EMPTY, "file:73")
        )
        val alternate = candidate(recording.recordingId, "netease", "live-73", 0.96).copy(
            title = "Original (Live)",
            variantType = "LIVE"
        )
        candidates.saveCandidate(alternate)

        val marked = repository.markAsAlternateVersion(alternate.candidateId, "LIVE")
        val snapshot = requireNotNull(repository.snapshotForLocalTrack(73L))

        assertEquals(IdentityCandidateStatus.ALTERNATE_VERSION, marked.status)
        assertEquals("LIVE", marked.variantType)
        assertTrue(snapshot.pendingCandidates.isEmpty())
        assertEquals("live-73", snapshot.alternateVersions.single().providerItemId)
        assertEquals("local", snapshot.activeSource?.provider)
        assertTrue(database.musicIdentityDao().source("netease", "live-73") == null)

        repository.requestCandidateRefresh(recording.recordingId)

        val job = requireNotNull(database.musicIdentityDao().job("manual-recording-refresh-${recording.recordingId}"))
        assertEquals("MANUAL_CANDIDATE_REFRESH", job.reason)
        assertEquals("PENDING", job.status)
    }

    @Test
    fun snapshotPagesOneThousandCandidatesWithoutDuplicatesOrCrashes() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(74L, "Paged song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:74")
        )
        repeat(60) { index ->
            database.musicIdentityDao().upsert(
                TrackSourceMappingEntity(
                    sourceId = null,
                    recordingId = recording.recordingId,
                    provider = "netease",
                    providerTrackId = "page-source-$index",
                    localTrackId = null,
                    dataPath = "",
                    title = "Paged song",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 180_000L,
                    quality = "",
                    qualityScore = index,
                    playable = false,
                    matchStatus = "CANDIDATE",
                    confidence = 0.5,
                    lastSuccessfulAt = 0L,
                    lastVerifiedAt = 0L,
                    legacyLocalKey = ""
                )
            )
        }
        database.runInTransaction {
            repeat(1_000) { index ->
                candidates.saveCandidate(
                    candidate(
                        recording.recordingId,
                        "qqmusic",
                        "page-candidate-$index",
                        index / 1_000.0
                    )
                )
            }
        }

        val first = requireNotNull(repository.snapshotForLocalTrack(74L, 0, 0, 50))
        val second = requireNotNull(repository.snapshotForLocalTrack(74L, 50, 50, 50))
        val allCandidateIds = (0 until 1_000 step 100).flatMap { offset ->
            requireNotNull(repository.snapshotForLocalTrack(74L, 0, offset, 100))
                .pendingCandidates
                .map { it.candidateId }
        }

        assertEquals(61, first.sourceTotal)
        assertEquals(1_000, first.candidateTotal)
        assertEquals(50, first.sources.size)
        assertEquals(50, first.pendingCandidates.size)
        assertEquals(11, second.sources.size)
        assertEquals(50, second.pendingCandidates.size)
        assertTrue(first.sources.map { it.sourceId }.intersect(second.sources.map { it.sourceId }.toSet()).isEmpty())
        assertEquals(1_000, allCandidateIds.size)
        assertEquals(1_000, allCandidateIds.distinct().size)
    }

    @Test
    fun mergeFailureAtOperationLogRollsBackEveryCanonicalReference() {
        val source = recordings.ensureCanonicalForTrack(
            Track(75L, "Rollback song", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:75")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(76L, "Rollback song candidate", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:76")
        )
        val sourceId = requireNotNull(database.musicIdentityDao().sourceForLocalTrack(75L)?.sourceId)
        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(source.recordingId, 10L))
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(null, "Rollback", 1L, 1L))
        database.playlistDao().upsertPlaylistRecordingItem(
            PlaylistRecordingItemEntity(playlistId, source.recordingId, 75L, 1024L, 10L)
        )
        database.historyDao().upsertRecordingHistory(
            RecordingPlayHistoryEntity(source.recordingId, 75L, 100L, 3)
        )
        database.playbackPersistenceDao().insertQueueIdentities(
            listOf(PlaybackQueueIdentityEntity(0, source.recordingId, sourceId))
        )
        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER force_identity_operation_failure " +
                "BEFORE INSERT ON identity_operations BEGIN " +
                "SELECT RAISE(ABORT, 'forced operation log failure'); END"
        )

        assertThrows(RuntimeException::class.java) {
            repository.mergeRecordings(source.recordingId, target.recordingId)
        }

        assertEquals(source.canonicalId, recordings.canonicalForRecording(source.recordingId)?.canonicalId)
        assertEquals(target.canonicalId, recordings.canonicalForRecording(target.recordingId)?.canonicalId)
        assertEquals(source.recordingId, database.musicIdentityDao().sourceForLocalTrack(75L)?.recordingId)
        assertEquals(target.recordingId, database.musicIdentityDao().sourceForLocalTrack(76L)?.recordingId)
        assertEquals(10L, database.libraryDao().recordingFavorite(source.recordingId)?.createdAt)
        assertNull(database.libraryDao().recordingFavorite(target.recordingId))
        assertEquals(1, database.playlistDao().playlistRecordingReferences(source.recordingId).size)
        assertTrue(database.playlistDao().playlistRecordingReferences(target.recordingId).isEmpty())
        assertEquals(3, database.historyDao().recordingHistory(source.recordingId)?.playCount)
        assertNull(database.historyDao().recordingHistory(target.recordingId))
        assertEquals(1, database.playbackPersistenceDao().queueRecordingCount(source.recordingId))
        assertEquals(0, database.playbackPersistenceDao().queueRecordingCount(target.recordingId))
        assertTrue(database.musicIdentityDao().identityOperations(source.recordingId, 10).isEmpty())
        assertTrue(database.musicIdentityDao().identityOperations(target.recordingId, 10).isEmpty())
    }

    @Test
    fun manualMergeSearchPreviewAndCommitPreserveCanonicalBusinessReferences() {
        val source = recordings.ensureCanonicalForTrack(
            Track(80L, "Merge song", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:80")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(81L, "Merge song candidate", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:81")
        )
        val libraryDao = database.libraryDao()
        val playlistDao = database.playlistDao()
        val historyDao = database.historyDao()
        val queueDao = database.playbackPersistenceDao()
        libraryDao.putRecordingFavorite(RecordingFavoriteEntity(source.recordingId, 20L))
        libraryDao.putRecordingFavorite(RecordingFavoriteEntity(target.recordingId, 30L))
        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(null, "Merge", 1L, 1L))
        playlistDao.upsertPlaylistRecordingItem(
            PlaylistRecordingItemEntity(playlistId, source.recordingId, 80L, 2048L, 20L)
        )
        playlistDao.upsertPlaylistRecordingItem(
            PlaylistRecordingItemEntity(playlistId, target.recordingId, 81L, 1024L, 30L)
        )
        historyDao.upsertRecordingHistory(
            RecordingPlayHistoryEntity(source.recordingId, 80L, 200L, 3)
        )
        historyDao.upsertRecordingHistory(
            RecordingPlayHistoryEntity(target.recordingId, 81L, 300L, 4)
        )
        val sourceId = database.musicIdentityDao().sourceForLocalTrack(80L)?.sourceId
        val targetSourceId = database.musicIdentityDao().sourceForLocalTrack(81L)?.sourceId
        historyDao.insertRecordingEvent(
            RecordingPlayEventEntity(null, source.recordingId, sourceId, 80L, 200L)
        )
        historyDao.insertRecordingEvent(
            RecordingPlayEventEntity(null, target.recordingId, targetSourceId, 81L, 300L)
        )
        queueDao.insertQueueIdentities(
            listOf(
                PlaybackQueueIdentityEntity(0, source.recordingId, sourceId),
                PlaybackQueueIdentityEntity(1, target.recordingId, targetSourceId)
            )
        )

        val results = repository.searchMergeCandidates(target.recordingId, "Merge song")
        val preview = repository.previewMerge(source.recordingId, target.recordingId)

        assertEquals(listOf(source.recordingId), results.map { it.recordingId })
        assertFalse(preview.blocked)
        assertEquals(1, preview.impact.favoriteCount)
        assertEquals(1, preview.impact.playlistItemCount)
        assertEquals(3, preview.impact.playHistoryCount)
        assertEquals(1, preview.impact.playEventCount)
        assertEquals(1, preview.impact.queueItemCount)
        assertEquals(1, preview.impact.sourceCount)

        val merged = repository.mergeRecordings(source.recordingId, target.recordingId)

        assertEquals(target.canonicalId, merged.canonicalId)
        assertNull(recordings.canonicalForRecording(source.recordingId))
        assertEquals(2, database.musicIdentityDao().sourceCount(target.recordingId))
        val mergedSources = database.musicIdentityDao().sources(target.recordingId)
        assertEquals(
            mergedSources.size,
            mergedSources.distinctBy { it.provider to it.providerTrackId }.size
        )
        assertEquals(target.recordingId, database.musicIdentityDao().activeSource(target.recordingId)?.recordingId)
        assertNull(libraryDao.recordingFavorite(source.recordingId))
        assertEquals(20L, libraryDao.recordingFavorite(target.recordingId)?.createdAt)
        assertEquals(7, historyDao.recordingHistory(target.recordingId)?.playCount)
        assertEquals(2, historyDao.recordingEventCount(target.recordingId))
        assertEquals(2, queueDao.queueRecordingCount(target.recordingId))
        assertEquals(1, playlistDao.playlistRecordingReferences(target.recordingId).size)
        assertEquals(1024L, playlistDao.playlistRecordingReferences(target.recordingId).single().sortKey)

        val operation = requireNotNull(repository.snapshotForLocalTrack(81L))
            .recentOperations.single { it.operationType == "MERGE_RECORDINGS" }
        assertTrue(operation.undoable)
        val operationRow = requireNotNull(database.musicIdentityDao().identityOperation(operation.id))
        assertFalse(operationRow.beforePayload.contains("data_path", ignoreCase = true))
        assertFalse(operationRow.beforePayload.contains("cookie", ignoreCase = true))
        assertFalse(operationRow.beforePayload.contains("authorization", ignoreCase = true))
        repository.undoIdentityOperation(operation.id)

        assertEquals(source.canonicalId, recordings.canonicalForRecording(source.recordingId)?.canonicalId)
        assertEquals(target.canonicalId, recordings.canonicalForRecording(target.recordingId)?.canonicalId)
        assertEquals(source.recordingId, database.musicIdentityDao().sourceForLocalTrack(80L)?.recordingId)
        assertEquals(target.recordingId, database.musicIdentityDao().sourceForLocalTrack(81L)?.recordingId)
        assertEquals(20L, libraryDao.recordingFavorite(source.recordingId)?.createdAt)
        assertEquals(30L, libraryDao.recordingFavorite(target.recordingId)?.createdAt)
        assertEquals(3, historyDao.recordingHistory(source.recordingId)?.playCount)
        assertEquals(4, historyDao.recordingHistory(target.recordingId)?.playCount)
        assertEquals(1, historyDao.recordingEventCount(source.recordingId))
        assertEquals(1, historyDao.recordingEventCount(target.recordingId))
        assertEquals(1, queueDao.queueRecordingCount(source.recordingId))
        assertEquals(1, queueDao.queueRecordingCount(target.recordingId))
        assertEquals(2048L, playlistDao.playlistRecordingReferences(source.recordingId).single().sortKey)
        assertEquals(1024L, playlistDao.playlistRecordingReferences(target.recordingId).single().sortKey)
        assertThrows(IllegalArgumentException::class.java) {
            repository.undoIdentityOperation(operation.id)
        }
    }

    @Test
    fun mergePreservesStrongWorkAndUndoRestoresItsSnapshot() {
        val source = recordings.ensureCanonicalForTrack(
            Track(801L, "Work song source", "Artist", "Album", 180_000L, Uri.EMPTY, "file:801")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(802L, "Work song target", "Artist", "Album", 180_000L, Uri.EMPTY, "file:802")
        )
        val dao = database.musicIdentityDao()
        val workId = dao.insertWork(
            CanonicalWorkEntity(null, "work-uuid-801", "work song", null, 1L, 1L)
        )
        dao.update(
            requireNotNull(dao.recording(source.recordingId)).copy(
                workId = workId,
                musicBrainzWorkId = "mb-work-801"
            )
        )

        repository.mergeRecordings(source.recordingId, target.recordingId)

        assertEquals(workId, dao.recording(target.recordingId)?.workId)
        assertEquals("work-uuid-801", dao.work(workId)?.canonicalUuid)
        val operation = dao.identityOperations(target.recordingId, 10)
            .single { it.operationType == "MERGE_RECORDINGS" }
        repository.undoIdentityOperation(requireNotNull(operation.id))
        assertEquals(workId, dao.recording(source.recordingId)?.workId)
        assertEquals("work-uuid-801", dao.work(workId)?.canonicalUuid)
    }

    @Test
    fun conflictingStrongWorksBlockMergeAndPersistCannotLink() {
        val source = recordings.ensureCanonicalForTrack(
            Track(803L, "Conflict work source", "Artist", "Album", 180_000L, Uri.EMPTY, "file:803")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(804L, "Conflict work target", "Artist", "Album", 180_000L, Uri.EMPTY, "file:804")
        )
        val dao = database.musicIdentityDao()
        dao.update(requireNotNull(dao.recording(source.recordingId)).copy(musicBrainzWorkId = "work-a"))
        dao.update(requireNotNull(dao.recording(target.recordingId)).copy(musicBrainzWorkId = "work-b"))

        assertThrows(IllegalArgumentException::class.java) {
            repository.mergeRecordings(source.recordingId, target.recordingId)
        }

        assertEquals(
            "CANNOT_LINK",
            dao.recordingRelation(
                minOf(source.recordingId, target.recordingId),
                maxOf(source.recordingId, target.recordingId)
            )?.relationType
        )
        assertTrue(dao.recording(source.recordingId) != null)
        assertTrue(dao.recording(target.recordingId) != null)
    }

    @Test
    fun previewBlocksOriginalLiveConflictBeforeAnyMergeWrite() {
        val source = recordings.ensureCanonicalForTrack(
            Track(82L, "Versioned song", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:82")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(83L, "Versioned song candidate", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:83")
        )
        database.musicIdentityDao().upsert(
            RecordingVariantEntity("version-live", source.recordingId, "LIVE", "Live", 1.0)
        )
        database.musicIdentityDao().upsert(
            RecordingVariantEntity("version-original", target.recordingId, "ORIGINAL", "Original", 1.0)
        )

        val preview = repository.previewMerge(source.recordingId, target.recordingId)

        assertTrue(preview.blocked)
        assertTrue(
            preview.warnings.any {
                it.code == RecordingMergeWarningCode.VARIANT_CONFLICT && it.blocking
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            repository.mergeRecordings(source.recordingId, target.recordingId)
        }
        assertEquals(1, database.musicIdentityDao().sourceCount(source.recordingId))
        assertEquals(1, database.musicIdentityDao().sourceCount(target.recordingId))
    }

    @Test
    fun multiSourceSplitCreatesIndependentVersionAndAppliesReferenceDestinations() {
        val original = recordings.ensureCanonicalForTrack(
            Track(84L, "Versioned song", "Same artist", "Album", 180_000L, Uri.EMPTY, "file:84")
        )
        recordings.attachIdentifier(
            original.recordingId,
            RecordingIdentifier(
                recordingId = original.recordingId,
                canonicalId = original.canonicalId,
                identifierType = "ISRC",
                identifierValue = "JP-ABC-12-34567",
                source = "TAG",
                confidence = 1.0,
                verifiedAt = 1L
            )
        )
        val dao = database.musicIdentityDao()
        dao.upsert(
            splitSource(original.recordingId, "webdav", "live-84", "Versioned song (Live)", true, 900)
        )
        dao.upsert(
            splitSource(original.recordingId, "luoxue", "remix-84", "Versioned song (Remix)", false, 500)
        )
        val liveId = requireNotNull(dao.source("webdav", "live-84")?.sourceId)
        val remixId = requireNotNull(dao.source("luoxue", "remix-84")?.sourceId)
        val localId = requireNotNull(dao.sourceForLocalTrack(84L)?.sourceId)
        val candidate = candidate(original.recordingId, "luoxue", "remix-84", 0.95)
        candidates.saveCandidate(candidate)
        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(original.recordingId, 10L))
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(null, "Split", 1L, 1L))
        database.playlistDao().upsertPlaylistRecordingItem(
            PlaylistRecordingItemEntity(playlistId, original.recordingId, 84L, 1024L, 10L)
        )
        database.playbackPersistenceDao().insertQueueIdentities(
            listOf(PlaybackQueueIdentityEntity(0, original.recordingId, liveId))
        )
        database.historyDao().upsertRecordingHistory(
            RecordingPlayHistoryEntity(original.recordingId, 84L, 100L, 2)
        )
        database.historyDao().insertRecordingEvent(
            RecordingPlayEventEntity(null, original.recordingId, liveId, 84L, 100L)
        )

        val preview = repository.previewSplit(original.recordingId, setOf(liveId, remixId))

        assertEquals(2, preview.selectedSources.size)
        assertEquals(1, preview.remainingSourceCount)
        assertEquals(1, preview.impact.favoriteCount)
        assertEquals(1, preview.impact.playlistItemCount)
        assertEquals(1, preview.impact.queueItemCount)

        val split = repository.splitSources(
            setOf(liveId, remixId),
            RecordingSplitOptions(
                favoriteDestination = RecordingSplitDestination.NEW_RECORDING,
                playlistDestination = RecordingSplitDestination.NEW_RECORDING,
                queueDestination = RecordingSplitDestination.ORIGINAL
            )
        )

        assertNotEquals(original.recordingId, split.recordingId)
        assertNotEquals(original.canonicalId, split.canonicalId)
        assertEquals(split.canonicalId, java.util.UUID.fromString(split.canonicalId).toString())
        assertEquals("", split.isrc)
        assertEquals(setOf(liveId, remixId), dao.sources(split.recordingId).mapNotNull { it.sourceId }.toSet())
        assertEquals(listOf(localId), dao.sources(original.recordingId).mapNotNull { it.sourceId })
        assertEquals(liveId, dao.activeSource(split.recordingId)?.sourceId)
        assertEquals(localId, dao.activeSource(original.recordingId)?.sourceId)
        assertTrue(requireNotNull(dao.activeSource(split.recordingId)).playable)
        assertTrue(requireNotNull(dao.activeSource(original.recordingId)).playable)
        assertEquals("LIVE", dao.variants(split.recordingId).single().variantType)
        assertEquals(split.recordingId, dao.candidate(candidate.candidateId)?.targetId)
        assertNull(database.libraryDao().recordingFavorite(original.recordingId))
        assertEquals(10L, database.libraryDao().recordingFavorite(split.recordingId)?.createdAt)
        assertTrue(database.playlistDao().playlistRecordingReferences(original.recordingId).isEmpty())
        assertEquals(1, database.playlistDao().playlistRecordingReferences(split.recordingId).size)
        val originalQueue = database.playbackPersistenceDao().loadQueueIdentities().single()
        assertEquals(original.recordingId, originalQueue.recordingId)
        assertNull(originalQueue.preferredSourceId)
        assertEquals(2, database.historyDao().recordingHistory(original.recordingId)?.playCount)
        val eventSource = database.openHelper.readableDatabase.query(
            "SELECT source_id FROM recording_play_events WHERE recording_id = ?",
            arrayOf(original.recordingId.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }
        assertNull(eventSource)

        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(original.recordingId, 20L))
        assertTrue(database.libraryDao().recordingFavorite(original.recordingId) != null)
        assertTrue(database.libraryDao().recordingFavorite(split.recordingId) != null)

        database.playbackPersistenceDao().insertQueueIdentities(
            listOf(PlaybackQueueIdentityEntity(1, split.recordingId, remixId))
        )
        val secondSplit = repository.splitSources(
            setOf(remixId),
            RecordingSplitOptions(queueDestination = RecordingSplitDestination.NEW_RECORDING)
        )
        assertNotEquals(original.canonicalId, secondSplit.canonicalId)
        assertNotEquals(split.canonicalId, secondSplit.canonicalId)
        val movedQueue = database.playbackPersistenceDao().loadQueueIdentities()
            .single { it.position == 1 }
        assertEquals(secondSplit.recordingId, movedQueue.recordingId)
        assertEquals(remixId, movedQueue.preferredSourceId)
    }

    @Test
    fun splitUndoRestoresSourceAndEverySelectedReference() {
        val original = recordings.ensureCanonicalForTrack(
            Track(85L, "Undo split", "Artist", "Album", 180_000L, Uri.EMPTY, "file:85")
        )
        val dao = database.musicIdentityDao()
        dao.upsert(splitSource(original.recordingId, "webdav", "undo-live-85", "Undo split (Live)", true, 900))
        val movedSourceId = requireNotNull(dao.source("webdav", "undo-live-85")?.sourceId)
        database.libraryDao().putRecordingFavorite(RecordingFavoriteEntity(original.recordingId, 11L))
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(null, "Undo", 1L, 1L))
        database.playlistDao().upsertPlaylistRecordingItem(
            PlaylistRecordingItemEntity(playlistId, original.recordingId, 85L, 1024L, 11L)
        )
        database.playbackPersistenceDao().insertQueueIdentities(
            listOf(PlaybackQueueIdentityEntity(0, original.recordingId, movedSourceId))
        )
        database.historyDao().insertRecordingEvent(
            RecordingPlayEventEntity(null, original.recordingId, movedSourceId, 85L, 12L)
        )

        val split = repository.splitSources(
            setOf(movedSourceId),
            RecordingSplitOptions(
                favoriteDestination = RecordingSplitDestination.NEW_RECORDING,
                playlistDestination = RecordingSplitDestination.NEW_RECORDING,
                queueDestination = RecordingSplitDestination.NEW_RECORDING
            )
        )
        val operation = requireNotNull(repository.snapshotForLocalTrack(85L))
            .recentOperations.single { it.operationType == "SPLIT_RECORDING" }
        val splitRelation = requireNotNull(
            RecordingRelationStore(database).relation(original.recordingId, split.recordingId)
        )
        assertEquals("CANNOT_LINK", splitRelation.relationType)
        assertTrue(splitRelation.locked)

        repository.undoIdentityOperation(operation.id)

        assertNull(recordings.canonicalForRecording(split.recordingId))
        assertNull(RecordingRelationStore(database).relation(original.recordingId, split.recordingId))
        assertEquals(original.canonicalId, recordings.canonicalForRecording(original.recordingId)?.canonicalId)
        assertEquals(original.recordingId, dao.source(movedSourceId)?.recordingId)
        assertEquals(11L, database.libraryDao().recordingFavorite(original.recordingId)?.createdAt)
        assertEquals(1, database.playlistDao().playlistRecordingReferences(original.recordingId).size)
        val queue = database.playbackPersistenceDao().loadQueueIdentities().single()
        assertEquals(original.recordingId, queue.recordingId)
        assertEquals(movedSourceId, queue.preferredSourceId)
        val restoredEvent = database.historyDao().recordingEvents(original.recordingId).single()
        assertEquals(movedSourceId, restoredEvent.sourceId)
    }

    @Test
    fun newerCandidateDecisionPreventsUndoFromOverwritingLaterIdentityState() {
        val source = recordings.ensureCanonicalForTrack(
            Track(86L, "Undo ordering", "Artist", "Album", 180_000L, Uri.EMPTY, "file:86")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(87L, "Undo ordering candidate", "Artist", "Album", 180_000L, Uri.EMPTY, "file:87")
        )
        repository.mergeRecordings(source.recordingId, target.recordingId)
        val mergeOperation = database.musicIdentityDao().identityOperations(target.recordingId, 10)
            .single { it.operationType == "MERGE_RECORDINGS" }
        val later = candidate(target.recordingId, "qqmusic", "later-87", 0.7)
        candidates.saveCandidate(later)
        repository.rejectCandidate(later.candidateId)

        assertFalse(
            requireNotNull(repository.snapshotForLocalTrack(87L)).recentOperations
                .single { it.id == mergeOperation.id }
                .undoable
        )
        assertThrows(IllegalArgumentException::class.java) {
            repository.undoIdentityOperation(requireNotNull(mergeOperation.id))
        }
        assertNull(recordings.canonicalForRecording(source.recordingId))
        assertEquals(target.recordingId, database.musicIdentityDao().sourceForLocalTrack(86L)?.recordingId)
    }

    @Test
    fun reversibleSnapshotRedactsSensitiveCandidateEvidenceAndStillUndoes() {
        val source = recordings.ensureCanonicalForTrack(
            Track(91L, "Snapshot safety", "Artist", "Album", 180_000L, Uri.EMPTY, "file:91")
        )
        val target = recordings.ensureCanonicalForTrack(
            Track(92L, "Snapshot safety candidate", "Artist", "Album", 180_000L, Uri.EMPTY, "file:92")
        )
        val sensitiveCandidate = candidate(source.recordingId, "qqmusic", "safe-91", 0.7).copy(
            evidenceJson = """{"reasons":["title:exact"],"apiUrl":"https://secret.example/audio","token":"abc"}"""
        )
        candidates.saveCandidate(sensitiveCandidate)

        repository.mergeRecordings(source.recordingId, target.recordingId)

        val operation = database.musicIdentityDao().identityOperations(target.recordingId, 10)
            .single { it.operationType == "MERGE_RECORDINGS" }
        assertFalse(operation.beforePayload.contains("secret.example"))
        assertFalse(operation.beforePayload.contains("\"abc\""))
        assertFalse(operation.afterPayload.contains("secret.example"))

        repository.undoIdentityOperation(requireNotNull(operation.id))

        val restored = requireNotNull(database.musicIdentityDao().candidate(sensitiveCandidate.candidateId))
        assertFalse(restored.evidenceJson.contains("secret.example"))
        assertFalse(restored.evidenceJson.contains("\"abc\""))
        assertTrue(restored.evidenceJson.contains("title:exact"))
    }

    @Test
    fun verifiedConfirmedSourceCanBecomePreferredAndRecordsHealth() = runBlocking {
        val recording = recordings.ensureCanonicalForTrack(
            Track(88L, "Verified source", "Artist", "Album", 180_000L, Uri.EMPTY, "file:88")
        )
        val dao = database.musicIdentityDao()
        dao.upsert(splitSource(recording.recordingId, "netease", "verified-88", "Verified source", false, 700))
        val sourceId = requireNotNull(dao.source("netease", "verified-88")?.sourceId)
        var verifiedProvider = ""
        val verifyingRepository = RecordingMatchRepository(
            database,
            RecordingSourceVerificationGateway { source ->
                verifiedProvider = source.provider
                RecordingSourceVerification(success = true, codec = "flac", bitrateKbps = 3200)
            }
        )

        val result = verifyingRepository.verifySource(sourceId)
        val verified = requireNotNull(dao.source(sourceId))

        assertTrue(result.success)
        assertEquals("netease", verifiedProvider)
        assertTrue(verified.playable)
        assertEquals("flac", verified.codec)
        assertEquals(3200, verified.bitrateKbps)
        assertTrue(verified.lastSuccessfulAt > 0L)
        assertEquals(0, verified.failureCount)

        assertThrows(IllegalArgumentException::class.java) {
            verifyingRepository.setPreferredSource(sourceId)
        }
        assertEquals("local", dao.activeSource(recording.recordingId)?.provider)
        assertTrue(dao.identityOperations(recording.recordingId, 10).isEmpty())
    }

    @Test
    fun verificationFailureRedactsSecretsAndFallsBackFromPreferredSource() = runBlocking {
        val recording = recordings.ensureCanonicalForTrack(
            Track(89L, "Fallback source", "Artist", "Album", 180_000L, Uri.EMPTY, "file:89")
        )
        val dao = database.musicIdentityDao()
        dao.upsert(splitSource(recording.recordingId, "qqmusic", "failed-89", "Fallback source", true, 900))
        val failedSourceId = requireNotNull(dao.source("qqmusic", "failed-89")?.sourceId)
        val localSourceId = requireNotNull(dao.sourceForLocalTrack(89L)?.sourceId)
        val verifyingRepository = RecordingMatchRepository(
            database,
            RecordingSourceVerificationGateway {
                RecordingSourceVerification(
                    success = false,
                    failureReason = "https://secret.example/audio?token=abc password=hidden"
                )
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            verifyingRepository.setPreferredSource(failedSourceId)
        }

        val result = verifyingRepository.verifySource(failedSourceId)
        val failed = requireNotNull(dao.source(failedSourceId))

        assertFalse(result.success)
        assertFalse(failed.playable)
        assertEquals(1, failed.failureCount)
        assertTrue(failed.lastFailureAt > 0L)
        assertFalse(failed.failureReason.contains("secret.example"))
        assertFalse(failed.failureReason.contains("abc"))
        assertFalse(failed.failureReason.contains("hidden"))
        assertEquals(localSourceId, dao.activeSource(recording.recordingId)?.sourceId)
    }

    @Test
    fun sourceManagementRejectsUnverifiedPreferredAndProtectsLastSource() {
        val recording = recordings.ensureCanonicalForTrack(
            Track(90L, "Protected source", "Artist", "Album", 180_000L, Uri.EMPTY, "file:90")
        )
        val dao = database.musicIdentityDao()
        val localSourceId = requireNotNull(dao.sourceForLocalTrack(90L)?.sourceId)
        dao.markSourceUnavailable(localSourceId, 1L)

        assertThrows(IllegalArgumentException::class.java) {
            repository.removeUnavailableSource(localSourceId)
        }

        dao.upsert(
            splitSource(recording.recordingId, "luoxue", "unverified-90", "Protected source", true, 500)
                .copy(lastSuccessfulAt = 0L, lastVerifiedAt = 0L)
        )
        val unavailableId = requireNotNull(dao.source("luoxue", "unverified-90")?.sourceId)
        assertThrows(IllegalArgumentException::class.java) {
            repository.setPreferredSource(unavailableId)
        }

        dao.markSourceUnavailable(unavailableId, 2L)
        repository.removeUnavailableSource(unavailableId)

        assertNull(dao.source(unavailableId))
        assertEquals(1, dao.sourceCount(recording.recordingId))
        assertEquals(localSourceId, dao.sources(recording.recordingId).single().sourceId)
    }

    private fun candidate(
        recordingId: Long,
        provider: String,
        providerItemId: String,
        score: Double
    ) = IdentityCandidate(
        candidateId = "$provider:$providerItemId",
        targetType = IdentityTargetType.RECORDING,
        targetId = recordingId,
        provider = provider,
        providerItemId = providerItemId,
        title = "Managed song",
        artist = "Managed artist",
        album = "Album",
        durationMs = 180_000L,
        score = score,
        status = IdentityCandidateStatus.PENDING,
        evidenceJson = """{"reasons":["title:exact","artist:exact"]}""",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun splitSource(
        recordingId: Long,
        provider: String,
        providerTrackId: String,
        title: String,
        playable: Boolean,
        qualityScore: Int
    ) = TrackSourceMappingEntity(
        sourceId = null,
        recordingId = recordingId,
        provider = provider,
        providerTrackId = providerTrackId,
        localTrackId = null,
        dataPath = "",
        title = title,
        artist = "Same artist",
        album = "Album",
        durationMs = 180_000L,
        quality = "FLAC",
        qualityScore = qualityScore,
        playable = playable,
        matchStatus = "CONFIRMED",
        confidence = 1.0,
        lastSuccessfulAt = if (playable) 100L else 0L,
        lastVerifiedAt = 100L,
        legacyLocalKey = ""
    )
}
