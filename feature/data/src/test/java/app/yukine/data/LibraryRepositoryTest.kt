package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.PlayHistoryEntity
import app.yukine.data.room.AudioFeatureEntity
import app.yukine.data.room.RecordingPlayHistoryEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.TrackEntityMapper
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import app.yukine.model.TrackIdentityTags
import app.yukine.identity.MusicIdentityDiagnostics
import app.yukine.streaming.RecordingRelationship
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import java.util.UUID
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
        val deletedCanonicalId = repository.loadCanonicalId(deleted.id)

        assertEquals(1, repository.deleteTrack(deleted.id))

        assertNull(database.libraryDao().loadTrack(deleted.id))
        assertFalse(repository.isFavorite(deleted.id))
        assertTrue(HistoryRepository(database.historyDao()).loadRecentlyPlayed(10).isEmpty())
        assertTrue(playlists.loadTracks(playlistId).isEmpty())
        assertEquals(listOf(first.id, last.id), playback.loadQueue().map { it.id })
        assertEquals(1, playback.loadQueueIndex())
        assertEquals(-1L, playback.loadPositionTrackId())
        assertEquals(0L, playback.loadPositionMs())
        assertTrue(database.musicIdentityDao().canonicalRecording(deletedCanonicalId) != null)
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
        val oldRecordingId = repository.loadRecordingId(old.id)
        val replacementRecordingId = repository.loadRecordingId(replacement.id)
        database.historyDao().upsertRecordingHistory(
            RecordingPlayHistoryEntity(oldRecordingId, old.id, 100L, 2)
        )
        database.historyDao().upsertRecordingHistory(
            RecordingPlayHistoryEntity(replacementRecordingId, replacement.id, 200L, 3)
        )
        val playlistId = playlists.create("Merge")
        playlists.addTrack(playlistId, old.id)
        playlists.addTrack(playlistId, replacement.id)
        playback.saveQueue(listOf(old, replacement, other), 0)
        playback.savePosition(old.id, 9_000L)

        repository.replaceTrackAndMigrateReferences(old.id, replacement)

        assertNull(database.libraryDao().loadTrack(old.id))
        assertTrue(repository.isFavorite(replacement.id))
        assertEquals(5, database.historyDao().history(replacement.id)?.playCount)
        assertNull(database.historyDao().recordingHistory(oldRecordingId))
        assertEquals(5, database.historyDao().recordingHistory(replacementRecordingId)?.playCount)
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

    @Test
    fun trackDeltaPreservesUnchangedReferencesAndOnlyMutatesChangedRows() {
        val unchanged = track(301L, "Unchanged", "webdav:7:unchanged.flac")
        val changed = track(302L, "Old title", "webdav:7:changed.flac")
        val removed = track(303L, "Removed", "webdav:7:removed.flac")
        val added = track(304L, "Added", "webdav:7:added.flac")
        repository.upsertTracks(listOf(unchanged, changed, removed))
        repository.setFavorite(unchanged.id, true)
        val playlistId = playlists.create("Incremental")
        playlists.addTrack(playlistId, unchanged.id)

        repository.applyTrackDelta(
            listOf(removed.id),
            listOf(track(changed.id, "New title", changed.dataPath), added)
        )

        val tracks = repository.loadTracks().associateBy { it.id }
        assertEquals(setOf(unchanged.id, changed.id, added.id), tracks.keys)
        assertEquals("New title", tracks.getValue(changed.id).title)
        assertTrue(repository.isFavorite(unchanged.id))
        assertEquals(listOf(unchanged.id), playlists.loadTracks(playlistId).map { it.id })
    }

    @Test
    fun structuredStreamingMatchReplacesDuplicateLookupRowsWithOneCatalogRow() {
        val selected = track(401L, "Catalog song", "webdav:1:catalog.flac")
        repository.upsertTracks(listOf(selected))
        val provider = "luoxue"
        val keys = listOf(
            "path:${selected.dataPath}",
            "id:${selected.id}",
            "meta:catalog song|artist"
        )
        keys.forEach { key ->
            repository.saveStreamingTrackMatch(key, provider, "__echo_source_match_v1__:{\"old\":true}", selected)
        }

        val encoded = "__echo_source_match_v2__:{\"primary\":\"wy:42\",\"candidates\":[]}"
        repository.replaceStreamingTrackMatches(keys, provider, keys.last(), encoded, selected)

        assertEquals("", repository.loadStreamingTrackMatch(keys[0], provider))
        assertEquals("", repository.loadStreamingTrackMatch(keys[1], provider))
        assertEquals("", repository.loadStreamingTrackMatch(keys[2], provider))
        assertEquals(encoded, repository.loadStreamingTrackMatch(selected, provider, keys))
        val recordingId = database.musicIdentityDao().recordingIdForLocalTrack(selected.id)
        assertEquals(
            "__stored_match__",
            database.musicIdentityDao().candidates("RECORDING", checkNotNull(recordingId)).single().providerItemId
        )
        assertTrue(
            RoomIdentityCandidateRepository(database)
                .pendingCandidates(app.yukine.identity.IdentityTargetType.RECORDING, recordingId)
                .isEmpty()
        )
        assertTrue(
            database.musicIdentityDao().candidatePage("RECORDING", recordingId, "PENDING", 20, 0).isEmpty()
        )
        assertEquals(0, database.musicIdentityDao().candidateCount("RECORDING", recordingId, "PENDING"))
    }

    @Test
    fun currentStreamingSourceWinsAndReportsLegacyDivergenceWithoutWritingLegacyTable() {
        val selected = track(402L, "Reconciled song", "webdav:1:reconciled.flac")
        repository.upsertTracks(listOf(selected))
        val provider = "netease"
        val key = "id:${selected.id}"

        repository.saveStreamingTrackMatch(key, provider, "new-provider-id", selected)
        assertNull(database.streamingTrackMatchDao().match(key, provider))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO streaming_track_matches(local_key,provider,provider_track_id,title,artist,data_path,updated_at) " +
                "VALUES(?,?,?,?,?,?,?)",
            arrayOf(key, provider, "legacy-provider-id", selected.title, selected.artist, selected.dataPath, 1L)
        )

        assertEquals(
            "new-provider-id",
            repository.loadStreamingTrackMatch(selected, provider, listOf(key))
        )
        assertEquals("compared=1,diverged=1", repository.streamingMatchCompatibilitySummary())
        val source = checkNotNull(database.musicIdentityDao().source("netease", "new-provider-id"))
        assertEquals("CANDIDATE", source.matchStatus)
        assertFalse(source.playable)
        assertEquals(0L, source.lastVerifiedAt)
        val candidate = RoomIdentityCandidateRepository(database)
            .pendingCandidates(app.yukine.identity.IdentityTargetType.RECORDING, source.recordingId)
            .single()
        assertEquals("netease", candidate.provider)
        assertEquals("new-provider-id", candidate.providerItemId)
    }

    @Test
    fun providerCandidateConfirmationAndRejectionUpdateTheUnifiedSource() {
        val confirmedTrack = track(408L, "Confirm candidate", "webdav:1:confirm.flac")
        val rejectedTrack = track(409L, "Reject candidate", "webdav:1:reject.flac")
        repository.upsertTracks(listOf(confirmedTrack, rejectedTrack))
        repository.saveStreamingTrackMatch("id:${confirmedTrack.id}", "netease", "confirm-id", confirmedTrack)
        repository.saveStreamingTrackMatch("id:${rejectedTrack.id}", "qqmusic", "reject-id", rejectedTrack)
        val candidateRepository = RoomIdentityCandidateRepository(database)
        val dao = database.musicIdentityDao()
        val confirmedRecordingId = checkNotNull(dao.recordingIdForLocalTrack(confirmedTrack.id))
        val rejectedRecordingId = checkNotNull(dao.recordingIdForLocalTrack(rejectedTrack.id))

        val confirmCandidate = candidateRepository.pendingCandidates(
            app.yukine.identity.IdentityTargetType.RECORDING,
            confirmedRecordingId
        ).single()
        candidateRepository.confirmCandidate(confirmCandidate.candidateId)

        assertEquals("CONFIRMED", dao.source("netease", "confirm-id")?.matchStatus)
        assertEquals("USER_CONFIRMED", dao.candidate(confirmCandidate.candidateId)?.status)

        val rejectCandidate = candidateRepository.pendingCandidates(
            app.yukine.identity.IdentityTargetType.RECORDING,
            rejectedRecordingId
        ).single()
        candidateRepository.rejectCandidate(rejectCandidate.candidateId)

        assertEquals("REJECTED", dao.source("qqmusic", "reject-id")?.matchStatus)
        assertFalse(checkNotNull(dao.source("qqmusic", "reject-id")).playable)
        assertEquals(
            "",
            repository.loadStreamingTrackMatch(rejectedTrack, "qqmusic", listOf("id:${rejectedTrack.id}"))
        )
    }

    @Test
    fun rankedStreamingCatalogPersistsReviewableVersionsWithoutConfirmingTopOne() {
        val selected = track(410L, "夜空", "webdav:1:ranked.flac").let {
            Track(
                it.id,
                it.title,
                "歌手",
                "专辑",
                180_000L,
                it.contentUri,
                it.dataPath
            )
        }
        repository.upsertTracks(listOf(selected))
        val live = StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "tx:live",
            title = "夜空 (Live)",
            artist = "歌手",
            album = "巡演现场",
            durationMs = 196_000L
        )
        val original = StreamingTrack(
            provider = StreamingProviderName.LUOXUE,
            providerTrackId = "wy:original",
            title = "夜空",
            artist = "歌手",
            album = "专辑",
            durationMs = 180_200L
        )
        val store = StreamingCandidateCatalogStore(database)

        store.replace(selected, "luoxue", listOf(live, original))

        val dao = database.musicIdentityDao()
        val recordingId = checkNotNull(dao.recordingIdForLocalTrack(selected.id))
        val pending = dao.candidatePage("RECORDING", recordingId, "PENDING", 20, 0)
        assertEquals(listOf("wy:original", "tx:live"), pending.map { it.providerItemId })
        val originalEvidence = org.json.JSONObject(pending.first().evidenceJson)
        assertEquals(0, originalEvidence.getInt("rank"))
        assertTrue(originalEvidence.getBoolean("primaryPlaybackCandidate"))
        assertFalse(originalEvidence.getBoolean("hardConflict"))
        assertTrue(org.json.JSONObject(pending.last().evidenceJson).getBoolean("hardConflict"))
        assertNull(dao.source("luoxue", "wy:original"))

        repository.saveStreamingTrackMatch("id:${selected.id}", "luoxue", "wy:original", selected)

        val storedEvidence = org.json.JSONObject(
            checkNotNull(dao.candidate("RECORDING", recordingId, "luoxue", "wy:original")).evidenceJson
        )
        assertTrue(storedEvidence.getBoolean("catalogManaged"))
        assertEquals("CANDIDATE", dao.source("luoxue", "wy:original")?.matchStatus)

        store.replace(selected, "luoxue", listOf(original))

        assertNull(dao.candidate("RECORDING", recordingId, "luoxue", "tx:live"))
        assertEquals("PENDING", dao.candidate("RECORDING", recordingId, "luoxue", "wy:original")?.status)
    }

    @Test
    fun physicalClusteringPublishesPhaseDiagnosticsForCandidateScoringAndCommit() {
        val local = track(413L, "Diagnostic Song", "/music/diagnostic.flac")
        val webDav = track(414L, "Diagnostic Song", "webdav:2:/diagnostic.flac")
        val entities = listOf(local, webDav).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)
        val diagnostics = MusicIdentityDiagnostics()

        val merged = OfflinePhysicalSourceClusterer(database, diagnostics)
            .clusterTracks(listOf(local.id, webDav.id))

        val snapshot = diagnostics.snapshot(MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER)
        assertEquals(1, merged)
        assertEquals(1L, snapshot.stages.getValue(MusicIdentityDiagnostics.Stage.DATABASE_COMMIT).workUnits)
        assertTrue(
            snapshot.stages.getValue(MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION).sampleCount > 0
        )
        assertTrue(snapshot.stages.getValue(MusicIdentityDiagnostics.Stage.SCORING).workUnits > 0L)
        assertEquals(2L, snapshot.stages.getValue(MusicIdentityDiagnostics.Stage.TOTAL).workUnits)
        val features = database.musicIdentityDao().sourceMatchFeatures()
        assertEquals(2, features.size)
        assertEquals(setOf("diagnostic song"), features.map { it.coreTitle }.toSet())
        assertTrue(features.all { it.algorithmVersion == SourceMatchFeaturePolicy.ALGORITHM_VERSION })
        assertTrue(features.all { it.metadataSignature.length == 64 })
        assertTrue(
            features.all {
                it.candidateAlgorithmVersion == SourceRecordingCandidateGenerator.ALGORITHM_VERSION &&
                    it.candidateSnapshotSignature.length == 64 &&
                    it.candidateGeneratedAt > 0L
            }
        )
        val persistedTimestamps = features.associate {
            it.sourceId to Triple(it.updatedAt, it.candidateGeneratedAt, it.candidateSnapshotSignature)
        }
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 2L)
        val reuseDiagnostics = MusicIdentityDiagnostics()

        assertEquals(
            0,
            SourceIdentityIngestor(database, reuseDiagnostics)
                .ingestLocalTracks(listOf(local.id, webDav.id))
        )

        assertEquals(
            0L,
            reuseDiagnostics.snapshot(MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER)
                .stages.getValue(MusicIdentityDiagnostics.Stage.NORMALIZATION).workUnits
        )
        assertEquals(
            0L,
            reuseDiagnostics.snapshot(MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER)
                .stages.getValue(MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION).workUnits
        )
        assertEquals(
            persistedTimestamps,
            database.musicIdentityDao().sourceMatchFeatures().associate {
                it.sourceId to Triple(it.updatedAt, it.candidateGeneratedAt, it.candidateSnapshotSignature)
            }
        )
    }

    @Test
    fun startupBackfillClustersLegacyPendingSourcesOnlyOnce() {
        val local = track(419L, "Legacy duplicate", "/music/legacy-duplicate.flac")
        val webDav = track(420L, "Legacy duplicate", "webdav:2:/legacy-duplicate.flac")
        val entities = listOf(local, webDav).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        // DAO-only construction reproduces a pre-ingestion database upgraded in place.
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)

        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(webDav.id))
        assertEquals(1, repository.ingestPendingConfirmedIdentitySources())
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(webDav.id))
        assertEquals(0, repository.ingestPendingConfirmedIdentitySources())
    }

    @Test
    fun negativeWebDavTrackIdsEnterCanonicalIngestion() {
        val first = track(-419L, "Negative WebDAV duplicate", "webdav:2:/negative-a.flac")
        val second = track(-420L, "Negative WebDAV duplicate", "webdav:2:/negative-b.flac")
        val entities = listOf(first, second).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)

        assertEquals(1, SourceIdentityIngestor(database).ingestLocalTracks(listOf(first.id, second.id)))
        assertEquals(repository.loadRecordingId(first.id), repository.loadRecordingId(second.id))
    }

    @Test
    fun mutuallyEquivalentRunnerUpsMergeAsOneClique() {
        val local = track(421L, "Three-way duplicate", "/music/three-way.flac")
        val firstWebDav = track(-421L, "Three-way duplicate", "webdav:2:/three-way-a.flac")
        val secondWebDav = track(-422L, "Three-way duplicate", "webdav:2:/three-way-b.flac")
        val tracks = listOf(local, firstWebDav, secondWebDav)
        val entities = tracks.map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)

        assertEquals(2, SourceIdentityIngestor(database).ingestLocalTracks(tracks.map { it.id }))
        assertEquals(1, tracks.map { track -> repository.loadRecordingId(track.id) }.distinct().size)
    }

    @Test
    fun exactPcmEvidenceRefinesSubThresholdMetadataOnlyOnColdClusteringPath() {
        val local = track(415L, "Audio verified", "/music/audio-verified.flac")
        val webDavBase = track(416L, "Audio verified", "webdav:2:/audio-verified.flac")
        val webDav = Track(
            webDavBase.id,
            webDavBase.title,
            webDavBase.artist,
            "Different album",
            128_000L,
            webDavBase.contentUri,
            webDavBase.dataPath,
            webDavBase.albumId,
            webDavBase.albumArtUri
        )
        val entities = listOf(local, webDav).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)
        val ingestor = SourceIdentityIngestor(database)

        assertEquals(0, ingestor.ingestLocalTracks(listOf(local.id, webDav.id)))
        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(webDav.id))

        val dao = database.musicIdentityDao()
        val sourceIds = listOf(local.id, webDav.id).map { trackId ->
            checkNotNull(dao.sourceForLocalTrack(trackId)?.sourceId)
        }
        dao.upsertAudioFeatures(sourceIds.map { sourceId -> exactPcmFeature(sourceId, "same-pcm") })

        assertEquals(1, ingestor.ingestLocalTracks(listOf(local.id, webDav.id)))
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(webDav.id))
    }

    @Test
    fun exactPcmEvidenceNeverOverridesLiveVersionConflict() {
        val original = track(417L, "Version guarded", "/music/version-guarded.flac")
        val live = track(418L, "Version guarded (Live)", "webdav:2:/version-guarded-live.flac")
        val entities = listOf(original, live).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)
        val dao = database.musicIdentityDao()
        listOf(original.id, live.id).forEach { trackId ->
            val sourceId = checkNotNull(dao.sourceForLocalTrack(trackId)?.sourceId)
            dao.upsertAudioFeatures(listOf(exactPcmFeature(sourceId, "same-pcm")))
        }

        assertEquals(0, SourceIdentityIngestor(database).ingestLocalTracks(listOf(original.id, live.id)))
        assertFalse(repository.loadRecordingId(original.id) == repository.loadRecordingId(live.id))
    }

    @Test
    fun providerCandidatesKeepDistinctSourceRowsForTheSameRecording() {
        val selected = track(405L, "Candidate set", "webdav:1:candidates.flac")
        repository.upsertTracks(listOf(selected))
        val recordingId = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(selected.id))

        repository.saveStreamingTrackMatch("id:${selected.id}", "QQMusic", "qq-first", selected)
        repository.saveStreamingTrackMatch("id:${selected.id}", "qqmusic", "qq-second", selected)

        val dao = database.musicIdentityDao()
        val first = checkNotNull(dao.source("qqmusic", "qq-first"))
        val second = checkNotNull(dao.source("qqmusic", "qq-second"))
        assertTrue(first.sourceId != second.sourceId)
        assertEquals(recordingId, first.recordingId)
        assertEquals(recordingId, second.recordingId)
        assertFalse(first.playable)
        assertFalse(second.playable)
    }

    @Test
    fun providerIdCollisionRemainsPendingAndDoesNotMoveTheOwnedSource() {
        val owner = track(406L, "Owner", "webdav:1:owner.flac")
        val contender = track(407L, "Contender", "webdav:1:contender.flac")
        repository.upsertTracks(listOf(owner, contender))
        val dao = database.musicIdentityDao()
        val ownerRecordingId = checkNotNull(dao.recordingIdForLocalTrack(owner.id))
        val contenderRecordingId = checkNotNull(dao.recordingIdForLocalTrack(contender.id))

        repository.saveStreamingTrackMatch("id:${owner.id}", "netease", "shared-id", owner)
        repository.saveStreamingTrackMatch("id:${contender.id}", "netease", "shared-id", contender)

        assertEquals(ownerRecordingId, dao.source("netease", "shared-id")?.recordingId)
        val pending = dao.candidates("RECORDING", contenderRecordingId)
        assertEquals("PENDING", pending.single { it.providerItemId == "__stored_match__" }.status)
        val collision = pending.single { it.providerItemId == "shared-id" }
        assertEquals("PENDING", collision.status)
        assertTrue(org.json.JSONObject(collision.evidenceJson).getBoolean("hardConflict"))
        assertEquals(
            "shared-id",
            repository.loadStreamingTrackMatch(contender, "netease", listOf("id:${contender.id}"))
        )
    }

    @Test
    fun batchStreamingSnapshotCombinesCanonicalAndLegacyMappings() {
        val current = track(403L, "Current", "webdav:1:current.flac")
        val legacy = track(404L, "Legacy", "webdav:1:legacy.flac")
        repository.upsertTracks(listOf(current, legacy))
        repository.saveStreamingTrackMatch("id:${current.id}", "netease", "current-id", current)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO streaming_track_matches(local_key,provider,provider_track_id,title,artist,data_path,updated_at) " +
                "VALUES(?,?,?,?,?,?,?)",
            arrayOf("id:${legacy.id}", "netease", "legacy-id", legacy.title, legacy.artist, legacy.dataPath, 1L)
        )

        val snapshot = repository.loadStreamingTrackMatches(
            listOf(current, legacy),
            listOf("netease"),
            mapOf(current.id to listOf("id:${current.id}"), legacy.id to listOf("id:${legacy.id}"))
        )

        assertEquals("current-id", snapshot.getValue(current.id).getValue("netease"))
        assertEquals("legacy-id", snapshot.getValue(legacy.id).getValue("netease"))
    }

    @Test
    fun persistedTracksReceiveStableOfflineCanonicalIdsForEverySourceKind() {
        val local = track(501L, "Local", "/music/local.flac")
        val webDav = track(502L, "WebDAV", "webdav:9:/remote.flac")
        val streaming = track(503L, "Streaming", "streaming:netease:song-503?quality=lossless")

        repository.upsertTracks(listOf(local, webDav, streaming))

        val canonicalIds = listOf(local, webDav, streaming).associate { value ->
            value.id to repository.loadCanonicalId(value.id)
        }
        canonicalIds.values.forEach { value -> assertEquals(value, UUID.fromString(value).toString()) }
        assertEquals(3, canonicalIds.values.toSet().size)
        assertEquals("local", database.musicIdentityDao().sourceForLocalTrack(local.id)?.provider)
        assertEquals("webdav", database.musicIdentityDao().sourceForLocalTrack(webDav.id)?.provider)
        assertEquals("netease", database.musicIdentityDao().sourceForLocalTrack(streaming.id)?.provider)
        assertEquals(
            "song-503",
            database.musicIdentityDao().sourceForLocalTrack(streaming.id)?.providerTrackId
        )
        canonicalIds.forEach { (trackId, canonicalUuid) ->
            val recordingId = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(trackId))
            val recording = checkNotNull(database.musicIdentityDao().recording(recordingId))
            val source = checkNotNull(database.musicIdentityDao().sourceForLocalTrack(trackId))
            assertEquals(canonicalUuid, recording.canonicalUuid)
            val workId = checkNotNull(recording.workId)
            val work = checkNotNull(database.musicIdentityDao().work(workId))
            assertEquals(canonicalUuid, work.canonicalUuid)
            if (source.matchStatus == "CONFIRMED") {
                assertEquals(source.sourceId, recording.activeSourceId)
            } else {
                assertNull(recording.activeSourceId)
            }
            val credit = database.musicIdentityDao().credits(recordingId).single()
            val artist = checkNotNull(database.musicIdentityDao().artist(credit.artistId))
            assertEquals(artist.id, work.primaryCreatorId)
            assertEquals(artist.artistUuid, UUID.fromString(artist.artistUuid).toString())
            assertEquals("UNRESOLVED", artist.matchStatus)
        }

        repository.upsertTracks(
            listOf(
                track(local.id, "Local renamed", local.dataPath),
                track(webDav.id, "WebDAV renamed", webDav.dataPath),
                track(streaming.id, "Streaming renamed", streaming.dataPath)
            )
        )
        repository.replaceScanManagedTracks(listOf(track(local.id, "Local rescanned", local.dataPath)))

        canonicalIds.forEach { (trackId, canonicalId) ->
            assertEquals(canonicalId, repository.loadCanonicalId(trackId))
        }
        assertEquals("", repository.loadCanonicalId(999_999L))
    }

    @Test
    fun audioFeaturesAreOwnedByStableSourceAndCascadeWhenSourceIsDeleted() {
        val track = track(504L, "Feature source", "/music/feature-source.flac")
        repository.upsertTracks(listOf(track))
        val dao = database.musicIdentityDao()
        val source = checkNotNull(dao.sourceForLocalTrack(track.id))
        val sourceId = checkNotNull(source.sourceId)
        val feature = AudioFeatureEntity(
            sourceId = sourceId,
            contentSignature = "content-signature",
            pcmHash = "pcm",
            chromaprint = "chromaprint",
            recordingEmbedding = byteArrayOf(1, 2),
            workEmbedding = byteArrayOf(3, 4),
            versionScores = "{\"LIVE\":0.1}",
            algorithmVersion = 2,
            audioSpecState = "READY",
            audioSpecAlgorithmVersion = 1,
            audioSpecAttemptCount = 1,
            lastAttemptAt = 10L,
            lastError = "",
            updatedAt = 10L
        )

        dao.upsertAudioFeatures(listOf(feature))

        val stored = checkNotNull(dao.audioFeature(sourceId))
        assertEquals("content-signature", stored.contentSignature)
        assertEquals("chromaprint", stored.chromaprint)
        assertTrue(stored.recordingEmbedding!!.contentEquals(byteArrayOf(1, 2)))
        assertEquals(1, dao.deleteSource(sourceId))
        assertNull(dao.audioFeature(sourceId))
    }

    @Test
    fun metadataEvidenceGroupsPhysicalSourcesIntoStableCanonicalRecording() {
        val local = track(511L, "Shared song", "/music/shared-song.flac")
        val webDav = track(512L, "Shared song", "webdav:9:/shared-song.flac")
        repository.upsertTracks(listOf(local, webDav))

        val grouped = database.musicIdentityDao().trackMergeIdentities()
            .associate { it.localTrackId to it.mergeIdentity }
        assertTrue(grouped.getValue(local.id).isNotBlank())
        assertEquals(grouped.getValue(local.id), grouped.getValue(webDav.id))
        assertEquals(repository.loadCanonicalId(local.id), repository.loadCanonicalId(webDav.id))

        val mbid = "123e4567-e89b-12d3-a456-426614174099"
        val identifiedLocal = withIdentity(
            track(513L, "Identified", "/music/identified.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        val identifiedWebDav = withIdentity(
            track(514L, "Identified", "webdav:9:/identified.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        repository.upsertTracks(listOf(identifiedLocal, identifiedWebDav))

        val authoritative = database.musicIdentityDao().trackMergeIdentities()
            .associate { it.localTrackId to it.mergeIdentity }
        val sharedIdentity = authoritative.getValue(identifiedLocal.id)
        assertTrue(sharedIdentity.isNotBlank())
        assertEquals(sharedIdentity, authoritative.getValue(identifiedWebDav.id))
        assertEquals(repository.loadCanonicalId(identifiedLocal.id), sharedIdentity)
    }

    @Test
    fun physicalCanonicalClusteringKeepsLiveAndRemixOutOfTheOriginalRecording() {
        val original = track(521L, "Echo", "/music/echo.flac")
        val webDav = track(522L, "Echo", "webdav:9:/echo.flac")
        val live = track(523L, "Echo (Live at Tokyo)", "webdav:9:/echo-live.flac")
        val remix = track(524L, "Echo (Club Remix)", "webdav:9:/echo-remix.flac")

        repository.upsertTracks(listOf(original, webDav, live, remix))

        assertEquals(repository.loadRecordingId(original.id), repository.loadRecordingId(webDav.id))
        assertFalse(repository.loadRecordingId(original.id) == repository.loadRecordingId(live.id))
        assertFalse(repository.loadRecordingId(original.id) == repository.loadRecordingId(remix.id))
        val originalRecordingId = requireNotNull(repository.loadRecordingId(original.id))
        val liveRecordingId = requireNotNull(repository.loadRecordingId(live.id))
        val liveRelation = requireNotNull(
            RecordingRelationStore(database).relation(originalRecordingId, liveRecordingId)
        )
        assertEquals(RecordingRelationship.SAME_WORK_DIFFERENT_VERSION.name, liveRelation.relationType)
        assertFalse(liveRelation.locked)
    }

    @Test
    fun confirmedPlatformSourceUsesTheSameCanonicalIngestorAsPhysicalSources() {
        val local = track(525L, "Unified source", "/music/unified-source.flac")
        val netease = track(
            526L,
            "Unified source",
            "streaming:netease:provider-unified-source"
        )
        repository.upsertTracks(listOf(local, netease))

        val localRecordingBefore = repository.loadRecordingId(local.id)
        val providerRecordingBefore = repository.loadRecordingId(netease.id)
        assertEquals(localRecordingBefore, providerRecordingBefore)
        assertEquals(
            "UNRESOLVED",
            database.musicIdentityDao().sourceForLocalTrack(netease.id)?.matchStatus
        )

        assertTrue(
            repository.confirmDirectProviderSource(
                netease.id,
                "netease",
                "provider-unified-source"
            )
        )

        val mergedRecordingId = repository.loadRecordingId(local.id)
        assertEquals(mergedRecordingId, repository.loadRecordingId(netease.id))
        val providerSource = checkNotNull(
            database.musicIdentityDao().source("netease", "provider-unified-source")
        )
        assertEquals("CONFIRMED", providerSource.matchStatus)
        assertEquals(mergedRecordingId, providerSource.recordingId)
        assertEquals(2, database.musicIdentityDao().sourceCount(mergedRecordingId))
    }

    @Test
    fun highConfidenceLibraryResidentPlatformSourceJoinsCanonicalRecording() {
        val local = track(527L, "Unresolved source", "/music/unresolved-source.flac")
        val qq = track(
            528L,
            "Unresolved source",
            "streaming:qqmusic:provider-unresolved-source"
        )
        repository.upsertTracks(listOf(local, qq))

        assertEquals("UNRESOLVED", database.musicIdentityDao().sourceForLocalTrack(qq.id)?.matchStatus)
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(qq.id))
        assertEquals(2, database.musicIdentityDao().sourceMatchFeatures().size)
        assertEquals(2, database.musicIdentityDao().sourceCount(repository.loadRecordingId(local.id)))
    }

    @Test
    fun translatedTitleAndRomanizedArtistSuffixJoinTheOriginalRecording() {
        val original = track(
            537L,
            "10年後の私になら",
            "/music/kohana-original.flac",
            artist = "こはならむ"
        )
        val translated = track(
            538L,
            "10年後の私になら (如果是10年后的我)",
            "streaming:qqmusic:kohana-translated",
            artist = "こはならむ (Kohana Lam)"
        )

        repository.upsertTracks(listOf(original, translated))

        assertEquals(repository.loadRecordingId(original.id), repository.loadRecordingId(translated.id))
    }

    @Test
    fun unresolvedLibrarySourceWithDifferentArtistDoesNotAutoMerge() {
        val local = track(529L, "Shared title", "/music/shared-title.flac")
        val qqBase = track(530L, "Shared title", "streaming:qqmusic:different-artist")
        val qq = Track(
            qqBase.id,
            qqBase.title,
            "Different artist",
            qqBase.album,
            qqBase.durationMs,
            qqBase.contentUri,
            qqBase.dataPath,
            qqBase.albumId,
            qqBase.albumArtUri
        )

        repository.upsertTracks(listOf(local, qq))

        assertEquals("UNRESOLVED", database.musicIdentityDao().sourceForLocalTrack(qq.id)?.matchStatus)
        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(qq.id))
    }

    @Test
    fun startupBackfillMergesLegacyUnresolvedLibraryProviderSourceOnlyOnce() {
        val local = track(531L, "Legacy provider duplicate", "/music/legacy-provider.flac")
        val netease = track(
            532L,
            "Legacy provider duplicate",
            "streaming:netease:legacy-provider-duplicate"
        )
        val entities = listOf(local, netease).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)

        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(netease.id))
        assertEquals(1, repository.ingestPendingConfirmedIdentitySources())
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(netease.id))
        assertEquals(0, repository.ingestPendingConfirmedIdentitySources())
    }

    @Test
    fun startupBackfillRepairsTrackWhoseProviderCandidateLostItsLocalMapping() {
        val local = track(535L, "Legacy candidate mapping", "/music/legacy-candidate.flac")
        val qq = track(
            536L,
            "Legacy candidate mapping",
            "streaming:qqmusic:legacy-candidate-mapping"
        )
        val entities = listOf(local, qq).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)
        val dao = database.musicIdentityDao()
        val qqSource = checkNotNull(dao.sourceForLocalTrack(qq.id))
        dao.upsert(qqSource.copy(localTrackId = null, matchStatus = "CANDIDATE", confidence = 0.95))

        assertNull(dao.sourceForLocalTrack(qq.id))
        assertEquals(1, repository.ingestPendingConfirmedIdentitySources())
        assertEquals(qq.id, dao.source("qqmusic", "legacy-candidate-mapping")?.localTrackId)
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(qq.id))
        assertEquals(0, repository.ingestPendingConfirmedIdentitySources())
    }

    @Test
    fun explicitConfirmedSourceBackfillMergesExistingPlatformRecording() {
        val local = track(533L, "Backfill source", "/music/backfill-source.flac")
        val qq = track(
            534L,
            "Backfill source",
            "streaming:qqmusic:provider-backfill-source"
        )
        val entities = listOf(local, qq).map { TrackEntityMapper.entity(it, 1L) }
        database.libraryDao().upsertTracks(entities)
        OfflineMusicIdentityStore(database.musicIdentityDao()).ensureTracks(entities, 1L)
        val dao = database.musicIdentityDao()
        val providerSource = checkNotNull(dao.sourceForLocalTrack(qq.id))
        assertEquals(1, dao.confirmDirectProviderSource(checkNotNull(providerSource.sourceId)))
        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(qq.id))

        assertEquals(1, repository.ingestConfirmedIdentitySources())

        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(qq.id))
    }

    @Test
    fun confirmedPlatformLiveVersionCannotMergeIntoPhysicalOriginal() {
        val original = track(529L, "Separate version", "/music/separate-version.flac")
        val live = track(
            530L,
            "Separate version (Live)",
            "streaming:luoxue:provider-live-version"
        )
        repository.upsertTracks(listOf(original, live))

        assertTrue(
            repository.confirmDirectProviderSource(
                live.id,
                "luoxue",
                "provider-live-version"
            )
        )

        assertFalse(repository.loadRecordingId(original.id) == repository.loadRecordingId(live.id))
        assertEquals(
            "CONFIRMED",
            database.musicIdentityDao().source("luoxue", "provider-live-version")?.matchStatus
        )
    }

    @Test
    fun manualSplitPreventsARescanFromAutomaticallyMergingTheSourcesAgain() {
        val local = track(531L, "Split song", "/music/split.flac")
        val webDav = track(532L, "Split song", "webdav:9:/split.flac")
        repository.upsertTracks(listOf(local, webDav))
        assertEquals(repository.loadRecordingId(local.id), repository.loadRecordingId(webDav.id))

        val webDavSourceId = checkNotNull(
            database.musicIdentityDao().sourceForLocalTrack(webDav.id)?.sourceId
        )
        val split = RoomRecordingIdentityRepository(database).splitSource(webDavSourceId)
        repository.upsertTracks(listOf(track(webDav.id, webDav.title, webDav.dataPath)))

        assertFalse(repository.loadRecordingId(local.id) == repository.loadRecordingId(webDav.id))
        val relation = requireNotNull(
            RecordingRelationStore(database).relation(repository.loadRecordingId(local.id)!!, split.recordingId)
        )
        assertEquals(RecordingRelationship.CANNOT_LINK.name, relation.relationType)
        assertTrue(relation.locked)
    }

    @Test
    fun favoriteBelongsToCanonicalRecordingAndSurvivesAcrossLocalAndWebDavSources() {
        val mbid = "123e4567-e89b-12d3-a456-426614174000"
        val webDav = withIdentity(
            track(701L, "Shared", "webdav:9:/shared.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        val local = withIdentity(
            track(702L, "Shared", "/music/shared.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        repository.upsertTracks(listOf(webDav, local))

        repository.setFavorite(webDav.id, true)

        assertTrue(repository.isFavorite(webDav.id))
        assertTrue(repository.isFavorite(local.id))
        assertEquals(setOf(webDav.id, local.id), repository.loadFavoriteIds())
        assertEquals(listOf(local.id), repository.loadFavoriteTracks().map { it.id })
        assertEquals(1, database.libraryDao().recordingFavoriteCountForTrack(local.id))

        repository.setFavorite(local.id, false)

        assertFalse(repository.isFavorite(webDav.id))
        assertFalse(repository.isFavorite(local.id))
    }

    @Test
    fun webDavTrackIdChangeReattachesToFavoritedCanonicalRecording() {
        val original = track(703L, "Stable favorite", "webdav:9:/stable.flac")
        repository.upsertTracks(listOf(original))
        repository.setFavorite(original.id, true)
        val recordingId = repository.loadRecordingId(original.id)

        val rescanned = track(704L, "Stable favorite", original.dataPath)
        repository.replaceTracksByDataPathPattern("webdav:9:%", listOf(rescanned))

        assertEquals(recordingId, repository.loadRecordingId(rescanned.id))
        assertTrue(repository.isFavorite(rescanned.id))
        assertEquals(setOf(rescanned.id), repository.loadFavoriteIds())
        assertEquals("LOCAL_ONLY", database.libraryDao().recordingFavorite(recordingId)?.syncState)
    }

    @Test
    fun activePlaybackSourcePrefersConfirmedLocalThenFallsBackToWebDav() {
        val mbid = "123e4567-e89b-12d3-a456-426614174001"
        val webDav = withIdentity(
            track(711L, "Shared", "webdav:9:/shared-active.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        val local = withIdentity(
            track(712L, "Shared", "/music/shared-active.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        repository.upsertTracks(listOf(webDav, local))

        val localPlayback = repository.loadActivePlaybackSource(webDav)
        assertEquals(webDav.id, localPlayback.id)
        assertEquals(local.dataPath, localPlayback.dataPath)

        val localSource = checkNotNull(database.musicIdentityDao().sourceForLocalTrack(local.id))
        database.musicIdentityDao().markSourceUnavailable(checkNotNull(localSource.sourceId), 100L)
        val recordingId = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(local.id))
        database.musicIdentityDao().refreshActiveSource(recordingId)

        val webDavPlayback = repository.loadActivePlaybackSource(local)
        assertEquals(local.id, webDavPlayback.id)
        assertEquals(webDav.dataPath, webDavPlayback.dataPath)
    }

    @Test
    fun activePlaybackSourceUsesIndependentHealthQualityAndNetworkScore() {
        val mbid = "123e4567-e89b-12d3-a456-426614174019"
        val webDav = withIdentity(
            track(713L, "Selection score", "webdav:9:/selection-score.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        val local = withIdentity(
            track(714L, "Selection score", "/music/selection-score.flac"),
            TrackIdentityTags(mbid, "", "", "", emptyList())
        )
        repository.upsertTracks(listOf(webDav, local))
        val dao = database.musicIdentityDao()
        val webDavSource = checkNotNull(dao.sourceForLocalTrack(webDav.id))
        val localSource = checkNotNull(dao.sourceForLocalTrack(local.id))
        val now = 1_700_000_000_000L
        dao.upsert(
            localSource.copy(qualityScore = 100, lastSuccessfulAt = 0L, lastVerifiedAt = 0L)
        )
        dao.upsert(
            webDavSource.copy(
                qualityScore = 1_000,
                lastSuccessfulAt = now,
                lastVerifiedAt = now
            )
        )
        val recordingId = checkNotNull(dao.recordingIdForLocalTrack(local.id))

        dao.refreshActiveSource(recordingId)

        assertEquals(webDavSource.sourceId, dao.recording(recordingId)?.activeSourceId)
        assertEquals(webDav.dataPath, repository.loadActivePlaybackSource(local).dataPath)
    }

    @Test
    fun activeConfirmedPlatformSourcePreservesLogicalTrackIdentity() {
        val logical = track(718L, "Canonical stream", "/music/canonical-stream.flac")
        repository.upsertTracks(listOf(logical))
        val dao = database.musicIdentityDao()
        val recordingId = repository.loadRecordingId(logical.id)
        dao.upsert(
            TrackSourceMappingEntity(
                sourceId = null,
                recordingId = recordingId,
                provider = "netease",
                providerTrackId = "active-718",
                localTrackId = null,
                dataPath = "streaming:netease:active-718",
                title = "Search title must not replace logical metadata",
                artist = "Search artist",
                album = "Search album",
                durationMs = logical.durationMs,
                quality = "lossless",
                qualityScore = 400,
                playable = true,
                matchStatus = "CONFIRMED",
                confidence = 1.0,
                lastSuccessfulAt = 100L,
                lastVerifiedAt = 100L,
                legacyLocalKey = "",
                codec = "flac",
                bitrateKbps = 1_411
            )
        )
        val platformSource = checkNotNull(dao.source("netease", "active-718"))
        assertEquals(1, dao.setActiveSource(recordingId, checkNotNull(platformSource.sourceId)))

        val resolved = repository.loadActivePlaybackSource(logical)

        assertEquals(logical.id, resolved.id)
        assertEquals(logical.title, resolved.title)
        assertEquals(Uri.EMPTY, resolved.contentUri)
        assertEquals("streaming:netease:active-718", resolved.dataPath)
        assertEquals("flac", resolved.codec)
        assertEquals(recordingId, repository.loadRecordingId(resolved.id))
    }

    @Test
    fun directProviderFavoriteConfirmsOnlyItsOwnCanonicalSourceWithoutPromotingIt() {
        val track = track(
            717L,
            "Pulled provider favorite",
            "streaming:netease:favorite-717"
        )
        repository.upsertTracks(listOf(track))
        val dao = database.musicIdentityDao()
        val initial = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertEquals("UNRESOLVED", initial.matchStatus)

        assertFalse(repository.confirmDirectProviderSource(track.id, "qqmusic", "favorite-717"))
        assertFalse(repository.confirmDirectProviderSource(track.id, "netease", "other-id"))
        assertTrue(repository.confirmDirectProviderSource(track.id, "netease", "favorite-717"))

        val confirmed = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertEquals("CONFIRMED", confirmed.matchStatus)
        assertEquals(1.0, confirmed.confidence, 0.0)
        assertEquals(
            "favorite-717",
            repository.loadConfirmedProviderTrackId(confirmed.recordingId, "netease")
        )
        assertNull(dao.recording(confirmed.recordingId)?.activeSourceId)
    }

    @Test
    fun successfulPlaybackUpdatesHealthAndOnlyPromotesAConfirmedSource() {
        val confirmedTrack = track(
            713L,
            "Confirmed stream",
            "streaming:netease:confirmed-713"
        )
        val candidateTrack = track(
            714L,
            "Candidate stream",
            "streaming:qqmusic:candidate-714"
        )
        repository.upsertTracks(listOf(confirmedTrack, candidateTrack))
        val dao = database.musicIdentityDao()
        val confirmedSource = checkNotNull(dao.sourceForLocalTrack(confirmedTrack.id))
        dao.upsert(
            confirmedSource.copy(
                playable = false,
                matchStatus = "CONFIRMED",
                lastSuccessfulAt = 0L,
                lastVerifiedAt = 0L
            )
        )
        val candidateSource = checkNotNull(dao.sourceForLocalTrack(candidateTrack.id))
        dao.upsert(
            candidateSource.copy(
                playable = false,
                matchStatus = "CANDIDATE",
                lastSuccessfulAt = 0L,
                lastVerifiedAt = 0L
            )
        )

        assertTrue(
            repository.recordSuccessfulPlayback(
                confirmedTrack,
                "netease",
                "confirmed-713"
            )
        )
        assertTrue(
            repository.recordSuccessfulPlayback(
                candidateTrack,
                "qqmusic",
                "candidate-714"
            )
        )

        val refreshedConfirmed = checkNotNull(dao.sourceForLocalTrack(confirmedTrack.id))
        val confirmedRecording = checkNotNull(dao.recording(refreshedConfirmed.recordingId))
        assertTrue(refreshedConfirmed.playable)
        assertTrue(refreshedConfirmed.lastSuccessfulAt > 0L)
        assertEquals(refreshedConfirmed.sourceId, confirmedRecording.activeSourceId)

        val refreshedCandidate = checkNotNull(dao.sourceForLocalTrack(candidateTrack.id))
        val candidateRecording = checkNotNull(dao.recording(refreshedCandidate.recordingId))
        assertTrue(refreshedCandidate.playable)
        assertTrue(refreshedCandidate.lastSuccessfulAt > 0L)
        assertEquals("CANDIDATE", refreshedCandidate.matchStatus)
        assertNull(candidateRecording.activeSourceId)
    }

    @Test
    fun playbackFailuresAreRecordedWithoutDisablingTransientOrCancelledSources() {
        val track = track(
            715L,
            "Failure policy",
            "streaming:netease:failure-715"
        )
        repository.upsertTracks(listOf(track))
        val dao = database.musicIdentityDao()
        val initial = checkNotNull(dao.sourceForLocalTrack(track.id))
        dao.upsert(initial.copy(matchStatus = "CONFIRMED", playable = true))
        dao.markSourceVerifiedSuccess(checkNotNull(initial.sourceId), 100L, "", 0)
        dao.setActiveSource(initial.recordingId, checkNotNull(initial.sourceId))

        repeat(4) {
            assertTrue(
                repository.recordPlaybackResolutionFailure(
                    "netease",
                    "failure-715",
                    "SOURCE_UNAVAILABLE",
                    true
                )
            )
        }
        var current = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertTrue(current.playable)
        assertEquals(4, current.failureCount)
        assertEquals(current.sourceId, dao.recording(current.recordingId)?.activeSourceId)

        repeat(2) {
            repository.recordPlaybackResolutionFailure(
                "netease",
                "failure-715",
                "SOURCE_UNAVAILABLE",
                false
            )
        }
        current = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertTrue(current.playable)
        assertEquals(2, current.failureCount)

        repository.recordPlaybackResolutionFailure(
            "netease",
            "failure-715",
            "SOURCE_UNAVAILABLE",
            false
        )
        current = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertFalse(current.playable)
        assertEquals(3, current.failureCount)
        assertNull(dao.recording(current.recordingId)?.activeSourceId)
    }

    @Test
    fun authAndRateLimitFailuresNeverDisableTheSourceButUnsupportedDoes() {
        val track = track(
            716L,
            "Provider policy",
            "streaming:qqmusic:policy-716"
        )
        repository.upsertTracks(listOf(track))
        val dao = database.musicIdentityDao()
        val initial = checkNotNull(dao.sourceForLocalTrack(track.id))
        dao.upsert(initial.copy(matchStatus = "CONFIRMED", playable = true))

        repeat(5) {
            repository.recordPlaybackResolutionFailure(
                "qqmusic",
                "policy-716",
                if (it % 2 == 0) "AUTH_REQUIRED" else "RATE_LIMITED",
                false
            )
        }
        assertTrue(checkNotNull(dao.sourceForLocalTrack(track.id)).playable)

        repository.recordPlaybackResolutionFailure(
            "qqmusic",
            "policy-716",
            "UNSUPPORTED_OPERATION",
            false
        )
        val disabled = checkNotNull(dao.sourceForLocalTrack(track.id))
        assertFalse(disabled.playable)
        assertEquals("UNSUPPORTED_OPERATION", disabled.failureReason)
    }

    @Test
    fun artistCreditsAreParsedAndArtistUuidRemainsStableAcrossRescan() {
        val featured = track(601L, "Featured", artist = "Hanser feat. Guest")
        val caseFolded = track(602L, "Case folded", artist = "hanser")
        val group = track(603L, "Group", artist = "Simon & Garfunkel")
        val unknown = track(604L, "Unknown", artist = "Various Artists")

        repository.upsertTracks(listOf(featured, caseFolded, group, unknown))

        val featuredRecording = checkNotNull(
            database.musicIdentityDao().recordingIdForLocalTrack(featured.id)
        )
        val featuredCredits = database.musicIdentityDao().credits(featuredRecording)
        assertEquals(listOf("PRIMARY", "FEATURED"), featuredCredits.map { it.role })
        assertEquals(listOf("Hanser", "Guest"), featuredCredits.map { it.creditedName })

        val hanserArtist = checkNotNull(database.musicIdentityDao().artist(featuredCredits.first().artistId))
        val caseFoldedRecording = checkNotNull(
            database.musicIdentityDao().recordingIdForLocalTrack(caseFolded.id)
        )
        val caseFoldedArtistId = database.musicIdentityDao().credits(caseFoldedRecording).single().artistId
        assertEquals(hanserArtist.id, caseFoldedArtistId)
        assertEquals("UNRESOLVED", hanserArtist.matchStatus)

        val groupRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(group.id))
        assertEquals(
            "Simon & Garfunkel",
            database.musicIdentityDao().credits(groupRecording).single().creditedName
        )
        val unknownRecording = checkNotNull(database.musicIdentityDao().recordingIdForLocalTrack(unknown.id))
        assertEquals("UNKNOWN", database.musicIdentityDao().credits(unknownRecording).single().role)

        repository.upsertTracks(listOf(track(featured.id, "Featured rescanned", artist = featured.artist)))

        val rescannedRecording = checkNotNull(
            database.musicIdentityDao().recordingIdForLocalTrack(featured.id)
        )
        val rescannedArtist = checkNotNull(
            database.musicIdentityDao().artist(
                database.musicIdentityDao().credits(rescannedRecording).first().artistId
            )
        )
        assertEquals(hanserArtist.artistUuid, rescannedArtist.artistUuid)
    }

    private fun track(
        id: Long,
        title: String = "Track $id",
        dataPath: String = "/music/$id.flac",
        artist: String = "Artist"
    ) = Track(
        id,
        title,
        artist,
        "Album",
        120_000L,
        Uri.parse("content://media/$id"),
        dataPath,
        id,
        null
    )

    private fun withIdentity(track: Track, tags: TrackIdentityTags) = Track(
        track.id,
        track.title,
        track.artist,
        track.album,
        track.durationMs,
        track.contentUri,
        track.dataPath,
        track.albumId,
        track.albumArtUri,
        track.codec,
        track.bitrateKbps,
        track.sampleRateHz,
        track.bitsPerSample,
        track.channelCount,
        track.replayGainTrackDb,
        track.replayGainAlbumDb,
        tags
    )

    private fun exactPcmFeature(sourceId: Long, pcmHash: String) = AudioFeatureEntity(
        sourceId = sourceId,
        contentSignature = "signature-$sourceId",
        pcmHash = pcmHash,
        chromaprint = "{\"version\":1,\"segments\":[]}",
        recordingEmbedding = null,
        workEmbedding = null,
        versionScores = "",
        algorithmVersion = 1,
        audioSpecState = "PENDING",
        audioSpecAlgorithmVersion = 0,
        audioSpecAttemptCount = 0,
        lastAttemptAt = 1L,
        lastError = "",
        updatedAt = 1L
    )
}
