package app.yukine.streaming

enum class IdentityScoringMode {
    V4_ONLY,
    V5_SHADOW,
    V5_ON;

    companion object {
        @JvmStatic
        fun fromStoredValue(value: String?): IdentityScoringMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: V5_SHADOW
    }
}

data class RecordingMatchEvaluationDecision(
    val active: MatchEvaluation,
    val shadow: MatchEvaluation? = null
)

/** Pure version selector. Persistence owns the mode; the evaluator owns no mutable state. */
object RecordingMatchEvaluationPolicy {
    @JvmStatic
    fun evaluate(
        reference: StreamingTrackMatchPolicy.Reference,
        candidate: StreamingTrackMatchPolicy.Reference,
        mode: IdentityScoringMode,
        includeExplanation: Boolean = true,
        recallEvidence: MetadataRecallEvidence? = null
    ): RecordingMatchEvaluationDecision = evaluate(
        RecordingMatchFeatureExtractor.extract(reference),
        RecordingMatchFeatureExtractor.extract(candidate),
        mode,
        includeExplanation,
        recallEvidence
    )

    @JvmStatic
    fun evaluate(
        reference: RecordingMatchFeatures,
        candidate: RecordingMatchFeatures,
        mode: IdentityScoringMode,
        includeExplanation: Boolean = true,
        recallEvidence: MetadataRecallEvidence? = null
    ): RecordingMatchEvaluationDecision {
        val v4 = RecordingMatchEvaluatorV2.evaluate(
            reference,
            candidate,
            includeExplanation,
            recallEvidence
        )
        return when (mode) {
            IdentityScoringMode.V4_ONLY -> RecordingMatchEvaluationDecision(active = v4)
            IdentityScoringMode.V5_SHADOW -> RecordingMatchEvaluationDecision(
                active = v4,
                shadow = RecordingMatchEvaluatorV2.evaluateV5(
                    reference,
                    candidate,
                    includeExplanation,
                    recallEvidence
                )
            )
            IdentityScoringMode.V5_ON -> {
                val v5 = RecordingMatchEvaluatorV2.evaluateV5(
                    reference,
                    candidate,
                    includeExplanation,
                    recallEvidence
                )
                // Skip shadow V4 evaluation when V5 result is definitive (clear non-match
                // or hard conflict). Shadow is only used for audit logging.
                val shadow = if (v5.sameRecordingProbability < 0.50 || v5.hasHardConflict) {
                    null
                } else {
                    v4
                }
                RecordingMatchEvaluationDecision(active = v5, shadow = shadow)
            }
        }
    }
}
