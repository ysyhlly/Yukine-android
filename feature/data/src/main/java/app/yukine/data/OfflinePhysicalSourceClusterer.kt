package app.yukine.data

import app.yukine.diagnostics.DiagnosticLog
import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.SourceMatchFeatureEntity
import app.yukine.data.room.SourceRecordingCandidateEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.MusicIdentityDiagnostics
import app.yukine.identity.IdentityMatchStatus
import app.yukine.fingerprint.AudioMatchRefiner
import app.yukine.fingerprint.ChromaprintAlignment
import app.yukine.fingerprint.ChromaprintSegmentAligner
import app.yukine.fingerprint.TraditionalAudioEvidence
import app.yukine.streaming.MatchEvaluation
import app.yukine.streaming.MetadataRecallEvidence
import app.yukine.streaming.EvidenceTrustProfile
import app.yukine.streaming.EvidenceTrustTier
import app.yukine.streaming.IdentityScoringMode
import app.yukine.streaming.ProviderRolePolicy
import app.yukine.streaming.RecordingMatchFeatureExtractor
import app.yukine.streaming.RecordingMatchFeatures
import app.yukine.streaming.RecordingMatchEvaluationPolicy
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingMatchHardConflict
import app.yukine.streaming.RecordingRelationship
import app.yukine.streaming.StreamingTrackMatchPolicy
import app.yukine.streaming.WorkCreditFeature
import app.yukine.streaming.WorkCreditRole
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
    fun ingestAllConfirmedSources(): Int = ingestRecordings(
        dao.identityAnchorSources().map(TrackSourceMappingEntity::recordingId)
    )

    /**
     * One-time/backward-compatible ingestion for databases created before persisted clustering.
     * Fully processed sources are excluded in SQL, keeping ordinary app startup off the full-library
     * scoring path.
     */
    fun ingestPendingConfirmedSources(): Int {
        val missingSources = dao.tracksWithoutIdentitySource()
        if (missingSources.isNotEmpty()) {
            database.runInTransaction {
                OfflineMusicIdentityStore(dao).ensureTracks(missingSources, System.currentTimeMillis())
            }
        }
        val pendingTrackIds = dao.pendingLibraryIdentityTrackIds(
            SourceMatchFeaturePolicy.ALGORITHM_VERSION,
            SourceRecordingCandidateGenerator.ALGORITHM_VERSION
        )
        val mergedCount = ingestLocalTracks(
            missingSources.mapNotNull { track -> track.id } + pendingTrackIds
        )
        val removedSelfOwnedCandidates = dao.deleteSelfOwnedPendingRecordingCandidates()
        return maxOf(mergedCount, missingSources.size, removedSelfOwnedCandidates)
    }

    fun ingestLocalTracks(localTrackIds: List<Long>): Int = IdentityMutationGate.withLock {
        ingestSourcesLocked(localTrackIds, emptyList())
    }

    /** Incremental entry used after a provider owner collision or a manual candidate decision. */
    fun ingestRecordings(recordingIds: List<Long>): Int = IdentityMutationGate.withLock {
        ingestSourcesLocked(emptyList(), recordingIds)
    }

    private fun ingestSourcesLocked(
        localTrackIds: List<Long>,
        recordingIds: List<Long>
    ): Int {
        val totalStartedAt = diagnostics.startNanos()
        val dedupPolicy = LibraryDedupPolicy.forMode(
            LibraryDedupModeStore(database.settingsDao()).mode()
        )
        val embeddingRecallMode = dedupPolicy.embeddingRecallMode
        val scoringMode = dedupPolicy.scoringMode
        val pendingTrackIds = localTrackIds.asSequence()
            .filter { it != 0L }
            .distinct()
            .toList()
        val requestedRecordingIds = recordingIds.asSequence()
            .filter { it != 0L }
            .distinct()
            .toList()
        if (pendingTrackIds.isEmpty() && requestedRecordingIds.isEmpty()) {
            diagnostics.recordElapsed(OPERATION, MusicIdentityDiagnostics.Stage.TOTAL, totalStartedAt)
            return 0
        }

        val snapshotStartedAt = diagnostics.startNanos()
        val featureSources = dao.matchFeatureSources()
        val identityAnchors = featureSources.filter { source ->
            ProviderRolePolicy.contributesIdentity(source.provider) &&
                (source.matchStatus == "CONFIRMED" || source.localTrackId != null)
        }
        val audioEvidenceBySourceId = featureSources.mapNotNull(TrackSourceMappingEntity::sourceId)
            .distinct()
            .chunked(SQLITE_IN_BATCH_SIZE)
            .flatMap(dao::audioFeatures)
            .associate { feature -> feature.sourceId to StoredAudioEvidenceCodec.decode(feature) }
        val storedFeatures = dao.sourceMatchFeatures().associateBy(SourceMatchFeatureEntity::sourceId)
        val storedCandidates = dao.sourceRecordingCandidates()
        val pendingRecordingIds = (
            pendingTrackIds
                .chunked(SQLITE_IN_BATCH_SIZE)
                .flatMap(dao::sourcesForLocalTracks)
                .map(TrackSourceMappingEntity::recordingId) + requestedRecordingIds
            )
            .asSequence()
            .filter { recordingId -> dao.recording(recordingId) != null }
            .distinct()
            .toList()
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
        val snapshotSignature = candidateGenerator.snapshotSignature(
            featureSources,
            effectiveFeatures,
            embeddingRecallMode,
            audioEvidenceBySourceId
        )
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
                System.currentTimeMillis(),
                embeddingRecallMode,
                audioEvidenceBySourceId
            )
            candidateRows = generated.candidates + generated.shadowCandidates
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
                .asSequence()
                .filter { candidate -> candidate.state == SourceRecordingCandidateGenerator.STATE_GENERATED }
                .groupBy(SourceRecordingCandidateEntity::sourceId)
                .mapValues { (_, rows) -> rows.map(SourceRecordingCandidateEntity::candidateRecordingId) }
        )
        val shadowCandidatesBySourceId = candidateRows
            .asSequence()
            .filter { candidate -> candidate.state == SourceRecordingCandidateGenerator.STATE_SHADOW }
            .groupBy(SourceRecordingCandidateEntity::sourceId)
        val redirects = HashMap<Long, Long>()
        val processed = HashSet<Long>()
        // Session-level cache for RecordingMatchFeatures to avoid redundant extraction
        // when the same source appears in multiple recording comparisons.
        val matchFeatureCache = HashMap<Long, RecordingMatchFeatures>()
        var mergedCount = 0
        pendingRecordingIds.forEach { initialRecordingId ->
            var currentRecordingId = resolveRedirect(initialRecordingId, redirects)
            if (currentRecordingId in processed) return@forEach
            while (true) {
                val currentRecording = dao.recording(currentRecordingId) ?: break
                val currentSources = candidateIndex.sources(currentRecordingId)
                if (!eligibleGroup(currentRecordingId, currentSources)) break
                if (embeddingRecallMode == EmbeddingRecallMode.SHADOW) {
                    evaluateShadowCandidates(
                        currentRecording,
                        currentSources,
                        candidateIndex,
                        shadowCandidatesBySourceId,
                        audioEvidenceBySourceId,
                        effectiveFeatures,
                        scoringMode,
                        dedupPolicy,
                        matchFeatureCache
                    )
                }

                val candidateGenerationStartedAt = diagnostics.startNanos()
                val candidateRecordingIds = candidateIndex.candidateRecordingIds(currentRecordingId)
                diagnostics.recordElapsed(
                    OPERATION,
                    MusicIdentityDiagnostics.Stage.CANDIDATE_GENERATION,
                    candidateGenerationStartedAt,
                    candidateRecordingIds.size.toLong()
                )
                val scoringStartedAt = diagnostics.startNanos()
                val candidateRecordingsById = dao.recordings(candidateRecordingIds.toList())
                    .associateBy { it.id }
                val evaluatedGroups = candidateRecordingIds
                    .asSequence()
                    .mapNotNull { candidateRecordingId ->
                        val candidateSources = candidateIndex.sources(candidateRecordingId)
                        val candidateRecording = candidateRecordingsById[candidateRecordingId]
                            ?: return@mapNotNull null
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
                                audioEvidenceBySourceId,
                                effectiveFeatures,
                                scoringMode,
                                dedupPolicy,
                                matchFeatureCache
                            )
                            ?: return@mapNotNull null
                        EvaluatedRecording(
                            recordingId = candidateRecordingId,
                            evaluation = evaluation,
                            locked = manual != null,
                            automaticEligible = dedupPolicy.allowMissingDuration ||
                                currentSources.all { it.durationMs > 0L } &&
                                candidateSources.all { it.durationMs > 0L }
                        )
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
                    .filter { candidate ->
                        candidate.automaticEligible &&
                        candidate.evaluation.relationship == RecordingRelationship.SAME_RECORDING &&
                            candidate.evaluation.sameRecordingProbability >=
                            dedupPolicy.autoMergeMinimumScore
                    }
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
                if (best.score < dedupPolicy.autoMergeMinimumScore) {
                    break
                }
                val ambiguousCandidates = candidateGroups.takeWhile { candidate ->
                    best.score - candidate.score < dedupPolicy.autoMergeMinimumMargin
                }
                if (ambiguousCandidates.size > 1 && !sameRecordingClique(
                        ambiguousCandidates,
                        candidateIndex,
                        audioEvidenceBySourceId,
                        effectiveFeatures,
                        scoringMode,
                        dedupPolicy,
                        matchFeatureCache
                    )
                ) break

                val sourceId = maxOf(currentRecordingId, best.recordingId)
                val targetId = minOf(currentRecordingId, best.recordingId)
                val safeEligible = best.score >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE &&
                    best.score - (candidateGroups.getOrNull(1)?.score ?: 0.0) >=
                    RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_MARGIN &&
                    currentSources.all { it.durationMs > 0L } &&
                    candidateIndex.sources(best.recordingId).all { it.durationMs > 0L }
                val commitStartedAt = diagnostics.startNanos()
                try {
                    recordings.mergeRecordingsAutomatically(
                        sourceRecordingId = sourceId,
                        targetRecordingId = targetId,
                        dedupMode = dedupPolicy.mode,
                        policyVersion = LibraryDedupPolicy.POLICY_VERSION,
                        evaluationBatch = snapshotSignature,
                        rollbackStatus = when {
                            dedupPolicy.mode != app.yukine.identity.LibraryDedupMode.AGGRESSIVE -> "NONE"
                            safeEligible -> "SAFE_ELIGIBLE"
                            else -> "ELIGIBLE"
                        }
                    )
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
                System.currentTimeMillis(),
                embeddingRecallMode,
                audioEvidenceBySourceId
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
            (pendingTrackIds.size + requestedRecordingIds.size).toLong()
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
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        scoringMode: IdentityScoringMode,
        dedupPolicy: LibraryDedupPolicy,
        matchFeatureCache: MutableMap<Long, RecordingMatchFeatures>
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
                    audioEvidenceBySourceId,
                    featuresBySourceId,
                    scoringMode,
                    dedupPolicy,
                    matchFeatureCache
                ) ?: return false
                if (evaluation.relationship != RecordingRelationship.SAME_RECORDING ||
                    evaluation.sameRecordingProbability <
                    dedupPolicy.autoMergeMinimumScore
                ) return false
            }
        }
        return true
    }

    private fun replaceCandidateSnapshot(
        sources: List<TrackSourceMappingEntity>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        generatedAt: Long,
        mode: EmbeddingRecallMode,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>
    ): SourceCandidateGenerationResult {
        val generated = candidateGenerator.generate(
            sources,
            featuresBySourceId,
            generatedAt,
            mode,
            audioEvidenceBySourceId
        )
        val sourceIds = sources.mapNotNull(TrackSourceMappingEntity::sourceId).distinct()
        var validCandidates = generated.candidates
        var validShadowCandidates = generated.shadowCandidates
        database.runInTransaction {
            val allGeneratedCandidates = generated.candidates + generated.shadowCandidates
            val existingRecordingIds = allGeneratedCandidates
                .map(SourceRecordingCandidateEntity::candidateRecordingId)
                .distinct()
                .chunked(SQLITE_IN_BATCH_SIZE)
                .flatMap(dao::existingRecordingIds)
                .toHashSet()
            validCandidates = generated.candidates.filter { candidate ->
                candidate.candidateRecordingId in existingRecordingIds
            }
            validShadowCandidates = generated.shadowCandidates.filter { candidate ->
                candidate.candidateRecordingId in existingRecordingIds
            }
            dao.clearSourceRecordingCandidates()
            (validCandidates + validShadowCandidates)
                .chunked(CANDIDATE_WRITE_BATCH_SIZE)
                .forEach { candidates ->
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
        return generated.copy(
            candidates = validCandidates,
            shadowCandidates = validShadowCandidates
        )
    }

    private fun eligibleGroup(
        recordingId: Long,
        sources: List<TrackSourceMappingEntity>
    ): Boolean = sources.isNotEmpty() && dao.activeSplitOperationCount(recordingId) == 0

    private fun evaluateShadowCandidates(
        currentRecording: CanonicalRecordingEntity,
        currentSources: List<TrackSourceMappingEntity>,
        candidateIndex: SourceIdentityCandidateIndex,
        shadowCandidatesBySourceId: Map<Long, List<SourceRecordingCandidateEntity>>,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        scoringMode: IdentityScoringMode,
        dedupPolicy: LibraryDedupPolicy,
        matchFeatureCache: MutableMap<Long, RecordingMatchFeatures>
    ) {
        val rows = currentSources.asSequence()
            .mapNotNull(TrackSourceMappingEntity::sourceId)
            .flatMap { sourceId -> shadowCandidatesBySourceId[sourceId].orEmpty().asSequence() }
            .distinctBy { candidate -> candidate.candidateRecordingId }
            .take(SourceRecordingCandidateGenerator.MAX_EMBEDDING_ONLY_TOP_K)
            .toList()
        if (rows.isEmpty()) return
        val startedAt = diagnostics.startNanos()
        rows.forEach { row ->
            val candidateRecording = dao.recording(row.candidateRecordingId) ?: return@forEach
            val candidateSources = candidateIndex.sources(row.candidateRecordingId)
            if (!eligibleGroup(row.candidateRecordingId, candidateSources)) return@forEach
            val evaluation = completeLinkEvaluation(
                currentRecording,
                currentSources,
                candidateRecording,
                candidateSources,
                audioEvidenceBySourceId,
                featuresBySourceId,
                scoringMode,
                dedupPolicy,
                matchFeatureCache
            ) ?: return@forEach
            val evidence = runCatching { JSONObject(row.evidenceJson) }.getOrElse { JSONObject() }
                .put("shadowEvaluation", JSONObject(evaluation.evidenceJson))
                .put("shadowEvaluatedAt", System.currentTimeMillis())
            dao.updateShadowCandidateEvidence(
                sourceId = row.sourceId,
                candidateRecordingId = row.candidateRecordingId,
                evidenceJson = evidence.toString(),
                updatedAt = System.currentTimeMillis()
            )
        }
        diagnostics.recordElapsed(
            OPERATION,
            MusicIdentityDiagnostics.Stage.SCORING,
            startedAt,
            rows.size.toLong()
        )
    }

    private fun completeLinkEvaluation(
        leftRecording: CanonicalRecordingEntity,
        leftSources: List<TrackSourceMappingEntity>,
        rightRecording: CanonicalRecordingEntity,
        rightSources: List<TrackSourceMappingEntity>,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        scoringMode: IdentityScoringMode,
        dedupPolicy: LibraryDedupPolicy,
        matchFeatureCache: MutableMap<Long, RecordingMatchFeatures>
    ): GroupEvaluation? {
        val hardConflicts = linkedSetOf<String>()
        val audioAlignments = JSONArray()
        val activePairs = ArrayList<ScoredPair>()
        val shadowPairs = ArrayList<ScoredPair>()
        var embeddingMinimum = 1.0
        var embeddingMaximum = 0.0
        var embeddingTotal = 0.0
        var embeddingCount = 0
        var embeddingVersion = 0
        val leftReferences = leftSources.associate { source ->
            source to reference(source, leftRecording, source.sourceId?.let(featuresBySourceId::get))
        }
        val rightReferences = rightSources.associate { source ->
            source to reference(source, rightRecording, source.sourceId?.let(featuresBySourceId::get))
        }
        // Use session-level cache to avoid redundant feature extraction across recording comparisons.
        val leftMatchFeatures = leftReferences.mapValues { (source, ref) ->
            source.sourceId?.let { id ->
                matchFeatureCache.getOrPut(id) { RecordingMatchFeatureExtractor.extract(ref) }
            } ?: RecordingMatchFeatureExtractor.extract(ref)
        }
        val rightMatchFeatures = rightReferences.mapValues { (source, ref) ->
            source.sourceId?.let { id ->
                matchFeatureCache.getOrPut(id) { RecordingMatchFeatureExtractor.extract(ref) }
            } ?: RecordingMatchFeatureExtractor.extract(ref)
        }
        leftSources.forEach { left ->
            rightSources.forEach { right ->
                val leftSourceId = left.sourceId ?: return@forEach
                val rightSourceId = right.sourceId ?: return@forEach
                val pair = evaluatePair(
                    left,
                    right,
                    leftReferences.getValue(left),
                    rightReferences.getValue(right),
                    audioEvidenceBySourceId,
                    featuresBySourceId,
                    scoringMode,
                    leftMatchFeatures.getValue(left),
                    rightMatchFeatures.getValue(right)
                )
                val evaluation = pair.evaluation
                val recallEvidence = evaluation.metadataRecallEvidence
                recallEvidence?.embeddingSimilarity?.let { similarity ->
                    embeddingMinimum = minOf(embeddingMinimum, similarity)
                    embeddingMaximum = maxOf(embeddingMaximum, similarity)
                    embeddingTotal += similarity
                    embeddingCount++
                    embeddingVersion = maxOf(
                        embeddingVersion,
                        recallEvidence.embeddingVersion
                    )
                }
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
                val leftTrust = sourceTrust(left.sourceId?.let(featuresBySourceId::get))
                val rightTrust = sourceTrust(right.sourceId?.let(featuresBySourceId::get))
                activePairs += ScoredPair(
                    leftSourceId = leftSourceId,
                    rightSourceId = rightSourceId,
                    evaluation = evaluation,
                    leftTrust = leftTrust,
                    rightTrust = rightTrust
                )
                pair.shadowEvaluation?.let { shadow ->
                    shadowPairs += ScoredPair(
                        leftSourceId = leftSourceId,
                        rightSourceId = rightSourceId,
                        evaluation = shadow,
                        leftTrust = leftTrust,
                        rightTrust = rightTrust
                    )
                }
                hardConflicts += evaluation.hardConflicts.map { it.name }
                // Early termination: terminal hard conflicts (ceiling ≤ 0.40) make merge
                // mathematically impossible (threshold 0.92), skip remaining pairs.
                if (evaluation.hardConflicts.any { it in TERMINAL_HARD_CONFLICTS }) {
                    return GroupEvaluation(
                        sameRecordingProbability = 0.0,
                        sameWorkProbability = 0.0,
                        relationship = RecordingRelationship.CANNOT_LINK,
                        confidence = 1.0,
                        scoreVersion = evaluation.scoreVersion,
                        evidenceJson = JSONObject()
                            .put("scoreVersion", evaluation.scoreVersion)
                            .put("scoringMode", scoringMode.name)
                            .put("dedupMode", dedupPolicy.mode.name)
                            .put("dedupPolicyVersion", LibraryDedupPolicy.POLICY_VERSION)
                            .put("aggregation", "TERMINAL_CONFLICT_EARLY_EXIT")
                            .put("sameRecording", 0.0)
                            .put("sameWork", 0.0)
                            .put("relationship", RecordingRelationship.CANNOT_LINK.name)
                            .put("hardConflicts", JSONArray(hardConflicts.toList()))
                            .toString()
                    )
                }
            }
        }
        if (activePairs.isEmpty()) return null
        val activeAggregate = aggregatePairs(
            activePairs,
            trustAware = true,
            autoMergeMinimumScore = dedupPolicy.autoMergeMinimumScore
        )
        val evidenceJson = JSONObject()
            .put("scoreVersion", activeAggregate.scoreVersion)
            .put("scoringMode", scoringMode.name)
            .put("dedupMode", dedupPolicy.mode.name)
            .put("dedupPolicyVersion", LibraryDedupPolicy.POLICY_VERSION)
            .put("autoMergeMinimumScore", dedupPolicy.autoMergeMinimumScore)
            .put("autoMergeMinimumMargin", dedupPolicy.autoMergeMinimumMargin)
            .put("completeLinkComparisons", activePairs.size)
            .put("aggregation", activeAggregate.aggregation)
            .put("sameRecording", activeAggregate.sameRecording)
            .put("sameWork", activeAggregate.sameWork)
            .put("relationship", activeAggregate.relationship.name)
            .put("hardConflicts", JSONArray(hardConflicts.toList()))
            .put("audioAlignments", audioAlignments)
        if (shadowPairs.isNotEmpty()) {
            val shadowAggregate = aggregatePairs(
                shadowPairs,
                trustAware = true,
                autoMergeMinimumScore = dedupPolicy.autoMergeMinimumScore
            )
            evidenceJson.put(
                "shadowScoring",
                JSONObject()
                    .put("scoreVersion", shadowAggregate.scoreVersion)
                    .put("aggregation", shadowAggregate.aggregation)
                    .put("sameRecording", shadowAggregate.sameRecording)
                    .put("sameWork", shadowAggregate.sameWork)
                    .put("relationship", shadowAggregate.relationship.name)
            )
        }
        if (embeddingCount > 0) {
            evidenceJson.put(
                "embedding",
                JSONObject()
                    .put("version", embeddingVersion)
                    .put("minimumSimilarity", embeddingMinimum)
                    .put("maximumSimilarity", embeddingMaximum)
                    .put("meanSimilarity", embeddingTotal / embeddingCount)
                    .put("comparisonCount", embeddingCount)
                    .put("recallOnly", true)
            )
        }
        return GroupEvaluation(
            sameRecordingProbability = activeAggregate.sameRecording,
            sameWorkProbability = activeAggregate.sameWork,
            relationship = activeAggregate.relationship,
            confidence = activeAggregate.confidence,
            scoreVersion = activeAggregate.scoreVersion,
            evidenceJson = evidenceJson.toString()
        )
    }

    private fun evaluatePair(
        left: TrackSourceMappingEntity,
        right: TrackSourceMappingEntity,
        leftReference: StreamingTrackMatchPolicy.Reference,
        rightReference: StreamingTrackMatchPolicy.Reference,
        audioEvidenceBySourceId: Map<Long, TraditionalAudioEvidence>,
        featuresBySourceId: Map<Long, SourceMatchFeatureEntity>,
        scoringMode: IdentityScoringMode,
        precomputedLeftFeatures: RecordingMatchFeatures? = null,
        precomputedRightFeatures: RecordingMatchFeatures? = null
    ): PairEvaluation {
        val leftFeature = left.sourceId?.let(featuresBySourceId::get)
        val rightFeature = right.sourceId?.let(featuresBySourceId::get)
        val embeddingVersion = minOf(
            leftFeature?.metadataVectorVersion ?: 0,
            rightFeature?.metadataVectorVersion ?: 0
        )
        val embeddingSimilarity = if (
            embeddingVersion == MetadataHashEmbeddingEncoder.VECTOR_VERSION
        ) {
            MetadataHashEmbeddingEncoder.cosine(
                leftFeature?.metadataVector,
                rightFeature?.metadataVector
            )
        } else {
            0.0
        }
        val recallEvidence = if (embeddingVersion > 0) {
            MetadataRecallEvidence(
                    embeddingSimilarity = embeddingSimilarity,
                    embeddingVersion = embeddingVersion,
                    recallOnly = true
                )
        } else {
            null
        }
        val decision = RecordingMatchEvaluationPolicy.evaluate(
            precomputedLeftFeatures ?: RecordingMatchFeatureExtractor.extract(leftReference),
            precomputedRightFeatures ?: RecordingMatchFeatureExtractor.extract(rightReference),
            scoringMode,
            recallEvidence = recallEvidence
        )
        val leftEvidence = left.sourceId?.let(audioEvidenceBySourceId::get)
        val rightEvidence = right.sourceId?.let(audioEvidenceBySourceId::get)
        if (leftEvidence == null || rightEvidence == null ||
            leftEvidence.pcmHash.isBlank() && leftEvidence.segments.isEmpty() ||
            rightEvidence.pcmHash.isBlank() && rightEvidence.segments.isEmpty()
        ) {
            return PairEvaluation(decision.active, decision.shadow, null)
        }
        val alignment = ChromaprintSegmentAligner.align(leftEvidence, rightEvidence)
        return PairEvaluation(
            AudioMatchRefiner.refine(decision.active, alignment),
            decision.shadow?.let { AudioMatchRefiner.refine(it, alignment) },
            alignment
        )
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
        recording: CanonicalRecordingEntity,
        feature: SourceMatchFeatureEntity?
    ): StreamingTrackMatchPolicy.Reference {
        val work = recording.workId?.let(dao::work)
        val album = source.albumId?.let(dao::album)
        val recordingIdentifiers = recording.id?.let(dao::identifiers).orEmpty()
        val workIdentifiers = work?.id?.let(dao::workIdentifiers).orEmpty()
        val workCredits = work?.id?.let(dao::workCredits).orEmpty().mapNotNull { credit ->
            val role = runCatching { WorkCreditRole.valueOf(credit.role) }.getOrNull()
                ?: return@mapNotNull null
            WorkCreditFeature(
                canonicalId = credit.artistId.toString(),
                canonicalName = credit.creditedName,
                role = role
            )
        }
        return StreamingTrackMatchPolicy.Reference(
            title = source.title,
            artist = source.artist,
            album = source.album,
            durationMs = source.durationMs.takeIf { it > 0L },
            isrc = recording.isrc,
            isrcs = recordingIdentifiers
                .filter { it.identifierType.equals("ISRC", ignoreCase = true) }
                .mapTo(linkedSetOf()) { it.identifierValue },
            recordingMbid = recording.musicBrainzRecordingId,
            workMbid = recording.musicBrainzWorkId,
            workIdentifiers = workIdentifiers.mapTo(linkedSetOf()) { identifier ->
                listOf(
                    identifier.identifierType,
                    identifier.namespace,
                    identifier.identifierValue
                ).joinToString("\u001F")
            },
            workCredits = workCredits,
            canonicalWorkId = work?.canonicalUuid.orEmpty(),
            canonicalWorkConfirmed = work != null &&
                work.primaryCreatorId != null &&
                source.matchStatus == IdentityMatchStatus.CONFIRMED.name,
            canonicalAlbumId = album?.albumUuid.orEmpty(),
            canonicalAlbumConfirmed = album?.matchStatus == IdentityMatchStatus.CONFIRMED.name,
            fingerprint = recording.acoustId,
            trustProfile = feature?.trustProfile() ?: EvidenceTrustProfile()
        )
    }

    private fun SourceMatchFeatureEntity.trustProfile() = EvidenceTrustProfile(
        title = EvidenceTrustTier.closest(titleTrust),
        artist = EvidenceTrustTier.closest(artistTrust),
        version = EvidenceTrustTier.closest(versionTrust),
        identifier = EvidenceTrustTier.closest(identifierTrust),
        workCredit = EvidenceTrustTier.closest(workCreditTrust)
    )

    private fun sourceTrust(feature: SourceMatchFeatureEntity?): Double =
        feature?.let {
            listOf(it.titleTrust, it.artistTrust, it.versionTrust, it.identifierTrust)
                .average()
                .coerceIn(0.0, 1.0)
        } ?: EvidenceTrustTier.UNRESOLVED.weight

    /**
     * Aggregates pair-level evaluations into a group-level decision.
     *
     * Trust-unaware path (`trustAware=false`): uses pure complete-link minimum — conservative but
     * vulnerable to a single low-quality source dragging the score below merge threshold.
     *
     * Trust-aware path (`trustAware=true`): uses [sourceBalancedMedian] to compute a robust
     * weighted median, then caps it with the high-trust minimum plus [TRUST_FLOOR_SLACK]. This
     * prevents a single corrupted-metadata source from unilaterally vetoing a merge that the
     * weighted majority supports.
     */
    private fun aggregatePairs(
        pairs: List<ScoredPair>,
        trustAware: Boolean,
        autoMergeMinimumScore: Double
    ): PairAggregate {
        if (!trustAware) {
            val recording = pairs.minOf { it.evaluation.sameRecordingProbability }
            val work = pairs.minOf { it.evaluation.sameWorkProbability }
            val relationship = pairs.fold(RecordingRelationship.SAME_RECORDING) { current, pair ->
                combineRelationship(current, pair.evaluation.relationship)
            }
            return PairAggregate(
                sameRecording = recording,
                sameWork = work,
                relationship = relationship,
                confidence = relationshipConfidence(relationship, recording, work),
                scoreVersion = pairs.maxOf { it.evaluation.scoreVersion },
                aggregation = "COMPLETE_LINK_MINIMUM"
            )
        }

        val robustRecording = sourceBalancedMedian(pairs) { it.sameRecordingProbability }
        val robustWork = sourceBalancedMedian(pairs) { it.sameWorkProbability }
        val highTrustPairs = pairs.filter { it.pairTrust >= HIGH_TRUST_THRESHOLD }
        val highTrustMinimum = highTrustPairs.minOfOrNull { it.evaluation.sameRecordingProbability }
        val recording = minOf(robustRecording, (highTrustMinimum ?: 1.0) + TRUST_FLOOR_SLACK)
        val anyHardConflict = pairs.any { it.evaluation.hasHardConflict }
        val relationship = when {
            anyHardConflict -> RecordingRelationship.CANNOT_LINK
            recording >= autoMergeMinimumScore ->
                RecordingRelationship.SAME_RECORDING
            robustWork >= SAME_WORK_MINIMUM_SCORE &&
                pairs.any { it.evaluation.relationship == RecordingRelationship.SAME_WORK_DIFFERENT_VERSION } ->
                RecordingRelationship.SAME_WORK_DIFFERENT_VERSION
            pairs.all { it.evaluation.relationship == RecordingRelationship.CANNOT_LINK } ->
                RecordingRelationship.CANNOT_LINK
            else -> RecordingRelationship.UNKNOWN
        }
        return PairAggregate(
            sameRecording = recording,
            sameWork = robustWork,
            relationship = relationship,
            confidence = relationshipConfidence(relationship, recording, robustWork),
            scoreVersion = pairs.maxOf { it.evaluation.scoreVersion },
            aggregation = "TRUST_SOURCE_BALANCED_MEDIAN_V2"
        )
    }

    /**
     * Computes a source-balanced weighted median of pair scores.
     *
     * Groups pair scores by originating source (left and right independently), computes a
     * trust-weighted median within each source group, then computes a cross-source weighted
     * median. This prevents a single low-quality source with many pairs from dominating the
     * aggregate score.
     */
    private fun sourceBalancedMedian(
        pairs: List<ScoredPair>,
        selector: (MatchEvaluation) -> Double
    ): Double {
        val summaries = ArrayList<WeightedScore>()
        pairs.groupBy(ScoredPair::leftSourceId).values.forEach { sourcePairs ->
            summaries += WeightedScore(
                weightedMedian(sourcePairs.map {
                    WeightedScore(selector(it.evaluation), it.pairTrust)
                }),
                sourcePairs.first().leftTrust
            )
        }
        pairs.groupBy(ScoredPair::rightSourceId).values.forEach { sourcePairs ->
            summaries += WeightedScore(
                weightedMedian(sourcePairs.map {
                    WeightedScore(selector(it.evaluation), it.pairTrust)
                }),
                sourcePairs.first().rightTrust
            )
        }
        return weightedMedian(summaries)
    }

    private fun weightedMedian(values: List<WeightedScore>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedBy(WeightedScore::score)
        val total = sorted.sumOf { it.weight.coerceAtLeast(0.001) }
        var cumulative = 0.0
        sorted.forEach { value ->
            cumulative += value.weight.coerceAtLeast(0.001)
            if (cumulative >= total / 2.0) return value.score
        }
        return sorted.last().score
    }

    private fun relationshipConfidence(
        relationship: RecordingRelationship,
        recording: Double,
        work: Double
    ): Double = when (relationship) {
        RecordingRelationship.SAME_RECORDING -> recording
        RecordingRelationship.SAME_WORK_DIFFERENT_VERSION -> work
        RecordingRelationship.CANNOT_LINK -> 1.0 - recording
        RecordingRelationship.UNKNOWN -> maxOf(recording, work)
    }.coerceIn(0.0, 1.0)

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
            DiagnosticLog.d(TAG, "$OPERATION ${diagnostics.snapshot(OPERATION).compactSummary()}")
        }
    }

    private data class RankedRecording(
        val recordingId: Long,
        val score: Double
    )

    private data class EvaluatedRecording(
        val recordingId: Long,
        val evaluation: GroupEvaluation,
        val locked: Boolean,
        val automaticEligible: Boolean
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
        val shadowEvaluation: MatchEvaluation?,
        val alignment: ChromaprintAlignment?
    )

    private data class ScoredPair(
        val leftSourceId: Long,
        val rightSourceId: Long,
        val evaluation: MatchEvaluation,
        val leftTrust: Double,
        val rightTrust: Double
    ) {
        val pairTrust: Double get() = minOf(leftTrust, rightTrust)
    }

    private data class WeightedScore(val score: Double, val weight: Double)

    private data class PairAggregate(
        val sameRecording: Double,
        val sameWork: Double,
        val relationship: RecordingRelationship,
        val confidence: Double,
        val scoreVersion: Int,
        val aggregation: String
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
        const val TARGET_RECORDING = "RECORDING"
        const val SQLITE_IN_BATCH_SIZE = 500
        const val CANDIDATE_WRITE_BATCH_SIZE = 1_000
        const val HIGH_TRUST_THRESHOLD = 0.85
        const val TRUST_FLOOR_SLACK = 0.03
        const val SAME_WORK_MINIMUM_SCORE = 0.85
        const val TAG = "IdentityDiagnostics"
        val OPERATION = MusicIdentityDiagnostics.Operation.PHYSICAL_CLUSTER
        val blockedCandidateStatuses = setOf("REJECTED", "ALTERNATE_VERSION")
        /**
         * Terminal hard conflicts with score ceiling ≤ 0.40. When detected, merge is
         * mathematically impossible (auto-merge threshold 0.92), enabling early exit.
         */
        val TERMINAL_HARD_CONFLICTS = setOf(
            RecordingMatchHardConflict.WORK_MBID,
            RecordingMatchHardConflict.CANONICAL_WORK,
            RecordingMatchHardConflict.FINGERPRINT
        )
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
    const val ALGORITHM_VERSION = 4
    private const val DURATION_BUCKET_MS = 10_000L
    private const val FEATURE_SEPARATOR = "\u001F"
    private const val SIGNATURE_SEPARATOR = "\u001E"

    fun metadataSignature(source: TrackSourceMappingEntity): String = sha256(
        listOf(
            source.title,
            source.artist,
            source.album,
            source.albumArtist,
            source.composer,
            source.releaseType,
            source.year.toString(),
            source.durationMs.toString()
        )
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
        val embedding = MetadataHashEmbeddingEncoder.encode(
            title = source.title,
            artist = source.artist,
            album = source.album,
            albumArtist = source.albumArtist,
            composer = source.composer,
            versionSignature = extracted.versionSignature,
            releaseType = source.releaseType,
            year = source.year
        )
        val trust = trustFor(source)
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
            updatedAt = updatedAt,
            metadataVector = embedding?.vector,
            metadataVectorVersion = embedding?.version ?: 0,
            metadataSimHash = embedding?.simHash,
            titleTrust = trust.weight,
            artistTrust = trust.weight,
            versionTrust = trust.weight,
            identifierTrust = if (source.matchStatus == IdentityMatchStatus.CONFIRMED.name) {
                EvidenceTrustTier.DIRECT_CONFIRMED.weight
            } else {
                EvidenceTrustTier.UNRESOLVED.weight
            },
            workCreditTrust = if (source.composer.isNotBlank()) {
                trust.weight
            } else {
                EvidenceTrustTier.UNRESOLVED.weight
            },
            evidenceProvenance = trust.name
        )
    }

    private fun trustFor(source: TrackSourceMappingEntity): EvidenceTrustTier = when {
        source.matchStatus != IdentityMatchStatus.CONFIRMED.name && source.localTrackId == null ->
            EvidenceTrustTier.UNRESOLVED
        source.provider.lowercase() in setOf("local", "document", "webdav") ->
            EvidenceTrustTier.PARSED_TAG
        source.matchStatus == IdentityMatchStatus.CONFIRMED.name ->
            EvidenceTrustTier.DIRECT_CONFIRMED
        source.legacyLocalKey.isNotBlank() -> EvidenceTrustTier.LEGACY
        else -> EvidenceTrustTier.UNRESOLVED
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
