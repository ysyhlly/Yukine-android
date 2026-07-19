package app.yukine.data

import app.yukine.data.room.GlobalDedupCandidateRow
import app.yukine.data.room.YukineDatabase
import app.yukine.streaming.RecordingRelationship
import org.json.JSONObject

data class DuplicateCandidate(
    val leftRecordingId: Long,
    val rightRecordingId: Long,
    val leftTitle: String,
    val leftArtist: String,
    val rightTitle: String,
    val rightArtist: String,
    val score: Double,
    val margin: Double,
    val relationType: String,
    val evidenceJson: String,
    val updatedAt: Long,
    val batchEligible: Boolean
)

data class DuplicateCandidatePage(
    val items: List<DuplicateCandidate>,
    val total: Int,
    val offset: Int,
    val reviewRequired: Int
)

data class DuplicateBatchConfirmResult(
    val confirmed: Int,
    val skipped: Int,
    val failed: Int
)

class DuplicateCandidateRepository(private val database: YukineDatabase) {
    private val dao = database.musicIdentityDao()

    fun page(limit: Int = 50, offset: Int = 0): DuplicateCandidatePage {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        return DuplicateCandidatePage(
            items = dao.globalDedupCandidates(safeLimit, safeOffset).map(::candidate),
            total = dao.globalDedupCandidateCount(),
            offset = safeOffset,
            reviewRequired = dao.dedupRollbackReviewRequiredCount()
        )
    }

    fun confirm(leftRecordingId: Long, rightRecordingId: Long): DuplicateBatchConfirmResult =
        confirmRows(
            listOfNotNull(
                dao.globalDedupCandidate(
                    minOf(leftRecordingId, rightRecordingId),
                    maxOf(leftRecordingId, rightRecordingId)
                )?.let(::candidate)
            )
        )

    fun confirmHighConfidence(limit: Int = 50): DuplicateBatchConfirmResult {
        val selected = ArrayList<DuplicateCandidate>()
        val usedRecordings = HashSet<Long>()
        page(limit = limit.coerceIn(1, 100)).items
            .asSequence()
            .filter(DuplicateCandidate::batchEligible)
            .forEach { candidate ->
                if (candidate.leftRecordingId !in usedRecordings &&
                    candidate.rightRecordingId !in usedRecordings
                ) {
                    selected += candidate
                    usedRecordings += candidate.leftRecordingId
                    usedRecordings += candidate.rightRecordingId
                }
            }
        return confirmRows(selected)
    }

    private fun confirmRows(candidates: List<DuplicateCandidate>): DuplicateBatchConfirmResult {
        var confirmed = 0
        var skipped = 0
        var failed = 0
        candidates.forEach { candidate ->
            runCatching {
                SourceIdentityIngestor(database).ingestRecordings(
                    listOf(candidate.leftRecordingId, candidate.rightRecordingId)
                )
                val merged = IdentityMutationGate.withLock {
                    if (dao.recording(candidate.leftRecordingId) == null ||
                        dao.recording(candidate.rightRecordingId) == null
                    ) {
                        return@withLock false
                    }
                    val refreshed = dao.globalDedupCandidate(
                        candidate.leftRecordingId,
                        candidate.rightRecordingId
                    )?.let(::candidate)
                    if (refreshed?.batchEligible != true) {
                        return@withLock false
                    }
                    RoomRecordingIdentityRepository(database).mergeRecordingsWithManualDecision(
                        sourceRecordingId = maxOf(candidate.leftRecordingId, candidate.rightRecordingId),
                        targetRecordingId = minOf(candidate.leftRecordingId, candidate.rightRecordingId)
                    )
                    true
                }
                if (merged) confirmed++ else skipped++
            }.onFailure {
                failed++
            }
        }
        return DuplicateBatchConfirmResult(confirmed, skipped, failed)
    }

    private fun candidate(row: GlobalDedupCandidateRow): DuplicateCandidate {
        val margin = row.sameRecordingProbability - row.runnerUpProbability
        val noHardConflict = runCatching {
            val conflicts = JSONObject(row.evidenceJson).optJSONArray("hardConflicts")
            conflicts == null || conflicts.length() == 0
        }.getOrDefault(false)
        val eligible = row.relationType == RecordingRelationship.SAME_RECORDING.name &&
            row.sameRecordingProbability >= 0.95 &&
            margin >= 0.08 &&
            noHardConflict
        return DuplicateCandidate(
            leftRecordingId = row.leftRecordingId,
            rightRecordingId = row.rightRecordingId,
            leftTitle = row.leftTitle,
            leftArtist = row.leftArtist,
            rightTitle = row.rightTitle,
            rightArtist = row.rightArtist,
            score = row.sameRecordingProbability,
            margin = margin,
            relationType = row.relationType,
            evidenceJson = row.evidenceJson,
            updatedAt = row.updatedAt,
            batchEligible = eligible
        )
    }
}
