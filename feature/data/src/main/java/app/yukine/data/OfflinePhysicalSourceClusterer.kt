package app.yukine.data

import android.util.Log
import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.SourceMatchFeatureEntity
import app.yukine.data.room.SourceRecordingCandidateEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.MusicIdentityDiagnostics
import app.yukine.fingerprint.AudioMatchRefiner
import app.yukine.fingerprint.ChromaprintAlignment
import app.yukine.fingerprint.ChromaprintSegmentAligner
import app.yukine.fingerprint.TraditionalAudioEvidence
import app.yukine.streaming.MatchEvaluation
import app.yukine.streaming.RecordingMatchFeatureExtractor
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingRelationship
import app.yukine.streaming.StreamingTrackMatchPolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single offline canonical-ingestion path for every source that has entered the user's library.
 *
 * Candidate generation uses a persisted Top20 metadata snapshot. Library-resident sources and
 * CONFIRMED provider sources become identity anchors. Search Top1 and other unverified candidates
 * without a local track remain outside the merge index and therefore cannot cause an auto merge.
 * Every anchor in both recordings must pass the V2 evaluator, so a bridge match cannot
 * transitively pull a conflicting Live/Remix/Cover source into the group.
 */
class SourceIdentityIngestor @JvmOverloads constructor(
    private val database: YukineDatabase,
    private val diagnostics: MusicIdentityDiagnostics = MusicIdentityDiagnostics.process()
) {
    private val dao = database.musicIdentityDao()
    private val recordings = RoomRecordingIdentityRepository(database)
    private val relations = RecordingRelationStore(database)
    private val candidateGenerator = SourceRecordingCandidateGenerator()

    /** Explicit/background backfill for existing databases; never belongs to the library-open path. */
    fun ingestAllConfirmedSources(): Int = ingestLocalTracks(
        dao.identityAnchorSources().mapNotNull(TrackSourceMappingEntity::localTrackId)
    )

    /**
     * One-time/backward-compatible ingestion for databases created before persisted clustering.
     * Fully processed sources are excluded in SQL, keeping ordinary app startup off the full-library
     * scoring path.
     */
    fun ingestPendingConfirmedSources(): Int = ingestLocalTracks(
        dao.pendingLibraryIdentityTrackIds(
            SourceMatchFeaturePolicy.ALGORITHM_VERSION,
            SourceRecordingCandidateGenerator.ALGORITHM_VERSION
        )
    )

    fun ingestLocalTracks(localTrackIds: List<Long>): Int = synchronized(INGESTION_LOCK) {
        ingestLocalTracksLocked(localTrackIds)
    }

    private fun ingestLocalTracksLocked(localTrackIds: List<Long>): Int {
        val totalStartedAt = diagnostics.startNanos()
        val pendingTrackIds = localTrackIds.asSequence()
            .filter { it != 0L }
            .distinct()
            .toList()
        if (pendingTrackIds.isEmpty()) {
            diagnostics.recordElapsed(OPERATION, MusicIdentityDiagnostics.Stage.TOTAL, totalStartedAt)
            return 0
        }

        val snapshotStartedAt = diagnostics.startNanos()
        val featureSources = dao.matchFeatureSources()
        val identityAnchors = featureSources.filter { source ->
            source.matchStatus == "CONFIRMED" || source.localTrackId != null
        }
        val audioEvidenceBySourceId = featureSources.mapNotNull(TrackSourceMappingEntity::sourceId)
            .distinct()
            .chunked(SQLITE_IN_BATCH_SIZE)
            .flatMap(dao::audioFeatures)
            .associate { feature -> feature.sourceId to StoredAudioEvidenceCodec.decode(feature) }
        val storedFeatures = dao.sourceMatchFeatures().associateBy(SourceMatchFeatureEntity::sourceId)
        val storedCandidates = dao.sourceRecordingCandidates()
        val pendingRecordingIds = pendingTrackIds
            .chunked(SQLITE_IN_BATCH_SIZE)
            .flatMap(dao::sourcesForLocalTracks)
            .map(TrackSourceMappingEntity::recordingId)
            .distinct()
        diagnostics.recordElapsed(
            OPERATION,
            MusicIdentityDiagnostics.Stage.SNAPSHOT_LOAD,
            snapshotStartedAt,
            featureSources.size.toLong() + audioEvidenceBySourceId.size.toLong() +
                storedFeatures.size.toLong() + storedCandidates.size.toLong() +
                pendingRecordingIds.size.toLong()
        )
        val normalizationStartedAt = diagnostics.startNanos()
        val refreshedFeatures = ArrayList<SourceMatchFeatureEntity>()
        val effectiveFeatures = HashMap<Long, SourceMatchFeatureEntity>(featureSources.size)
        featureSources.forEach { source ->
            val sourceId = source.sourceId ?: return@forEach
            val signature = SourceMatchFeaturePolicy.metadataSignature(source)
            val existing = storedFeatures[sourceId]
            val feature = if (
                existing?.algorithmVersion == SourceMatchFeaturePolicy.ALGORITHM_VERSION &&
                existing.metadataSignature == signature
            ) {
                existing
            } else {
                SourceMatchFeaturePolicy.build(source, System.currentTimeMillis(), signature)
                    ?.also(refreshedFeatures::add)
                    ?: return@forEach
            }
            effectiveFeatures[sourceId] = feature
        }
        if (refreshedFeatures.isNotEmpty()) {
            dao.upsertSourceMatchFeatures(refreshedFeatures)
        }
        diagnostics.recordElapsed(
            OPERATION,
            MusicIdentityDiagnostics.Stage.NORMALIZATION,
            normalizationStartedAt,
            refreshedFeatures.size.toLong()
        )
        val candidateGenerationStartedAt = diagnostics.startNanos()
        val snapshotSignature = candidateGenerator.snapshotSignature(featureSources, effectiveFeatures)
        val reusableCandidateSnapshot = refreshedFeatures.isEmpty() &&
            effectiveFeatures.isNotEmpty() &&
            effectiveFeatures.values.all { feature ->
                feature.candidateAlgorithmVersion == SourceRecordingCandidateGenerator.ALGORITHM_VERSION &&
                    feature.candidateSnapshotSignature == snapshotSignature
            } &&
            storedCandidates.all { candidate ->
                candidate.algorithmVersion == SourceRecordingCandidateGenerator.ALGORITHM_VERSION
            }
        val candidateRows: List<SourceRecordingCandidateEntity>
        val coarseComparisonCount: Int
        if (reusableCandidateSnapshot) {
            candidateRows = storedCandidates
            coarseComparisonCount = 0
        } else {
            val generated = replaceCandidateSnapshot(
                featureSources,
                effectiveFeatures,
                System.currentTimeMillis()
            )
            candidateRows = generated.candidates
            coarseComparisonCount = generated.coarseComparisonCount
        }
        diagnostics.recordElapsed(
            OPERATION,
            MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION,
            candidateGenerationStartedAt,
            coarseComparisonCount.toLong()
        )
        val candidateIndex = SourceIdentityCandidateIndex(
            sources = identityAnchors,
            featuresBySourceId = effectiveFeatures,
            precomputedCandidatesBySourceId = candidateRows
                .groupBy(SourceRecordingCandidateEntity::sourceId)
                .mapValues { (_, rows) -> rows.map(SourceRecordingCandidateEntity::candidateRecordingId) }
        )
        val redirects = HashMap<Long, Long>()
        val processed = HashSet<Long>()
        var mergedCount = 0
        pendingRecordingIds.forEach { initialRecordingId ->
            var currentRecordingId = resolveRedirect(initialRecordingId, redirects)
            if (currentRecordingId in processed) return@forEach
            while (true) {
                val currentRecording = dao.recording(currentRecordingId) ?: break
                val currentSources = candidateIndex.sources(currentRecordingId)
                if (!eligibleGroup(currentRecordingId, currentSources)) break

                val candidateGenerationStartedAt = diagnostics.startNanos()
                val candidateRecordingIds = candidateIndex.candidateRecordingIds(currentRecordingId)
                diagnostics.recordElapsed(
                    OPERATION,
                    MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION,
                    candidateGenerationStartedAt,
                    candidateRecordingIds.size.toLong()
                )
                val scoringStartedAt = diagnostics.startNanos()
                val evaluatedGroups = candidateRecordingIds
                    .asSequence()
                    .mapNotNull { candidateRecordingId ->
                        val candidateSources = candidateIndex.sources(candidateRecordingId)
                        val candidateRecording = dao.recording(candidateRecordingId) ?: return@mapNotNull null
                        if (!eligibleGroup(candidateRecordingId, candidateSources)) return@mapNotNull null
                        val manual = manualRelationship(
                            currentRecordingId,
                            currentSources,
                            candidateRecordingId,
                            candidateSources
                        )
                        val evaluation = manual?.toGroupEvaluation()
                            ?: completeLinkEvaluation(
                                currentRecording,
                                currentSources,
                                candidateRecording,
                                candidateSources,
                                audioEvidenceBySourceId
                            )
                            ?: return@mapNotNull null
                        EvaluatedRecording(candidateRecordingId, evaluation, manual != null)
                    }
                    .toList()
                relations.upsert(
                    evaluatedGroups.map { candidate ->
                        RecordingRelationDraft(
                            leftRecordingId = currentRecordingId,
                            rightRecordingId = candidate.recordingId,
                            relationship = candidate.evaluation.relationship,
                            sameRecordingProbability = candidate.evaluation.sameRecordingProbability,
                            sameWorkProbability = candidate.evaluation.sameWorkProbability,
                            confidence = candidate.evaluation.confidence,
                            origin = if (candidate.locked) {
                                "USER_CANDIDATE_DECISION"
                            } else {
                                "MATCH_EVALUATOR_V${candidate.evaluation.scoreVersion}"
                            },
                            algorithmVersion = candidate.evaluation.scoreVersion,
                            evidenceJson = candidate.evaluation.evidenceJson,
                            locked = candidate.locked
                        )
                    }
                )
                val candidateGroups = evaluatedGroups
                    .asSequence()
                    .filter { it.evaluation.relationship == RecordingRelationship.SAME_RECORDING }
                    .map { RankedRecording(it.recordingId, it.evaluation.sameRecordingProbability) }
                    .sortedWith(
                        compareByDescending<RankedRecording>(RankedRecording::score)
                            .thenBy(RankedRecording::recordingId)
                    )
                    .toList()
                diagnostics.recordElapsed(
                    OPERATION,
                    MusicIdentityDiagnostics.Stage.SCORING,
                    scoringStartedAt,
                    candidateRecordingIds.size.toLong()
                )

                val best = candidateGroups.firstOrNull() ?: break
                if (best.score < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE) {
                    break
                }
                val ambiguousCandidates = candidateGroups.takeWhile { candidate ->
                    best.score - candidate.score < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_MARGIN
                }
                if (ambiguousCandidates.size > 1 && !sameRecordingClique(
                        ambiguousCandidates,
                        candidateIndex,
                        audioEvidenceBySourceId
                    )
                ) break

                val sourceId = maxOf(currentRecordingId, best.recordingId)
                val targetId = minOf(currentRecordingId, best.recordingId)
                val commitStartedAt = diagnostics.startNanos()
                try {
                    recordings.mergeRecordings(sourceId, targetId)
                    candidateIndex.merge(sourceId, targetId)
                    redirects[sourceId] = targetId
                    currentRecordingId = targetId
                    mergedCount++
                } catch (_: IllegalArgumentException) {
                    // The repository is the final authority for artist/identifier/version constraints.
                    break
                } finally {
                    diagnostics.recordElapsed(
                        OPERATION,
                        MusicIdentityDiagnostics.Stage.DATABASE_COMMIT,
                        commitStartedAt,
                        1L
                    )
                }
            }
            processed += currentRecordingId
        }
        if (mergedCount > 0) {
            val refreshedSources = dao.matchFeatureSources()
            val refreshStartedAt = diagnostics.startNanos()
            val generated = replaceCandidateSnapshot(
                refreshedSources,
                effectiveFeatures,
                System.currentTimeMillis()
            )
            diagnostics.recordElapsed(
                OPERATION,
                MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION,
                refreshStartedAt,
                generated.coarseComparisonCount.toLong()
            )
        }
        diagnostics.recordElapsed(
            OPERATION,
            MusicIdentityDiagnostics.Stage.TOTAL,
            totalStartedAt,
            pendingTrackIds.size.toLong()
        )
        logDiagnostics()
        return mergedCount
    }

    /**
     * A near-tied runner-up is not ambiguous when every near-tied recording is mutually the same
     * recording. This preserves the margin for competing matches while allowing A/B/C duplicate
     * copies to collapse without depending on ingestion order.
     */
    private fun sameRecordingClique(
        candidates: List<RankedRecording>,
        candidateIndex: SourceIdentityCandidateIndex,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>
    ): Boolean {
        candidates.indices.forEach { leftIndex ->
            val leftId = candidates[leftIndex].recordingId
            val leftRecording = dao.recording(leftId) ?: return false
            val leftSources = candidateIndex.sources(leftId)
            for (rightIndex in leftIndex + 1 until candidates.size) {
                val rightId = candidates[rightIndex].recordingId
                val rightRecording = dao.recording(rightId) ?: return false
                val rightSources = candidateIndex.sources(rightId)
                if (manualRelationship(leftId, leftSources, rightId, rightSources) != null) {
                    return false
                }
                val evaluation = completeLinkEvaluation(
                    leftRecording,
                    leftSources,
                    rightRecording,
                    rightSources,
                    audioEvidenceBySourceId
                ) ?: return false
                if (evaluation.relationship != RecordingRelationship.SAME_RECORDING ||
                    evaluation.sameRecordingProbability <
                    RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE
                ) return false
            }
        }
        return true
    }

    private fun replaceCandidateSnapshot(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        generatedAt: Long
    ): SourceCandidateGenerationResult {
        val generated = candidateGenerator.generate(sources, featuresBySourceId, generatedAt)
        val sourceIds = sources.mapNotNull(TrackSourceMappingEntity::sourceId).distinct()
        var validCandidates = generated.candidates
        database.runInTransaction {
            val existingRecordingIds = generated.candidates
                .map(SourceRecordingCandidateEntity::candidateRecordingId)
                .distinct()
                .chunked(SQLITE_IN_BATCH_SIZE)
                .flatMap(dao::existingRecordingIds)
                .toHashSet()
            validCandidates = generated.candidates.filter { candidate ->
                candidate.candidateRecordingId in existingRecordingIds
            }
            dao.clearSourceRecordingCandidates()
            validCandidates.chunked(CANDIDATE_WRITE_BATCH_SIZE).forEach { candidates ->
                dao.upsertSourceRecordingCandidates(candidates)
            }
            sourceIds.chunked(SQLITE_IN_BATCH_SIZE).forEach { ids ->
                dao.markSourceCandidateGeneration(
                    sourceIds = ids,
                    algorithmVersion = SourceRecordingCandidateGenerator.ALGORITHM_VERSION,
                    snapshotSignature = generated.snapshotSignature,
                    generatedAt = generatedAt
                )
            }
        }
        return generated.copy(candidates = validCandidates)
    }

    private fun eligibleGroup(
        recordingId: Long,
        sources: List<TrackSourceMappingEntity>
    ): Boolean = sources.isNotEmpty() && dao.activeSplitOperationCount(recordingId) == 0

    private fun completeLinkEvaluation(
        leftRecording: CanonicalRecordingEntity,
        leftSources: List<TrackSourceMappingEntity>,
        rightRecording: CanonicalRecordingEntity,
        rightSources: List<TrackSourceMappingEntity>,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>
    ): GroupEvaluation? {
        var minimumRecordingProbability = 1.0
        var minimumWorkProbability = 1.0
        var relationship = RecordingRelationship.SAME_RECORDING
        val hardConflicts = linkedSetOf<String>()
        val audioAlignments = JSONArray()
        var scoreVersion = RecordingMatchEvaluatorV2.SCORE_VERSION
        var comparisonCount = 0
        leftSources.forEach { left ->
            rightSources.forEach { right ->
                val pair = evaluatePair(
                    left,
                    right,
                    reference(left, leftRecording),
                    reference(right, rightRecording),
                    audioEvidenceBySourceId
                )
                val evaluation = pair.evaluation
                scoreVersion = maxOf(scoreVersion, evaluation.scoreVersion)
                pair.alignment?.let { alignment ->
                    audioAlignments.put(JSONObject()
                        .put("leftSourceId", left.sourceId)
                        .put("rightSourceId", right.sourceId)
                        .put("similarity", alignment.similarity)
                        .put("matchedSegments", alignment.matchedSegmentCount)
                        .put("inlierRatio", alignment.inlierRatio)
                        .put("offsetMs", alignment.offsetMs)
                        .put("speedRatio", alignment.speedRatio)
                        .put("coverageMs", alignment.continuousCoverageMs)
                        .put("structuralJump", alignment.structuralJump)
                        .put("exactPcm", alignment.exactPcm))
                }
                comparisonCount++
                minimumRecordingProbability = minOf(
                    minimumRecordingProbability,
                    evaluation.sameRecordingProbability
                )
                minimumWorkProbability = minOf(minimumWorkProbability, evaluation.sameWorkProbability)
                hardConflicts += evaluation.hardConflicts.map { it.name }
                relationship = combineRelationship(relationship, evaluation.relationship)
            }
        }
        if (comparisonCount == 0) return null
        val confidence = when (relationship) {
            RecordingRelationship.SAME_RECORDING -> minimumRecordingProbability
            RecordingRelationship.SAME_WORK_DIFFERENT_VERSION -> minimumWorkProbability
            RecordingRelationship.CANNOT_LINK -> 1.0 - minimumRecordingProbability
            RecordingRelationship.UNKNOWN -> maxOf(minimumRecordingProbability, minimumWorkProbability)
        }.coerceIn(0.0, 1.0)
        return GroupEvaluation(
            sameRecordingProbability = minimumRecordingProbability,
            sameWorkProbability = minimumWorkProbability,
            relationship = relationship,
            confidence = confidence,
            scoreVersion = scoreVersion,
            evidenceJson = JSONObject()
                .put("scoreVersion", scoreVersion)
                .put("completeLinkComparisons", comparisonCount)
                .put("sameRecording", minimumRecordingProbability)
                .put("sameWork", minimumWorkProbability)
                .put("relationship", relationship.name)
                .put("hardConflicts", JSONArray(hardConflicts.toList()))
                .put("audioAlignments", audioAlignments)
                .toString()
        )
    }

    private fun evaluatePair(
        left: TrackSourceMappingEntity,
        right: TrackSourceMappingEntity,
        leftReference: StreamingTrackMatchPolicy.Reference,
        rightReference: StreamingTrackMatchPolicy.Reference,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>
    ): PairEvaluation {
        val metadata = RecordingMatchEvaluatorV2.evaluate(leftReference, rightReference)
        val leftEvidence = left.sourceId?.let(audioEvidenceBySourceId::get)
        val rightEvidence = right.sourceId?.let(audioEvidenceBySourceId::get)
        if (leftEvidence == null || rightEvidence == null ||
            leftEvidence.pcmHash.isBlank() && leftEvidence.segments.isEmpty() ||
            rightEvidence.pcmHash.isBlank() && rightEvidence.segments.isEmpty()
        ) {
            return PairEvaluation(metadata, null)
        }
        val alignment = ChromaprintSegmentAligner.align(leftEvidence, rightEvidence)
        return PairEvaluation(AudioMatchRefiner.refine(metadata, alignment), alignment)
    }

    private fun manualRelationship(
        leftRecordingId: Long,
        leftSources: List<TrackSourceMappingEntity>,
        rightRecordingId: Long,
        rightSources: List<TrackSourceMappingEntity>
    ): ManualRelationship? {
        val statuses = candidateDecisionStatuses(leftRecordingId, rightSources) +
            candidateDecisionStatuses(rightRecordingId, leftSources)
        return when {
            "REJECTED" in statuses -> ManualRelationship(RecordingRelationship.CANNOT_LINK, "REJECTED")
            "ALTERNATE_VERSION" in statuses -> ManualRelationship(
                RecordingRelationship.SAME_WORK_DIFFERENT_VERSION,
                "ALTERNATE_VERSION"
            )
            else -> null
        }
    }

    private fun candidateDecisionStatuses(
        targetRecordingId: Long,
        sources: List<TrackSourceMappingEntity>
    ): Set<String> = sources.mapNotNullTo(linkedSetOf()) { source ->
        dao.candidate(TARGET_RECORDING, targetRecordingId, source.provider, source.providerTrackId)
            ?.status
            ?.takeIf { it in blockedCandidateStatuses }
    }

    private fun reference(
        source: TrackSourceMappingEntity,
        recording: CanonicalRecordingEntity
    ) = StreamingTrackMatchPolicy.Reference(
        title = source.title,
        artist = source.artist,
        album = source.album,
        durationMs = source.durationMs.takeIf { it > 0L },
        isrc = recording.isrc,
        recordingMbid = recording.musicBrainzRecordingId,
        workMbid = recording.musicBrainzWorkId,
        fingerprint = recording.acoustId
    )

    private fun combineRelationship(
        current: RecordingRelationship,
        incoming: RecordingRelationship
    ): RecordingRelationship = when {
        current == RecordingRelationship.CANNOT_LINK || incoming == RecordingRelationship.CANNOT_LINK ->
            RecordingRelationship.CANNOT_LINK
        current == RecordingRelationship.SAME_WORK_DIFFERENT_VERSION ||
            incoming == RecordingRelationship.SAME_WORK_DIFFERENT_VERSION ->
            RecordingRelationship.SAME_WORK_DIFFERENT_VERSION
        current == RecordingRelationship.UNKNOWN || incoming == RecordingRelationship.UNKNOWN ->
            RecordingRelationship.UNKNOWN
        else -> RecordingRelationship.SAME_RECORDING
    }

    private fun resolveRedirect(recordingId: Long, redirects: Map<Long, Long>): Long {
        var resolved = recordingId
        val visited = HashSet<Long>()
        while (visited.add(resolved)) {
            resolved = redirects[resolved] ?: return resolved
        }
        return resolved
    }

    private fun logDiagnostics() {
        runCatching {
            Log.d(TAG, "$OPERATION ${diagnostics.snapshot(OPERATION).compactSummary()}")
        }
    }

    private data class RankedRecording(
        val recordingId: Long,
        val score: Double
    )

    private data class EvaluatedRecording(
        val recordingId: Long,
        val evaluation: GroupEvaluation,
        val locked: Boolean
    )

    private data class GroupEvaluation(
        val sameRecordingProbability: Double,
        val sameWorkProbability: Double,
        val relationship: RecordingRelationship,
        val confidence: Double,
        val scoreVersion: Int,
        val evidenceJson: String
    )

    private data class PairEvaluation(
        val evaluation: MatchEvaluation,
        val alignment: ChromaprintAlignment?
    )

    private data class ManualRelationship(
        val relationship: RecordingRelationship,
        val status: String
    ) {
        fun toGroupEvaluation() = GroupEvaluation(
            sameRecordingProbability = 0.0,
            sameWorkProbability = if (relationship == RecordingRelationship.SAME_WORK_DIFFERENT_VERSION) 1.0 else 0.0,
            relationship = relationship,
            confidence = 1.0,
            scoreVersion = RecordingMatchEvaluatorV2.SCORE_VERSION,
            evidenceJson = JSONObject()
                .put("manualDecision", status)
                .put("relationship", relationship.name)
                .toString()
        )
    }

    private companion object {
        val INGESTION_LOCK = Any()
        const val TARGET_RECORDING = "RECORDING"
        const val SQLITE_IN_BATCH_SIZE = 500
        const val CANDIDATE_WRITE_BATCH_SIZE = 1_000
        const val TAG = "IdentityDiagnostics"
        val OPERATION = MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER
        val blockedCandidateStatuses = setOf("REJECTED", "ALTERNATE_VERSION")
    }
}

