package app.yukine.data

import app.yukine.data.room.RecordingRelationEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingRelationship

internal data class RecordingRelationDraft(
    val leftRecordingId: Long,
    val rightRecordingId: Long,
    val relationship: RecordingRelationship,
    val sameRecordingProbability: Double,
    val sameWorkProbability: Double,
    val confidence: Double,
    val origin: String,
    val algorithmVersion: Int = RecordingMatchEvaluatorV2.SCORE_VERSION,
    val evidenceJson: String = "",
    val locked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Owns pair normalization and manual-over-automatic precedence for canonical recording relations. */
internal class RecordingRelationStore(private val database: YukineDatabase) {
    private val dao get() = database.musicIdentityDao()

    fun relation(firstRecordingId: Long, secondRecordingId: Long): RecordingRelationEntity? {
        val pair = normalizedPair(firstRecordingId, secondRecordingId)
        return dao.recordingRelation(pair.first, pair.second)
    }

    fun upsert(drafts: Collection<RecordingRelationDraft>) {
        if (drafts.isEmpty()) return
        val normalized = drafts.map(::entity)
        val ids = normalized.flatMapTo(linkedSetOf()) {
            listOf(it.leftRecordingId, it.rightRecordingId)
        }.toList()
        val current = dao.recordingRelations(ids).associateBy(::key).toMutableMap()
        val accepted = ArrayList<RecordingRelationEntity>()
        normalized.forEach { candidate ->
            val relationKey = key(candidate)
            val existing = current[relationKey]
            val selected = select(existing, candidate)
            if (selected != existing) {
                current[relationKey] = selected
                accepted += selected
            }
        }
        if (accepted.isNotEmpty()) dao.upsertRecordingRelations(accepted)
    }

    fun setManualCannotLink(
        firstRecordingId: Long,
        secondRecordingId: Long,
        origin: String,
        evidenceJson: String = "",
        now: Long = System.currentTimeMillis()
    ) = upsert(
        listOf(
            RecordingRelationDraft(
                leftRecordingId = firstRecordingId,
                rightRecordingId = secondRecordingId,
                relationship = RecordingRelationship.CANNOT_LINK,
                sameRecordingProbability = 0.0,
                sameWorkProbability = 0.0,
                confidence = 1.0,
                origin = origin,
                algorithmVersion = RecordingMatchEvaluatorV2.SCORE_VERSION,
                evidenceJson = evidenceJson,
                locked = true,
                updatedAt = now
            )
        )
    )

    /** Re-anchors all external constraints before the source recording is deleted. */
    fun rewriteAfterMerge(sourceRecordingId: Long, targetRecordingId: Long) {
        val sourceRelations = dao.recordingRelations(sourceRecordingId)
        if (sourceRelations.isEmpty()) return
        dao.deleteRecordingRelations(sourceRecordingId)
        sourceRelations.forEach { sourceRelation ->
            val otherId = sourceRelation.other(sourceRecordingId)
            if (otherId == targetRecordingId) return@forEach
            val pair = normalizedPair(targetRecordingId, otherId)
            val moved = sourceRelation.copy(
                leftRecordingId = pair.first,
                rightRecordingId = pair.second,
                updatedAt = maxOf(sourceRelation.updatedAt, System.currentTimeMillis())
            )
            val existing = dao.recordingRelation(pair.first, pair.second)
            dao.upsertRecordingRelation(selectForMerge(existing, moved))
        }
    }

    private fun entity(draft: RecordingRelationDraft): RecordingRelationEntity {
        val pair = normalizedPair(draft.leftRecordingId, draft.rightRecordingId)
        val now = draft.updatedAt.coerceAtLeast(0L)
        return RecordingRelationEntity(
            leftRecordingId = pair.first,
            rightRecordingId = pair.second,
            relationType = draft.relationship.name,
            sameRecordingProbability = draft.sameRecordingProbability.coerceIn(0.0, 1.0),
            sameWorkProbability = draft.sameWorkProbability.coerceIn(0.0, 1.0),
            confidence = draft.confidence.coerceIn(0.0, 1.0),
            origin = draft.origin.trim().ifBlank { "ALGORITHM" },
            algorithmVersion = draft.algorithmVersion.coerceAtLeast(0),
            evidenceJson = draft.evidenceJson,
            locked = draft.locked,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun select(
        existing: RecordingRelationEntity?,
        candidate: RecordingRelationEntity
    ): RecordingRelationEntity {
        if (existing == null) return candidate
        if (existing.locked && !candidate.locked) return existing
        if (candidate.locked && !existing.locked) return candidate.copy(createdAt = existing.createdAt)
        if (existing.locked && candidate.locked) {
            return if (relationPriority(candidate.relationType) > relationPriority(existing.relationType)) {
                candidate.copy(createdAt = existing.createdAt)
            } else {
                existing
            }
        }
        return if (candidate.algorithmVersion > existing.algorithmVersion ||
            candidate.algorithmVersion == existing.algorithmVersion && candidate.updatedAt >= existing.updatedAt
        ) {
            candidate.copy(createdAt = existing.createdAt)
        } else {
            existing
        }
    }

    private fun selectForMerge(
        existing: RecordingRelationEntity?,
        moved: RecordingRelationEntity
    ): RecordingRelationEntity {
        if (existing == null) return moved
        val selected = when {
            existing.locked && !moved.locked -> existing
            moved.locked && !existing.locked -> moved
            relationPriority(moved.relationType) > relationPriority(existing.relationType) -> moved
            relationPriority(moved.relationType) < relationPriority(existing.relationType) -> existing
            moved.confidence > existing.confidence -> moved
            else -> existing
        }
        return selected.copy(
            createdAt = minOf(existing.createdAt, moved.createdAt),
            updatedAt = maxOf(existing.updatedAt, moved.updatedAt),
            sameRecordingProbability = maxOf(existing.sameRecordingProbability, moved.sameRecordingProbability),
            sameWorkProbability = maxOf(existing.sameWorkProbability, moved.sameWorkProbability)
        )
    }

    private fun normalizedPair(firstRecordingId: Long, secondRecordingId: Long): Pair<Long, Long> {
        require(firstRecordingId > 0L && secondRecordingId > 0L) { "Recording IDs must be positive" }
        require(firstRecordingId != secondRecordingId) { "A recording cannot relate to itself" }
        return minOf(firstRecordingId, secondRecordingId) to maxOf(firstRecordingId, secondRecordingId)
    }

    private fun key(value: RecordingRelationEntity) = value.leftRecordingId to value.rightRecordingId

    private fun RecordingRelationEntity.other(recordingId: Long): Long =
        if (leftRecordingId == recordingId) rightRecordingId else leftRecordingId

    private fun relationPriority(value: String): Int = when (value) {
        RecordingRelationship.CANNOT_LINK.name -> 4
        RecordingRelationship.SAME_WORK_DIFFERENT_VERSION.name -> 3
        RecordingRelationship.SAME_RECORDING.name -> 2
        else -> 1
    }
}
