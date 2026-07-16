package app.yukine.fingerprint

import app.yukine.streaming.MatchEvaluation
import app.yukine.streaming.MatchEvidence
import app.yukine.streaming.RecordingRelationship
import kotlin.math.abs

/** Decoded cold-path evidence. Raw Chromaprint words stay out of library and playback queries. */
data class TraditionalAudioEvidence(
    val pcmHash: String = "",
    val segments: List<ChromaprintSegment> = emptyList()
)

data class ChromaprintSegment(
    val startMs: Long,
    val durationMs: Long,
    val words: IntArray
)

data class ChromaprintAlignment(
    val similarity: Double,
    val matchedSegmentCount: Int,
    val inlierRatio: Double,
    val offsetMs: Long,
    val speedRatio: Double,
    val continuousCoverageMs: Long,
    val structuralJump: Boolean,
    val exactPcm: Boolean
) {
    val stable: Boolean
        get() = !structuralJump && matchedSegmentCount >= 2 && inlierRatio >= MINIMUM_INLIER_RATIO

    private companion object {
        const val MINIMUM_INLIER_RATIO = 0.67
    }
}

/**
 * CPU-only bounded sequence alignment. A line is fitted as:
 * candidateTime = speedRatio * sourceTime + offset.
 */
object ChromaprintSegmentAligner {
    private const val WORD_DURATION_MS = 124L
    private const val MINIMUM_WORD_OVERLAP = 6
    private const val MINIMUM_PAIR_SIMILARITY = 0.72
    private const val INLIER_RESIDUAL_MS = 750.0
    private const val STRUCTURAL_RESIDUAL_MS = 2_000.0
    private const val MINIMUM_SPEED_RATIO = 0.85
    private const val MAXIMUM_SPEED_RATIO = 1.15

    fun align(
        source: TraditionalAudioEvidence,
        candidate: TraditionalAudioEvidence
    ): ChromaprintAlignment {
        val exactPcm = source.pcmHash.isNotBlank() && source.pcmHash == candidate.pcmHash
        val matches = source.segments.mapNotNull { left ->
            candidate.segments.asSequence()
                .mapNotNull { right -> bestPair(left, right) }
                .maxWithOrNull(compareBy<PairMatch>(PairMatch::similarity, PairMatch::overlapWords))
                ?.takeIf { it.similarity >= MINIMUM_PAIR_SIMILARITY }
        }.sortedBy(PairMatch::sourceTimeMs)
        if (matches.isEmpty()) {
            return ChromaprintAlignment(
                similarity = if (exactPcm) 1.0 else 0.0,
                matchedSegmentCount = 0,
                inlierRatio = if (exactPcm) 1.0 else 0.0,
                offsetMs = 0L,
                speedRatio = 1.0,
                continuousCoverageMs = 0L,
                structuralJump = false,
                exactPcm = exactPcm
            )
        }
        val fit = fit(matches)
        val residuals = matches.map { match ->
            abs(match.candidateTimeMs - (fit.ratio * match.sourceTimeMs + fit.offset))
        }
        val inliers = matches.indices.filter { residuals[it] <= INLIER_RESIDUAL_MS }
        val ordered = matches.zipWithNext().all { (first, second) ->
            second.candidateTimeMs >= first.candidateTimeMs
        }
        val plausibleSpeed = fit.ratio in MINIMUM_SPEED_RATIO..MAXIMUM_SPEED_RATIO
        val structuralJump = !ordered || !plausibleSpeed ||
            residuals.any { it > STRUCTURAL_RESIDUAL_MS }
        val coverage = if (inliers.isEmpty()) 0L else {
            val first = matches[inliers.first()]
            val last = matches[inliers.last()]
            (last.sourceTimeMs - first.sourceTimeMs + last.coverageMs).coerceAtLeast(first.coverageMs)
        }
        val similarity = if (inliers.isNotEmpty()) {
            inliers.map { matches[it].similarity }.average()
        } else {
            matches.map(PairMatch::similarity).average()
        }
        return ChromaprintAlignment(
            similarity = if (exactPcm) 1.0 else similarity.coerceIn(0.0, 1.0),
            matchedSegmentCount = matches.size,
            inlierRatio = inliers.size.toDouble() / matches.size,
            offsetMs = fit.offset.toLong(),
            speedRatio = fit.ratio,
            continuousCoverageMs = coverage,
            structuralJump = structuralJump,
            exactPcm = exactPcm
        )
    }