/** Compatibility facade for callers that still describe the operation as physical clustering. */
class OfflinePhysicalSourceClusterer @JvmOverloads constructor(
    database: YukineDatabase,
    diagnostics: MusicIdentityDiagnostics = MusicIdentityDiagnostics.process()
) {
    private val delegate = SourceIdentityIngestor(database, diagnostics)

    fun clusterTracks(localTrackIds: List<Long>): Int = delegate.ingestLocalTracks(localTrackIds)
}

/**
 * Immutable-metadata candidate index for strict library-source clustering.
 *
 * Production uses persisted Top20 candidates; the exact title/artist/duration bucket remains a
 * compatibility fallback for isolated tests and old callers. Complete-link V2 remains final.
 */
internal class SourceIdentityCandidateIndex(
    sources: List<TrackSourceMappingEntity>,
    private val featuresBySourceId: Map<Long, SourceMatchFeatureEntity> = emptyMap(),
    precomputedCandidatesBySourceId: Map<Long, List<Long>>? = null,
    private val sourceEligibility: (TrackSourceMappingEntity) -> Boolean = {
        it.matchStatus == "CONFIRMED" || it.localTrackId != null
    }
) {
    private val sourcesByRecording = HashMap<Long, MutableList<TrackSourceMappingEntity>>()
    private val recordingIdBySourceId = HashMap<Long, Long>()
    private val descriptorsBySourceId = HashMap<Long, SourceCandidateDescriptor>()
    private val recordingIdsByBucket = HashMap<SourceCandidateBucket, MutableSet<Long>>()
    private val bucketsByRecording = HashMap<Long, MutableSet<SourceCandidateBucket>>()
    private val precomputedCandidatesBySourceId = precomputedCandidatesBySourceId?.mapValuesTo(
        HashMap()
    ) { (_, recordingIds) -> recordingIds.distinct().toMutableList() }

    init {
        sources.asSequence()
            .filter(::isIdentityAnchor)
            .forEach { source ->
                sourcesByRecording.getOrPut(source.recordingId) { ArrayList() } += source
                source.sourceId?.let { recordingIdBySourceId[it] = source.recordingId }
                val descriptor = descriptor(source)
                descriptor?.bucket()?.let { bucket ->
                    source.sourceId?.let { descriptorsBySourceId[it] = descriptor }
                    recordingIdsByBucket.getOrPut(bucket) { linkedSetOf() } += source.recordingId
                    bucketsByRecording.getOrPut(source.recordingId) { linkedSetOf() } += bucket
                }
            }
    }

    fun sources(recordingId: Long): List<TrackSourceMappingEntity> =
        sourcesByRecording[recordingId].orEmpty()

    fun candidateRecordingIds(recordingId: Long): Set<Long> {
        val result = linkedSetOf<Long>()
        precomputedCandidatesBySourceId?.let { precomputed ->
            val rankedLists = sources(recordingId).mapNotNull { source ->
                source.sourceId?.let(precomputed::get)
            }
            val maximumDepth = rankedLists.maxOfOrNull { candidates -> candidates.size } ?: 0
            for (rank in 0 until maximumDepth) {
                rankedLists.forEach { candidates ->
                    val candidateId = candidates.getOrNull(rank) ?: return@forEach
                    if (candidateId != recordingId && sourcesByRecording.containsKey(candidateId)) {
                        result += candidateId
                    }
                    if (result.size >= MAX_PRECOMPUTED_CANDIDATES) return result
                }
            }
            return result
        }
        sources(recordingId).forEach { source ->
            val descriptor = source.sourceId?.let(descriptorsBySourceId::get)
                ?: descriptor(source)
                ?: return@forEach
            for (offset in -1L..1L) {
                val key = descriptor.bucket(offset)
                recordingIdsByBucket[key].orEmpty().forEach { candidateId ->
                    if (candidateId != recordingId) result += candidateId
                }
            }
        }
        return result
    }

    fun merge(sourceRecordingId: Long, targetRecordingId: Long) {
        if (sourceRecordingId == targetRecordingId) return
        val movedSources = sourcesByRecording.remove(sourceRecordingId).orEmpty()
        if (movedSources.isNotEmpty()) {
            sourcesByRecording.getOrPut(targetRecordingId) { ArrayList() }.addAll(movedSources)
            movedSources.mapNotNull(TrackSourceMappingEntity::sourceId).forEach { sourceId ->
                recordingIdBySourceId[sourceId] = targetRecordingId
            }
        }
        val movedBuckets = bucketsByRecording.remove(sourceRecordingId).orEmpty()
        val targetBuckets = bucketsByRecording.getOrPut(targetRecordingId) { linkedSetOf() }
        movedBuckets.forEach { bucket ->
            recordingIdsByBucket[bucket]?.let { recordingIds ->
                recordingIds -= sourceRecordingId
                recordingIds += targetRecordingId
            }
            targetBuckets += bucket
        }
        precomputedCandidatesBySourceId?.forEach { (sourceId, recordingIds) ->
            recordingIds.replaceAll { candidateId ->
                if (candidateId == sourceRecordingId) targetRecordingId else candidateId
            }
            recordingIdBySourceId[sourceId]?.let(recordingIds::remove)
            val distinct = recordingIds.distinct()
            recordingIds.clear()
            recordingIds.addAll(distinct)
        }
    }

    private fun descriptor(source: TrackSourceMappingEntity): SourceCandidateDescriptor? {
        if (!isIdentityAnchor(source)) return null
        val feature = source.sourceId
            ?.let(featuresBySourceId::get)
            ?.takeIf { it.algorithmVersion == SourceMatchFeaturePolicy.ALGORITHM_VERSION }
            ?: SourceMatchFeaturePolicy.build(source, updatedAt = 0L)
            ?: return null
        val title = feature.coreTitle
        val artist = feature.normalizedArtist
        if (title.isBlank() || artist.isBlank() || title in unknownValues || artist in unknownValues) {
            return null
        }
        if (feature.durationBucket < 0L) return null
        return SourceCandidateDescriptor(title, artist, feature.durationBucket)
    }

    private fun SourceCandidateDescriptor.bucket(offset: Long = 0L) = SourceCandidateBucket(
        title = title,
        artist = artist,
        durationBucket = durationBucket + offset
    )

    private fun isIdentityAnchor(source: TrackSourceMappingEntity): Boolean =
        sourceEligibility(source)

    private data class SourceCandidateDescriptor(
        val title: String,
        val artist: String,
        val durationBucket: Long
    )

    private data class SourceCandidateBucket(
        val title: String,
        val artist: String,
        val durationBucket: Long
    )

    private companion object {
        const val MAX_PRECOMPUTED_CANDIDATES = 20
        val unknownValues = setOf(
            "<unknown>",
            "unknown",
            "unknown song",
            "unknown artist",
            "未知歌曲",
            "未知艺人"
        )
    }
}

