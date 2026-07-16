package app.yukine

import android.net.Uri
import app.yukine.data.RecordingMatchDataSource
import app.yukine.data.RecordingSourceVerification
import app.yukine.data.IdentityOperation
import app.yukine.data.RecordingMatchSnapshot
import app.yukine.data.RecordingMergeImpact
import app.yukine.data.RecordingMergePreview
import app.yukine.data.RecordingMergeSearchResult
import app.yukine.data.RecordingMergeSummary
import app.yukine.data.RecordingMergeWarning
import app.yukine.data.RecordingMergeWarningCode
import app.yukine.data.RecordingSplitDestination
import app.yukine.data.RecordingSplitOptions
import app.yukine.data.RecordingSplitPreview
import app.yukine.data.RecordingSplitReference
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityMatchStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.TrackSourceMapping
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchViewModelTest {
    @Test
    fun openLoadsRoomSnapshotOnlyAfterExplicitNavigationAndCandidateActionsReload() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)

        assertFalse(viewModel.uiState.value.visible)
        assertEquals(0, source.loadCount)

        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        assertTrue(viewModel.uiState.value.visible)
        assertEquals(1, source.loadCount)
        assertEquals(7L, viewModel.uiState.value.snapshot?.track?.id)

        viewModel.markAsAlternateVersion("candidate-7", "LIVE")

        assertEquals("candidate-7:LIVE", source.markedAlternate)
        assertEquals(2, source.loadCount)
        assertTrue(viewModel.uiState.value.snapshot?.pendingCandidates?.isEmpty() == true)
        assertEquals(1, viewModel.uiState.value.snapshot?.alternateVersions?.size)
    }

    @Test
    fun webDavStableNegativeTrackIdOpensButInvalidSentinelDoesNot() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)
        val webDavTrackId = -4_204_206L

        viewModel.open(webDavTrackId, AppLanguage.MODE_CHINESE)

        assertTrue(viewModel.uiState.value.visible)
        assertEquals(webDavTrackId, viewModel.uiState.value.localTrackId)
        assertEquals(webDavTrackId, source.lastLoadedTrackId)
        assertEquals(webDavTrackId, viewModel.uiState.value.snapshot?.track?.id)

        viewModel.close()
        val loadsBeforeInvalidOpen = source.loadCount
        viewModel.open(TrackIdentity.INVALID_ID, AppLanguage.MODE_CHINESE)

        assertFalse(viewModel.uiState.value.visible)
        assertEquals(loadsBeforeInvalidOpen, source.loadCount)
    }

    @Test
    fun loadMoreAppendsPagesWithoutDuplicatingSourcesOrCandidates() {
        val source = FakeRecordingMatchDataSource(paged = true)
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)
        viewModel.open(7L, AppLanguage.MODE_ENGLISH)

        assertEquals(1, viewModel.uiState.value.snapshot?.sources?.size)
        assertEquals(1, viewModel.uiState.value.snapshot?.pendingCandidates?.size)

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.snapshot?.sources?.map { it.sourceId })
        assertEquals(
            listOf("candidate-7", "candidate-8"),
            viewModel.uiState.value.snapshot?.pendingCandidates?.map { it.candidateId }
        )
    }

    @Test
    fun batchRejectObviousMismatchesReloadsCandidatesAndReportsResult() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        viewModel.rejectObviousMismatches()

        assertEquals(70L, source.batchRejectedRecordingId)
        assertEquals("recording.match.batch.reject.completed", viewModel.uiState.value.messageKey)
        assertTrue(viewModel.uiState.value.snapshot?.pendingCandidates?.isEmpty() == true)
        assertEquals(2, source.loadCount)
    }

    @Test
    fun manualMergeSearchesRoomPreviewsImpactAndReloadsAfterCommit() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        var committed = 0
        viewModel.bindDataSource(source)
        viewModel.bindIdentityChangedListener { committed++ }
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        viewModel.openMerge()

        assertTrue(viewModel.uiState.value.merge.visible)
        assertEquals(listOf(71L), viewModel.uiState.value.merge.results.map { it.recordingId })

        viewModel.previewMerge(71L)

        assertEquals(71L, viewModel.uiState.value.merge.preview?.source?.recording?.recordingId)
        assertEquals(3, viewModel.uiState.value.merge.preview?.impact?.playlistItemCount)

        viewModel.confirmMerge()

        assertEquals("71:70", source.merged)
        assertEquals(1, committed)
        assertFalse(viewModel.uiState.value.merge.visible)
        assertEquals("recording.match.merge.completed", viewModel.uiState.value.messageKey)
        assertEquals(2, source.loadCount)
    }

    @Test
    fun hardConflictPreviewCannotBeCommitted() {
        val source = FakeRecordingMatchDataSource(blockMerge = true)
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)
        viewModel.open(7L, AppLanguage.MODE_CHINESE)
        viewModel.openMerge()
        viewModel.previewMerge(71L)

        assertTrue(viewModel.uiState.value.merge.preview?.blocked == true)

        viewModel.confirmMerge()

        assertEquals("", source.merged)
        assertTrue(viewModel.uiState.value.merge.visible)
    }

    @Test
    fun manualSplitSelectsSourcesAndAppliesTypedReferenceDestinations() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        var changed = 0
        viewModel.bindDataSource(source)
        viewModel.bindIdentityChangedListener { changed++ }
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        viewModel.openSplit()
        viewModel.toggleSplitSource(2L)
        viewModel.previewSplit()
        viewModel.updateSplitDestination(
            RecordingSplitReference.FAVORITE,
            RecordingSplitDestination.NEW_RECORDING
        )
        viewModel.updateSplitDestination(
            RecordingSplitReference.QUEUE,
            RecordingSplitDestination.NEW_RECORDING
        )
        viewModel.confirmSplit()

        assertEquals(setOf(2L), source.splitSourceIds)
        assertEquals(RecordingSplitDestination.NEW_RECORDING, source.splitOptions.favoriteDestination)
        assertEquals(RecordingSplitDestination.ORIGINAL, source.splitOptions.playlistDestination)
        assertEquals(RecordingSplitDestination.NEW_RECORDING, source.splitOptions.queueDestination)
        assertEquals(1, changed)
        assertFalse(viewModel.uiState.value.split.visible)
        assertEquals("recording.match.split.completed", viewModel.uiState.value.messageKey)
    }

    @Test
    fun latestMergeOperationCanBeUndoneOnceAndReloadsIdentity() {
        val source = FakeRecordingMatchDataSource(withOperation = true)
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        var changed = 0
        viewModel.bindDataSource(source)
        viewModel.bindIdentityChangedListener { changed++ }
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        val operation = viewModel.uiState.value.snapshot?.recentOperations?.single()
        assertTrue(operation?.undoable == true)
        viewModel.undoIdentityOperation(requireNotNull(operation).id)

        assertEquals(1, source.undoCount)
        assertEquals(1, changed)
        assertEquals("recording.match.operation.undo.completed", viewModel.uiState.value.messageKey)
        assertFalse(viewModel.uiState.value.snapshot?.recentOperations?.single()?.undoable == true)
    }

    @Test
    fun sourceActionsRunThroughDataSourceReloadAndNotifyIdentityChange() {
        val source = FakeRecordingMatchDataSource()
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        var changed = 0
        viewModel.bindDataSource(source)
        viewModel.bindIdentityChangedListener { changed++ }
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        viewModel.verifySource(1L)
        assertEquals(1L, source.verifiedSourceId)
        assertEquals("recording.match.source.verified", viewModel.uiState.value.messageKey)

        viewModel.setPreferredSource(1L)
        assertEquals(1L, source.preferredSourceId)
        assertEquals("recording.match.source.preferred", viewModel.uiState.value.messageKey)

        viewModel.removeUnavailableSource(2L)
        assertEquals(2L, source.removedSourceId)
        assertEquals("recording.match.source.removed", viewModel.uiState.value.messageKey)
        assertEquals(4, source.loadCount)
        assertEquals(3, changed)
    }

    @Test
    fun failedSourceVerificationReloadsHealthButKeepsFailureVisible() {
        val source = FakeRecordingMatchDataSource(verificationSuccess = false)
        val viewModel = RecordingMatchViewModel(Dispatchers.Unconfined)
        viewModel.bindDataSource(source)
        viewModel.open(7L, AppLanguage.MODE_CHINESE)

        viewModel.verifySource(1L)

        assertEquals(1L, source.verifiedSourceId)
        assertEquals(2, source.loadCount)
        assertEquals("[url] token=[redacted]", viewModel.uiState.value.errorMessage)
        assertEquals("", viewModel.uiState.value.messageKey)
    }

    private class FakeRecordingMatchDataSource(
        private val paged: Boolean = false,
        private val blockMerge: Boolean = false,
        private val withOperation: Boolean = false,
        private val verificationSuccess: Boolean = true
    ) : RecordingMatchDataSource {
        var loadCount = 0
        var markedAlternate = ""
        var merged = ""
        var splitSourceIds: Set<Long> = emptySet()
        var splitOptions = RecordingSplitOptions()
        var undoCount = 0
        var verifiedSourceId = -1L
        var preferredSourceId = -1L
        var removedSourceId = -1L
        var batchRejectedRecordingId = -1L
        var lastLoadedTrackId = TrackIdentity.INVALID_ID
        private var reverted = false
        private var alternate = false
        private var batchRejected = false

        override fun snapshotForLocalTrack(
            localTrackId: Long,
            sourceOffset: Int,
            candidateOffset: Int,
            pageSize: Int
        ): RecordingMatchSnapshot {
            loadCount++
            lastLoadedTrackId = localTrackId
            val firstCandidate = candidate("candidate-7")
            val firstSource = source(1L)
            val pageSources = if (paged && sourceOffset > 0) listOf(source(2L)) else listOf(firstSource)
            val pageCandidates = when {
                alternate || batchRejected -> emptyList()
                paged && candidateOffset > 0 -> listOf(candidate("candidate-8"))
                else -> listOf(firstCandidate)
            }
            return RecordingMatchSnapshot(
                track = Track(
                    localTrackId,
                    "Song",
                    "Artist",
                    "Album",
                    180_000L,
                    Uri.EMPTY,
                    if (localTrackId < 0L) "webdav:9:/song.flac" else "file:$localTrackId"
                ),
                recording = CanonicalRecording(
                    recordingId = 70L,
                    canonicalId = "123e4567-e89b-12d3-a456-426614174000",
                    title = "Song",
                    primaryArtistDisplay = "Artist"
                ),
                activeSource = firstSource,
                sources = pageSources,
                identifiers = emptyList(),
                variants = emptyList(),
                pendingCandidates = pageCandidates,
                alternateVersions = if (alternate) {
                    listOf(firstCandidate.copy(status = IdentityCandidateStatus.ALTERNATE_VERSION, variantType = "LIVE"))
                } else {
                    emptyList()
                },
                sourceTotal = if (paged) 2 else 1,
                candidateTotal = if (alternate || batchRejected) 0 else if (paged) 2 else 1,
                recentOperations = if (withOperation) listOf(
                    IdentityOperation(
                        id = 9L,
                        operationType = "MERGE_RECORDINGS",
                        sourceRecordingId = 71L,
                        targetRecordingId = 70L,
                        createdAt = 1L,
                        revertedAt = if (reverted) 2L else null,
                        undoable = !reverted
                    )
                ) else emptyList()
            )
        }

        override fun confirmCandidate(candidateId: String): IdentityCandidate = candidate(candidateId)

        override fun rejectCandidate(candidateId: String): IdentityCandidate =
            candidate(candidateId).copy(status = IdentityCandidateStatus.REJECTED)

        override fun rejectObviousMismatches(recordingId: Long): Int {
            batchRejectedRecordingId = recordingId
            batchRejected = true
            return 1
        }

        override fun markAsAlternateVersion(candidateId: String, variantType: String): IdentityCandidate {
            markedAlternate = "$candidateId:$variantType"
            alternate = true
            return candidate(candidateId).copy(
                status = IdentityCandidateStatus.ALTERNATE_VERSION,
                variantType = variantType
            )
        }

        override fun requestCandidateRefresh(recordingId: Long) = Unit

        override fun searchMergeCandidates(
            targetRecordingId: Long,
            query: String,
            limit: Int
        ): List<RecordingMergeSearchResult> = listOf(
            RecordingMergeSearchResult(
                recordingId = 71L,
                canonicalId = "223e4567-e89b-12d3-a456-426614174000",
                title = "Song",
                primaryArtistDisplay = "Artist",
                durationMs = 180_000L,
                sourceCount = 1,
                variantTypes = listOf("ORIGINAL")
            )
        )

        override fun previewMerge(sourceRecordingId: Long, targetRecordingId: Long): RecordingMergePreview =
            RecordingMergePreview(
                source = summary(sourceRecordingId, "223e4567-e89b-12d3-a456-426614174000"),
                target = summary(targetRecordingId, "123e4567-e89b-12d3-a456-426614174000"),
                impact = RecordingMergeImpact(1, 3, 4, 4, 2, 1),
                warnings = if (blockMerge) {
                    listOf(RecordingMergeWarning(RecordingMergeWarningCode.PRIMARY_ARTIST_CONFLICT, true))
                } else {
                    emptyList()
                }
            )

        override fun mergeRecordings(sourceRecordingId: Long, targetRecordingId: Long): CanonicalRecording {
            merged = "$sourceRecordingId:$targetRecordingId"
            return summary(targetRecordingId, "123e4567-e89b-12d3-a456-426614174000").recording
        }

        override fun sourcesForSplit(recordingId: Long): List<TrackSourceMapping> = listOf(source(1L), source(2L))

        override fun previewSplit(recordingId: Long, sourceIds: Set<Long>): RecordingSplitPreview =
            RecordingSplitPreview(
                original = summary(recordingId, "123e4567-e89b-12d3-a456-426614174000"),
                selectedSources = sourcesForSplit(recordingId).filter { it.sourceId in sourceIds },
                remainingSourceCount = 1,
                impact = RecordingMergeImpact(1, 2, 3, 3, 1, sourceIds.size)
            )

        override fun splitSources(
            sourceIds: Set<Long>,
            options: RecordingSplitOptions
        ): CanonicalRecording {
            splitSourceIds = sourceIds
            splitOptions = options
            return CanonicalRecording(
                recordingId = 72L,
                canonicalId = "323e4567-e89b-12d3-a456-426614174000",
                title = "Song",
                primaryArtistDisplay = "Artist"
            )
        }

        override fun undoIdentityOperation(operationId: Long): IdentityOperation {
            undoCount++
            reverted = true
            return IdentityOperation(
                id = operationId,
                operationType = "MERGE_RECORDINGS",
                sourceRecordingId = 71L,
                targetRecordingId = 70L,
                createdAt = 1L,
                revertedAt = 2L,
                undoable = false
            )
        }

        override suspend fun verifySource(sourceId: Long): RecordingSourceVerification {
            verifiedSourceId = sourceId
            return RecordingSourceVerification(
                success = verificationSuccess,
                codec = if (verificationSuccess) "flac" else "",
                bitrateKbps = if (verificationSuccess) 3200 else 0,
                failureReason = if (verificationSuccess) "" else "https://secret.example/audio token=abc"
            )
        }

        override fun setPreferredSource(sourceId: Long) {
            preferredSourceId = sourceId
        }

        override fun removeUnavailableSource(sourceId: Long) {
            removedSourceId = sourceId
        }

        private fun summary(recordingId: Long, canonicalId: String) = RecordingMergeSummary(
            recording = CanonicalRecording(
                recordingId = recordingId,
                canonicalId = canonicalId,
                title = "Song",
                primaryArtistDisplay = "Artist"
            ),
            sources = listOf(source(recordingId)),
            identifiers = emptyList(),
            variants = emptyList()
        )

        private fun source(id: Long) = TrackSourceMapping(
            sourceId = id,
            recordingId = 70L,
            canonicalId = "123e4567-e89b-12d3-a456-426614174000",
            provider = if (id == 1L) "local" else "webdav",
            providerTrackId = "source-$id",
            playable = true,
            matchStatus = IdentityMatchStatus.CONFIRMED
        )

        private fun candidate(id: String) = IdentityCandidate(
            candidateId = id,
            targetType = IdentityTargetType.RECORDING,
            targetId = 70L,
            provider = "netease",
            providerItemId = id,
            title = "Song",
            artist = "Artist",
            score = 0.9,
            status = IdentityCandidateStatus.PENDING
        )
    }
}