    private fun bestPair(left: ChromaprintSegment, right: ChromaprintSegment): PairMatch? {
        if (left.words.size < MINIMUM_WORD_OVERLAP || right.words.size < MINIMUM_WORD_OVERLAP) {
            return null
        }
        var best: PairMatch? = null
        val minimumOffset = -(left.words.size - MINIMUM_WORD_OVERLAP)
        val maximumOffset = right.words.size - MINIMUM_WORD_OVERLAP
        for (offset in minimumOffset..maximumOffset) {
            val leftStart = maxOf(0, -offset)
            val rightStart = maxOf(0, offset)
            val overlap = minOf(left.words.size - leftStart, right.words.size - rightStart)
            if (overlap < MINIMUM_WORD_OVERLAP) continue
            var differentBits = 0L
            for (index in 0 until overlap) {
                differentBits += Integer.bitCount(
                    left.words[leftStart + index] xor right.words[rightStart + index]
                )
            }
            val similarity = 1.0 - differentBits.toDouble() / (overlap * Int.SIZE_BITS)
            val match = PairMatch(
                similarity = similarity,
                overlapWords = overlap,
                sourceTimeMs = left.startMs + leftStart * WORD_DURATION_MS,
                candidateTimeMs = right.startMs + rightStart * WORD_DURATION_MS,
                coverageMs = minOf(left.durationMs, right.durationMs)
            )
            if (best == null || match.similarity > best.similarity ||
                match.similarity == best.similarity && match.overlapWords > best.overlapWords
            ) {
                best = match
            }
        }
        return best
    }

    private fun fit(matches: List<PairMatch>): LinearFit {
        if (matches.size < 2) {
            val match = matches.single()
            return LinearFit(1.0, (match.candidateTimeMs - match.sourceTimeMs).toDouble())
        }
        val sourceMean = matches.map(PairMatch::sourceTimeMs).average()
        val candidateMean = matches.map(PairMatch::candidateTimeMs).average()
        val variance = matches.sumOf { match ->
            val delta = match.sourceTimeMs - sourceMean
            delta * delta
        }
        if (variance <= 0.0) return LinearFit(1.0, candidateMean - sourceMean)
        val covariance = matches.sumOf { match ->
            (match.sourceTimeMs - sourceMean) * (match.candidateTimeMs - candidateMean)
        }
        val ratio = covariance / variance
        return LinearFit(ratio, candidateMean - ratio * sourceMean)
    }

    private data class PairMatch(
        val similarity: Double,
        val overlapWords: Int,
        val sourceTimeMs: Long,
        val candidateTimeMs: Long,
        val coverageMs: Long
    )

    private data class LinearFit(val ratio: Double, val offset: Double)
}

/** Adds bounded audio evidence without weakening metadata/identifier hard conflicts. */
object AudioMatchRefiner {
    const val SCORE_VERSION = 4
    private const val STRONG_SIMILARITY = 0.88
    private const val STRONG_COVERAGE_MS = 18_000L
    private const val PARTIAL_RECORDING_CEILING = 0.90

    fun refine(metadata: MatchEvaluation, alignment: ChromaprintAlignment): MatchEvaluation {
        if (metadata.hasHardConflict || alignment.structuralJump) return metadata
        val strong = alignment.similarity >= STRONG_SIMILARITY && alignment.stable &&
            alignment.continuousCoverageMs >= STRONG_COVERAGE_MS
        if (!alignment.exactPcm && !strong) {
            val partial = minOf(
                PARTIAL_RECORDING_CEILING,
                metadata.sameRecordingProbability + alignment.similarity * 0.03
            )
            return metadata.copy(
                sameRecordingProbability = maxOf(metadata.sameRecordingProbability, partial),
                recordingConfidenceCeiling = maxOf(metadata.recordingConfidenceCeiling, partial),
                explanation = metadata.explanation + alignment.explanation("partial"),
                scoreVersion = SCORE_VERSION
            )
        }
        val refinedProbability = if (alignment.exactPcm) 1.0 else {
            val strength = alignment.similarity * alignment.inlierRatio
            maxOf(
                metadata.sameRecordingProbability,
                metadata.sameRecordingProbability +
                    (1.0 - metadata.sameRecordingProbability) * 0.72 * strength
            ).coerceIn(0.0, 1.0)
        }
        return metadata.copy(
            sameRecordingProbability = refinedProbability,
            relationship = if (refinedProbability >= 0.92) {
                RecordingRelationship.SAME_RECORDING
            } else {
                metadata.relationship
            },
            recordingConfidenceCeiling = maxOf(
                metadata.recordingConfidenceCeiling,
                refinedProbability
            ),
            identifierEvidence = metadata.identifierEvidence + if (alignment.exactPcm) {
                "pcm_hash"
            } else {
                "chromaprint_alignment_v1"
            },
            evidence = metadata.evidence + MatchEvidence.FINGERPRINT,
            explanation = metadata.explanation + alignment.explanation(
                if (alignment.exactPcm) "exact_pcm" else "strong"
            ),
            scoreVersion = SCORE_VERSION
        )
    }

    private fun ChromaprintAlignment.explanation(level: String): String =
        "audio:$level similarity=${"%.4f".format(java.util.Locale.ROOT, similarity)} " +
            "segments=$matchedSegmentCount inliers=${"%.4f".format(java.util.Locale.ROOT, inlierRatio)} " +
            "offsetMs=$offsetMs speed=${"%.5f".format(java.util.Locale.ROOT, speedRatio)} " +
            "coverageMs=$continuousCoverageMs"
}