/** Shared feature schema policy. Bump [ALGORITHM_VERSION] whenever normalization semantics change. */
internal object SourceMatchFeaturePolicy {
    const val ALGORITHM_VERSION = 1
    private const val DURATION_BUCKET_MS = 10_000L
    private const val FEATURE_SEPARATOR = "\u001F"
    private const val SIGNATURE_SEPARATOR = "\u001E"

    fun metadataSignature(source: TrackSourceMappingEntity): String = sha256(
        listOf(source.title, source.artist, source.album, source.durationMs.toString())
            .joinToString(SIGNATURE_SEPARATOR)
    )

    fun build(
        source: TrackSourceMappingEntity,
        updatedAt: Long,
        metadataSignature: String = metadataSignature(source)
    ): SourceMatchFeatureEntity? {
        val sourceId = source.sourceId ?: return null
        val reference = StreamingTrackMatchPolicy.Reference(
            title = source.title,
            artist = source.artist,
            album = source.album,
            durationMs = source.durationMs.takeIf { it > 0L }
        )
        val extracted = RecordingMatchFeatureExtractor.extract(reference)
        return SourceMatchFeatureEntity(
            sourceId = sourceId,
            normalizedTitle = StreamingTrackMatchPolicy.canonicalTitle(source.title),
            coreTitle = extracted.normalizedTitle,
            normalizedArtist = StreamingTrackMatchPolicy.canonicalArtistKey(listOf(source.artist)),
            normalizedAlbum = extracted.albumKey.orEmpty(),
            versionType = extracted.versionType.name,
            versionSignature = extracted.versionSignature,
            durationBucket = source.durationMs.takeIf { it > 0L }
                ?.div(DURATION_BUCKET_MS)
                ?: -1L,
            titleTokens = extracted.titleTokens.joinToString(FEATURE_SEPARATOR),
            titleBigrams = extracted.titleBigrams.joinToString(FEATURE_SEPARATOR),
            titleTrigrams = ngrams(extracted.normalizedTitle, 3).joinToString(FEATURE_SEPARATOR),
            metadataSignature = metadataSignature,
            algorithmVersion = ALGORITHM_VERSION,
            updatedAt = updatedAt
        )
    }

    private fun ngrams(value: String, width: Int): Set<String> {
        val points = value.codePoints()
            .filter { !Character.isWhitespace(it) }
            .toArray()
        if (points.isEmpty()) return emptySet()
        if (points.size < width) return setOf(String(points, 0, points.size))
        return (0..points.size - width).mapTo(sortedSetOf()) { index ->
            String(points, index, width)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

/** Test/backward-compatible physical index; production ingestion uses confirmed anchors only. */
internal class PhysicalSourceCandidateIndex(
    sources: List<TrackSourceMappingEntity>
) {
    private val delegate = SourceIdentityCandidateIndex(
        sources = sources,
        sourceEligibility = { source ->
            source.localTrackId != null && source.provider.lowercase() in physicalProviders
        }
    )

    fun sources(recordingId: Long): List<TrackSourceMappingEntity> = delegate.sources(recordingId)

    fun candidateRecordingIds(recordingId: Long): Set<Long> =
        delegate.candidateRecordingIds(recordingId)

    fun merge(sourceRecordingId: Long, targetRecordingId: Long) =
        delegate.merge(sourceRecordingId, targetRecordingId)

    private companion object {
        val physicalProviders = setOf("local", "document", "webdav")
    }
}
